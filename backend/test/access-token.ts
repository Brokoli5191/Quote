export const TEAM_DOMAIN = "team.example.com";
export const AUDIENCE = "test-audience";
export const CERTS_URL = `https://${TEAM_DOMAIN}/cdn-cgi/access/certs`;

function base64Url(value: string | Uint8Array): string {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export interface AccessSigner {
  jwk: JsonWebKey & { kid: string };
  sign(email: string): Promise<string>;
}

// Signs Cloudflare Access style RS256 tokens with a throwaway key.
export async function createSigner(kid = "test-key"): Promise<AccessSigner> {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"]
  ) as CryptoKeyPair;
  const publicJwk = await crypto.subtle.exportKey("jwk", pair.publicKey) as JsonWebKey;
  return {
    jwk: { ...publicJwk, kid },
    async sign(email: string) {
      const header = base64Url(JSON.stringify({ alg: "RS256", kid }));
      const payload = base64Url(JSON.stringify({
        aud: AUDIENCE,
        email,
        exp: Math.floor(Date.now() / 1000) + 3600,
        iss: `https://${TEAM_DOMAIN}`
      }));
      const signature = await crypto.subtle.sign(
        "RSASSA-PKCS1-v1_5",
        pair.privateKey,
        new TextEncoder().encode(`${header}.${payload}`)
      );
      return `${header}.${payload}.${base64Url(new Uint8Array(signature))}`;
    }
  };
}
