import { SELF } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import { authorizeParams, BACKEND_SECRET, backendToken, decodeJwt, FHIR, fhir, loginPkce, ORIGIN, pkce, REDIRECT } from "./helpers";

const form = { "Content-Type": "application/x-www-form-urlencoded" };
const SCOPE = "launch/patient openid fhirUser offline_access patient/Patient.rs patient/Observation.rs patient/Observation.c";

async function token(body: Record<string, string>, headers: Record<string, string> = {}) {
  const r = await SELF.fetch(`${ORIGIN}/auth/token`, { method: "POST", headers: { ...form, ...headers }, body: new URLSearchParams(body) });
  return { status: r.status, json: (await r.json()) as any, headers: r.headers };
}

async function authorizeCode(challenge: string, scope = SCOPE) {
  const f = authorizeParams(challenge, scope);
  f.set("decision", "allow");
  f.set("user", "ana");
  const r = await SELF.fetch(`${ORIGIN}/auth/authorize`, { method: "POST", headers: form, body: f, redirect: "manual" });
  return new URL(r.headers.get("Location")!).searchParams.get("code")!;
}

describe("authorization_code + PKCE (SMART standalone launch, cliente público)", () => {
  it("flujo completo: página de consentimiento → code → token → acceso FHIR", async () => {
    const { verifier, challenge } = await pkce();
    // 1. GET /auth/authorize muestra la página de prueba con los scopes
    const page = await SELF.fetch(`${ORIGIN}/auth/authorize?${authorizeParams(challenge, SCOPE)}`);
    expect(page.status).toBe(200);
    const html = await page.text();
    expect(html).toContain("Servidor de PRUEBA");
    expect(html).toContain("patient/Observation.rs");
    // 2. El usuario de prueba autoriza
    const f = authorizeParams(challenge, SCOPE);
    f.set("decision", "allow");
    f.set("user", "ana");
    const r = await SELF.fetch(`${ORIGIN}/auth/authorize`, { method: "POST", headers: form, body: f, redirect: "manual" });
    expect(r.status).toBe(302);
    const loc = new URL(r.headers.get("Location")!);
    expect(`${loc.protocol}${loc.pathname}`).toBe(REDIRECT);
    expect(loc.searchParams.get("state")).toBe("estado-123");
    const code = loc.searchParams.get("code")!;
    expect(code).toBeTruthy();
    // 3. Canje del código con code_verifier
    const t = await token({ grant_type: "authorization_code", code, redirect_uri: REDIRECT, code_verifier: verifier, client_id: "gooluco-android" });
    expect(t.status).toBe(200);
    expect(t.headers.get("cache-control")).toBe("no-store");
    expect(t.json).toMatchObject({ token_type: "Bearer", patient: "demo-ana", expires_in: 3600 });
    expect(t.json.scope.split(" ")).toEqual(expect.arrayContaining(["launch/patient", "openid", "fhirUser", "patient/Observation.rs", "patient/Observation.c"]));
    expect(t.json.refresh_token).toBeTruthy();
    const at = decodeJwt(t.json.access_token);
    expect(at.header).toMatchObject({ alg: "ES256", kid: "test-key" });
    expect(at.payload).toMatchObject({ iss: ORIGIN, aud: FHIR, patient: "demo-ana", client_id: "gooluco-android" });
    const idt = decodeJwt(t.json.id_token);
    expect(idt.payload).toMatchObject({ iss: ORIGIN, aud: "gooluco-android", fhirUser: `${FHIR}/Patient/demo-ana` });
    // 4. El access token abre el FHIR del paciente en contexto
    const p = await fhir("/Patient/demo-ana", t.json.access_token);
    expect(p.status).toBe(200);
    expect(((await p.json()) as any).name[0].family).toContain("FICTICIA");
    // 5. El código es de un solo uso
    const again = await token({ grant_type: "authorization_code", code, redirect_uri: REDIRECT, code_verifier: verifier, client_id: "gooluco-android" });
    expect(again.status).toBe(400);
    expect(again.json.error).toBe("invalid_grant");
  });

  it("rechaza un code_verifier que no corresponde al code_challenge", async () => {
    const { challenge } = await pkce();
    const other = await pkce();
    const code = await authorizeCode(challenge);
    const t = await token({ grant_type: "authorization_code", code, redirect_uri: REDIRECT, code_verifier: other.verifier, client_id: "gooluco-android" });
    expect(t.status).toBe(400);
    expect(t.json.error).toBe("invalid_grant");
  });

  it("rechaza redirect_uri distinto en el canje", async () => {
    const { verifier, challenge } = await pkce();
    const code = await authorizeCode(challenge);
    const t = await token({ grant_type: "authorization_code", code, redirect_uri: "http://127.0.0.1:9999/cb", code_verifier: verifier, client_id: "gooluco-android" });
    expect(t.json.error).toBe("invalid_grant");
  });

  it("exige PKCE S256 (sin challenge o con 'plain' → error por redirección)", async () => {
    const { challenge } = await pkce();
    for (const extra of [{ code_challenge_method: "plain" }, { code_challenge: "" }]) {
      const p = authorizeParams(challenge, SCOPE, extra as unknown as Record<string, string>);
      const r = await SELF.fetch(`${ORIGIN}/auth/authorize?${p}`, { redirect: "manual" });
      expect(r.status).toBe(302);
      const loc = new URL(r.headers.get("Location")!);
      expect(loc.searchParams.get("error")).toBe("invalid_request");
      expect(loc.searchParams.get("state")).toBe("estado-123");
    }
  });

  it("exige aud = base FHIR del servidor", async () => {
    const { challenge } = await pkce();
    const r = await SELF.fetch(`${ORIGIN}/auth/authorize?${authorizeParams(challenge, SCOPE, { aud: "https://otro.example/fhir" })}`, { redirect: "manual" });
    expect(new URL(r.headers.get("Location")!).searchParams.get("error")).toBe("invalid_request");
  });

  it("no redirige si client_id o redirect_uri no están registrados", async () => {
    const { challenge } = await pkce();
    const r1 = await SELF.fetch(`${ORIGIN}/auth/authorize?${authorizeParams(challenge, SCOPE, { redirect_uri: "https://malicioso.example/cb" })}`, { redirect: "manual" });
    expect(r1.status).toBe(400);
    expect(r1.headers.get("Location")).toBeNull();
    const r2 = await SELF.fetch(`${ORIGIN}/auth/authorize?${authorizeParams(challenge, SCOPE, { client_id: "desconocido" })}`, { redirect: "manual" });
    expect(r2.status).toBe(400);
  });

  it("acepta redirect de loopback (RFC 8252) para pruebas con curl/navegador", async () => {
    const { challenge } = await pkce();
    const r = await SELF.fetch(`${ORIGIN}/auth/authorize?${authorizeParams(challenge, SCOPE, { redirect_uri: "http://127.0.0.1:53682/callback" })}`);
    expect(r.status).toBe(200);
  });

  it("si el usuario deniega → access_denied", async () => {
    const { challenge } = await pkce();
    const f = authorizeParams(challenge, SCOPE);
    f.set("decision", "deny");
    const r = await SELF.fetch(`${ORIGIN}/auth/authorize`, { method: "POST", headers: form, body: f, redirect: "manual" });
    expect(new URL(r.headers.get("Location")!).searchParams.get("error")).toBe("access_denied");
  });

  it("solo concede scopes permitidos al cliente (p. ej. system/* no para la app)", async () => {
    const t = await loginPkce("launch/patient patient/Observation.rs system/Observation.rs user/Observation.cruds");
    expect(t.scope.split(" ").sort()).toEqual(["launch/patient", "patient/Observation.rs"]);
    expect(t.refresh_token).toBeUndefined();
    expect(t.id_token).toBeUndefined();
  });

  it("acepta scopes v1 (.read) mapeándolos a v2", async () => {
    const t = await loginPkce("launch/patient patient/Observation.read");
    expect(t.scope).toContain("patient/Observation.rs");
  });

  it("refresh_token con rotación (el anterior queda inválido)", async () => {
    const t = await loginPkce("launch/patient offline_access patient/Observation.rs");
    const r1 = await token({ grant_type: "refresh_token", refresh_token: t.refresh_token, client_id: "gooluco-android" });
    expect(r1.status).toBe(200);
    expect(r1.json.access_token).toBeTruthy();
    expect(r1.json.patient).toBe("demo-ana");
    expect(r1.json.refresh_token).not.toBe(t.refresh_token);
    const r2 = await token({ grant_type: "refresh_token", refresh_token: t.refresh_token, client_id: "gooluco-android" });
    expect(r2.json.error).toBe("invalid_grant");
  });
});

