import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import worker, { runMaintenance, type Env } from "../src/index";
import { AUDIENCE, CERTS_URL, createSigner, TEAM_DOMAIN, type AccessSigner } from "./access-token";
import { createTestDatabase, type TestDatabase } from "./d1";

const BASE_URL = "https://quote.test";
const MODERATOR = "a.rather.long.moderator.address@moderation.example.com";
const INSTALLATION = "123e4567-e89b-42d3-a456-426614174000";

let signer: AccessSigner;
let database: TestDatabase;
let env: Env;

beforeAll(async () => {
  signer = await createSigner();
  vi.stubGlobal("fetch", async (input: RequestInfo | URL) => {
    if (String(input) === CERTS_URL) return Response.json({ keys: [signer.jwk] });
    return new Response("Unexpected request", { status: 500 });
  });
});

afterAll(() => {
  vi.unstubAllGlobals();
});

beforeEach(() => {
  database = createTestDatabase();
  env = { DB: database.db, RATE_LIMIT_SALT: "test-salt", ACCESS_TEAM_DOMAIN: TEAM_DOMAIN, ACCESS_AUD: AUDIENCE };
});

function call(request: Request, target: Env = env): Promise<Response> {
  return worker.fetch(request, target);
}

interface SubmitOptions {
  quoteText?: string;
  author?: string;
  installationId?: string;
  ip?: string;
}

async function submit(options: SubmitOptions = {}, target: Env = env) {
  const response = await call(new Request(`${BASE_URL}/api/submissions`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "CF-Connecting-IP": options.ip ?? "203.0.113.7" },
    body: JSON.stringify({
      quoteText: options.quoteText ?? "The obstacle is the way.",
      author: options.author ?? "Marcus Aurelius",
      category: "Stoicism",
      tags: ["wisdom"],
      installationId: options.installationId ?? INSTALLATION,
      appVersion: "2.1.0",
      language: "en"
    })
  }), target);
  return { response, body: await response.json() as Record<string, unknown> };
}

function withdraw(id: unknown, installationId = INSTALLATION): Promise<Response> {
  return call(new Request(`${BASE_URL}/api/submissions/${id}`, {
    method: "DELETE",
    headers: { "X-Installation-Id": installationId }
  }));
}

