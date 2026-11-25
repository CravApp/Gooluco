// CapabilityStatement (GET [base]/metadata) — https://hl7.org/fhir/R4/capabilitystatement.html
// La extensión oauth-uris y el servicio "SMART-on-FHIR" siguen el IG SMART App Launch 2.x
// (https://hl7.org/fhir/smart-app-launch/conformance.html). En SMART 2.x el descubrimiento normativo
// es /.well-known/smart-configuration; la extensión en CapabilityStatement se mantiene por compatibilidad.
import { Q_BASE } from "./questionnaires";

export function capabilityStatement(base: string, fhirBase: string) {
  const json = ["json", "application/fhir+json"];
  const searchParam = (name: string, type: string, documentation: string) => ({ name, type, documentation });
  return {
    resourceType: "CapabilityStatement",
    id: "gooluco-api",
    url: `${fhirBase}/metadata`,
    version: "0.1.0",
    name: "GoolucoFhirApi",
    title: "Gooluco / Equilibra — API FHIR R4 (prueba)",
    status: "draft",
    experimental: true,
    date: "2026-11-25",
    publisher: "CravApp",
    kind: "instance",
    software: { name: "gooluco-api", version: "0.1.0" },
    implementation: { description: "Servidor FHIR R4 mínimo sobre Cloudflare Workers + D1 (datos ficticios)", url: fhirBase },
    fhirVersion: "4.0.1",
    format: json,
    rest: [
      {
        mode: "server",
        security: {
          extension: [
            {
              url: "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris",
              extension: [
                { url: "authorize", valueUri: `${base}/auth/authorize` },
                { url: "token", valueUri: `${base}/auth/token` },
                { url: "introspect", valueUri: `${base}/auth/introspect` },
              ],
            },
          ],
          service: [
            {
              coding: [{ system: "http://terminology.hl7.org/CodeSystem/restful-security-service", code: "SMART-on-FHIR", display: "SMART-on-FHIR" }],
              text: "OAuth2 con SMART App Launch 2.x (authorization_code + PKCE S256; client_credentials simplificado)",
            },
          ],
          description: "Ver [base]/.well-known/smart-configuration",
        },
        resource: [
          {
            type: "Patient",
            interaction: [{ code: "read" }, { code: "search-type" }, { code: "create" }],
            searchParam: [searchParam("_id", "token", "Id lógico")],
          },
          {
            type: "Observation",
            supportedProfile: [
              "http://hl7.org/fhir/StructureDefinition/bodyweight",
              "http://hl7.org/fhir/StructureDefinition/bodyheight",
              "http://hl7.org/fhir/StructureDefinition/bmi",
            ],
            interaction: [{ code: "read" }, { code: "search-type" }, { code: "create" }],
            searchParam: [
              searchParam("_id", "token", "Id lógico"),
              searchParam("patient", "reference", "Paciente (Patient/id o id)"),
              searchParam("subject", "reference", "Igual que patient"),
              searchParam("code", "token", "LOINC: 29463-7 peso, 8302-2 talla, 39156-5 IMC, 8280-0 cintura"),
              searchParam("date", "date", "effective[x]; prefijos eq, ne, gt, lt, ge, le, sa, eb"),
            ],
          },
          {
            type: "QuestionnaireResponse",
            interaction: [{ code: "read" }, { code: "search-type" }, { code: "create" }],
            searchParam: [
              searchParam("_id", "token", "Id lógico"),
              searchParam("patient", "reference", "Paciente"),
              searchParam("subject", "reference", "Igual que patient"),
              searchParam("questionnaire", "reference", `Canónico, p. ej. ${Q_BASE}/pss4-ema`),
              searchParam("authored", "date", "Fecha de respuesta"),
            ],
          },
          {
            type: "Questionnaire",
            interaction: [{ code: "read" }, { code: "search-type" }],
            searchParam: [searchParam("url", "uri", "URL canónica"), searchParam("name", "string", "Nombre"), searchParam("_id", "token", "Id lógico")],
          },
        ],
      },
    ],
  };
}

/** SMART App Launch 2.x §"FHIR Authorization Endpoint and Capabilities Discovery". */
export function smartConfiguration(base: string) {
  return {
    issuer: base,
    jwks_uri: `${base}/.well-known/jwks.json`,
    authorization_endpoint: `${base}/auth/authorize`,
    token_endpoint: `${base}/auth/token`,
    introspection_endpoint: `${base}/auth/introspect`,
    token_endpoint_auth_methods_supported: ["client_secret_basic", "client_secret_post"],
    grant_types_supported: ["authorization_code", "refresh_token", "client_credentials"],
    scopes_supported: [
      "openid", "fhirUser", "launch/patient", "offline_access", "online_access",
      "patient/Patient.rs", "patient/Observation.crs", "patient/Observation.rs", "patient/Observation.c",
      "patient/QuestionnaireResponse.crs",
      "system/Patient.crs", "system/Observation.crs", "system/QuestionnaireResponse.crs",
    ],
    response_types_supported: ["code"],
    code_challenge_methods_supported: ["S256"],
    capabilities: [
      "launch-standalone",
      "client-public",
      "client-confidential-symmetric",
      "context-standalone-patient",
      "permission-patient",
      "permission-offline",
      "permission-online",
      "permission-v2",
      "permission-v1",
      "sso-openid-connect",
      "authorize-post",
    ],
  };
}

export function openidConfiguration(base: string) {
  return {
    issuer: base,
    authorization_endpoint: `${base}/auth/authorize`,
    token_endpoint: `${base}/auth/token`,
    jwks_uri: `${base}/.well-known/jwks.json`,
    response_types_supported: ["code"],
    subject_types_supported: ["public"],
    id_token_signing_alg_values_supported: ["ES256"],
    scopes_supported: ["openid", "fhirUser"],
    claims_supported: ["iss", "sub", "aud", "exp", "iat", "nonce", "fhirUser"],
    code_challenge_methods_supported: ["S256"],
  };
}
