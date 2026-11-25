// Servidor de autorización OAuth 2.0 / SMART App Launch 2.x (de PRUEBA).
//  - authorization_code + PKCE S256 (cliente público "gooluco-android")
//    https://hl7.org/fhir/smart-app-launch/app-launch.html
//  - client_credentials con secreto compartido (simplificación de SMART Backend Services, que exige private_key_jwt)
//    https://hl7.org/fhir/smart-app-launch/backend-services.html
import { Hono, type Context } from "hono";
import type { AppEnv, Env } from "../env";
import { baseUrl, fhirBase } from "../env";
import { findClient, findUser, redirectAllowed, safeEqual, type Client } from "./clients";
import { loadKeys, randomToken, sha256b64url, signJwt, verifyJwt, JwtError } from "./jwt";
import { negotiateScopes } from "./scopes";
import { authorizePage, errorPage } from "./page";
import { openidConfiguration, smartConfiguration } from "../fhir/capability";

const CODE_TTL = 120; // s
const REFRESH_TTL = 30 * 24 * 3600; // s

const noStore = { "Cache-Control": "no-store", Pragma: "no-cache" };

function oauthError(c: Context, status: 400 | 401, error: string, description: string) {
  const headers: Record<string, string> = { ...noStore };
  if (status === 401) headers["WWW-Authenticate"] = 'Basic realm="gooluco-api"';
  return c.json({ error, error_description: description }, status, headers);
}

function redirectWith(uri: string, params: Record<string, string | undefined>) {
  const u = new URL(uri);
  for (const [k, v] of Object.entries(params)) if (v !== undefined) u.searchParams.set(k, v);
  return new Response(null, { status: 302, headers: { Location: u.toString(), ...noStore } });
}

const AUTH_PARAMS = ["response_type", "client_id", "redirect_uri", "scope", "state", "aud", "code_challenge", "code_challenge_method", "nonce", "launch"] as const;

interface AuthReq {
  params: Record<string, string>;
  client: Client;
  requested: string[];
  granted: string[];
}

/** Valida la petición de autorización. Devuelve Response si hay error. */
function validateAuthorize(env: Env, reqUrl: string, raw: Record<string, string | undefined>): AuthReq | Response {
  const params: Record<string, string> = {};
  for (const k of AUTH_PARAMS) if (raw[k] !== undefined && raw[k] !== "") params[k] = raw[k]!;
  const client = findClient(params.client_id);
  // RFC 6749 §4.1.2.1: si client_id o redirect_uri son inválidos NO se redirige.
  if (!client) return new Response(errorPage("client_id desconocido."), { status: 400, headers: { "Content-Type": "text/html; charset=utf-8" } });
  if (!client.grantTypes.includes("authorization_code"))
    return new Response(errorPage("Este cliente no puede usar authorization_code."), { status: 400, headers: { "Content-Type": "text/html; charset=utf-8" } });
  if (!params.redirect_uri || !redirectAllowed(client, params.redirect_uri))
    return new Response(errorPage("redirect_uri no registrado para este cliente."), { status: 400, headers: { "Content-Type": "text/html; charset=utf-8" } });
  const fail = (error: string, d: string) => redirectWith(params.redirect_uri, { error, error_description: d, state: params.state });
  if (params.response_type !== "code") return fail("unsupported_response_type", "response_type debe ser code");
  if (!params.state) return fail("invalid_request", "state es obligatorio (SMART App Launch)");
  if (!params.code_challenge || params.code_challenge_method !== "S256")
    return fail("invalid_request", "PKCE obligatorio: code_challenge con code_challenge_method=S256");
  if (!/^[A-Za-z0-9_-]{43}$/.test(params.code_challenge)) return fail("invalid_request", "code_challenge inválido");
  // SMART: aud debe ser la URL base FHIR del servidor de recursos.
  if (!params.aud || params.aud.replace(/\/+$/, "") !== fhirBase(env, reqUrl)) return fail("invalid_request", `aud debe ser ${fhirBase(env, reqUrl)}`);
  if (params.launch) return fail("invalid_request", "EHR launch no soportado; usar launch standalone (launch/patient)");
  const requested = (params.scope ?? "").split(/\s+/).filter(Boolean);
  if (!requested.length) return fail("invalid_scope", "scope vacío");
  const granted = negotiateScopes(requested, client.allowedScopes);
  if (!granted.length) return fail("invalid_scope", "ningún scope solicitado es concedible");
  return { params, client, requested, granted };
}