async function admin(method: string, path: string, body?: unknown) {
  const response = await call(new Request(`${BASE_URL}${path}`, {
    method,
    headers: {
      "Cf-Access-Jwt-Assertion": await signer.sign(MODERATOR),
      ...(body === undefined ? {} : { "Content-Type": "application/json" })
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  }));
  const text = await response.text();
  return { response, body: (text ? JSON.parse(text) : null) as Record<string, unknown> | null };
}

function approval(overrides: Record<string, unknown> = {}) {
  return {
    action: "approve",
    quoteText: "The obstacle is the way.",
    author: "Marcus Aurelius",
    category: "Stoicism",
    tags: ["wisdom"],
    ...overrides
  };
}

async function approvedQuote(quoteText: string, installationId = crypto.randomUUID()) {
  const { body } = await submit({ quoteText, installationId });
  const approved = await admin("PATCH", `/admin/api/submissions/${body.id}`, approval({ quoteText }));
  expect(approved.response.status).toBe(200);
  return { submissionId: body.id as string, communityQuoteId: approved.body!.communityQuoteId as string };
}

function row(sql: string, ...values: Array<string | number>) {
  return database.sqlite.prepare(sql).get(...values);
}

describe("submission rate limits", () => {
  it("keeps the slot of a withdrawn submission in the installation limit", async () => {
    for (let index = 0; index < 5; index++) {
      const { response, body } = await submit({ quoteText: `Withdrawn quote number ${index}` });
      expect(response.status).toBe(201);
      expect((await withdraw(body.id)).status).toBe(204);
    }
    const { response, body } = await submit({ quoteText: "One more quote" });
    expect(response.status).toBe(429);
    expect(body.code).toBe("rate_limited");
  });

  it("keeps the slot of a withdrawn submission in the IP limit", async () => {
    for (let index = 0; index < 20; index++) {
      const installationId = crypto.randomUUID();
      const { body } = await submit({ quoteText: `Quote from installation ${index}`, installationId });
      expect((await withdraw(body.id, installationId)).status).toBe(204);
    }
    const { response } = await submit({ quoteText: "Yet another quote", installationId: crypto.randomUUID() });
    expect(response.status).toBe(429);
  });

  it("still reports a withdrawn submission as not found", async () => {
    const { body } = await submit();
    await withdraw(body.id);
    const status = await call(new Request(`${BASE_URL}/api/submissions/${body.id}/status`, {
      headers: { "X-Installation-Id": INSTALLATION }
    }));
    expect(status.status).toBe(404);
    expect(row("SELECT COUNT(*) AS count FROM submissions")).toEqual(expect.objectContaining({ count: 0 }));
    expect(row("SELECT COUNT(*) AS count FROM deleted_submission_slots")).toEqual(expect.objectContaining({ count: 1 }));
  });

  it("lets the same text be submitted again after a withdrawal", async () => {
    const first = await submit();
    await withdraw(first.body.id);
    const again = await submit();
    expect(again.response.status).toBe(201);
    expect(again.body.id).not.toBe(first.body.id);
  });

  it("drops slots once they are outside the 24-hour window", async () => {
    database.sqlite.exec(
      `INSERT INTO deleted_submission_slots (installation_hash, ip_hash, submitted_at) VALUES
        ('old', 'old', unixepoch() - 90000), ('recent', 'recent', unixepoch() - 600)`
    );
    await runMaintenance(env);
    expect(database.sqlite.prepare("SELECT installation_hash AS hash FROM deleted_submission_slots").all())
      .toEqual([expect.objectContaining({ hash: "recent" })]);
  });
});

describe("submission validation", () => {
  it("counts characters the way the database does", async () => {
    const short = await submit({ quoteText: "a😀" });
    expect(short.response.status).toBe(400);
    expect(short.body).toEqual({ code: "validation", error: "quoteText must contain between 3 and 500 characters." });

    const emoji = await submit({ quoteText: "😀😀😀" });
    expect(emoji.response.status).toBe(201);
  });

  it("answers a CHECK constraint failure with a validation error", async () => {
    const logged = vi.spyOn(console, "error").mockImplementation(() => undefined);
    const failing = {
      ...database.db,
      prepare(sql: string) {
        const statement = database.db.prepare(sql);
        if (!sql.includes("INSERT INTO submissions")) return statement;
        return {
          bind: () => ({
            run: async () => { throw new Error("D1_ERROR: CHECK constraint failed: length(author) <= 100: SQLITE_CONSTRAINT"); }
          })
        };
      }
    } as unknown as D1Database;
    const { response, body } = await submit({}, { ...env, DB: failing });
    expect(response.status).toBe(400);
    expect(body.code).toBe("validation");
    expect(logged).toHaveBeenCalled();
    logged.mockRestore();
  });
});

describe("permanent deletion", () => {
  it("blocks the deleted text with any author and punctuation", async () => {
    const { body } = await submit({ quoteText: "Buy cheap pills now", author: "Spammer" });
    await admin("PATCH", `/admin/api/submissions/${body.id}`, {
      ...approval({ quoteText: "Buy cheap pills now", author: "Spammer" }),
      action: "reject"
    });
    const deleted = await admin("DELETE", `/admin/api/submissions/${body.id}`);
    expect(deleted.response.status).toBe(204);

    const sameInstallation = await submit({ quoteText: "Buy cheap pills now", author: "" });
    expect(sameInstallation.response.status).toBe(409);
    const otherInstallation = await submit({
      quoteText: "Buy cheap pills, now!",
      author: "Anon",
      installationId: crypto.randomUUID()
    });
    expect(otherInstallation.response.status).toBe(409);
    expect(otherInstallation.body.code).toBe("duplicate");
  });

  it("blocks both the submitted and the edited text of a deleted community quote", async () => {
    const { body } = await submit({ quoteText: "Original submitted wording" });
    const approved = await admin("PATCH", `/admin/api/submissions/${body.id}`, approval({ quoteText: "Edited published wording" }));
    const communityId = approved.body!.communityQuoteId;
    await admin("PATCH", `/admin/api/community/${communityId}`, { action: "unpublish" });
    const deleted = await admin("DELETE", `/admin/api/community/${communityId}`);
    expect(deleted.response.status).toBe(204);
    expect(row("SELECT COUNT(*) AS count FROM submissions")).toEqual(expect.objectContaining({ count: 0 }));

    for (const quoteText of ["Original submitted wording", "Edited published wording."]) {
      const again = await submit({ quoteText, author: "Someone else", installationId: crypto.randomUUID() });
      expect(again.response.status).toBe(409);
    }
  });

  it("keeps the rate-limit slot of a rejected submission it deletes", async () => {
    const { body } = await submit();
    await admin("PATCH", `/admin/api/submissions/${body.id}`, { ...approval(), action: "reject" });
    await admin("DELETE", `/admin/api/submissions/${body.id}`);
    expect(row("SELECT COUNT(*) AS count FROM deleted_submission_slots")).toEqual(expect.objectContaining({ count: 1 }));
  });
});

describe("moderation", () => {
  it("stores a long private note and the moderator in separate fields", async () => {
    const { body } = await submit();
    const note = "n".repeat(490);
    const approved = await admin("PATCH", `/admin/api/submissions/${body.id}`, approval({ note }));
    expect(approved.response.status).toBe(200);
    expect(row("SELECT reviewer_note AS note, reviewed_by AS reviewer FROM submissions WHERE id = ?", body.id as string))
      .toEqual(expect.objectContaining({ note, reviewer: MODERATOR }));

    const listed = await admin("GET", "/admin/api/submissions?status=approved");
    expect((listed.body!.submissions as Array<Record<string, unknown>>)[0]).toMatchObject({
      reviewerNote: note,
      reviewedBy: MODERATOR
    });
  });

  it("records who edited a community quote and replaces the private note", async () => {
    const { submissionId, communityQuoteId } = await approvedQuote("A quote to edit later.");
    const note = "e".repeat(500);
    const edited = await admin("PATCH", `/admin/api/community/${communityQuoteId}`, {
      ...approval({ quoteText: "A quote edited later.", note }),
      action: "update"
    });
    expect(edited.response.status).toBe(200);
    expect(row("SELECT reviewer_note AS note FROM submissions WHERE id = ?", submissionId))
      .toEqual(expect.objectContaining({ note }));
    expect(row("SELECT updated_by AS editor, quote_text AS text FROM community_quotes WHERE id = ?", communityQuoteId))
      .toEqual(expect.objectContaining({ editor: MODERATOR, text: "A quote edited later." }));
  });

  it("rejects a translation of two emoji as a validation error", async () => {
    const { body } = await submit();
    const approved = await admin("PATCH", `/admin/api/submissions/${body.id}`, approval({ translation: "😀😀" }));
    expect(approved.response.status).toBe(400);
    expect(approved.body!.code).toBe("validation");
  });

  it("reports a repeated approval as already approved", async () => {
    const { body } = await submit();
    await admin("PATCH", `/admin/api/submissions/${body.id}`, approval());
    const again = await admin("PATCH", `/admin/api/submissions/${body.id}`, approval());
    expect(again.response.status).toBe(409);
    expect(again.body).toMatchObject({ code: "already_reviewed", status: "approved" });
  });

  it("refuses to approve a submission whose text is already published", async () => {
    await approvedQuote("Be kind.");
    const { body } = await submit({ quoteText: "Be kind, always", installationId: crypto.randomUUID() });
    const approved = await admin("PATCH", `/admin/api/submissions/${body.id}`, approval({ quoteText: "Be kind!" }));
    expect(approved.response.status).toBe(409);
    expect(approved.body!.code).toBe("duplicate");
    expect(row("SELECT status FROM submissions WHERE id = ?", body.id as string))
      .toEqual(expect.objectContaining({ status: "pending" }));
  });
});

describe("community quote duplicates", () => {
  it("refuses to republish a quote whose text was published again meanwhile", async () => {
    const first = await approvedQuote("Be kind.");
    await admin("PATCH", `/admin/api/community/${first.communityQuoteId}`, { action: "unpublish" });
    await approvedQuote("Be kind!");
    const revision = row("SELECT revision FROM sync_state WHERE id = 1");

    const republished = await admin("PATCH", `/admin/api/community/${first.communityQuoteId}`, { action: "republish" });
    expect(republished.response.status).toBe(409);
    expect(republished.body!.code).toBe("duplicate");
    expect(row("SELECT active FROM community_quotes WHERE id = ?", first.communityQuoteId))
      .toEqual(expect.objectContaining({ active: 0 }));
    expect(row("SELECT revision FROM sync_state WHERE id = 1")).toEqual(revision);
  });

  it("republishes a quote without a published duplicate", async () => {
    const quote = await approvedQuote("Stay curious.");
    await admin("PATCH", `/admin/api/community/${quote.communityQuoteId}`, { action: "unpublish" });
    const republished = await admin("PATCH", `/admin/api/community/${quote.communityQuoteId}`, { action: "republish" });
    expect(republished.response.status).toBe(200);
    expect(row("SELECT active, updated_by AS editor FROM community_quotes WHERE id = ?", quote.communityQuoteId))
      .toEqual(expect.objectContaining({ active: 1, editor: MODERATOR }));
  });

  it("refuses to edit a published quote into the text of another published one", async () => {
    await approvedQuote("Alpha is the first letter.");
    const beta = await approvedQuote("Beta is the second letter.");
    const edited = await admin("PATCH", `/admin/api/community/${beta.communityQuoteId}`, {
      ...approval({ quoteText: "Alpha is the first letter!", note: "Should not be stored" }),
      action: "update"
    });
    expect(edited.response.status).toBe(409);
    expect(row("SELECT quote_text AS text FROM community_quotes WHERE id = ?", beta.communityQuoteId))
      .toEqual(expect.objectContaining({ text: "Beta is the second letter." }));
    expect(row("SELECT reviewer_note AS note FROM submissions WHERE id = ?", beta.submissionId))
      .toEqual(expect.objectContaining({ note: null }));
  });

  it("allows editing an unpublished quote, but not republishing it as a duplicate", async () => {
    await approvedQuote("Gamma rays are invisible.");
    const delta = await approvedQuote("Delta is a river mouth.");
    await admin("PATCH", `/admin/api/community/${delta.communityQuoteId}`, { action: "unpublish" });
    const edited = await admin("PATCH", `/admin/api/community/${delta.communityQuoteId}`, {
      ...approval({ quoteText: "Gamma rays are invisible" }),
      action: "update"
    });
    expect(edited.response.status).toBe(200);
    const republished = await admin("PATCH", `/admin/api/community/${delta.communityQuoteId}`, { action: "republish" });
    expect(republished.response.status).toBe(409);
  });
});

describe("routing", () => {
  it("answers HEAD like GET without a body", async () => {
    for (const path of ["/", "/health", "/privacy", "/privacy?lang=de", "/api/community/quotes"]) {
      const response = await call(new Request(`${BASE_URL}${path}`, { method: "HEAD" }));
      expect(response.status, path).toBe(200);
      expect(await response.text(), path).toBe("");
    }
    const health = await call(new Request(`${BASE_URL}/health`, { method: "HEAD" }));
    expect(health.headers.get("Content-Type")).toContain("application/json");
  });

  it("answers other methods on read-only pages with 405", async () => {
    for (const path of ["/", "/health", "/privacy", "/api/community/quotes"]) {
      const response = await call(new Request(`${BASE_URL}${path}`, { method: "POST" }));
      expect(response.status, path).toBe(405);
      expect(response.headers.get("Allow"), path).toBe("GET, HEAD");
    }
  });

  it("answers unsupported admin methods with 405 only after authentication", async () => {
    const unauthenticated = await call(new Request(`${BASE_URL}/admin/api/submissions`, { method: "POST" }));
    expect(unauthenticated.status).toBe(401);

    const list = await admin("POST", "/admin/api/submissions");
    expect(list.response.status).toBe(405);
    expect(list.response.headers.get("Allow")).toBe("GET, HEAD");
    const submission = await admin("PUT", `/admin/api/submissions/${crypto.randomUUID()}`);
    expect(submission.response.headers.get("Allow")).toBe("PATCH, DELETE");
    const head = await admin("HEAD", "/admin/api/submissions");
    expect(head.response.status).toBe(200);
    expect(head.body).toBeNull();
  });
});
