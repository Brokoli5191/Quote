import { verifyAccess } from "./access";
import { dashboardPage } from "./dashboard";
import type { Env } from "./index";

interface ModerationInput {
  action: "approve" | "reject";
  quoteText: string;
  author: string;
  category: string;
  tags: string[];
  note?: string;
}

type ModerationValidation =
  | { ok: true; value: ModerationInput }
  | { ok: false; message: string };

function clean(value: unknown): string {
  return typeof value === "string" ? value.trim().replace(/\s+/g, " ") : "";
}

export function validateModeration(input: unknown): ModerationValidation {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    return { ok: false, message: "The request body must be a JSON object." };
  }
  const body = input as Record<string, unknown>;
  const action = body.action;
  if (action !== "approve" && action !== "reject") {
    return { ok: false, message: "action must be approve or reject." };
  }

  const quoteText = clean(body.quoteText);
  const author = clean(body.author);
  const category = clean(body.category);
  const note = body.note === undefined ? undefined : clean(body.note);
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
  if (!Array.isArray(body.tags) || body.tags.length > 8) {
    return { ok: false, message: "tags must contain at most 8 items." };
  }

  const tags: string[] = [];
  const seen = new Set<string>();
  for (const value of body.tags) {
    const tag = clean(value);
    if (tag.length < 1 || tag.length > 30) {
      return { ok: false, message: "Each tag must contain between 1 and 30 characters." };
    }
    const key = tag.toLocaleLowerCase("en-US");
    if (!seen.has(key)) {
      seen.add(key);
      tags.push(tag);
    }
  }
  return { ok: true, value: { action, quoteText, author, category, tags, note } };
}

function adminJson(body: unknown, status = 200): Response {
  return Response.json(body, {
    status,
    headers: { "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" }
  });
}

async function listSubmissions(url: URL, env: Env): Promise<Response> {
  const requestedStatus = url.searchParams.get("status") ?? "pending";
  const statuses = ["pending", "approved", "rejected", "duplicate"];
  if (!statuses.includes(requestedStatus)) return adminJson({ error: "Invalid status." }, 400);

  const [rows, counts] = await Promise.all([
    env.DB.prepare(
      `SELECT s.id,
        COALESCE(c.quote_text, s.quote_text) AS quoteText,
        COALESCE(c.author, s.author) AS author,
        COALESCE(c.category, s.category) AS category,
        COALESCE(c.tags_json, s.tags_json) AS tagsJson,
        s.status, s.app_version AS appVersion, s.submitted_at AS submittedAt,
        s.reviewed_at AS reviewedAt, s.reviewer_note AS reviewerNote,
        c.id AS communityQuoteId, c.active AS communityActive, c.revision AS communityRevision
       FROM submissions s LEFT JOIN community_quotes c ON c.submission_id = s.id
       WHERE s.status = ? ORDER BY s.submitted_at DESC LIMIT 100`
    ).bind(requestedStatus).all(),
    env.DB.prepare("SELECT status, COUNT(*) AS count FROM submissions GROUP BY status").all()
  ]);

  return adminJson({ submissions: rows.results, counts: counts.results });
}

async function updateCommunityQuote(
  request: Request,
  env: Env,
  id: string,
  reviewer: string
): Promise<Response> {
  let input: Record<string, unknown>;
  try {
    input = await request.json<Record<string, unknown>>();
  } catch {
    return adminJson({ error: "Request body contains invalid JSON." }, 400);
  }
  const action = input.action;
  if (action !== "update" && action !== "unpublish" && action !== "republish") {
    return adminJson({ error: "action must be update, unpublish, or republish." }, 400);
  }
  const existing = await env.DB.prepare(
    "SELECT id, active FROM community_quotes WHERE id = ? LIMIT 1"
  ).bind(id).first<{ id: string; active: number }>();
  if (!existing) return adminJson({ error: "Community quote not found." }, 404);

  const statements: D1PreparedStatement[] = [
    env.DB.prepare("UPDATE sync_state SET revision = revision + 1 WHERE id = 1")
  ];
  if (action === "update") {
    const validation = validateModeration({ ...input, action: "approve" });
    if (!validation.ok) return adminJson({ error: validation.message }, 400);
    const value = validation.value;
    statements.push(
      env.DB.prepare(
        `UPDATE community_quotes SET quote_text = ?, author = ?, category = ?, tags_json = ?,
          revision = (SELECT revision FROM sync_state WHERE id = 1), updated_at = unixepoch()
         WHERE id = ?`
      ).bind(value.quoteText, value.author, value.category, JSON.stringify(value.tags), id)
    );
  } else {
    statements.push(
      env.DB.prepare(
        `UPDATE community_quotes SET active = ?, revision = (SELECT revision FROM sync_state WHERE id = 1),
          updated_at = unixepoch() WHERE id = ?`
      ).bind(action === "republish" ? 1 : 0, id)
    );
  }
  statements.push(
    env.DB.prepare(
      "UPDATE submissions SET reviewer_note = ? WHERE id = (SELECT submission_id FROM community_quotes WHERE id = ?)"
    ).bind(`${action} by ${reviewer}`, id)
  );
  await env.DB.batch(statements);
  return adminJson({
    id,
    status: action === "unpublish" ? "unpublished" : action === "republish" ? "published" : "updated"
  });
}