async function formOrQuery(c: Context): Promise<Record<string, string>> {
  if (c.req.method === "GET") return c.req.query();
  const ct = c.req.header("content-type") ?? "";
  if (!ct.includes("application/x-www-form-urlencoded") && !ct.includes("multipart/form-data")) return {};
  const body = await c.req.parseBody();
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(body)) if (typeof v === "string") out[k] = v;
  return out;
}

export const authRoutes = new Hono<AppEnv>();

// --- Descubrimiento -----------------------------------------------------------
const smartCfg = (c: Context<AppEnv>) => c.json(smartConfiguration(baseUrl(c.env, c.req.url)), 200, { "Cache-Control": "public, max-age=300" });
authRoutes.get("/fhir/.well-known/smart-configuration", smartCfg);
authRoutes.get("/.well-known/smart-configuration", smartCfg);
authRoutes.get("/.well-known/openid-configuration", (c) => c.json(openidConfiguration(baseUrl(c.env, c.req.url))));
authRoutes.get("/.well-known/jwks.json", async (c) => {
  const keys = await loadKeys(c.env.JWT_PRIVATE_JWK);
  return c.json({ keys: [keys.publicJwk] });
});

// --- /auth/authorize ------------------------------------------------------------
authRoutes.get("/auth/authorize", async (c) => {
  const v = validateAuthorize(c.env, c.req.url, c.req.query());
  if (v instanceof Response) return v;
  return c.html(authorizePage(v.client, v.params, v.granted));
});

authRoutes.post("/auth/authorize", async (c) => {
  const body = await formOrQuery(c);
  const v = validateAuthorize(c.env, c.req.url, body);
  if (v instanceof Response) return v;
  // authorize-post: un POST sin "decision" equivale al GET (muestra la página de consentimiento)
  if (!body.decision) return c.html(authorizePage(v.client, v.params, v.granted));
  if (body.decision !== "allow") return redirectWith(v.params.redirect_uri, { error: "access_denied", error_description: "El usuario denegó el acceso", state: v.params.state });
  const user = findUser(body.user);
  if (!user) return redirectWith(v.params.redirect_uri, { error: "access_denied", error_description: "usuario de prueba inválido", state: v.params.state });

  const code = randomToken(32);
  const scope = v.granted.join(" ");
  const fhirUser = `Patient/${user.patientId}`;
  await c.env.DB.prepare(
    "INSERT INTO auth_code (code_hash, client_id, redirect_uri, code_challenge, scope, user_id, patient_id, fhir_user, nonce, expires_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
  )
    .bind(await sha256b64url(code), v.client.clientId, v.params.redirect_uri, v.params.code_challenge, scope, user.id, user.patientId, fhirUser, v.params.nonce ?? null, Math.floor(Date.now() / 1000) + CODE_TTL)
    .run();
  return redirectWith(v.params.redirect_uri, { code, state: v.params.state });
});

// --- /auth/token ----------------------------------------------------------------
interface Grant { sub: string; clientId: string; scope: string; patient?: string | null; fhirUser?: string | null; nonce?: string | null; userId?: string }

