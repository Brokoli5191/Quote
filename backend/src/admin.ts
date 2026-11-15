import { verifyAccess } from "./access";
import { dashboardPage } from "./dashboard";
import type { Env } from "./index";
import { keepRateLimitSlots } from "./limits";
import {
  blockedTextHash,
  characterCount,
  cleanText,
  isCheckViolation,
  isReadRequest,
  isUniqueViolation,
  parseLanguage,
  quoteComparisonKey,
  type QuoteLanguage
} from "./validation";

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
const DUPLICATE_MESSAGE = "A published community quote with the same text already exists.";

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
  const quoteLength = characterCount(quoteText);
  if (quoteLength < 3 || quoteLength > 500) {
    return { ok: false, message: "quoteText must contain between 3 and 500 characters." };
  }
  if (characterCount(author) > 100) return { ok: false, message: "author must not exceed 100 characters." };
  const categoryLength = characterCount(category);
  if (categoryLength < 1 || categoryLength > 50) {
    return { ok: false, message: "category must contain between 1 and 50 characters." };
  }
  if (note !== undefined && characterCount(note) > 500) {
    return { ok: false, message: "note must not exceed 500 characters." };
  }
  if (reviewNote && characterCount(reviewNote) > 500) {
    return { ok: false, message: "reviewNote must not exceed 500 characters." };
  }
  const translationLength = translation ? characterCount(translation) : 0;
  if (translation && (translationLength < 3 || translationLength > 500)) {
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
    if (tag.length < 1 || characterCount(tag) > 30) {
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

function adminError(status: number, code: string, error: string, details: Record<string, unknown> = {}): Response {
  return adminJson({ ...details, error, code }, status);
}

function methodNotAllowed(allow: string): Response {
  const response = adminError(405, "method_not_allowed", "Method not allowed.");
  response.headers.set("Allow", allow);
  return response;
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
        s.reviewed_at AS reviewedAt, s.reviewed_by AS reviewedBy,
        s.reviewer_note AS reviewerNote, s.review_note AS reviewNote,
        c.id AS communityQuoteId, c.active AS communityActive, c.revision AS communityRevision,
        c.updated_at AS communityUpdatedAt, c.updated_by AS communityUpdatedBy
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

// True for a published community quote other than the one with the given id that has the
// given comparison key. A null key (text without letters or digits) never matches.
const OTHER_PUBLISHED_WITH_KEY =
  "EXISTS (SELECT 1 FROM community_quotes WHERE active = 1 AND normalized_text = ? AND id <> ?)";

interface Assignment {
  sql: string;
  bindings: unknown[];
}

function updateStatement(
  env: Env,
  table: "community_quotes" | "submissions",
  assignments: Assignment[],
  id: string,
  guard: string,
  guardBindings: unknown[]
): D1PreparedStatement {
  return env.DB.prepare(
    `UPDATE ${table} SET ${assignments.map(assignment => assignment.sql).join(", ")} WHERE id = ? AND ${guard}`
  ).bind(...assignments.flatMap(assignment => assignment.bindings), id, ...guardBindings);
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
    "SELECT id, submission_id AS submissionId FROM community_quotes WHERE id = ? LIMIT 1"
  ).bind(id).first<{ id: string; submissionId: string }>();
  if (!existing) return adminError(404, "not_found", "Community quote not found.");

  // Two published quotes must never share a comparison key. The condition is part of every
  // statement of the batch, so a concurrent approval or republish cannot slip in between a
  // check and the write. It reads the same before and after the community quote changes.
  let guard = "1";
  let guardBindings: unknown[] = [];
  let quoteChanges: Assignment[] = [{ sql: "active = ?", bindings: [action === "republish" ? 1 : 0] }];
  const submissionChanges: Assignment[] = [];
  if (action === "update") {
    const validation = validateModeration({ ...input, action: "approve" });
    if (!validation.ok) return adminError(400, "validation", validation.message);
    const value = validation.value;
    const comparisonKey = quoteComparisonKey(value.quoteText);
    guard = `(NOT EXISTS (SELECT 1 FROM community_quotes WHERE id = ? AND active = 1)
      OR NOT ${OTHER_PUBLISHED_WITH_KEY})`;
    guardBindings = [id, comparisonKey || null, id];
    quoteChanges = [
      { sql: "quote_text = ?", bindings: [value.quoteText] },
      { sql: "author = ?", bindings: [value.author] },
      { sql: "category = ?", bindings: [value.category] },
      { sql: "tags_json = ?", bindings: [JSON.stringify(value.tags)] },
      { sql: "normalized_text = ?", bindings: [comparisonKey] },
      { sql: "language = COALESCE(?, language)", bindings: [value.language ?? null] },
      {
        sql: "translation = CASE WHEN ? = 1 THEN ? ELSE translation END",
        bindings: [value.translation === undefined ? 0 : 1, value.translation ?? null]
      }
    ];
    // The private note only changes when the moderator wrote a new one.
    if (value.note) submissionChanges.push({ sql: "reviewer_note = ?", bindings: [value.note] });
    if (value.reviewNote !== undefined) {
      submissionChanges.push({ sql: "review_note = ?", bindings: [value.reviewNote] });
    }
  } else if (action === "republish") {
    guard = `NOT EXISTS (SELECT 1 FROM community_quotes self JOIN community_quotes other
      ON other.normalized_text = self.normalized_text AND other.id <> self.id
      WHERE self.id = ? AND self.normalized_text <> '' AND other.active = 1)`;
    guardBindings = [id];
  }
  quoteChanges.push(
    { sql: "updated_by = ?", bindings: [reviewer] },
    { sql: "revision = (SELECT revision FROM sync_state WHERE id = 1), updated_at = unixepoch()", bindings: [] }
  );

  const statements: D1PreparedStatement[] = [
    env.DB.prepare(`UPDATE sync_state SET revision = revision + 1 WHERE id = 1 AND ${guard}`).bind(...guardBindings),
    updateStatement(env, "community_quotes", quoteChanges, id, guard, guardBindings)
  ];
  if (submissionChanges.length > 0) {
    statements.push(updateStatement(env, "submissions", submissionChanges, existing.submissionId, guard, guardBindings));
  }
  const results = await env.DB.batch(statements);
  if (results[1].meta.changes === 0) {
    const stillExists = await env.DB.prepare("SELECT 1 AS found FROM community_quotes WHERE id = ?")
      .bind(id).first<{ found: number }>();
    if (!stillExists) return adminError(404, "not_found", "Community quote not found.");
    return adminError(409, "duplicate", DUPLICATE_MESSAGE);
  }
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
    // The status lets the dashboard recognise a repeated approval as already done.
    return adminError(409, "already_reviewed", "Submission cannot be changed from its current state.", {
      status: existing.status
    });
  }

  const value = validation.value;
  const note = value.note || null;
  if (value.action === "reject") {
    const result = await env.DB.prepare(
      `UPDATE submissions SET status = 'rejected', reviewed_at = unixepoch(), reviewed_by = ?,
        reviewer_note = ?, review_note = ?
       WHERE id = ? AND status = 'pending'`
    ).bind(reviewer, note, value.reviewNote ?? null, id).run();
    if (result.meta.changes === 0) {
      return adminError(409, "already_reviewed", "Submission was already reviewed.");
    }
    return adminJson({ id, status: "rejected" });
  }

  // The state and duplicate checks are part of the batch, so a second concurrent approval,
  // or an approval or republish of the same text, changes nothing and is reported below.
  const comparisonKey = quoteComparisonKey(value.quoteText);
  const approvable = `EXISTS (SELECT 1 FROM submissions WHERE id = ? AND status IN ('pending', 'rejected'))
    AND NOT EXISTS (SELECT 1 FROM community_quotes WHERE active = 1 AND normalized_text = ?)`;
  const approvableBindings = [id, comparisonKey || null];
  const communityQuoteId = crypto.randomUUID();
  let results: D1Result[];
  try {
    results = await env.DB.batch([
      env.DB.prepare(`UPDATE sync_state SET revision = revision + 1 WHERE id = 1 AND ${approvable}`)
        .bind(...approvableBindings),
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
        ...approvableBindings
      ),
      env.DB.prepare(
        `UPDATE submissions SET status = 'approved', reviewed_at = unixepoch(), reviewed_by = ?,
          reviewer_note = ?, review_note = ?
         WHERE id = ? AND status IN ('pending', 'rejected')
           AND EXISTS (SELECT 1 FROM community_quotes WHERE id = ? AND submission_id = ?)`
      ).bind(reviewer, note, value.reviewNote ?? null, id, communityQuoteId, id)
    ]);
  } catch (error) {
    if (isUniqueViolation(error)) {
      return adminError(409, "already_reviewed", "Submission was already approved.", { status: "approved" });
    }
    throw error;
  }
  if (results[1].meta.changes === 0) return approvalConflict(env, id, comparisonKey);
  return adminJson({ id, status: "approved", communityQuoteId });
}

