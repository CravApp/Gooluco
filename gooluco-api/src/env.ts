import type { AccessContext } from "./auth/scopes";

export interface Env {
  DB: D1Database;
  /** Clave privada EC P-256 (JWK JSON) para firmar tokens ES256. Secreto. */
  JWT_PRIVATE_JWK: string;
  /** Secreto compartido del cliente de backend de prueba (client_credentials). Secreto. */
  BACKEND_CLIENT_SECRET: string;
  ACCESS_TOKEN_TTL_SECONDS?: string;
  PUBLIC_BASE_URL?: string;
}

export type AppEnv = {
  Bindings: Env;
  Variables: { auth: AccessContext };
};

/** URL pública del servidor (sin barra final). */
export function baseUrl(env: Env, reqUrl: string): string {
  if (env.PUBLIC_BASE_URL) return env.PUBLIC_BASE_URL.replace(/\/+$/, "");
  return new URL(reqUrl).origin;
}

/** Base FHIR (= "aud" que el cliente envía en /authorize y que llevan los access tokens). */
export function fhirBase(env: Env, reqUrl: string): string {
  return `${baseUrl(env, reqUrl)}/fhir`;
}