async function moderate(request: Request, env: Env, id: string, reviewer: string): Promise<Response> {
  let input: unknown;
  try {
    input = await request.json();
  } catch {
    return adminJson({ error: "Request body contains invalid JSON." }, 400);
  }
  const validation = validateModeration(input);
  if (!validation.ok) return adminJson({ error: validation.message }, 400);

  const existing = await env.DB.prepare(
    "SELECT id, status FROM submissions WHERE id = ? LIMIT 1"
  ).bind(id).first<{ id: string; status: string }>();
  if (!existing) return adminJson({ error: "Submission not found." }, 404);
  const canApproveRejected = existing.status === "rejected" && validation.value.action === "approve";
  if (existing.status !== "pending" && !canApproveRejected) {
    return adminJson({ error: "Submission cannot be changed from its current state." }, 409);
  }

  const value = validation.value;
  const note = value.note ? `${value.note} [reviewed by ${reviewer}]` : `Reviewed by ${reviewer}`;
  if (value.action === "reject") {
    await env.DB.prepare(
      "UPDATE submissions SET status = 'rejected', reviewed_at = unixepoch(), reviewer_note = ? WHERE id = ? AND status = 'pending'"
    ).bind(note, id).run();
    return adminJson({ id, status: "rejected" });
  }

  const communityQuoteId = crypto.randomUUID();
  await env.DB.batch([
    env.DB.prepare("UPDATE sync_state SET revision = revision + 1 WHERE id = 1"),
    env.DB.prepare(
      `INSERT INTO community_quotes (
        id, submission_id, quote_text, author, category, tags_json, revision
      ) VALUES (?, ?, ?, ?, ?, ?, (SELECT revision FROM sync_state WHERE id = 1))`
    ).bind(
      communityQuoteId,
      id,
      value.quoteText,
      value.author,
      value.category,
      JSON.stringify(value.tags)
    ),
    env.DB.prepare(
      "UPDATE submissions SET status = 'approved', reviewed_at = unixepoch(), reviewer_note = ? WHERE id = ? AND status IN ('pending', 'rejected')"
    ).bind(note, id)
  ]);
  return adminJson({ id, status: "approved", communityQuoteId });
}

async function deleteRejectedSubmission(env: Env, id: string): Promise<Response> {
  const existing = await env.DB.prepare(
    "SELECT status FROM submissions WHERE id = ? LIMIT 1"
  ).bind(id).first<{ status: string }>();
  if (!existing) return adminJson({ error: "Submission not found." }, 404);
  if (existing.status !== "rejected") {
    return adminJson({ error: "Only rejected submissions can be deleted." }, 409);
  }
  await env.DB.prepare("DELETE FROM submissions WHERE id = ? AND status = 'rejected'").bind(id).run();
  return new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
}

export async function handleAdmin(request: Request, env: Env): Promise<Response> {
  if (!env.ACCESS_TEAM_DOMAIN || !env.ACCESS_AUD) {
    return new Response("Cloudflare Access is not configured for this dashboard.", { status: 503 });
  }
  const reviewer = await verifyAccess(request, env);
  if (!reviewer) return adminJson({ error: "Unauthorized." }, 401);

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
  return adminJson({ error: "Not found." }, 404);
}