async function approvalConflict(env: Env, id: string, comparisonKey: string): Promise<Response> {
  const state = await env.DB.prepare(
    `SELECT (SELECT status FROM submissions WHERE id = ?) AS status,
      EXISTS (SELECT 1 FROM community_quotes WHERE active = 1 AND normalized_text = ?) AS duplicate`
  ).bind(id, comparisonKey || null).first<{ status: string | null; duplicate: number }>();
  if (!state?.status) return adminError(404, "not_found", "Submission not found.");
  // A concurrent approval of this submission also published its text, so the state decides first.
  if (state.status === "approved") {
    return adminError(409, "already_reviewed", "Submission was already approved.", { status: "approved" });
  }
  if (state.duplicate) return adminError(409, "duplicate", DUPLICATE_MESSAGE);
  return adminError(409, "already_reviewed", "Submission was already reviewed.", { status: state.status });
}

// Permanent deletes keep the content hash and a hash of the text's comparison key, so
// removing a row does not reopen the door for the same text, with any author. The rate-limit
// slot is kept as well. Every statement only acts while `condition` still holds.
async function blockAndKeepSlot(
  env: Env,
  submissionId: string,
  texts: Array<string | null>,
  condition: string,
  conditionBindings: unknown[]
): Promise<D1PreparedStatement[]> {
  const textHashes = new Set<string>();
  for (const text of texts) {
    const textHash = text ? await blockedTextHash(text) : null;
    if (textHash) textHashes.add(textHash);
  }
  return [
    env.DB.prepare(
      `INSERT OR IGNORE INTO blocked_content_hashes (content_hash)
       SELECT content_hash FROM submissions WHERE id = ? AND ${condition}`
    ).bind(submissionId, ...conditionBindings),
    ...[...textHashes].map(textHash =>
      env.DB.prepare(`INSERT OR IGNORE INTO blocked_text_hashes (text_hash) SELECT ? WHERE ${condition}`)
        .bind(textHash, ...conditionBindings)
    ),
    keepRateLimitSlots(env.DB, `id = ? AND ${condition}`, submissionId, ...conditionBindings)
  ];
}