async function issueTokens(c: Context<AppEnv>, g: Grant, opts: { refresh: boolean; idToken: boolean }) {
  const env = c.env;
  const base = baseUrl(env, c.req.url);
  const aud = fhirBase(env, c.req.url);
  const keys = await loadKeys(env.JWT_PRIVATE_JWK);
  const ttl = Number(env.ACCESS_TOKEN_TTL_SECONDS ?? "3600") || 3600;
  const now = Math.floor(Date.now() / 1000);
  const access = await signJwt(
    {
      iss: base, sub: g.sub, aud, iat: now, exp: now + ttl, jti: crypto.randomUUID(),
      client_id: g.clientId, scope: g.scope,
      ...(g.patient ? { patient: g.patient } : {}),
      ...(g.fhirUser ? { fhirUser: `${aud}/${g.fhirUser}` } : {}),
    },
    keys,
  );
  const scopes = g.scope.split(" ");
  const res: Record<string, unknown> = { access_token: access, token_type: "Bearer", expires_in: ttl, scope: g.scope };
  if (g.patient && scopes.includes("launch/patient")) res.patient = g.patient;
  if (opts.idToken && scopes.includes("openid") && scopes.includes("fhirUser") && g.fhirUser) {
    res.id_token = await signJwt(
      { iss: base, sub: g.sub, aud: g.clientId, iat: now, exp: now + ttl, fhirUser: `${aud}/${g.fhirUser}`, ...(g.nonce ? { nonce: g.nonce } : {}) },
      keys,
    );
  }
  if (opts.refresh && (scopes.includes("offline_access") || scopes.includes("online_access"))) {
    const rt = randomToken(32);
    await env.DB.prepare("INSERT INTO refresh_token (token_hash, client_id, scope, user_id, patient_id, fhir_user, expires_at) VALUES (?,?,?,?,?,?,?)")
      .bind(await sha256b64url(rt), g.clientId, g.scope, g.sub, g.patient ?? null, g.fhirUser ?? null, now + REFRESH_TTL)
      .run();
    res.refresh_token = rt;
  }
  return c.json(res, 200, noStore);
}

/** Autenticación del cliente: Basic (client_secret_basic), client_secret_post o público (solo client_id). */
function authenticateClient(c: Context<AppEnv>, body: Record<string, string>): { client: Client } | { error: Response } {
  let id = body.client_id;
  let secret = body.client_secret;
  const authz = c.req.header("authorization");
  if (authz?.toLowerCase().startsWith("basic ")) {
    try {
      const decoded = atob(authz.slice(6).trim());
      const i = decoded.indexOf(":");
      id = decodeURIComponent(decoded.slice(0, i));
      secret = decodeURIComponent(decoded.slice(i + 1));
    } catch {
      return { error: oauthError(c, 401, "invalid_client", "cabecera Basic inválida") };
    }
  }
  if (body.client_assertion_type || body.client_assertion)
    return { error: oauthError(c, 400, "invalid_client", "private_key_jwt no está implementado en esta prueba; usar client_secret_basic") };
  const client = findClient(id);
  if (!client) return { error: oauthError(c, 401, "invalid_client", "cliente desconocido") };
  if (client.type === "confidential-symmetric") {
    const expected = client.secret?.(c.env);
    if (!expected || !secret || !safeEqual(secret, expected)) return { error: oauthError(c, 401, "invalid_client", "secreto de cliente inválido") };
  } else if (secret) {
    return { error: oauthError(c, 400, "invalid_client", "cliente público: no envíe client_secret") };
  }
  return { client };
}

