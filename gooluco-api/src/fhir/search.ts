// Parámetros de búsqueda FHIR R4: date (con prefijos) y token.
// https://hl7.org/fhir/R4/search.html#date  ·  https://hl7.org/fhir/R4/search.html#token
export type DatePrefix = "eq" | "ne" | "gt" | "lt" | "ge" | "le" | "sa" | "eb";

export interface DateRange { prefix: DatePrefix; low: string; high: string }

const RE_DATE = /^(\d{4})(?:-(\d{2})(?:-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2})(\.\d+)?)?(Z|[+-]\d{2}:\d{2})?)?)?)?$/;

/** Convierte "ge2026-11-20" en el rango [low, high) implícito por la precisión del valor. */
export function parseDateParam(raw: string): DateRange | null {
  let prefix: DatePrefix = "eq";
  let v = raw;
  const m0 = /^(eq|ne|gt|lt|ge|le|sa|eb)(.*)$/.exec(raw);
  if (m0) { prefix = m0[1] as DatePrefix; v = m0[2]; }
  const m = RE_DATE.exec(v);
  if (!m) return null;
  const [, y, mo, d, hh, mm, ss, , tz] = m;
  let low: Date, high: Date;
  if (hh !== undefined) {
    const iso = `${y}-${mo}-${d}T${hh}:${mm}:${ss ?? "00"}${tz ?? "Z"}`;
    low = new Date(iso);
    if (isNaN(low.getTime())) return null;
    high = new Date(low.getTime() + (ss !== undefined ? 1000 : 60_000));
  } else if (d !== undefined) {
    low = new Date(Date.UTC(+y, +mo - 1, +d));
    high = new Date(Date.UTC(+y, +mo - 1, +d + 1));
  } else if (mo !== undefined) {
    low = new Date(Date.UTC(+y, +mo - 1, 1));
    high = new Date(Date.UTC(+y, +mo, 1));
  } else {
    low = new Date(Date.UTC(+y, 0, 1));
    high = new Date(Date.UTC(+y + 1, 0, 1));
  }
  if (isNaN(low.getTime()) || isNaN(high.getTime())) return null;
  return { prefix, low: low.toISOString(), high: high.toISOString() };
}

/** Fragmento SQL para la columna `col` (ISO UTC, comparación lexicográfica). */
export function dateSql(col: string, r: DateRange): { sql: string; args: string[] } {
  switch (r.prefix) {
    case "eq": return { sql: `(${col} >= ? AND ${col} < ?)`, args: [r.low, r.high] };
    case "ne": return { sql: `(${col} < ? OR ${col} >= ?)`, args: [r.low, r.high] };
    case "gt": case "sa": return { sql: `${col} >= ?`, args: [r.high] };
    case "ge": return { sql: `${col} >= ?`, args: [r.low] };
    case "lt": case "eb": return { sql: `${col} < ?`, args: [r.low] };
    case "le": return { sql: `${col} < ?`, args: [r.high] };
  }
}

/** token: "system|code", "code", "|code" o "system|". */
export function parseToken(raw: string): { system?: string | null; code?: string } {
  const i = raw.indexOf("|");
  if (i < 0) return { code: raw };
  const system = raw.slice(0, i);
  const code = raw.slice(i + 1);
  return { system: system === "" ? null : system, code: code === "" ? undefined : code };
}

/** "Patient/demo-ana", "demo-ana" o URL absoluta …/Patient/demo-ana → "demo-ana". */
export function patientIdFromRef(raw: string): string | null {
  const m = /(?:^|\/)Patient\/([A-Za-z0-9\-.]{1,64})$/.exec(raw);
  if (m) return m[1];
  if (/^[A-Za-z0-9\-.]{1,64}$/.test(raw)) return raw;
  return null;
}

/** Normaliza un dateTime FHIR a ISO UTC para indexar. Las fechas sin hora se toman a medianoche UTC. */
export function normalizeDateTime(v: string): string | null {
  const r = parseDateParam(v);
  return r ? r.low : null;
}
