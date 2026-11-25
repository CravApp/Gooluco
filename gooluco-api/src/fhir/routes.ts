// Interacciones REST FHIR R4: read, search-type (GET) y create (POST).
// https://hl7.org/fhir/R4/http.html
import { createRoute, z, type OpenAPIHono } from "@hono/zod-openapi";
import type { Context } from "hono";
import type { AppEnv } from "../env";
import { baseUrl, fhirBase } from "../env";
import { checkAccess, type Perm } from "../auth/scopes";
import { fhirError, fhirResponse } from "./outcome";
import { capabilityStatement } from "./capability";
import { findQuestionnaireByCanonical, QUESTIONNAIRES } from "./questionnaires";
import {
  BundleSchema, CapabilityStatementSchema, ObservationSchema, OperationOutcomeSchema, PatientSchema,
  QuestionnaireResponseSchema, QuestionnaireSchema,
} from "./schemas";
import { createResource, patientExists, readResource, searchResources, type FhirResource, type SearchCriteria, type StoredType } from "./store";
import { normalizeDateTime, parseDateParam, parseToken, patientIdFromRef, type DateRange } from "./search";
import { LOINC, MEASURES, OBS_CATEGORY, UCUM } from "./terminology";

const FJ = "application/fhir+json";
const oo = { content: { [FJ]: { schema: OperationOutcomeSchema } } };
const errors = {
  400: { description: "Petición inválida (OperationOutcome)", ...oo },
  401: { description: "Falta token o token inválido", ...oo },
  403: { description: "Scope insuficiente o fuera del compartimento del paciente", ...oo },
};
const body = (schema: z.ZodType) => ({ required: true, content: { [FJ]: { schema }, "application/json": { schema } } });
const IdParam = z.object({ id: z.string().regex(/^[A-Za-z0-9\-.]{1,64}$/).openapi({ param: { name: "id", in: "path" }, example: "demo-ana" }) });
const multi = (desc: string, example?: string) => z.union([z.string(), z.array(z.string())]).optional().openapi({ description: desc, example });
const paging = { _count: z.string().optional().openapi({ description: "Tamaño de página (1–200, por defecto 50)" }), _offset: z.string().optional().openapi({ description: "Desplazamiento (paginación)" }) };

const sec = (...scopes: string[]): Record<string, string[]>[] => [{ smart: scopes }, { bearer: [] }];

function deny(c: Context<AppEnv>, type: string, perm: Perm) {
  const d = checkAccess(c.get("auth"), type, perm);
  if (!d.allowed) {
    const need = `patient/${type}.${perm} | system/${type}.${perm}`;
    return {
      res: fhirError(403, "forbidden", `Scope insuficiente: se requiere ${need}`, {
        "WWW-Authenticate": `Bearer error="insufficient_scope", scope="${need}"`,
      }),
    };
  }
  return { restriction: d.patientRestriction };
}

function paramsOf(c: Context): { count: number; offset: number } | Response {
  const count = c.req.query("_count") ? Number(c.req.query("_count")) : 50;
  const offset = c.req.query("_offset") ? Number(c.req.query("_offset")) : 0;
  if (!Number.isInteger(count) || count < 0 || count > 200) return fhirError(400, "value", "_count debe ser un entero 0–200");
  if (!Number.isInteger(offset) || offset < 0) return fhirError(400, "value", "_offset inválido");
  return { count, offset };
}

function bundle(c: Context<AppEnv>, type: string, total: number, resources: FhirResource[], count: number, offset: number) {
  const fb = fhirBase(c.env, c.req.url);
  const self = new URL(c.req.url);
  const link = [{ relation: "self", url: self.toString() }];
  if (offset + count < total) {
    const next = new URL(self);
    next.searchParams.set("_offset", String(offset + count));
    next.searchParams.set("_count", String(count));
    link.push({ relation: "next", url: next.toString() });
  }
  return fhirResponse({
    resourceType: "Bundle",
    type: "searchset",
    total,
    link,
    entry: resources.map((r) => ({ fullUrl: `${fb}/${type}/${r.id}`, resource: r, search: { mode: "match" } })),
  });
}

/** Lista de valores (repetidos = AND, comas = OR) */
function values(c: Context, name: string): string[][] {
  return (c.req.queries(name) ?? []).map((v) => v.split(",").map((s) => s.trim()).filter(Boolean));
}

