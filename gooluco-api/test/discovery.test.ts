import { SELF } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import { FHIR, ORIGIN } from "./helpers";

describe("descubrimiento", () => {
  it("GET /fhir/metadata devuelve un CapabilityStatement R4 con seguridad SMART", async () => {
    const r = await SELF.fetch(`${FHIR}/metadata`);
    expect(r.status).toBe(200);
    expect(r.headers.get("content-type")).toContain("application/fhir+json");
    const cs = (await r.json()) as any;
    expect(cs.resourceType).toBe("CapabilityStatement");
    expect(cs.fhirVersion).toBe("4.0.1");
    expect(cs.kind).toBe("instance");
    const sec = cs.rest[0].security;
    expect(sec.service[0].coding[0]).toMatchObject({ system: "http://terminology.hl7.org/CodeSystem/restful-security-service", code: "SMART-on-FHIR" });
    const uris = sec.extension.find((e: any) => e.url === "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris");
    expect(uris.extension.map((e: any) => e.url)).toEqual(expect.arrayContaining(["authorize", "token"]));
    const types = cs.rest[0].resource.map((x: any) => x.type);
    expect(types).toEqual(expect.arrayContaining(["Patient", "Observation", "QuestionnaireResponse", "Questionnaire"]));
  });

  it("/.well-known/smart-configuration cumple los campos obligatorios de SMART 2.x", async () => {
    for (const p of [`${FHIR}/.well-known/smart-configuration`, `${ORIGIN}/.well-known/smart-configuration`]) {
      const r = await SELF.fetch(p);
      expect(r.status).toBe(200);
      const c = (await r.json()) as any;
      expect(c.issuer).toBe(ORIGIN);
      expect(c.authorization_endpoint).toBe(`${ORIGIN}/auth/authorize`);
      expect(c.token_endpoint).toBe(`${ORIGIN}/auth/token`);
      expect(c.grant_types_supported).toEqual(expect.arrayContaining(["authorization_code", "client_credentials"]));
      expect(c.code_challenge_methods_supported).toContain("S256");
      expect(c.code_challenge_methods_supported).not.toContain("plain");
      expect(c.capabilities).toEqual(expect.arrayContaining(["launch-standalone", "client-public", "context-standalone-patient", "permission-patient", "permission-v2"]));
      expect(c.jwks_uri).toBe(`${ORIGIN}/.well-known/jwks.json`);
    }
  });

  it("JWKS publica la clave ES256 sin la parte privada", async () => {
    const r = await SELF.fetch(`${ORIGIN}/.well-known/jwks.json`);
    const j = (await r.json()) as any;
    expect(j.keys[0]).toMatchObject({ kty: "EC", crv: "P-256", alg: "ES256", use: "sig", kid: "test-key" });
    expect(j.keys[0].d).toBeUndefined();
  });

  it("sirve /openapi.json (3.1) y /docs", async () => {
    const r = await SELF.fetch(`${ORIGIN}/openapi.json`);
    const doc = (await r.json()) as any;
    expect(doc.openapi).toBe("3.1.0");
    expect(Object.keys(doc.paths)).toEqual(expect.arrayContaining(["/fhir/Observation", "/auth/token", "/fhir/metadata"]));
    expect(doc.components.securitySchemes.smart.type).toBe("oauth2");
    const d = await SELF.fetch(`${ORIGIN}/docs`);
    expect(d.status).toBe(200);
    expect(await d.text()).toContain("<html");
  });

  it("ruta desconocida → 404 OperationOutcome", async () => {
    const r = await SELF.fetch(`${FHIR}/Encounter`, { headers: { Authorization: "Bearer x" } });
    expect([401, 404]).toContain(r.status);
    const r2 = await SELF.fetch(`${ORIGIN}/nada`);
    expect(r2.status).toBe(404);
    expect(((await r2.json()) as any).resourceType).toBe("OperationOutcome");
  });
});
