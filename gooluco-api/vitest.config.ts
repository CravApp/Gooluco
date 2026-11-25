import path from "node:path";
import { webcrypto } from "node:crypto";
import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    cloudflareTest(async () => {
      const migrations = await readD1Migrations(path.join(import.meta.dirname, "migrations"));
      const seed = await readD1Migrations(path.join(import.meta.dirname, "seed"));
      // Clave efímera solo para la corrida de tests: nunca se escribe a disco ni al repo.
      const { privateKey } = await webcrypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
      const jwk = await webcrypto.subtle.exportKey("jwk", privateKey);
      return {
        wrangler: { configPath: "./wrangler.toml" },
        miniflare: {
          bindings: {
            TEST_MIGRATIONS: migrations,
            TEST_SEED: seed,
            JWT_PRIVATE_JWK: JSON.stringify({ ...jwk, kid: "test-key" }),
            BACKEND_CLIENT_SECRET: "secreto-solo-para-tests",
          },
        },
      };
    }),
  ],
  test: { setupFiles: ["./test/apply-migrations.ts"] },
});
