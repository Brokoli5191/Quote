import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Env } from "../src/index";
import { AUDIENCE, CERTS_URL, createSigner, TEAM_DOMAIN, type AccessSigner } from "./access-token";

const env = { ACCESS_TEAM_DOMAIN: TEAM_DOMAIN, ACCESS_AUD: AUDIENCE } as Env;
let signer: AccessSigner;
let certsAvailable: boolean;
let certsRequests: number;

// access.ts caches the key set per isolate, so every test loads a fresh copy of the module.
async function loadAccess() {
  vi.resetModules();
  return import("../src/access");
}

function requestWith(token: string): Request {
  return new Request("https://quote.test/admin/api/submissions", { headers: { "Cf-Access-Jwt-Assertion": token } });
}

beforeEach(async () => {
  signer ??= await createSigner();
  certsAvailable = true;
  certsRequests = 0;
  vi.spyOn(console, "error").mockImplementation(() => undefined);
  vi.spyOn(console, "warn").mockImplementation(() => undefined);
  vi.stubGlobal("fetch", async (input: RequestInfo | URL) => {
    expect(String(input)).toBe(CERTS_URL);
    certsRequests++;
    return certsAvailable ? Response.json({ keys: [signer.jwk] }) : new Response("Bad gateway", { status: 502 });
  });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("verifyAccess", () => {
  it("verifies a signed token", async () => {
    const { verifyAccess } = await loadAccess();
    expect(await verifyAccess(requestWith(await signer.sign("mod@example.com")), env))
      .toEqual({ status: "verified", email: "mod@example.com" });
  });

  it("denies a token with a broken signature", async () => {
    const { verifyAccess } = await loadAccess();
    const token = await signer.sign("mod@example.com");
    const tampered = `${token.slice(0, token.lastIndexOf(".") + 1)}AAAA`;
    expect(await verifyAccess(requestWith(tampered), env)).toEqual({ status: "denied" });
  });

  it("reports unavailable keys instead of denying the login", async () => {
    certsAvailable = false;
    const { verifyAccess } = await loadAccess();
    expect(await verifyAccess(requestWith(await signer.sign("mod@example.com")), env))
      .toEqual({ status: "unavailable" });
  });

  it("keeps using the previous key set when a refresh fails", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    const { verifyAccess } = await loadAccess();
    const token = await signer.sign("mod@example.com");
    expect((await verifyAccess(requestWith(token), env)).status).toBe("verified");

    certsAvailable = false;
    vi.setSystemTime(Date.now() + 2 * 60 * 60 * 1000);
    const refreshed = await signer.sign("mod@example.com");
    expect((await verifyAccess(requestWith(refreshed), env)).status).toBe("verified");
    expect(certsRequests).toBe(2);
    // The failed refresh is retried after a minute, not on every request.
    expect((await verifyAccess(requestWith(refreshed), env)).status).toBe("verified");
    expect(certsRequests).toBe(2);
  });
});

describe("handleAdmin", () => {
  it("answers 503 when the Access keys cannot be loaded", async () => {
    certsAvailable = false;
    vi.resetModules();
    const { handleAdmin } = await import("../src/admin");
    const response = await handleAdmin(requestWith(await signer.sign("mod@example.com")), env);
    expect(response.status).toBe(503);
    expect(await response.json()).toMatchObject({ code: "access_unavailable" });
  });

  it("answers 401 without a token", async () => {
    vi.resetModules();
    const { handleAdmin } = await import("../src/admin");
    const response = await handleAdmin(new Request("https://quote.test/admin"), env);
    expect(response.status).toBe(401);
  });
});
