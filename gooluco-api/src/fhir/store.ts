// Persistencia en D1 de recursos FHIR como JSON con columnas indexadas para búsqueda.
import { dateSql, type DateRange } from "./search";

export type StoredType = "Patient" | "Observation" | "QuestionnaireResponse";

export interface IndexFields {
  patientId?: string | null;
  codings?: { system?: string; code: string }[];
  date?: string | null;
  questionnaire?: string | null;
}

export type FhirResource = Record<string, unknown> & { resourceType: string; id?: string; meta?: Record<string, unknown> };

export async function createResource(db: D1Database, type: StoredType, resource: FhirResource, idx: IndexFields): Promise<FhirResource> {
  const id = crypto.randomUUID();
  const lastUpdated = new Date().toISOString();
  const meta = { ...(resource.meta ?? {}), versionId: "1", lastUpdated };
  const stored: FhirResource = { ...resource, resourceType: type, id, meta };
  const first = idx.codings?.[0];
  const stmts = [
    db
      .prepare(
        "INSERT INTO resource (type, id, version_id, last_updated, patient_id, code_system, code, date, questionnaire, json) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?)",
      )
      .bind(type, id, lastUpdated, idx.patientId ?? null, first?.system ?? null, first?.code ?? null, idx.date ?? null, idx.questionnaire ?? null, JSON.stringify(stored)),
    ...(idx.codings ?? []).map((c) =>
      db.prepare("INSERT INTO resource_code (type, id, system, code) VALUES (?, ?, ?, ?)").bind(type, id, c.system ?? null, c.code),
    ),
  ];
  await db.batch(stmts);
  return stored;
}

export interface StoredRow { json: string; patient_id: string | null; version_id: number }

export async function readResource(db: D1Database, type: StoredType, id: string): Promise<{ resource: FhirResource; patientId: string | null; versionId: number } | null> {
  const row = await db.prepare("SELECT json, patient_id, version_id FROM resource WHERE type = ? AND id = ?").bind(type, id).first<StoredRow>();
  if (!row) return null;
  return { resource: JSON.parse(row.json), patientId: row.patient_id, versionId: row.version_id };
}

export interface SearchCriteria {
  ids?: string[];
  patientIds?: string[];
  tokens?: { system?: string | null; code?: string }[][]; // AND de OR
  dates?: DateRange[];
  questionnaires?: string[];
  count: number;
  offset: number;
  sortDateDesc?: boolean;
}

export async function searchResources(db: D1Database, type: StoredType, c: SearchCriteria): Promise<{ total: number; resources: FhirResource[] }> {
  const where: string[] = ["r.type = ?"];
  const args: unknown[] = [type];
  if (c.ids?.length) { where.push(`r.id IN (${c.ids.map(() => "?").join(",")})`); args.push(...c.ids); }
  if (c.patientIds?.length) { where.push(`r.patient_id IN (${c.patientIds.map(() => "?").join(",")})`); args.push(...c.patientIds); }
  if (c.questionnaires?.length) { where.push(`r.questionnaire IN (${c.questionnaires.map(() => "?").join(",")})`); args.push(...c.questionnaires); }
  for (const group of c.tokens ?? []) {
    const ors: string[] = [];
    for (const t of group) {
      const parts = ["rc.type = r.type", "rc.id = r.id"];
      if (t.code !== undefined) { parts.push("rc.code = ?"); args.push(t.code); }
      if (t.system === null) parts.push("rc.system IS NULL");
      else if (t.system !== undefined) { parts.push("rc.system = ?"); args.push(t.system); }
      ors.push(`EXISTS (SELECT 1 FROM resource_code rc WHERE ${parts.join(" AND ")})`);
    }
    if (ors.length) where.push(`(${ors.join(" OR ")})`);
  }
  for (const d of c.dates ?? []) {
    const f = dateSql("r.date", d);
    where.push(`r.date IS NOT NULL AND ${f.sql}`);
    args.push(...f.args);
  }
  const w = where.join(" AND ");
  const totalRow = await db.prepare(`SELECT COUNT(*) AS n FROM resource r WHERE ${w}`).bind(...args).first<{ n: number }>();
  const order = c.sortDateDesc === false ? "r.date ASC, r.id ASC" : "r.date DESC, r.last_updated DESC, r.id ASC";
  const rows = await db
    .prepare(`SELECT r.json FROM resource r WHERE ${w} ORDER BY ${order} LIMIT ? OFFSET ?`)
    .bind(...args, c.count, c.offset)
    .all<{ json: string }>();
  return { total: totalRow?.n ?? 0, resources: rows.results.map((r) => JSON.parse(r.json)) };
}

export async function patientExists(db: D1Database, id: string): Promise<boolean> {
  const r = await db.prepare("SELECT 1 AS ok FROM resource WHERE type = 'Patient' AND id = ?").bind(id).first();
  return !!r;
}
