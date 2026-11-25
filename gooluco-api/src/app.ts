import { OpenAPIHono, z } from "@hono/zod-openapi";
import { Scalar } from "@scalar/hono-api-reference";
import { cors } from "hono/cors";
import type { AppEnv } from "./env";
import { authRoutes } from "./auth/routes";
import { requireBearer } from "./auth/middleware";
import { registerFhirRoutes } from "./fhir/routes";
import { fhirError } from "./fhir/outcome";

export const OPENAPI_INFO = {
  openapi: "3.1.0",
  info: {
    title: "Gooluco / Equilibra — API FHIR R4 + SMART on FHIR (prueba)",
    version: "0.1.0",
    description:
      "Subconjunto FHIR R4 (Patient, Observation, QuestionnaireResponse, Questionnaire) protegido con OAuth2 según SMART App Launch 2.x. " +
      "Servidor de PRUEBA: usuarios y datos ficticios. Ver https://hl7.org/fhir/R4/ y https://hl7.org/fhir/smart-app-launch/.",
    license: { name: "Propietario (CravApp)", identifier: "LicenseRef-CravApp" },
  },
  tags: [
    { name: "SMART", description: "Descubrimiento y servidor de autorización OAuth2/SMART" },
    { name: "FHIR", description: "Capacidades del servidor" },
    { name: "Patient", description: "Paciente" },
    { name: "Observation", description: "Peso (29463-7), talla (8302-2), IMC (39156-5), cintura (8280-0)" },
    { name: "QuestionnaireResponse", description: "Respuestas PSS-4 / FINDRISC / Nórdico" },
    { name: "Questionnaire", description: "Definiciones de cuestionarios" },
  ],
};

const OIDC_SCOPES: Record<string, string> = {
  openid: "OpenID Connect (id_token)",
  fhirUser: "URL del recurso FHIR del usuario",
  "launch/patient": "Contexto de paciente en launch standalone",
  offline_access: "Refresh token",
  online_access: "Refresh token mientras el usuario esté en línea",
};
function resourceScopes(ctx: "patient" | "system"): Record<string, string> {
  const out: Record<string, string> = {};
  const perms: Record<string, string> = { c: "crear", r: "leer", s: "buscar", rs: "leer y buscar", crs: "crear, leer y buscar" };
  for (const t of ["Patient", "Observation", "QuestionnaireResponse"]) {
    for (const [p, d] of Object.entries(perms)) out[`${ctx}/${t}.${p}`] = `${t}: ${d}${ctx === "patient" ? " (paciente en contexto)" : ""}`;
  }
  return out;
}

