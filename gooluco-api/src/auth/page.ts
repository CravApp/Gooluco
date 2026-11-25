// Página de autorización de PRUEBA (usuarios ficticios, sin contraseña). No usar con datos reales.
import { TEST_USERS, type Client } from "./clients";

const esc = (s: string) => s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);

export function authorizePage(client: Client, params: Record<string, string>, grantedPreview: string[]): string {
  const hidden = Object.entries(params)
    .map(([k, v]) => `<input type="hidden" name="${esc(k)}" value="${esc(v)}">`)
    .join("\n");
  const users = TEST_USERS.map(
    (u, i) => `<label class="u"><input type="radio" name="user" value="${esc(u.id)}" ${i === 0 ? "checked" : ""}> ${esc(u.display)}</label>`,
  ).join("\n");
  const scopes = grantedPreview.map((s) => `<li><code>${esc(s)}</code></li>`).join("");
  return `<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Autorizar — Gooluco (prueba)</title>
<style>body{font-family:system-ui,sans-serif;max-width:560px;margin:2rem auto;padding:0 1rem;color:#1b2a2f}
.warn{background:#fff4d6;border:1px solid #e7c35a;padding:.6rem .8rem;border-radius:8px}
.u{display:block;padding:.5rem;border:1px solid #d6dde0;border-radius:8px;margin:.4rem 0}
button{padding:.6rem 1.2rem;border-radius:8px;border:0;margin-right:.5rem;font-size:1rem;cursor:pointer}
.ok{background:#0f7b6c;color:#fff}.no{background:#e3e7e9}</style></head>
<body>
<h1>Autorizar acceso</h1>
<p class="warn"><strong>Servidor de PRUEBA.</strong> Los usuarios son ficticios y no hay contraseña. No ingreses datos reales.</p>
<p><strong>${esc(client.name)}</strong> (<code>${esc(client.clientId)}</code>) solicita:</p>
<ul>${scopes || "<li>(ningún scope concedible)</li>"}</ul>
<form method="post" action="/auth/authorize">
${hidden}
<fieldset style="border:0;padding:0"><legend>Inicia sesión como (usuario de prueba):</legend>
${users}
</fieldset>
<p><button class="ok" name="decision" value="allow" type="submit">Autorizar</button>
<button class="no" name="decision" value="deny" type="submit">Denegar</button></p>
</form>
</body></html>`;
}

export function errorPage(message: string): string {
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Error de autorización</title></head>
<body style="font-family:system-ui,sans-serif;max-width:560px;margin:2rem auto"><h1>Error de autorización</h1><p>${esc(message)}</p></body></html>`;
}