async function deleteRejectedSubmission(env: Env, id: string): Promise<Response> {
  const existing = await env.DB.prepare(
    "SELECT status, quote_text AS quoteText FROM submissions WHERE id = ? LIMIT 1"
  ).bind(id).first<{ status: string; quoteText: string }>();
  if (!existing) return adminError(404, "not_found", "Submission not found.");
  if (existing.status !== "rejected") {
    return adminError(409, "conflict", "Only rejected submissions can be deleted.");
  }
  const rejected = "EXISTS (SELECT 1 FROM submissions WHERE id = ? AND status = 'rejected')";
  const results = await env.DB.batch([
    ...await blockAndKeepSlot(env, id, [existing.quoteText], rejected, [id]),
    env.DB.prepare("DELETE FROM submissions WHERE id = ? AND status = 'rejected'").bind(id)
  ]);
  if (results[results.length - 1].meta.changes === 0) {
    return adminError(409, "conflict", "Only rejected submissions can be deleted.");
  }
  return new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
}

async function deleteUnpublishedCommunityQuote(env: Env, id: string): Promise<Response> {
  const existing = await env.DB.prepare(
    `SELECT c.submission_id AS submissionId, c.active, c.quote_text AS quoteText, s.quote_text AS submittedText
     FROM community_quotes c LEFT JOIN submissions s ON s.id = c.submission_id WHERE c.id = ? LIMIT 1`
  ).bind(id).first<{ submissionId: string; active: number; quoteText: string; submittedText: string | null }>();
  if (!existing) return adminError(404, "not_found", "Community quote not found.");
  if (existing.active !== 0) {
    return adminError(409, "conflict", "Only unpublished community quotes can be deleted.");
  }

  // Nothing happens if the quote was republished in the meantime.
  const unpublished = "EXISTS (SELECT 1 FROM community_quotes WHERE id = ? AND active = 0)";
  const results = await env.DB.batch([
    env.DB.prepare(`UPDATE sync_state SET revision = revision + 1 WHERE id = 1 AND ${unpublished}`).bind(id),
    env.DB.prepare(
      `INSERT INTO community_quote_deletions (id, revision)
       SELECT ?, (SELECT revision FROM sync_state WHERE id = 1) WHERE ${unpublished}`
    ).bind(id, id),
    ...await blockAndKeepSlot(
      env,
      existing.submissionId,
      [existing.quoteText, existing.submittedText],
      unpublished,
      [id]
    ),
    env.DB.prepare("DELETE FROM community_quotes WHERE id = ? AND active = 0").bind(id),
    env.DB.prepare(
      "DELETE FROM submissions WHERE id = ? AND NOT EXISTS (SELECT 1 FROM community_quotes WHERE submission_id = ?)"
    ).bind(existing.submissionId, existing.submissionId)
  ]);
  if (results[results.length - 2].meta.changes === 0) {
    return adminError(409, "conflict", "Only unpublished community quotes can be deleted.");
  }
  return new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
}

