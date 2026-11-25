import { SELF } from "cloudflare:test";

export const ORIGIN = "https://gooluco.test";
export const FHIR = `${ORIGIN}/fhir`;
export const REDIRECT = "pe.cravapp.gooluco:/oauth2redirect";
export const BACKEND_SECRET = "secreto-solo-para-tests";

export function b64url(buf: ArrayBuffer | Uint8Array): string {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export async function pkce() {
  const verifier = b64url(crypto.getRandomValues(new Uint8Array(48)));
  const challenge = b64url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
  return { verifier, challenge };
}

export function decodeJwt(t: string): { header: Record<string, unknown>; payload: Record<string, unknown> } {
  const [h, p] = t.split(".");
  const d = (x: string) => JSON.parse(atob(x.replace(/-/g, "+").replace(/_/g, "/")));
  return { header: d(h), payload: d(p) };
}

export function authorizeParams(challenge: string, scope: string, extra: Record<string, string> = {}) {
  return new URLSearchParams({
    response_type: "code",
    client_id: "gooluco-android",
    redirect_uri: REDIRECT,
    scope,
    state: "estado-123",
    aud: FHIR,
    code_challenge: challenge,
    code_challenge_method: "S256",
    ...extra,
  });
}

/** Ejecuta el flujo completo authorization_code + PKCE y devuelve la respuesta del token endpoint. */
export async function loginPkce(scope: string, user = "ana") {
  const { verifier, challenge } = await pkce();
  const params = authorizeParams(challenge, scope);
  const form = new URLSearchParams(params);
  form.set("decision", "allow");
  form.set("user", user);
  const auth = await SELF.fetch(`${ORIGIN}/auth/authorize`, {
    method: "POST",
    body: form,
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    redirect: "manual",
  });
  if (auth.status !== 302) throw new Error(`authorize ${auth.status}: ${await auth.text()}`);
  const loc = new URL(auth.headers.get("Location")!);
  const code = loc.searchParams.get("code")!;
  const tok = await SELF.fetch(`${ORIGIN}/auth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "authorization_code", code, redirect_uri: REDIRECT, code_verifier: verifier, client_id: "gooluco-android" }),
  });
  const json = (await tok.json()) as Record<string, string>;
  if (tok.status !== 200) throw new Error(`token ${tok.status}: ${JSON.stringify(json)}`);
  return json;
}

export async function backendToken(scope: string) {
  const r = await SELF.fetch(`${ORIGIN}/auth/token`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Authorization: `Basic ${btoa(`gooluco-backend:${BACKEND_SECRET}`)}`,
    },
    body: new URLSearchParams({ grant_type: "client_credentials", scope }),
  });
  const json = (await r.json()) as Record<string, string>;
  if (r.status !== 200) throw new Error(`token ${r.status}: ${JSON.stringify(json)}`);
  return json.access_token;
}

export function fhir(path: string, token?: string, init: RequestInit = {}) {
  const headers = new Headers(init.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  headers.set("Accept", "application/fhir+json");
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/fhir+json");
  return SELF.fetch(`${FHIR}${path}`, { ...init, headers });
}

export function weight(patient: string, kg: number, when: string, extra: Record<string, unknown> = {}) {
  return {
    resourceType: "Observation",
    status: "final",
    category: [{ coding: [{ system: "http://terminology.hl7.org/CodeSystem/observation-category", code: "vital-signs" }] }],
    code: { coding: [{ system: "http://loinc.org", code: "29463-7", display: "Body weight" }] },
    subject: { reference: `Patient/${patient}` },
    effectiveDateTime: when,
    valueQuantity: { value: kg, unit: "kg", system: "http://unitsofmeasure.org", code: "kg" },
    ...extra,
  };
}
