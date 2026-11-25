// Tipos de los bindings disponibles en `cloudflare:test`.
declare namespace Cloudflare {
  interface Env {
    DB: D1Database;
    JWT_PRIVATE_JWK: string;
    BACKEND_CLIENT_SECRET: string;
    ACCESS_TOKEN_TTL_SECONDS?: string;
    TEST_MIGRATIONS: import("cloudflare:test").D1Migration[];
    TEST_SEED: import("cloudflare:test").D1Migration[];
  }
}