async function routeAdmin(request: Request, env: Env, reviewer: string): Promise<Response> {
  const url = new URL(request.url);
  if (url.pathname === "/admin" || url.pathname === "/admin/") {
    return isReadRequest(request) ? dashboardPage() : methodNotAllowed("GET, HEAD");
  }
  if (url.pathname === "/admin/api/submissions") {
    return isReadRequest(request) ? listSubmissions(url, env) : methodNotAllowed("GET, HEAD");
  }
  const match = url.pathname.match(/^\/admin\/api\/submissions\/([0-9a-f-]+)$/i);
  if (match) {
    if (request.method === "PATCH") return moderate(request, env, match[1], reviewer);
    if (request.method === "DELETE") return deleteRejectedSubmission(env, match[1]);
    return methodNotAllowed("PATCH, DELETE");
  }
  const communityMatch = url.pathname.match(/^\/admin\/api\/community\/([0-9a-f-]+)$/i);
  if (communityMatch) {
    if (request.method === "PATCH") return updateCommunityQuote(request, env, communityMatch[1], reviewer);
    if (request.method === "DELETE") return deleteUnpublishedCommunityQuote(env, communityMatch[1]);
    return methodNotAllowed("PATCH, DELETE");
  }
  return adminError(404, "not_found", "Not found.");
}

export async function handleAdmin(request: Request, env: Env): Promise<Response> {
  if (!env.ACCESS_TEAM_DOMAIN || !env.ACCESS_AUD) {
    return new Response("Cloudflare Access is not configured for this dashboard.", { status: 503 });
  }
  const access = await verifyAccess(request, env);
  if (access.status === "unavailable") {
    return adminError(
      503,
      "access_unavailable",
      "Cloudflare Access keys could not be loaded, so the login cannot be checked. Please try again in a minute."
    );
  }
  if (access.status !== "verified") return adminError(401, "unauthorized", "Unauthorized.");

  try {
    return await routeAdmin(request, env, access.email);
  } catch (error) {
    // validateModeration mirrors the schema's CHECK constraints; should they ever disagree,
    // the moderator gets a validation error instead of a generic server error.
    if (isCheckViolation(error)) {
      console.error("Admin write violated a CHECK constraint", error);
      return adminError(400, "validation", "A field is outside the allowed length.");
    }
    throw error;
  }
}