describe("client_credentials (SMART Backend Services simplificado)", () => {
  it("emite token system/* con client_secret_basic", async () => {
    const t = await backendToken("system/Observation.rs system/Patient.rs");
    const { payload } = decodeJwt(t);
    expect(payload.scope).toBe("system/Observation.rs system/Patient.rs");
    expect(payload.sub).toBe("gooluco-backend");
    expect(payload.patient).toBeUndefined();
    const s = await fhir("/Observation?code=29463-7", t);
    const b = (await s.json()) as any;
    const patients = new Set(b.entry.map((e: any) => e.resource.subject.reference));
    expect(patients.has("Patient/demo-ana") && patients.has("Patient/demo-beto")).toBe(true);
  });

  it("acepta client_secret_post", async () => {
    const t = await token({ grant_type: "client_credentials", scope: "system/Patient.rs", client_id: "gooluco-backend", client_secret: BACKEND_SECRET });
    expect(t.status).toBe(200);
  });

  it("rechaza secreto incorrecto (401 invalid_client)", async () => {
    const t = await token({ grant_type: "client_credentials", scope: "system/Patient.rs" }, { Authorization: `Basic ${btoa("gooluco-backend:malo")}` });
    expect(t.status).toBe(401);
    expect(t.json.error).toBe("invalid_client");
  });

  it("rechaza scopes que no son system/*", async () => {
    const t = await token({ grant_type: "client_credentials", scope: "patient/Observation.rs", client_id: "gooluco-backend", client_secret: BACKEND_SECRET });
    expect(t.json.error).toBe("invalid_scope");
  });

  it("el cliente público no puede usar client_credentials", async () => {
    const t = await token({ grant_type: "client_credentials", scope: "system/Patient.rs", client_id: "gooluco-android" });
    expect(t.json.error).toBe("unauthorized_client");
  });

  it("private_key_jwt aún no implementado → error explícito", async () => {
    const t = await token({ grant_type: "client_credentials", scope: "system/Patient.rs", client_assertion_type: "urn:ietf:params:oauth:client-assertion-type:jwt-bearer", client_assertion: "x.y.z" });
    expect(t.json.error).toBe("invalid_client");
  });

  it("introspección de un token válido e inválido", async () => {
    const at = await backendToken("system/Patient.rs");
    const h = { Authorization: `Basic ${btoa(`gooluco-backend:${BACKEND_SECRET}`)}`, ...form };
    const ok = await SELF.fetch(`${ORIGIN}/auth/introspect`, { method: "POST", headers: h, body: new URLSearchParams({ token: at }) });
    expect(((await ok.json()) as any).active).toBe(true);
    const bad = await SELF.fetch(`${ORIGIN}/auth/introspect`, { method: "POST", headers: h, body: new URLSearchParams({ token: at.slice(0, -4) + "AAAA" }) });
    expect(((await bad.json()) as any).active).toBe(false);
  });
});
