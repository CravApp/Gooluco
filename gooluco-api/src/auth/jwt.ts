// JWT compactos firmados con ES256 (ECDSA P-256 + SHA-256) usando Web Crypto.
const enc = new TextEncoder();
const dec = new TextDecoder();

export function b64url(input: ArrayBuffer | Uint8Array | string): string {
  const bytes = typeof input === "string" ? enc.encode(input) : input instanceof Uint8Array ? input : new Uint8Array(input);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function b64urlDecode(s: string): Uint8Array {
  const pad = s.length % 4 === 0 ? "" : "=".repeat(4 - (s.length % 4));
  const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/") + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export async function sha256b64url(text: string): Promise<string> {
  return b64url(await crypto.subtle.digest("SHA-256", enc.encode(text)));
}

export function randomToken(bytes = 32): string {
  return b64url(crypto.getRandomValues(new Uint8Array(bytes)));
}

export interface KeyPair {
  privateKey: CryptoKey;
  publicKey: CryptoKey;
  publicJwk: JsonWebKey & { kid: string; alg: string; use: string };
}

const cache = new Map<string, Promise<KeyPair>>();

export function loadKeys(privateJwkJson: string | undefined): Promise<KeyPair> {
  if (!privateJwkJson) throw new Error("JWT_PRIVATE_JWK no está configurado (ver .dev.vars.example)");
  let p = cache.get(privateJwkJson);
  if (!p) {
    p = (async () => {
      const jwk = JSON.parse(privateJwkJson) as JsonWebKey & { kid?: string };
      if (jwk.kty !== "EC" || jwk.crv !== "P-256" || !jwk.d) throw new Error("JWT_PRIVATE_JWK debe ser una clave privada EC P-256");
      const { d: _d, key_ops: _ko, ext: _ext, ...pub } = jwk as JsonWebKey & { kid?: string };
      const kid = jwk.kid ?? (await thumbprint(pub));
      const algo = { name: "ECDSA", namedCurve: "P-256" };
      const privateKey = await crypto.subtle.importKey("jwk", { ...jwk, key_ops: ["sign"] }, algo, false, ["sign"]);
      const publicKey = await crypto.subtle.importKey("jwk", { ...pub, key_ops: ["verify"] }, algo, true, ["verify"]);
      return {
        privateKey,
        publicKey,
        publicJwk: { kty: "EC", crv: "P-256", x: pub.x, y: pub.y, kid, alg: "ES256", use: "sig" },
      };
    })();
    cache.set(privateJwkJson, p);
  }
  return p;
}

/** RFC 7638 thumbprint de una clave EC pública. */
async function thumbprint(pub: JsonWebKey): Promise<string> {
  const canonical = JSON.stringify({ crv: pub.crv, kty: pub.kty, x: pub.x, y: pub.y });
  return sha256b64url(canonical);
}

export async function signJwt(payload: Record<string, unknown>, keys: KeyPair): Promise<string> {
  const header = { alg: "ES256", typ: "JWT", kid: keys.publicJwk.kid };
  const signingInput = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
  // Web Crypto devuelve la firma ECDSA en formato IEEE P1363 (r||s), que es justo el de JWS.
  const sig = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, keys.privateKey, enc.encode(signingInput));
  return `${signingInput}.${b64url(sig)}`;
}

export class JwtError extends Error {}

export async function verifyJwt(
  token: string,
  keys: KeyPair,
  expected: { iss: string; aud: string },
): Promise<Record<string, unknown>> {
  const parts = token.split(".");
  if (parts.length !== 3) throw new JwtError("token mal formado");
  const [h, p, s] = parts;
  let header: { alg?: string; kid?: string };
  let payload: Record<string, unknown>;
  try {
    header = JSON.parse(dec.decode(b64urlDecode(h)));
    payload = JSON.parse(dec.decode(b64urlDecode(p)));
  } catch {
    throw new JwtError("token mal formado");
  }
  if (header.alg !== "ES256") throw new JwtError("alg no admitido");
  const ok = await crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    keys.publicKey,
    b64urlDecode(s),
    enc.encode(`${h}.${p}`),
  );
  if (!ok) throw new JwtError("firma inválida");
  const now = Math.floor(Date.now() / 1000);
  if (typeof payload.exp !== "number" || payload.exp < now) throw new JwtError("token expirado");
  if (payload.iss !== expected.iss) throw new JwtError("iss inválido");
  const aud = payload.aud;
  const auds = Array.isArray(aud) ? aud : [aud];
  if (!auds.includes(expected.aud)) throw new JwtError("aud inválido");
  return payload;
}
