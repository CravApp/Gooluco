// Registro ESTÁTICO de clientes y usuarios de PRUEBA. Todos los datos son ficticios.
import type { Env } from "../env";

export interface Client {
  clientId: string;
  name: string;
  type: "public" | "confidential-symmetric";
  grantTypes: ("authorization_code" | "refresh_token" | "client_credentials")[];
  redirectUris: string[];
  /** Si true, se acepta cualquier redirect de loopback http://127.0.0.1:<puerto>/… o http://localhost:<puerto>/… (RFC 8252 §7.3). */
  allowLoopback?: boolean;
  allowedScopes: string[];
  secret?: (env: Env) => string | undefined;
}

export const CLIENTS: Client[] = [
  {
    clientId: "gooluco-android",
    name: "Equilibra (app Android Gooluco)",
    type: "public",
    grantTypes: ["authorization_code", "refresh_token"],
    // Postman (OAuth 2.0 interactivo) usa su callback fijo; solo para pruebas.
    redirectUris: ["pe.cravapp.gooluco:/oauth2redirect", "https://oauth.pstmn.io/v1/callback"],
    allowLoopback: true,
    allowedScopes: [
      "openid",
      "fhirUser",
      "launch/patient",
      "offline_access",
      "online_access",
      "patient/Patient.rs",
      "patient/Observation.crs",
      "patient/QuestionnaireResponse.crs",
    ],
  },
  {
    clientId: "gooluco-backend",
    name: "Servicio de backend de prueba (SMART Backend Services simplificado)",
    type: "confidential-symmetric",
    grantTypes: ["client_credentials"],
    redirectUris: [],
    allowedScopes: [
      "system/Patient.crs",
      "system/Observation.crs",
      "system/QuestionnaireResponse.crs",
    ],
    secret: (env) => env.BACKEND_CLIENT_SECRET,
  },
];

export function findClient(id: string | undefined | null): Client | undefined {
  return CLIENTS.find((c) => c.clientId === id);
}

export function redirectAllowed(client: Client, uri: string): boolean {
  if (client.redirectUris.includes(uri)) return true;
  if (!client.allowLoopback) return false;
  try {
    const u = new URL(uri);
    return u.protocol === "http:" && (u.hostname === "127.0.0.1" || u.hostname === "localhost" || u.hostname === "[::1]") && !u.hash;
  } catch {
    return false;
  }
}

export interface TestUser {
  id: string;
  display: string;
  patientId: string;
}

/** Usuarios ficticios para la página de autorización de prueba. */
export const TEST_USERS: TestUser[] = [
  { id: "ana", display: "Ana Prueba (FICTICIA) — Patient/demo-ana", patientId: "demo-ana" },
  { id: "beto", display: "Beto Ejemplo (FICTICIO) — Patient/demo-beto", patientId: "demo-beto" },
];

export function findUser(id: string | undefined | null): TestUser | undefined {
  return TEST_USERS.find((u) => u.id === id);
}

/** Comparación en tiempo constante (para secretos). */
export function safeEqual(a: string, b: string): boolean {
  const ea = new TextEncoder().encode(a);
  const eb = new TextEncoder().encode(b);
  let diff = ea.length ^ eb.length;
  const n = Math.max(ea.length, eb.length);
  for (let i = 0; i < n; i++) diff |= (ea[i % (ea.length || 1)] ?? 0) ^ (eb[i % (eb.length || 1)] ?? 0);
  return diff === 0 && ea.length === eb.length;
}
