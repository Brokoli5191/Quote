import { verifyAccess } from "./access";
import { dashboardPage } from "./dashboard";
import type { Env } from "./index";
import { cleanText, parseLanguage, quoteComparisonKey, type QuoteLanguage } from "./validation";

interface ModerationInput {
  action: "approve" | "reject";
  quoteText: string;
  author: string;
  category: string;
  tags: string[];
  // Private moderator note.
  note?: string;
  // Note shown to the submitter in the app. undefined keeps the stored value, null clears it.
  reviewNote?: string | null;
  // undefined keeps the stored language.
  language?: QuoteLanguage;
  // Translation into the other language. undefined keeps the stored value, null clears it.
  translation?: string | null;
}

type ModerationValidation =
  | { ok: true; value: ModerationInput }
  | { ok: false; message: string };

const ADMIN_PAGE_SIZE = 100;
const UNIQUE_VIOLATION = "UNIQUE constraint failed";

function optionalText(value: unknown): string | null | undefined {
  if (value === undefined) return undefined;
  const text = cleanText(value);
  return text.length > 0 ? text : null;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

export function validateModeration(input: unknown): ModerationValidation {
  if (!isObject(input)) {
    return { ok: false, message: "The request body must be a JSON object." };
  }
  const body = input;
  const action = body.action;
  if (action !== "approve" && action !== "reject") {
    return { ok: false, message: "action must be approve or reject." };
  }

  const quoteText = cleanText(body.quoteText);
  const author = cleanText(body.author);
  const category = cleanText(body.category);
  const note = body.note === undefined ? undefined : cleanText(body.note);
  const reviewNote = optionalText(body.reviewNote);
  const translation = optionalText(body.translation);
  const language = parseLanguage(body.language);
  if (quoteText.length < 3 || quoteText.length > 500) {
    return { ok: false, message: "quoteText must contain between 3 and 500 characters." };
  }
  if (author.length > 100) return { ok: false, message: "author must not exceed 100 characters." };
  if (category.length < 1 || category.length > 50) {
    return { ok: false, message: "category must contain between 1 and 50 characters." };
  }
  if (note !== undefined && note.length > 500) {
    return { ok: false, message: "note must not exceed 500 characters." };
  }
  if (reviewNote && reviewNote.length > 500) {
    return { ok: false, message: "reviewNote must not exceed 500 characters." };
  }
  if (translation && (translation.length < 3 || translation.length > 500)) {
    return { ok: false, message: "translation must contain between 3 and 500 characters." };
  }
  if (!language.ok) return { ok: false, message: language.message };
  if (!Array.isArray(body.tags) || body.tags.length > 8) {
    return { ok: false, message: "tags must contain at most 8 items." };
  }

  const tags: string[] = [];
  const seen = new Set<string>();
  for (const value of body.tags) {
    const tag = cleanText(value);
    if (tag.length < 1 || tag.length > 30) {
      return { ok: false, message: "Each tag must contain between 1 and 30 characters." };
    }
    const key = tag.toLocaleLowerCase("en-US");
    if (!seen.has(key)) {
      seen.add(key);
      tags.push(tag);
    }
  }
  return {
    ok: true,
    value: {
      action,
      quoteText,
      author,
      category,
      tags,
      note,
      reviewNote,
      language: language.value,
      translation
    }
  };
}

function adminJson(body: unknown, status = 200): Response {
  return Response.json(body, {
    status,
    headers: { "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" }
  });
}

function adminError(status: number, code: string, error: string): Response {
  return adminJson({ error, code }, status);
}

async function readJsonObject(request: Request): Promise<Record<string, unknown> | null> {
  try {
    const input: unknown = await request.json();
    return isObject(input) ? input : null;
  } catch {
    return null;
  }
}

async function listSubmissions(url: URL, env: Env): Promise<Response> {
  const requestedStatus = url.searchParams.get("status") ?? "pending";
  const statuses = ["pending", "approved", "rejected", "duplicate"];
  if (!statuses.includes(requestedStatus)) return adminError(400, "invalid_request", "Invalid status.");

  // Keyset paging: "<submittedAt>.<id>" of the last row of the previous page.
  // The pending queue runs oldest first so new submissions cannot push older ones out of view.
  const cursorValue = url.searchParams.get("cursor");
  const cursor = cursorValue?.match(/^(\d+)\.([0-9a-f-]{36})$/i);
  if (cursorValue && !cursor) return adminError(400, "invalid_request", "Invalid cursor.");
  const ascending = requestedStatus === "pending";
  const order = ascending ? "ASC" : "DESC";
  const comparison = ascending ? ">" : "<";
  const cursorFilter = cursor
    ? `AND (s.submitted_at ${comparison} ? OR (s.submitted_at = ? AND s.id ${comparison} ?))`
    : "";
  const bindings: unknown[] = [requestedStatus];
  if (cursor) bindings.push(Number(cursor[1]), Number(cursor[1]), cursor[2]);

  const [rows, counts] = await Promise.all([
    env.DB.prepare(
      `SELECT s.id,
        COALESCE(c.quote_text, s.quote_text) AS quoteText,
        COALESCE(c.author, s.author) AS author,
        COALESCE(c.category, s.category) AS category,
        COALESCE(c.tags_json, s.tags_json) AS tagsJson,
        COALESCE(c.language, s.language) AS language,
        c.translation AS translation,
        s.status, s.app_version AS appVersion, s.submitted_at AS submittedAt,
        s.reviewed_at AS reviewedAt, s.reviewer_note AS reviewerNote, s.review_note AS reviewNote,
        c.id AS communityQuoteId, c.active AS communityActive, c.revision AS communityRevision
       FROM submissions s LEFT JOIN community_quotes c ON c.submission_id = s.id
       WHERE s.status = ? ${cursorFilter}
       ORDER BY s.submitted_at ${order}, s.id ${order} LIMIT ${ADMIN_PAGE_SIZE + 1}`
    ).bind(...bindings).all<{ id: string; submittedAt: number }>(),
    env.DB.prepare("SELECT status, COUNT(*) AS count FROM submissions GROUP BY status").all()
  ]);

  const submissions = rows.results.slice(0, ADMIN_PAGE_SIZE);
  const last = submissions[submissions.length - 1];
  const nextCursor = rows.results.length > ADMIN_PAGE_SIZE && last ? `${last.submittedAt}.${last.id}` : null;
  return adminJson({ submissions, counts: counts.results, nextCursor });
}

async function updateCommunityQuote(
  request: Request,
  env: Env,
  id: string,
  reviewer: string
): Promise<Response> {
  const input = await readJsonObject(request);
  if (!input) return adminError(400, "invalid_request", "The request body must be a JSON object.");
  const action = input.action;
  if (action !== "update" && action !== "unpublish" && action !== "republish") {
    return adminError(400, "invalid_request", "action must be update, unpublish, or republish.");
  }
  const existing = await env.DB.prepare(
    "SELECT id, submission_id AS submissionId, active FROM community_quotes WHERE id = ? LIMIT 1"
  ).bind(id).first<{ id: string; submissionId: string; active: number }>();
  if (!existing) return adminError(404, "not_found", "Community quote not found.");

  const statements: D1PreparedStatement[] = [
    env.DB.prepare("UPDATE sync_state SET revision = revision + 1 WHERE id = 1")
  ];
  if (action === "update") {
    const validation = validateModeration({ ...input, action: "approve" });
    if (!validation.ok) return adminError(400, "validation", validation.message);
    const value = validation.value;
    statements.push(
      env.DB.prepare(
        `UPDATE community_quotes SET quote_text = ?, author = ?, category = ?, tags_json = ?,
          normalized_text = ?, language = COALESCE(?, language),
          translation = CASE WHEN ? = 1 THEN ? ELSE translation END,
          revision = (SELECT revision FROM sync_state WHERE id = 1), updated_at = unixepoch()
         WHERE id = ?`
      ).bind(
        value.quoteText,
        value.author,
        value.category,
        JSON.stringify(value.tags),
        quoteComparisonKey(value.quoteText),
        value.language ?? null,
        value.translation === undefined ? 0 : 1,
        value.translation ?? null,
        id
      )
    );
    // Notes only change when the moderator actually wrote something; an edit without a
    // note keeps the original review history.
    if (value.note) {
      statements.push(
        env.DB.prepare("UPDATE submissions SET reviewer_note = ? WHERE id = ?")
          .bind(`${value.note} [edited by ${reviewer}]`, existing.submissionId)
      );
    }
    if (value.reviewNote !== undefined) {
      statements.push(
        env.DB.prepare("UPDATE submissions SET review_note = ? WHERE id = ?")
          .bind(value.reviewNote, existing.submissionId)
      );
    }
  } else {
    statements.push(
      env.DB.prepare(
        `UPDATE community_quotes SET active = ?, revision = (SELECT revision FROM sync_state WHERE id = 1),
          updated_at = unixepoch() WHERE id = ?`
      ).bind(action === "republish" ? 1 : 0, id)
    );
  }
  await env.DB.batch(statements);
  return adminJson({
    id,
    status: action === "unpublish" ? "unpublished" : action === "republish" ? "published" : "updated"
  });
}

async function moderate(request: Request, env: Env, id: string, reviewer: string): Promise<Response> {
  const input = await readJsonObject(request);
  if (!input) return adminError(400, "invalid_request", "The request body must be a JSON object.");
  const validation = validateModeration(input);
  if (!validation.ok) return adminError(400, "validation", validation.message);

  const existing = await env.DB.prepare(
    "SELECT id, status, language FROM submissions WHERE id = ? LIMIT 1"
  ).bind(id).first<{ id: string; status: string; language: QuoteLanguage }>();
  if (!existing) return adminError(404, "not_found", "Submission not found.");
  const canApproveRejected = existing.status === "rejected" && validation.value.action === "approve";
  if (existing.status !== "pending" && !canApproveRejected) {
    return adminError(409, "already_reviewed", "Submission cannot be changed from its current state.");
  }

  const value = validation.value;
  const note = value.note ? `${value.note} [reviewed by ${reviewer}]` : `Reviewed by ${reviewer}`;
  if (value.action === "reject") {
    const result = await env.DB.prepare(
      `UPDATE submissions SET status = 'rejected', reviewed_at = unixepoch(), reviewer_note = ?, review_note = ?
       WHERE id = ? AND status = 'pending'`
    ).bind(note, value.reviewNote ?? null, id).run();
    if (result.meta.changes === 0) {
      return adminError(409, "already_reviewed", "Submission was already reviewed.");
    }
    return adminJson({ id, status: "rejected" });
  }

  const comparisonKey = quoteComparisonKey(value.quoteText);
  const published = await env.DB.prepare(
    "SELECT id FROM community_quotes WHERE active = 1 AND normalized_text = ? LIMIT 1"
  ).bind(comparisonKey || null).first<{ id: string }>();
  if (published) {
    return adminError(409, "duplicate", "A published community quote with the same text already exists.");
  }

  // Every statement re-checks the submission state, so a second concurrent approval
  // changes nothing and is reported as a conflict.
  const approvable = "EXISTS (SELECT 1 FROM submissions WHERE id = ? AND status IN ('pending', 'rejected'))";
  const communityQuoteId = crypto.randomUUID();
  let results: D1Result[];
  try {
    results = await env.DB.batch([
      env.DB.prepare(`UPDATE sync_state SET revision = revision + 1 WHERE id = 1 AND ${approvable}`).bind(id),
      env.DB.prepare(
        `INSERT INTO community_quotes (
          id, submission_id, quote_text, author, category, tags_json, language, translation,
          normalized_text, revision
        )
        SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, (SELECT revision FROM sync_state WHERE id = 1)
        WHERE ${approvable}`
      ).bind(
        communityQuoteId,
        id,
        value.quoteText,
        value.author,
        value.category,
        JSON.stringify(value.tags),
        value.language ?? existing.language,
        value.translation ?? null,
        comparisonKey,
        id
      ),
      env.DB.prepare(
        `UPDATE submissions SET status = 'approved', reviewed_at = unixepoch(), reviewer_note = ?, review_note = ?
         WHERE id = ? AND status IN ('pending', 'rejected')`
      ).bind(note, value.reviewNote ?? null, id)
    ]);
  } catch (error) {
    if (String(error).includes(UNIQUE_VIOLATION)) {
      return adminError(409, "already_reviewed", "Submission was already approved.");
    }
    throw error;
  }
  if (results[1].meta.changes === 0) {
    return adminError(409, "already_reviewed", "Submission was already approved.");
  }
  return adminJson({ id, status: "approved", communityQuoteId });
}

// Permanent deletes keep the content hash in blocked_content_hashes, so removing a row does
// not reopen the door for the same text.
function blockContentHash(env: Env, submissionId: string): D1PreparedStatement {
  return env.DB.prepare(
    "INSERT OR IGNORE INTO blocked_content_hashes (content_hash) SELECT content_hash FROM submissions WHERE id = ?"
  ).bind(submissionId);
}

async function deleteRejectedSubmission(env: Env, id: string): Promise<Response> {
  const existing = await env.DB.prepare(
    "SELECT status FROM submissions WHERE id = ? LIMIT 1"
  ).bind(id).first<{ status: string }>();
  if (!existing) return adminError(404, "not_found", "Submission not found.");
  if (existing.status !== "rejected") {
    return adminError(409, "conflict", "Only rejected submissions can be deleted.");
  }
  const [, deleted] = await env.DB.batch([
    blockContentHash(env, id),
    env.DB.prepare("DELETE FROM submissions WHERE id = ? AND status = 'rejected'").bind(id)
  ]);
  if (deleted.meta.changes === 0) {
    return adminError(409, "conflict", "Only rejected submissions can be deleted.");
  }
  return new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
}

async function deleteUnpublishedCommunityQuote(env: Env, id: string): Promise<Response> {
  const existing = await env.DB.prepare(
    "SELECT id, submission_id AS submissionId, active FROM community_quotes WHERE id = ? LIMIT 1"
  ).bind(id).first<{ id: string; submissionId: string; active: number }>();
  if (!existing) return adminError(404, "not_found", "Community quote not found.");
  if (existing.active !== 0) {
    return adminError(409, "conflict", "Only unpublished community quotes can be deleted.");
  }

  await env.DB.batch([
    env.DB.prepare("UPDATE sync_state SET revision = revision + 1 WHERE id = 1"),
    env.DB.prepare(
      "INSERT INTO community_quote_deletions (id, revision) VALUES (?, (SELECT revision FROM sync_state WHERE id = 1))"
    ).bind(id),
    env.DB.prepare("DELETE FROM community_quotes WHERE id = ? AND active = 0").bind(id),
    blockContentHash(env, existing.submissionId),
    env.DB.prepare("DELETE FROM submissions WHERE id = ?").bind(existing.submissionId)
  ]);
  return new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
}

export async function handleAdmin(request: Request, env: Env): Promise<Response> {
  if (!env.ACCESS_TEAM_DOMAIN || !env.ACCESS_AUD) {
    return new Response("Cloudflare Access is not configured for this dashboard.", { status: 503 });
  }
  const reviewer = await verifyAccess(request, env);
  if (!reviewer) return adminError(401, "unauthorized", "Unauthorized.");

  const url = new URL(request.url);
  if ((url.pathname === "/admin" || url.pathname === "/admin/") && request.method === "GET") {
    return dashboardPage();
  }
  if (url.pathname === "/admin/api/submissions" && request.method === "GET") {
    return listSubmissions(url, env);
  }
  const match = url.pathname.match(/^\/admin\/api\/submissions\/([0-9a-f-]+)$/i);
  if (match && request.method === "PATCH") {
    return moderate(request, env, match[1], reviewer);
  }
  if (match && request.method === "DELETE") {
    return deleteRejectedSubmission(env, match[1]);
  }
  const communityMatch = url.pathname.match(/^\/admin\/api\/community\/([0-9a-f-]+)$/i);
  if (communityMatch && request.method === "PATCH") {
    return updateCommunityQuote(request, env, communityMatch[1], reviewer);
  }
  if (communityMatch && request.method === "DELETE") {
    return deleteUnpublishedCommunityQuote(env, communityMatch[1]);
  }
  return adminError(404, "not_found", "Not found.");
}