const PUBLIC_FHIR = [/^\/fhir\/metadata$/, /^\/fhir\/\.well-known\//, /^\/fhir\/Questionnaire(\/|$)/];

export function buildApp() {
  const app = new OpenAPIHono<AppEnv>({
    defaultHook: (result) => {
      if (!result.success) {
        const issues = result.error.issues.map((i) => `${i.path.join(".") || "(raíz)"}: ${i.message}`).join("; ");
        return fhirError(400, "invalid", `Validación: ${issues}`);
      }
    },
  });

  // CORS para clientes web de prueba (la app Android no lo necesita).
  app.use("*", cors({ origin: "*", allowHeaders: ["Authorization", "Content-Type"], exposeHeaders: ["Location", "ETag"] }));

  // Content-Type de escritura FHIR (https://hl7.org/fhir/R4/http.html#mime-type)
  app.use("/fhir/*", async (c, next) => {
    if (c.req.method === "POST" || c.req.method === "PUT") {
      const ct = (c.req.header("content-type") ?? "").split(";")[0].trim().toLowerCase();
      if (ct !== "application/fhir+json" && ct !== "application/json")
        return fhirError(415, "not-supported", "Content-Type debe ser application/fhir+json");
    }
    const accept = c.req.header("accept");
    if (accept && !/\*\/\*|application\/(fhir\+)?json|application\/\*/.test(accept))
      return fhirError(406, "not-supported", "Solo se sirve application/fhir+json");
    await next();
  });

  // Bearer obligatorio salvo recursos públicos
  app.use("/fhir/*", async (c, next) => {
    if (PUBLIC_FHIR.some((r) => r.test(c.req.path))) return next();
    return requireBearer(c, next);
  });

  app.route("/", authRoutes);
  registerFhirRoutes(app);
  registerAuthDocs(app);

  app.openAPIRegistry.registerComponent("securitySchemes", "smart", {
    type: "oauth2",
    description:
      "SMART App Launch 2.x. authorization_code exige PKCE S256 y el parámetro aud = {servidor}/fhir. client_credentials usa secreto compartido (simplificación de SMART Backend Services).",
    flows: {
      authorizationCode: {
        authorizationUrl: "/auth/authorize",
        tokenUrl: "/auth/token",
        refreshUrl: "/auth/token",
        scopes: { ...OIDC_SCOPES, ...resourceScopes("patient") },
      },
      clientCredentials: {
        tokenUrl: "/auth/token",
        scopes: resourceScopes("system"),
      },
    },
  });
  app.openAPIRegistry.registerComponent("securitySchemes", "bearer", { type: "http", scheme: "bearer", bearerFormat: "JWT", description: "Pegue aquí un access token obtenido con el flujo SMART" });

  app.doc31("/openapi.json", (c) => ({ ...OPENAPI_INFO, servers: [{ url: new URL(c.req.url).origin, description: "Este servidor" }] }));
  app.get("/docs", Scalar({ url: "/openapi.json", pageTitle: "Gooluco API — docs" }));
  app.get("/", (c) => c.redirect("/docs"));

  app.notFound((c) => fhirError(404, "not-found", `Ruta no soportada: ${c.req.method} ${c.req.path}`));
  app.onError((err) => {
    console.error(err);
    return fhirError(500, "exception", "Error interno del servidor");
  });
  return app;
}

function registerAuthDocs(app: OpenAPIHono<AppEnv>) {
  const json = (description: string, schema: z.ZodType = z.looseObject({})) => ({ description, content: { "application/json": { schema } } });
  const OAuthError = z.object({ error: z.string(), error_description: z.string().optional() }).openapi("OAuthError");
  const r = app.openAPIRegistry;
  r.registerPath({ method: "get", path: "/fhir/.well-known/smart-configuration", operationId: "getWellKnownSmartConfigurationFhir", tags: ["SMART"], summary: "Descubrimiento SMART (SMART App Launch 2.x §Conformance)", responses: { 200: json("smart-configuration") } });
  r.registerPath({ method: "get", path: "/.well-known/smart-configuration", operationId: "getWellKnownSmartConfiguration", tags: ["SMART"], summary: "Alias en la raíz", responses: { 200: json("smart-configuration") } });
  r.registerPath({ method: "get", path: "/.well-known/openid-configuration", operationId: "getWellKnownOpenidConfiguration", tags: ["SMART"], summary: "Metadatos OpenID Connect", responses: { 200: json("openid-configuration") } });
  r.registerPath({ method: "get", path: "/.well-known/jwks.json", operationId: "getWellKnownJwks", tags: ["SMART"], summary: "Clave pública ES256 de los tokens", responses: { 200: json("JWKS") } });
  r.registerPath({
    method: "get", path: "/auth/authorize", operationId: "getAuthAuthorize", tags: ["SMART"], summary: "Autorización (página de consentimiento de PRUEBA)",
    request: {
      query: z.object({
        response_type: z.literal("code"), client_id: z.string().openapi({ example: "gooluco-android" }),
        redirect_uri: z.string().openapi({ example: "pe.cravapp.gooluco:/oauth2redirect" }),
        scope: z.string().openapi({ example: "launch/patient openid fhirUser offline_access patient/Observation.rs patient/Observation.c" }),
        state: z.string(), aud: z.string().openapi({ example: "https://api.example/fhir" }),
        code_challenge: z.string(), code_challenge_method: z.literal("S256"), nonce: z.string().optional(),
      }),
    },
    responses: { 200: { description: "HTML de consentimiento", content: { "text/html": { schema: z.string() } } }, 302: { description: "Redirección con error" }, 400: { description: "client_id/redirect_uri inválidos", content: { "text/html": { schema: z.string() } } } },
  });
  r.registerPath({
    method: "post", path: "/auth/authorize", operationId: "postAuthAuthorize", tags: ["SMART"], summary: "Decisión del consentimiento (o authorize-post)",
    request: { body: { content: { "application/x-www-form-urlencoded": { schema: z.looseObject({ decision: z.enum(["allow", "deny"]).optional(), user: z.string().optional() }) } } } },
    responses: { 200: { description: "HTML de consentimiento", content: { "text/html": { schema: z.string() } } }, 302: { description: "Redirección a redirect_uri con code&state o error" } },
  });
  r.registerPath({
    method: "post", path: "/auth/token", operationId: "postAuthToken", tags: ["SMART"], summary: "Token (authorization_code+PKCE, refresh_token, client_credentials)",
    request: {
      body: {
        required: true,
        content: {
          "application/x-www-form-urlencoded": {
            schema: z.object({
              grant_type: z.enum(["authorization_code", "refresh_token", "client_credentials"]),
              code: z.string().optional(), redirect_uri: z.string().optional(), code_verifier: z.string().optional(),
              client_id: z.string().optional(), client_secret: z.string().optional(), refresh_token: z.string().optional(), scope: z.string().optional(),
            }),
          },
        },
      },
    },
    responses: {
      200: json("Token response", z.object({ access_token: z.string(), token_type: z.literal("Bearer"), expires_in: z.number(), scope: z.string(), patient: z.string().optional(), id_token: z.string().optional(), refresh_token: z.string().optional() }).openapi("TokenResponse")),
      400: json("Error OAuth2", OAuthError), 401: json("Cliente no autenticado", OAuthError),
    },
  });
  r.registerPath({
    method: "post", path: "/auth/introspect", operationId: "postAuthIntrospect", tags: ["SMART"], summary: "Introspección (RFC 7662), clientes confidenciales",
    request: { body: { content: { "application/x-www-form-urlencoded": { schema: z.object({ token: z.string() }) } } } },
    responses: { 200: json("Estado del token"), 401: json("Cliente no autenticado", OAuthError) },
  });
}
