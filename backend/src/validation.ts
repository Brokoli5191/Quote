export type QuoteLanguage = "en" | "de";

export const QUOTE_LANGUAGES: readonly QuoteLanguage[] = ["en", "de"];

// Zero-width, bidi embedding/override/isolate and other invisible format characters.
// They let identical-looking quotes slip past duplicate detection and can disguise text
// in the moderation dashboard.
const INVISIBLE_CHARACTERS = /[­؜᠎​-‏‪-‮⁠-⁤⁦-⁩﻿]/g;
// Control characters other than the whitespace ones, which are collapsed to a space instead.
const CONTROL_CHARACTERS = /[\u0000-\u0008\u000E-\u001F\u007F-\u009F]/g;

export function cleanText(value: unknown): string {
  return typeof value === "string"
    ? value
      .replace(INVISIBLE_CHARACTERS, "")
      .replace(CONTROL_CHARACTERS, "")
      .replace(/\s+/g, " ")
      .trim()
    : "";
}

export function normalizeForComparison(value: string): string {
  return value.normalize("NFKC").toLocaleLowerCase("en-US");
}

// Looser key used to match a submission against already published community quotes:
// only letters and digits count, so punctuation, quotation marks and spacing do not matter.
export function quoteComparisonKey(value: string): string {
  return normalizeForComparison(cleanText(value)).replace(/[^\p{L}\p{N}]+/gu, "");
}

export type LanguageResult =
  | { ok: true; value: QuoteLanguage | undefined }
  | { ok: false; message: string };

// Missing (undefined or null) languages are reported as undefined so callers can pick a default.
export function parseLanguage(value: unknown): LanguageResult {
  if (value === undefined || value === null) return { ok: true, value: undefined };
  const language = typeof value === "string" ? value.trim().toLowerCase() : "";
  if (language === "en" || language === "de") return { ok: true, value: language };
  return { ok: false, message: "language must be en or de." };
}

// Rate limits bucket IPv6 clients by their /64 prefix, because a single connection
// usually controls the whole prefix. IPv4 and IPv4-mapped addresses stay unchanged.
export function ipRateLimitKey(ip: string): string {
  const value = ip.trim();
  if (!value.includes(":")) return value;
  const hextets = parseIpv6(value);
  if (!hextets) return value.toLowerCase();
  if (hextets.slice(0, 5).every(part => part === 0) && hextets[5] === 0xffff) {
    return [hextets[6] >> 8, hextets[6] & 255, hextets[7] >> 8, hextets[7] & 255].join(".");
  }
  return `${hextets.slice(0, 4).map(part => part.toString(16)).join(":")}::/64`;
}

function parseIpv6(value: string): number[] | null {
  let address = value.toLowerCase();
  const zoneIndex = address.indexOf("%");
  if (zoneIndex >= 0) address = address.slice(0, zoneIndex);

  const lastColon = address.lastIndexOf(":");
  const tail = address.slice(lastColon + 1);
  if (tail.includes(".")) {
    const octets = tail.split(".");
    if (octets.length !== 4 || !octets.every(octet => /^\d{1,3}$/.test(octet) && Number(octet) <= 255)) {
      return null;
    }
    const [a, b, c, d] = octets.map(Number);
    address = `${address.slice(0, lastColon + 1)}${((a << 8) | b).toString(16)}:${((c << 8) | d).toString(16)}`;
  }

  const halves = address.split("::");
  if (halves.length > 2) return null;
  const head = halves[0] ? halves[0].split(":") : [];
  const rest = halves.length === 2 && halves[1] ? halves[1].split(":") : [];
  const missing = 8 - head.length - rest.length;
  if (halves.length === 1 ? head.length !== 8 : missing < 1) return null;

  const parts = [...head, ...Array<string>(halves.length === 2 ? missing : 0).fill("0"), ...rest];
  if (!parts.every(part => /^[0-9a-f]{1,4}$/.test(part))) return null;
  return parts.map(part => parseInt(part, 16));
}

export type BodyReadResult =
  | { ok: true; text: string }
  | { ok: false; status: 400 | 413; code: "invalid_request" | "too_large"; message: string };

// Reads the request body but stops as soon as it grows beyond maxBytes, also when the
// client omits Content-Length (chunked uploads).
export async function readBodyWithLimit(request: Request, maxBytes: number): Promise<BodyReadResult> {
  const tooLarge = { ok: false, status: 413, code: "too_large", message: "Request body is too large." } as const;
  const declared = request.headers.get("Content-Length");
  if (declared !== null) {
    if (!/^\d+$/.test(declared.trim())) {
      return { ok: false, status: 400, code: "invalid_request", message: "Content-Length is invalid." };
    }
    if (Number(declared) > maxBytes) return tooLarge;
  }
  if (!request.body) return { ok: true, text: "" };

  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > maxBytes) {
      await reader.cancel().catch(() => undefined);
      return tooLarge;
    }
    chunks.push(value);
  }

  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return { ok: true, text: new TextDecoder().decode(bytes) };
}
