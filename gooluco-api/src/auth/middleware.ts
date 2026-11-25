// Validación de Bearer tokens (RFC 6750) para el servidor de recursos FHIR.
import type { MiddlewareHandler } from "hono";
import type { AppEnv } from "../env";
import { baseUrl, fhirBase } from "../env";
import { loadKeys, verifyJwt, JwtError } from "./jwt";
import { fhirError } from "../fhir/outcome";

export const requireBearer: MiddlewareHandler<AppEnv> = async (c, next) => {
  const h = c.req.header("authorization");
  const realm = `Bearer realm="${fhirBase(c.env, c.req.url)}"`;
  if (!h || !/^Bearer\s+/i.test(h)) {
    return fhirError(401, "login", "Se requiere un access token (Authorization: Bearer …)", { "WWW-Authenticate": realm });
  }
  const token = h.replace(/^Bearer\s+/i, "").trim();
  try {
    const keys = await loadKeys(c.env.JWT_PRIVATE_JWK);
    const p = await verifyJwt(token, keys, { iss: baseUrl(c.env, c.req.url), aud: fhirBase(c.env, c.req.url) });
    c.set("auth", {
      sub: String(p.sub),
      clientId: String(p.client_id),
      scopes: String(p.scope ?? "").split(" ").filter(Boolean),
      patient: typeof p.patient === "string" ? p.patient : undefined,
      fhirUser: typeof p.fhirUser === "string" ? p.fhirUser : undefined,
    });
  } catch (e) {
    if (!(e instanceof JwtError)) throw e;
    return fhirError(401, "login", `Token inválido: ${e.message}`, { "WWW-Authenticate": `${realm}, error="invalid_token", error_description="${ascii(e.message)}"` });
  }
  await next();
};

/** Las cabeceras HTTP deben ser ASCII. */
function ascii(s: string): string {
  return s.normalize("NFD").replace(/[^\x20-\x7e]/g, "").replace(/"/g, "'");
}