function patientFilter(c: Context, restriction?: string): { ids?: string[]; empty: boolean } | Response {
  const raw = [...values(c, "patient"), ...values(c, "subject")];
  let ids: string[] | undefined;
  for (const group of raw) {
    const g = group.map(patientIdFromRef);
    if (g.some((x) => x === null)) return fhirError(400, "value", "parámetro patient/subject inválido");
    const set = g as string[];
    ids = ids ? ids.filter((x) => set.includes(x)) : set;
  }
  if (restriction) ids = ids ? ids.filter((x) => x === restriction) : [restriction];
  return { ids, empty: ids !== undefined && ids.length === 0 };
}

function dateFilter(c: Context, name: string): DateRange[] | Response {
  const out: DateRange[] = [];
  for (const v of c.req.queries(name) ?? []) {
    const r = parseDateParam(v);
    if (!r) return fhirError(400, "value", `${name} inválido: ${v}`);
    out.push(r);
  }
  return out;
}

function created(c: Context<AppEnv>, r: FhirResource) {
  const fb = fhirBase(c.env, c.req.url);
  return fhirResponse(r, 201, {
    Location: `${fb}/${r.resourceType}/${r.id}/_history/1`,
    ETag: 'W/"1"',
    "Last-Modified": new Date(String(r.meta?.lastUpdated)).toUTCString(),
  });
}

async function readHandler(c: Context<AppEnv>, type: StoredType, id: string) {
  const a = deny(c, type, "r");
  if (a.res) return a.res;
  const r = await readResource(c.env.DB, type, id);
  if (!r) return fhirError(404, "not-found", `${type}/${id} no existe`);
  if (a.restriction && r.patientId !== a.restriction) return fhirError(403, "forbidden", `${type}/${id} está fuera del compartimento del paciente del token`);
  return fhirResponse(r.resource, 200, { ETag: `W/"${r.versionId}"` });
}

/** Valida subject → Patient y el compartimento del token. */
async function subjectPatient(c: Context<AppEnv>, subject: { reference?: string } | undefined, restriction?: string): Promise<string | Response> {
  const ref = subject?.reference ?? "";
  if (!/^Patient\/[A-Za-z0-9\-.]{1,64}$/.test(ref)) return fhirError(400, "required", "subject.reference debe ser Patient/{id}", {}, ["subject"]);
  const pid = ref.slice("Patient/".length);
  if (restriction && pid !== restriction) return fhirError(403, "forbidden", "El token solo permite escribir datos del paciente en contexto", {}, ["subject"]);
  if (!(await patientExists(c.env.DB, pid))) return fhirError(422, "processing", `Patient/${pid} no existe`, {}, ["subject"]);
  return pid;
}