authRoutes.post("/auth/token", async (c) => {
  const ct = c.req.header("content-type") ?? "";
  if (!ct.includes("application/x-www-form-urlencoded")) return oauthError(c, 400, "invalid_request", "Content-Type debe ser application/x-www-form-urlencoded");
  const body = await formOrQuery(c);
  const auth = authenticateClient(c, body);
  if ("error" in auth) return auth.error;
  const { client } = auth;
  const grantType = body.grant_type;
  if (!grantType || !client.grantTypes.includes(grantType as Client["grantTypes"][number]))
    return oauthError(c, 400, "unauthorized_client", `grant_type ${grantType ?? "(vacío)"} no permitido para este cliente`);
  const db = c.env.DB;
  const now = Math.floor(Date.now() / 1000);

  if (grantType === "authorization_code") {
    if (!body.code || !body.redirect_uri || !body.code_verifier) return oauthError(c, 400, "invalid_request", "faltan code, redirect_uri o code_verifier");
    if (!/^[A-Za-z0-9\-._~]{43,128}$/.test(body.code_verifier)) return oauthError(c, 400, "invalid_grant", "code_verifier inválido (RFC 7636 §4.1)");
    const hash = await sha256b64url(body.code);
    // Consumir el código de forma atómica (un solo uso).
    const row = await db
      .prepare("UPDATE auth_code SET used = 1 WHERE code_hash = ? AND used = 0 RETURNING client_id, redirect_uri, code_challenge, scope, user_id, patient_id, fhir_user, nonce, expires_at")
      .bind(hash)
      .first<{ client_id: string; redirect_uri: string; code_challenge: string; scope: string; user_id: string; patient_id: string | null; fhir_user: string | null; nonce: string | null; expires_at: number }>();
    if (!row) return oauthError(c, 400, "invalid_grant", "código inválido o ya usado");
    if (row.expires_at < now) return oauthError(c, 400, "invalid_grant", "código expirado");
    if (row.client_id !== client.clientId) return oauthError(c, 400, "invalid_grant", "el código pertenece a otro cliente");
    if (row.redirect_uri !== body.redirect_uri) return oauthError(c, 400, "invalid_grant", "redirect_uri no coincide");
    if ((await sha256b64url(body.code_verifier)) !== row.code_challenge) return oauthError(c, 400, "invalid_grant", "PKCE: code_verifier no coincide con code_challenge");
    return issueTokens(c, { sub: row.user_id, clientId: client.clientId, scope: row.scope, patient: row.patient_id, fhirUser: row.fhir_user, nonce: row.nonce }, { refresh: true, idToken: true });
  }

  if (grantType === "refresh_token") {
    if (!body.refresh_token) return oauthError(c, 400, "invalid_request", "falta refresh_token");
    const hash = await sha256b64url(body.refresh_token);
    const row = await db
      .prepare("UPDATE refresh_token SET revoked = 1 WHERE token_hash = ? AND revoked = 0 RETURNING client_id, scope, user_id, patient_id, fhir_user, expires_at")
      .bind(hash)
      .first<{ client_id: string; scope: string; user_id: string; patient_id: string | null; fhir_user: string | null; expires_at: number }>();
    if (!row || row.expires_at < now || row.client_id !== client.clientId) return oauthError(c, 400, "invalid_grant", "refresh_token inválido");
    let scope = row.scope;
    if (body.scope) {
      // Solo se puede reducir el alcance (RFC 6749 §6).
      const narrowed = negotiateScopes(body.scope.split(/\s+/).filter(Boolean), row.scope.split(" "));
      if (!narrowed.length) return oauthError(c, 400, "invalid_scope", "scope fuera del concedido originalmente");
      scope = narrowed.join(" ");
    }
    // Rotación: el refresh token anterior queda revocado y se emite uno nuevo.
    return issueTokens(c, { sub: row.user_id, clientId: client.clientId, scope, patient: row.patient_id, fhirUser: row.fhir_user }, { refresh: true, idToken: true });
  }

  // client_credentials (SMART Backend Services simplificado)
  const requested = (body.scope ?? "").split(/\s+/).filter(Boolean);
  if (!requested.length) return oauthError(c, 400, "invalid_scope", "scope obligatorio");
  if (requested.some((s) => !s.startsWith("system/"))) return oauthError(c, 400, "invalid_scope", "client_credentials solo admite scopes system/*");
  const granted = negotiateScopes(requested, client.allowedScopes);
  if (!granted.length) return oauthError(c, 400, "invalid_scope", "ningún scope concedible");
  return issueTokens(c, { sub: client.clientId, clientId: client.clientId, scope: granted.join(" ") }, { refresh: false, idToken: false });
});

// --- /auth/introspect (RFC 7662, solo para pruebas; requiere cliente confidencial) ------
authRoutes.post("/auth/introspect", async (c) => {
  const body = await formOrQuery(c);
  const auth = authenticateClient(c, body);
  if ("error" in auth) return auth.error;
  if (auth.client.type !== "confidential-symmetric") return oauthError(c, 401, "invalid_client", "introspección solo para clientes confidenciales");
  if (!body.token) return c.json({ active: false }, 200, noStore);
  try {
    const keys = await loadKeys(c.env.JWT_PRIVATE_JWK);
    const p = await verifyJwt(body.token, keys, { iss: baseUrl(c.env, c.req.url), aud: fhirBase(c.env, c.req.url) });
    return c.json({ active: true, ...p }, 200, noStore);
  } catch (e) {
    if (e instanceof JwtError) return c.json({ active: false }, 200, noStore);
    throw e;
  }
});
