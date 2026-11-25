# Colección Postman — validar los usuarios ficticios ana y beto

Archivos:

| Archivo | Qué es |
|---|---|
| `gooluco-api.postman_collection.json` | Colección Postman v2.1 con scripts de prueba (`pm.test`) en cada request |
| `gooluco-api-live.postman_environment.json` | Environment del despliegue de prueba `https://gooluco-api.mercadeoperuviano.workers.dev` (sin secretos) |
| `build_collection.py` | Generador de ambos JSON (`python3 postman/build_collection.py`); edita aquí y regenera |

> Solo usuarios y datos **FICTICIOS** (`ana` → `Patient/demo-ana`, `beto` → `Patient/demo-beto`).
> No hay contraseñas: la página de consentimiento es de prueba.

## Carpetas (ejecutar en este orden)

1. **Descubrimiento** — `GET /fhir/metadata`, `GET /fhir/.well-known/smart-configuration`, `Questionnaire/pss4-ema`.
2. **Ana** y 3. **Beto** — por cada usuario:
   1. *Autorizar*: el pre-request genera `code_verifier` y `code_challenge` S256 con CryptoJS y un `state`;
      hace `POST /auth/authorize` como el usuario de prueba (`decision=allow`, `user=ana|beto`) con
      **followRedirects desactivado**, y el test toma el `code` del header `Location` del 302 y verifica el `state`.
   2. *Token*: canjea `code` + `code_verifier` en `/auth/token` y guarda `{usuario}_access_token`,
      `{usuario}_refresh_token` y `{usuario}_patient` en variables de la colección.
   3. Leer su `Patient`.
   4–6. `POST Observation` de peso (29463-7, kg), talla (8302-2, cm) e IMC (39156-5, kg/m2, con `derivedFrom`), valores ficticios.
   7. Buscar sus Observations por `patient` + `code` + `date` (deben aparecer las 3 recién creadas).
   8–9. `POST QuestionnaireResponse` PSS-4 (total calculado con ítems 2 y 3 invertidos) y búsqueda por `questionnaire` + `authored`.
   10–12. Refresh token con **rotación**, reuso del refresh anterior → `invalid_grant`, y el token renovado funciona.
4. **Seguridad cruzada** (usa los tokens de Ana y Beto): sin token → 401; token manipulado → 401;
   Ana lee `Patient/demo-beto` → 403; Beto lee el peso y la PSS-4 de Ana → 403; Beto busca
   Observations de Ana → Bundle vacío; Ana crea un peso para Beto → 403; login de Ana con scope limitado
   (`patient/Observation.rs`) → `POST Observation`, `QuestionnaireResponse` y `Patient` → 403 (`insufficient_scope`),
   pero sí puede buscar sus Observations.
5. **OAuth 2.0 interactivo (Postman UI)** — ejemplo para hacerlo a mano (no se ejecuta con newman).

## Importar y ejecutar en Postman

1. *Import* → arrastra los dos JSON.
2. Arriba a la derecha elige el environment **Gooluco API — live (workers.dev)**.
3. Clic derecho en la colección → *Run collection*. Deja marcadas Descubrimiento, Ana, Beto y Seguridad cruzada
   (desmarca la carpeta *OAuth 2.0 interactivo*) y ejecuta en ese orden.

## Ejecutar con newman (línea de comandos)

```bash
npm i -g newman newman-reporter-htmlextra
newman run postman/gooluco-api.postman_collection.json \
  -e postman/gooluco-api-live.postman_environment.json \
  --folder Descubrimiento --folder Ana --folder Beto --folder "Seguridad cruzada" \
  -r cli,htmlextra --reporter-htmlextra-export newman-report.html
```

Para correrla contra `wrangler dev`, duplica el environment y cambia `base_url` a `http://127.0.0.1:8787` y
`fhir_base` a `http://127.0.0.1:8787/fhir` (antes: `npm run db:migrate:local && npm run db:seed:local`).

## OAuth 2.0 interactivo en la interfaz de Postman

El cliente de prueba `gooluco-android` acepta el callback de Postman `https://oauth.pstmn.io/v1/callback`
(agregado en `src/auth/clients.ts` solo para pruebas). En la carpeta *OAuth 2.0 interactivo* la request ya trae
esta configuración en la pestaña **Authorization** (tipo *OAuth 2.0*):

| Campo | Valor |
|---|---|
| Grant type | **Authorization Code (With PKCE)** |
| Callback URL | `https://oauth.pstmn.io/v1/callback` (sin «Authorize using browser», o con él; ambos usan ese callback) |
| Auth URL | `{{base_url}}/auth/authorize` |
| Access Token URL | `{{base_url}}/auth/token` |
| Client ID | `gooluco-android` (Client Secret vacío: cliente público) |
| Code Challenge Method | `SHA-256` |
| Scope | `launch/patient openid fhirUser offline_access patient/Patient.rs patient/Observation.crs patient/QuestionnaireResponse.crs` |
| State | cualquier valor |
| Client Authentication | *Send client credentials in body* |
| **Advanced → Auth Request** | agregar `aud` = `{{fhir_base}}` (*Send in URL*) — SMART lo exige |

Pasos: *Get New Access Token* → se abre la página «Servidor de PRUEBA» → elige **Ana** o **Beto** → *Autorizar* →
*Use Token*. La respuesta del token trae `patient` (`demo-ana` o `demo-beto`); pon ese valor en la variable de
colección `oauth_patient` y envía la request *Patient en contexto*. Debe responder 200.
