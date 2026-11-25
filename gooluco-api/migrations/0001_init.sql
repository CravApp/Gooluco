-- Gooluco API: almacenamiento genérico de recursos FHIR R4 + estado OAuth2/SMART.
CREATE TABLE IF NOT EXISTS resource (
  type          TEXT NOT NULL,             -- Patient | Observation | QuestionnaireResponse
  id            TEXT NOT NULL,
  version_id    INTEGER NOT NULL DEFAULT 1,
  last_updated  TEXT NOT NULL,             -- ISO-8601 UTC
  patient_id    TEXT,                      -- compartimento Patient (para scopes patient/*)
  code_system   TEXT,                      -- Observation.code.coding[0].system
  code          TEXT,                      -- Observation.code.coding[0].code
  date          TEXT,                      -- Observation.effective / QR.authored (ISO UTC)
  questionnaire TEXT,                      -- QuestionnaireResponse.questionnaire
  json          TEXT NOT NULL,
  PRIMARY KEY (type, id)
);
CREATE INDEX IF NOT EXISTS idx_resource_patient ON resource (type, patient_id, date);
CREATE INDEX IF NOT EXISTS idx_resource_code ON resource (type, code, date);

-- Índice auxiliar de todos los códigos de Observation.code.coding (búsqueda por token).
CREATE TABLE IF NOT EXISTS resource_code (
  type   TEXT NOT NULL,
  id     TEXT NOT NULL,
  system TEXT,
  code   TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_resource_code_lookup ON resource_code (type, code, system);

-- Códigos de autorización de un solo uso (se guardan como hash SHA-256).
CREATE TABLE IF NOT EXISTS auth_code (
  code_hash      TEXT PRIMARY KEY,
  client_id      TEXT NOT NULL,
  redirect_uri   TEXT NOT NULL,
  code_challenge TEXT NOT NULL,
  scope          TEXT NOT NULL,
  user_id        TEXT NOT NULL,
  patient_id     TEXT,
  fhir_user      TEXT,
  nonce          TEXT,
  expires_at     INTEGER NOT NULL,         -- epoch segundos
  used           INTEGER NOT NULL DEFAULT 0
);

-- Refresh tokens (offline_access), guardados como hash SHA-256.
CREATE TABLE IF NOT EXISTS refresh_token (
  token_hash  TEXT PRIMARY KEY,
  client_id   TEXT NOT NULL,
  scope       TEXT NOT NULL,
  user_id     TEXT NOT NULL,
  patient_id  TEXT,
  fhir_user   TEXT,
  expires_at  INTEGER NOT NULL,
  revoked     INTEGER NOT NULL DEFAULT 0
);
