// Genera .dev.vars (ignorado por git) con una clave ES256 nueva y un secreto de backend aleatorio.
import { webcrypto as crypto } from "node:crypto";
import { existsSync, writeFileSync } from "node:fs";

const file = new URL("../.dev.vars", import.meta.url);
if (existsSync(file) && !process.argv.includes("--force")) {
  console.log(".dev.vars ya existe (usa --force para regenerarlo).");
  process.exit(0);
}
const { privateKey } = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
const jwk = await crypto.subtle.exportKey("jwk", privateKey);
delete jwk.key_ops;
delete jwk.ext;
jwk.kid = `dev-${Date.now().toString(36)}`;
const secret = Buffer.from(crypto.getRandomValues(new Uint8Array(24))).toString("base64url");
writeFileSync(file, `JWT_PRIVATE_JWK='${JSON.stringify(jwk)}'\nBACKEND_CLIENT_SECRET='${secret}'\n`, { mode: 0o600 });
console.log("Escrito .dev.vars (no lo subas a git).");
