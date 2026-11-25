#!/usr/bin/env bash
# Prueba de humo contra `wrangler dev` (o un despliegue): flujo PKCE completo con curl.
# Uso: BASE=http://127.0.0.1:8787 ./scripts/smoke.sh
set -euo pipefail
BASE="${BASE:-http://127.0.0.1:8787}"
FHIR="$BASE/fhir"
REDIRECT="http://127.0.0.1:53682/callback"   # loopback (RFC 8252)
SCOPE="launch/patient openid fhirUser offline_access patient/Patient.rs patient/Observation.rs patient/Observation.c"

echo "== 1. Descubrimiento"
curl -fsS "$FHIR/metadata" | jq -c '{resourceType, fhirVersion, security: .rest[0].security.service[0].coding[0].code}'
curl -fsS "$FHIR/.well-known/smart-configuration" | jq -c '{authorization_endpoint, token_endpoint, code_challenge_methods_supported}'

echo "== 2. PKCE: code_verifier y code_challenge (S256)"
VERIFIER=$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)
CHALLENGE=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')
STATE=$(openssl rand -hex 8)

echo "== 3. /auth/authorize (página de consentimiento de prueba)"
AUTH_Q=(--data-urlencode response_type=code --data-urlencode client_id=gooluco-android --data-urlencode "redirect_uri=$REDIRECT"
  --data-urlencode "scope=$SCOPE" --data-urlencode "state=$STATE" --data-urlencode "aud=$FHIR"
  --data-urlencode "code_challenge=$CHALLENGE" --data-urlencode code_challenge_method=S256)
curl -fsS -G "$BASE/auth/authorize" "${AUTH_Q[@]}" | grep -o 'Servidor de PRUEBA' | head -1
# Simula el clic "Autorizar" como el usuario ficticio "ana"
LOC=$(curl -sS -o /dev/null -w '%{redirect_url}' -X POST "$BASE/auth/authorize" "${AUTH_Q[@]}" --data-urlencode decision=allow --data-urlencode user=ana)
echo "redirect -> ${LOC%%code=*}code=…"
CODE=$(printf '%s' "$LOC" | sed -E 's/.*[?&]code=([^&]+).*/\1/')
GOT_STATE=$(printf '%s' "$LOC" | sed -E 's/.*[?&]state=([^&]+).*/\1/')
[ "$GOT_STATE" = "$STATE" ] && echo "state OK"

echo "== 4. /auth/token (authorization_code + code_verifier)"
TOKEN_JSON=$(curl -fsS -X POST "$BASE/auth/token" -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=authorization_code --data-urlencode "code=$CODE" --data-urlencode "redirect_uri=$REDIRECT" \
  --data-urlencode "code_verifier=$VERIFIER" --data-urlencode client_id=gooluco-android)
echo "$TOKEN_JSON" | jq -c '{token_type, expires_in, scope, patient, has_id_token: (.id_token != null), has_refresh: (.refresh_token != null)}'
AT=$(echo "$TOKEN_JSON" | jq -r .access_token)

echo "== 5. POST Observation (peso OKOK simulado)"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
CREATED=$(curl -fsS -X POST "$FHIR/Observation" -H "Authorization: Bearer $AT" -H 'Content-Type: application/fhir+json' -d @- <<JSON
{"resourceType":"Observation","status":"final",
 "category":[{"coding":[{"system":"http://terminology.hl7.org/CodeSystem/observation-category","code":"vital-signs"}]}],
 "code":{"coding":[{"system":"http://loinc.org","code":"29463-7","display":"Body weight"}]},
 "subject":{"reference":"Patient/demo-ana"},"effectiveDateTime":"$NOW",
 "device":{"display":"Balanza OKOK (simulada)"},
 "valueQuantity":{"value":69.8,"unit":"kg","system":"http://unitsofmeasure.org","code":"kg"}}
JSON
)
OBS_ID=$(echo "$CREATED" | jq -r .id)
echo "$CREATED" | jq -c '{id, meta, value: .valueQuantity.value}'

echo "== 6. GET search Observation?patient&code&date"
TODAY=$(date -u +%Y-%m-%d)
curl -fsS -G "$FHIR/Observation" -H "Authorization: Bearer $AT" \
  --data-urlencode patient=Patient/demo-ana --data-urlencode "code=http://loinc.org|29463-7" --data-urlencode "date=ge$TODAY" \
  | jq -c --arg id "$OBS_ID" '{resourceType, type, total, found: ([.entry[].resource.id] | index($id) != null)}'

echo "== 7. Denegación de scope (token sin QuestionnaireResponse) y de compartimento"
curl -sS -o /dev/null -w 'QuestionnaireResponse search -> HTTP %{http_code}\n' "$FHIR/QuestionnaireResponse" -H "Authorization: Bearer $AT"
curl -sS -o /dev/null -w 'Patient/demo-beto (otro paciente) -> HTTP %{http_code}\n' "$FHIR/Patient/demo-beto" -H "Authorization: Bearer $AT"
curl -sS -o /dev/null -w 'Sin token -> HTTP %{http_code}\n' "$FHIR/Observation"
echo "OK"