export function registerFhirRoutes(app: OpenAPIHono<AppEnv>) {
  // ---------- metadata ----------
  app.openapi(
    createRoute({
      method: "get", path: "/fhir/metadata", operationId: "getMetadata", tags: ["FHIR"], summary: "CapabilityStatement (incluye seguridad SMART-on-FHIR)",
      responses: { 200: { description: "CapabilityStatement", content: { [FJ]: { schema: CapabilityStatementSchema } } } },
    }),
    (c) => fhirResponse(capabilityStatement(baseUrl(c.env, c.req.url), fhirBase(c.env, c.req.url))) as never,
  );

  // ---------- Patient ----------
  app.openapi(
    createRoute({
      method: "get", path: "/fhir/Patient/{id}", operationId: "getPatientById", tags: ["Patient"], summary: "read", security: sec("patient/Patient.r", "system/Patient.r"),
      request: { params: IdParam },
      responses: { 200: { description: "Patient", content: { [FJ]: { schema: PatientSchema } } }, 404: { description: "No existe", ...oo }, ...errors },
    }),
    (c) => readHandler(c, "Patient", c.req.param("id")!) as never,
  );
  app.openapi(
    createRoute({
      method: "get", path: "/fhir/Patient", operationId: "searchPatient", tags: ["Patient"], summary: "search-type", security: sec("patient/Patient.s", "system/Patient.s"),
      request: { query: z.object({ _id: multi("Id lógico", "demo-ana"), ...paging }) },
      responses: { 200: { description: "Bundle searchset", content: { [FJ]: { schema: BundleSchema } } }, ...errors },
    }),
    (async (c: Context<AppEnv>) => {
      const a = deny(c, "Patient", "s");
      if (a.res) return a.res;
      const p = paramsOf(c);
      if (p instanceof Response) return p;
      let ids: string[] | undefined;
      for (const g of values(c, "_id")) ids = ids ? ids.filter((x) => g.includes(x)) : g;
      if (a.restriction) ids = (ids ?? [a.restriction]).filter((x) => x === a.restriction);
      if (ids && !ids.length) return bundle(c, "Patient", 0, [], p.count, p.offset);
      const r = await searchResources(c.env.DB, "Patient", { ids, count: p.count, offset: p.offset });
      return bundle(c, "Patient", r.total, r.resources, p.count, p.offset);
    }) as never,
  );
  app.openapi(
    createRoute({
      method: "post", path: "/fhir/Patient", operationId: "postPatient", tags: ["Patient"], summary: "create (solo system/Patient.c)", security: sec("system/Patient.c"),
      request: { body: body(PatientSchema) },
      responses: { 201: { description: "Creado", content: { [FJ]: { schema: PatientSchema } } }, ...errors },
    }),
    (async (c: Context<AppEnv>) => {
      const d = checkAccess(c.get("auth"), "Patient", "c");
      if (!d.allowed || d.patientRestriction) return fhirError(403, "forbidden", "Crear pacientes requiere system/Patient.c");
      const res = (await c.req.json()) as FhirResource;
      const stored = await createResource(c.env.DB, "Patient", res, {});
      // El paciente es su propio compartimento.
      await c.env.DB.prepare("UPDATE resource SET patient_id = id WHERE type = 'Patient' AND id = ?").bind(stored.id).run();
      return created(c, stored);
    }) as never,
  );

  // ---------- Observation ----------
  app.openapi(
    createRoute({
      method: "get", path: "/fhir/Observation/{id}", operationId: "getObservationById", tags: ["Observation"], summary: "read", security: sec("patient/Observation.r", "system/Observation.r"),
      request: { params: IdParam },
      responses: { 200: { description: "Observation", content: { [FJ]: { schema: ObservationSchema } } }, 404: { description: "No existe", ...oo }, ...errors },
    }),
    (c) => readHandler(c, "Observation", c.req.param("id")!) as never,
  );
  app.openapi(
    createRoute({
      method: "get", path: "/fhir/Observation", operationId: "searchObservation", tags: ["Observation"], summary: "search-type (patient, code, date)",
      security: sec("patient/Observation.s", "system/Observation.s"),
      request: {
        query: z.object({
          patient: multi("Patient/{id} o {id}", "Patient/demo-ana"),
          subject: multi("Igual que patient"),
          code: multi("token LOINC (system|code); coma = OR", "http://loinc.org|29463-7"),
          date: multi("effectiveDateTime con prefijo (ge, le, gt, lt, eq…); repetir = AND", "ge2026-11-01"),
          _id: multi("Id lógico"),
          ...paging,
        }),
      },
      responses: { 200: { description: "Bundle searchset", content: { [FJ]: { schema: BundleSchema } } }, ...errors },
    }),
    (async (c: Context<AppEnv>) => {
      const a = deny(c, "Observation", "s");
      if (a.res) return a.res;
      return searchCommon(c, "Observation", a.restriction, "date");
    }) as never,
  );
  app.openapi(
    createRoute({
      method: "post", path: "/fhir/Observation", operationId: "postObservation", tags: ["Observation"], summary: "create (peso, talla, IMC, cintura)",
      security: sec("patient/Observation.c", "system/Observation.c"),
      request: { body: body(ObservationSchema) },
      responses: { 201: { description: "Creado", content: { [FJ]: { schema: ObservationSchema } } }, 422: { description: "Regla de negocio", ...oo }, ...errors },
    }),
    (async (c: Context<AppEnv>) => {
      const a = deny(c, "Observation", "c");
      if (a.res) return a.res;
      const obs = c.req.valid("json" as never) as unknown as z.infer<typeof ObservationSchema>;
      const pid = await subjectPatient(c, obs.subject, a.restriction);
      if (pid instanceof Response) return pid;
      const date = normalizeDateTime(obs.effectiveDateTime);
      if (!date) return fhirError(400, "value", "effectiveDateTime inválido", {}, ["effectiveDateTime"]);
      const codings = (obs.code.coding ?? []).filter((x) => x.code).map((x) => ({ system: x.system, code: x.code! }));
      if (!codings.length) return fhirError(400, "required", "code.coding[].code es obligatorio", {}, ["code"]);
      const loinc = codings.find((x) => x.system === LOINC && MEASURES[x.code]);
      if (loinc) {
        const m = MEASURES[loinc.code];
        const q = obs.valueQuantity;
        if (!q) return fhirError(400, "required", `${m.display} requiere valueQuantity`, {}, ["valueQuantity"]);
        if (q.system !== UCUM || !q.code || !m.ucum.includes(q.code))
          return fhirError(400, "value", `${m.display}: unidad UCUM esperada ${m.ucum.join(" o ")} (system ${UCUM})`, {}, ["valueQuantity"]);
        if (!(q.value > 0)) return fhirError(400, "value", "valueQuantity.value debe ser > 0", {}, ["valueQuantity.value"]);
        if (m.vitalSign && !(obs.category ?? []).some((cc) => (cc.coding ?? []).some((x) => x.system === OBS_CATEGORY && x.code === "vital-signs")))
          return fhirError(400, "required", "Perfil Vital Signs: category debe incluir observation-category|vital-signs", {}, ["category"]);
        if (m.profile) {
          const meta = (obs.meta ?? {}) as { profile?: string[] };
          obs.meta = { ...meta, profile: Array.from(new Set([...(meta.profile ?? []), m.profile])) };
        }
      }
      const stored = await createResource(c.env.DB, "Observation", obs as FhirResource, { patientId: pid, codings, date });
      return created(c, stored);
    }) as never,
  );

  // ---------- QuestionnaireResponse ----------
  app.openapi(
    createRoute({
      method: "get", path: "/fhir/QuestionnaireResponse/{id}", operationId: "getQuestionnaireResponseById", tags: ["QuestionnaireResponse"], summary: "read",
      security: sec("patient/QuestionnaireResponse.r", "system/QuestionnaireResponse.r"),
      request: { params: IdParam },
      responses: { 200: { description: "QuestionnaireResponse", content: { [FJ]: { schema: QuestionnaireResponseSchema } } }, 404: { description: "No existe", ...oo }, ...errors },
    }),
    (c) => readHandler(c, "QuestionnaireResponse", c.req.param("id")!) as never,
  );
  app.openapi(
    createRoute({
      method: "get", path: "/fhir/QuestionnaireResponse", operationId: "searchQuestionnaireResponse", tags: ["QuestionnaireResponse"], summary: "search-type (patient, questionnaire, authored)",
      security: sec("patient/QuestionnaireResponse.s", "system/QuestionnaireResponse.s"),
      request: {
        query: z.object({
          patient: multi("Patient/{id} o {id}"), subject: multi("Igual que patient"),
          questionnaire: multi("Canónico del Questionnaire", "https://github.com/CravApp/Gooluco/fhir/Questionnaire/pss4-ema"),
          authored: multi("Fecha con prefijo", "ge2026-11-01"), _id: multi("Id lógico"), ...paging,
        }),
      },
      responses: { 200: { description: "Bundle searchset", content: { [FJ]: { schema: BundleSchema } } }, ...errors },
    }),
    (async (c: Context<AppEnv>) => {
      const a = deny(c, "QuestionnaireResponse", "s");
      if (a.res) return a.res;
      return searchCommon(c, "QuestionnaireResponse", a.restriction, "authored");
    }) as never,
  );
  app.openapi(
    createRoute({
      method: "post", path: "/fhir/QuestionnaireResponse", operationId: "postQuestionnaireResponse", tags: ["QuestionnaireResponse"], summary: "create (PSS-4, FINDRISC, Nórdico)",
      security: sec("patient/QuestionnaireResponse.c", "system/QuestionnaireResponse.c"),
      request: { body: body(QuestionnaireResponseSchema) },
      responses: { 201: { description: "Creado", content: { [FJ]: { schema: QuestionnaireResponseSchema } } }, 422: { description: "Regla de negocio", ...oo }, ...errors },
    }),
    (async (c: Context<AppEnv>) => {
      const a = deny(c, "QuestionnaireResponse", "c");
      if (a.res) return a.res;
      const qr = c.req.valid("json" as never) as unknown as z.infer<typeof QuestionnaireResponseSchema>;
      const q = findQuestionnaireByCanonical(qr.questionnaire);
      if (!q) return fhirError(422, "processing", `Questionnaire desconocido: ${qr.questionnaire}`, {}, ["questionnaire"]);
      const pid = await subjectPatient(c, qr.subject, a.restriction);
      if (pid instanceof Response) return pid;
      const date = normalizeDateTime(qr.authored);
      if (!date) return fhirError(400, "value", "authored inválido", {}, ["authored"]);
      const linkIds = collectLinkIds(q.item as QItem[]);
      const unknown = collectLinkIds((qr.item ?? []) as QItem[]).filter((l) => !linkIds.includes(l));
      if (unknown.length) return fhirError(400, "value", `linkId desconocido(s) para ${q.id}: ${unknown.join(", ")}`, {}, ["item"]);
      const stored = await createResource(c.env.DB, "QuestionnaireResponse", qr as FhirResource, { patientId: pid, date, questionnaire: String(q.url) });
      return created(c, stored);
    }) as never,
  );

  // ---------- Questionnaire (definiciones públicas, sin datos de pacientes) ----------
  app.openapi(
    createRoute({
      method: "get", path: "/fhir/Questionnaire/{id}", operationId: "getQuestionnaireById", tags: ["Questionnaire"], summary: "read (público)",
      request: { params: z.object({ id: z.string().openapi({ param: { name: "id", in: "path" }, example: "pss4-ema" }) }) },
      responses: { 200: { description: "Questionnaire", content: { [FJ]: { schema: QuestionnaireSchema } } }, 404: { description: "No existe", ...oo } },
    }),
    ((c: Context<AppEnv>) => {
      const q = QUESTIONNAIRES.find((x) => x.id === c.req.param("id"));
      return q ? fhirResponse(q) : fhirError(404, "not-found", `Questionnaire/${c.req.param("id")} no existe`);
    }) as never,
  );
  app.openapi(
    createRoute({
      method: "get", path: "/fhir/Questionnaire", operationId: "searchQuestionnaire", tags: ["Questionnaire"], summary: "search-type (público)",
      request: { query: z.object({ url: z.string().optional(), name: z.string().optional(), _id: z.string().optional() }) },
      responses: { 200: { description: "Bundle searchset", content: { [FJ]: { schema: BundleSchema } } } },
    }),
    ((c: Context<AppEnv>) => {
      const { url, name, _id } = c.req.query();
      const list = QUESTIONNAIRES.filter(
        (q) => (!url || q.url === url.split("|")[0]) && (!name || String(q.name).toLowerCase().startsWith(name.toLowerCase())) && (!_id || q.id === _id),
      );
      return bundle(c, "Questionnaire", list.length, list as FhirResource[], 200, 0);
    }) as never,
  );
}

