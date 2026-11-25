// Exporta la especificación OpenAPI 3.1 a un archivo (para redocly lint en CI).
import { writeFileSync } from "node:fs";
import { buildApp, OPENAPI_INFO } from "../src/app";

const out = process.argv[2] ?? "openapi.json";
const doc = buildApp().getOpenAPI31Document({ ...OPENAPI_INFO, servers: [{ url: "http://localhost:8787", description: "wrangler dev" }] });
writeFileSync(out, JSON.stringify(doc, null, 2));
console.log(`OpenAPI escrito en ${out}`);
