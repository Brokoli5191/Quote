export type QuoteLanguage = "en" | "de";

export const QUOTE_LANGUAGES: readonly QuoteLanguage[] = ["en", "de"];

// Zero-width, bidi embedding/override/isolate and other invisible format characters, blank
// "letters" (Hangul fillers, the blank Braille pattern), interlinear annotation marks and Unicode
// tag characters. They let identical-looking quotes slip past duplicate detection, pass the minimum
// length while showing nothing, and can disguise or smuggle text in the moderation dashboard.
// Variation selectors stay: they pick emoji presentation, and the comparison key drops them anyway.
const INVISIBLE_CHARACTERS =
  /[\u00AD\u034F\u061C\u115F\u1160\u17B4\u17B5\u180E\u200B-\u200F\u202A-\u202E\u2060-\u2064\u2066-\u206F\u2800\u3164\uFEFF\uFFA0\uFFF9-\uFFFB\u{E0000}-\u{E007F}]/gu;
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

// SQLite's length() in the CHECK constraints counts code points while String.length counts
// UTF-16 code units, so every length limit is checked in code points: "a😀" has two characters.
export function characterCount(value: string): number {
  return [...value].length;
}

export function isCheckViolation(error: unknown): boolean {
  return String(error).includes("CHECK constraint failed");
}

export function isUniqueViolation(error: unknown): boolean {
  return String(error).includes("UNIQUE constraint failed");
}

export async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}

export function normalizeForComparison(value: string): string {
  return value.normalize("NFKC").toLocaleLowerCase("en-US");
}

// Looser key used to match a submission against already published community quotes:
// only letters and digits count, so punctuation, quotation marks and spacing do not matter.
export function quoteComparisonKey(value: string): string {
  return normalizeForComparison(cleanText(value)).replace(/[^\p{L}\p{N}]+/gu, "");
}

// One-way form of the comparison key, kept for texts a moderator deleted permanently so the
// same text is refused again whatever its author or punctuation. Texts without letters or
// digits have no key; for them only the exact content hash is blocked.
export async function blockedTextHash(value: string): Promise<string | null> {
  const key = quoteComparisonKey(value);
  return key ? sha256Hex(key) : null;
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

// HEAD is served wherever GET is; the Worker's fetch handler drops the body.
export function isReadRequest(request: Request): boolean {
  return request.method === "GET" || request.method === "HEAD";
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