type QItem = { linkId: string; item?: QItem[] };
function collectLinkIds(items: QItem[] | undefined): string[] {
  const out: string[] = [];
  for (const it of items ?? []) {
    out.push(it.linkId);
    out.push(...collectLinkIds(it.item));
  }
  return out;
}

async function searchCommon(c: Context<AppEnv>, type: "Observation" | "QuestionnaireResponse", restriction: string | undefined, dateParam: "date" | "authored") {
  const p = paramsOf(c);
  if (p instanceof Response) return p;
  const pf = patientFilter(c, restriction);
  if (pf instanceof Response) return pf;
  if (pf.empty) return bundle(c, type, 0, [], p.count, p.offset);
  const dates = dateFilter(c, dateParam);
  if (dates instanceof Response) return dates;
  const crit: SearchCriteria = { patientIds: pf.ids, dates, count: p.count, offset: p.offset };
  const idGroups = values(c, "_id");
  if (idGroups.length) crit.ids = idGroups.reduce((acc, g) => acc.filter((x) => g.includes(x)));
  if (type === "Observation") crit.tokens = values(c, "code").map((g) => g.map(parseToken));
  else crit.questionnaires = values(c, "questionnaire").flat().map((q) => String(findQuestionnaireByCanonical(q)?.url ?? q.split("|")[0]));
  const r = await searchResources(c.env.DB, type, crit);
  return bundle(c, type, r.total, r.resources, p.count, p.offset);
}
