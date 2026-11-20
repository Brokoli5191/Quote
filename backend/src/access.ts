import type { Env } from "./index";

interface AccessHeader {
  alg?: string;
  kid?: string;
}

interface AccessPayload {
  aud?: string | string[];
  email?: string;
  exp?: number;
  iss?: string;
}

interface JwksResponse {
  keys: AccessJwk[];
}

type AccessJwk = JsonWebKey & { kid?: string };

export type AccessResult =
  | { status: "verified"; email: string }
  | { status: "denied" }
  // The signing keys could not be loaded, so the token could not be checked at all.
  | { status: "unavailable" };

class AccessKeysUnavailableError extends Error {}

const DENIED: AccessResult = { status: "denied" };

let cachedKeys: { expiresAt: number; fetchedAt: number; keys: AccessJwk[] } | undefined;
// When fetching the key set last failed. While the certs endpoint is down, it is asked at most
// once a minute, not once per admin request.
let lastFailedFetchAt = Number.NEGATIVE_INFINITY;

// Minimum time between forced key refreshes, so tokens with made-up key IDs cannot
// make the Worker fetch the key set on every request.
const FORCED_REFRESH_INTERVAL_MS = 60 * 1000;

function decodePart<T>(value: string): T {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  return JSON.parse(atob(padded)) as T;
}

function decodeBytes(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  return Uint8Array.from(atob(padded), character => character.charCodeAt(0));
}

async function fetchKeys(teamDomain: string): Promise<AccessJwk[]> {
  const response = await fetch(`https://${teamDomain}/cdn-cgi/access/certs`);
  if (!response.ok) throw new Error(`Access certs endpoint answered ${response.status}`);
  const body = await response.json<JwksResponse>();
  if (!Array.isArray(body?.keys)) throw new Error("Access certs response has no key list");
  return body.keys;
}

async function getKeys(teamDomain: string, forceRefresh = false): Promise<AccessJwk[]> {
  const now = Date.now();
  if (cachedKeys && cachedKeys.expiresAt > now) {
    if (!forceRefresh || now - cachedKeys.fetchedAt < FORCED_REFRESH_INTERVAL_MS) return cachedKeys.keys;
  }

  if (now - lastFailedFetchAt < FORCED_REFRESH_INTERVAL_MS) {
    if (cachedKeys && !forceRefresh) return cachedKeys.keys;
    throw new AccessKeysUnavailableError("Cloudflare Access keys could not be loaded less than a minute ago");
  }

  try {
    const keys = await fetchKeys(teamDomain);
    cachedKeys = { keys, fetchedAt: now, expiresAt: now + 60 * 60 * 1000 };
    return keys;
  } catch (error) {
    lastFailedFetchAt = now;
    // An expired key set still verifies every token signed with a key that has not rotated,
    // so a failed hourly refresh keeps it and tries again a minute later. A forced refresh
    // is only needed for an unknown key, which the old set cannot verify.
    if (cachedKeys && !forceRefresh) {
      console.warn("Using the previous Cloudflare Access keys after a failed refresh", error);
      cachedKeys = { ...cachedKeys, expiresAt: now + FORCED_REFRESH_INTERVAL_MS };
      return cachedKeys.keys;
    }
    throw new AccessKeysUnavailableError(`Could not load Cloudflare Access keys: ${String(error)}`);
  }
}

// Cloudflare Access rotates its signing keys; a token signed with a key that is not in the
// cached set triggers one refresh of the key set before it is rejected.
async function findKey(teamDomain: string, kid: string): Promise<AccessJwk | undefined> {
  const cached = (await getKeys(teamDomain)).find(key => key.kid === kid);
  if (cached) return cached;
  return (await getKeys(teamDomain, true)).find(key => key.kid === kid);
}

export async function verifyAccess(request: Request, env: Env): Promise<AccessResult> {
  if (!env.ACCESS_TEAM_DOMAIN || !env.ACCESS_AUD) return DENIED;

  const token = request.headers.get("Cf-Access-Jwt-Assertion");
  if (!token) return DENIED;
  const parts = token.split(".");
  if (parts.length !== 3) return DENIED;

  try {
    const header = decodePart<AccessHeader>(parts[0]);
    const payload = decodePart<AccessPayload>(parts[1]);
    const issuer = `https://${env.ACCESS_TEAM_DOMAIN}`;
    const audiences = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
    if (
      header.alg !== "RS256" ||
      !header.kid ||
      !payload.email ||
      !payload.exp ||
      payload.exp <= Math.floor(Date.now() / 1000) ||
      payload.iss !== issuer ||
      !audiences.includes(env.ACCESS_AUD)
    ) {
      return DENIED;
    }

    const jwk = await findKey(env.ACCESS_TEAM_DOMAIN, header.kid);
    if (!jwk) return DENIED;
    const key = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"]
    );
    const valid = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      key,
      decodeBytes(parts[2]),
      new TextEncoder().encode(`${parts[0]}.${parts[1]}`)
    );
    return valid ? { status: "verified", email: payload.email } : DENIED;
  } catch (error) {
    if (error instanceof AccessKeysUnavailableError) {
      console.error(error.message);
      return { status: "unavailable" };
    }
    console.error("Access token verification failed", error);
    return DENIED;
  }
}
