import { describe, expect, it } from "vitest";
import { backendToken, FHIR, fhir, loginPkce, weight } from "./helpers";

const ALL = "launch/patient patient/Patient.rs patient/Observation.crs patient/QuestionnaireResponse.crs";

describe("autorización de recursos (scopes)", () => {
  it("sin token → 401 con WWW-Authenticate Bearer", async () => {
    const r = await fhir("/Observation");
    expect(r.status).toBe(401);
    expect(r.headers.get("www-authenticate")).toMatch(/^Bearer/);
    expect(((await r.json()) as any).resourceType).toBe("OperationOutcome");
  });

  it("token manipulado → 401 invalid_token", async () => {
    const t = await loginPkce("launch/patient patient/Observation.rs");
    const r = await fhir("/Observation", t.access_token.slice(0, -3) + "abc");
    expect(r.status).toBe(401);
    expect(r.headers.get("www-authenticate")).toContain('error="invalid_token"');
  });

  it("solo .rs → no puede crear (403 insufficient_scope)", async () => {
    const t = await loginPkce("launch/patient patient/Observation.rs");
    const r = await fhir("/Observation", t.access_token, { method: "POST", body: JSON.stringify(weight("demo-ana", 70, "2026-11-25T12:00:00Z")) });
    expect(r.status).toBe(403);
    expect(r.headers.get("www-authenticate")).toContain("insufficient_scope");
  });

  it("solo .c → puede crear pero no buscar ni leer", async () => {
    const t = await loginPkce("launch/patient patient/Observation.c");
    const c = await fhir("/Observation", t.access_token, { method: "POST", body: JSON.stringify(weight("demo-ana", 70.1, "2026-11-25T12:00:00Z")) });
    expect(c.status).toBe(201);
    const id = ((await c.json()) as any).id;
    expect((await fhir("/Observation", t.access_token)).status).toBe(403);
    expect((await fhir(`/Observation/${id}`, t.access_token)).status).toBe(403);
  });

  it("scope de otro recurso no sirve (Patient.rs no da Observation)", async () => {
    const t = await loginPkce("launch/patient patient/Patient.rs");
    expect((await fhir("/Observation", t.access_token)).status).toBe(403);
    expect((await fhir("/Patient/demo-ana", t.access_token)).status).toBe(200);
  });

  it("compartimento del paciente: Ana no ve ni escribe datos de Beto", async () => {
    const t = await loginPkce(ALL, "ana");
    expect((await fhir("/Patient/demo-beto", t.access_token)).status).toBe(403);
    expect((await fhir("/Observation/demo-beto-peso-1", t.access_token)).status).toBe(403);
    const s = (await (await fhir("/Observation?patient=demo-beto", t.access_token)).json()) as any;
    expect(s.total).toBe(0);
    const w = await fhir("/Observation", t.access_token, { method: "POST", body: JSON.stringify(weight("demo-beto", 80, "2026-11-25T12:00:00Z")) });
    expect(w.status).toBe(403);
  });
});

describe("Observation: create / read / search", () => {
  it("crea un peso OKOK, lo lee y lo encuentra por code+date+patient", async () => {
    const t = await loginPkce(ALL, "beto");
    const body = weight("demo-beto", 84.6, "2026-11-25T11:30:00-05:00", { device: { display: "Balanza OKOK (simulada)" } });
    const c = await fhir("/Observation", t.access_token, { method: "POST", body: JSON.stringify(body) });
    expect(c.status).toBe(201);
    expect(c.headers.get("content-type")).toContain("application/fhir+json");
    const obs = (await c.json()) as any;
    expect(obs.id).toBeTruthy();
    expect(obs.meta.versionId).toBe("1");
    expect(obs.meta.profile).toContain("http://hl7.org/fhir/StructureDefinition/bodyweight");
    expect(c.headers.get("location")).toBe(`${FHIR}/Observation/${obs.id}/_history/1`);
    expect(c.headers.get("etag")).toBe('W/"1"');

    const r = await fhir(`/Observation/${obs.id}`, t.access_token);
    expect(r.status).toBe(200);
    expect(((await r.json()) as any).valueQuantity.value).toBe(84.6);

    const q = new URLSearchParams([["patient", "Patient/demo-beto"], ["code", "http://loinc.org|29463-7"], ["date", "ge2026-11-25"], ["date", "lt2026-11-26"]]);
    const s = await fhir(`/Observation?${q}`, t.access_token);
    const b = (await s.json()) as any;
    expect(b.resourceType).toBe("Bundle");
    expect(b.type).toBe("searchset");
    expect(b.entry.map((e: any) => e.resource.id)).toContain(obs.id);
    expect(b.entry.every((e: any) => e.search.mode === "match" && e.fullUrl.startsWith(`${FHIR}/Observation/`))).toBe(true);
    // 11:30 -05:00 = 16:30Z
    const exact = (await (await fhir(`/Observation?date=eq2026-11-25T16:30&code=29463-7`, t.access_token)).json()) as any;
    expect(exact.entry.map((e: any) => e.resource.id)).toContain(obs.id);
    const before = (await (await fhir(`/Observation?date=lt2026-11-25&code=29463-7`, t.access_token)).json()) as any;
    expect(before.entry.map((e: any) => e.resource.id)).not.toContain(obs.id);
  });

  it("acepta talla, IMC y cintura con sus unidades UCUM", async () => {
    const t = await backendToken("system/Observation.crs");
    const mk = (code: string, unit: string, value: number, cat = true) => ({
      ...weight("demo-ana", value, "2026-11-24T12:00:00Z"),
      code: { coding: [{ system: "http://loinc.org", code }] },
      valueQuantity: { value, unit, system: "http://unitsofmeasure.org", code: unit },
      ...(cat ? {} : { category: undefined }),
    });
    for (const [code, unit, v, cat] of [["8302-2", "cm", 160, true], ["39156-5", "kg/m2", 27.4, true], ["8280-0", "cm", 88, false]] as const) {
      const r = await fhir("/Observation", t, { method: "POST", body: JSON.stringify(mk(code, unit, v, cat)) });
      expect(r.status, code).toBe(201);
    }
    const s = (await (await fhir("/Observation?patient=demo-ana&code=8302-2,39156-5,8280-0&date=eq2026-11-24", t)).json()) as any;
    expect(s.total).toBe(3);
  });

  it("valida unidad UCUM, categoría vital-signs, subject y Content-Type", async () => {
    const t = await backendToken("system/Observation.crs");
    const lb = weight("demo-ana", 150, "2026-11-25T12:00:00Z", { valueQuantity: { value: 150, unit: "lb", system: "http://unitsofmeasure.org", code: "[lb_av]" } });
    const r1 = await fhir("/Observation", t, { method: "POST", body: JSON.stringify(lb) });
    expect(r1.status).toBe(400);
    expect(((await r1.json()) as any).issue[0].expression).toEqual(["valueQuantity"]);
    const nocat = weight("demo-ana", 70, "2026-11-25T12:00:00Z", { category: [] });
    expect((await fhir("/Observation", t, { method: "POST", body: JSON.stringify(nocat) })).status).toBe(400);
    const ghost = weight("no-existe", 70, "2026-11-25T12:00:00Z");
    expect((await fhir("/Observation", t, { method: "POST", body: JSON.stringify(ghost) })).status).toBe(422);
    const nostatus = { ...weight("demo-ana", 70, "2026-11-25T12:00:00Z"), status: undefined };
    const r4 = await fhir("/Observation", t, { method: "POST", body: JSON.stringify(nostatus) });
    expect(r4.status).toBe(400);
    expect(((await r4.json()) as any).issue[0].code).toBe("invalid");
    const r5 = await fhir("/Observation", t, { method: "POST", body: "hola", headers: { "Content-Type": "text/plain" } });
    expect(r5.status).toBe(415);
  });

  it("paginación con _count y link next", async () => {
    const t = await backendToken("system/Observation.crs");
    const r = (await (await fhir("/Observation?_count=1", t)).json()) as any;
    expect(r.entry.length).toBe(1);
    expect(r.total).toBeGreaterThan(1);
    expect(r.link.find((l: any) => l.relation === "next").url).toContain("_offset=1");
    expect((await fhir("/Observation?date=ayer", t)).status).toBe(400);
  });

  it("404 para un id inexistente", async () => {
    const t = await backendToken("system/Observation.rs");
    const r = await fhir("/Observation/no-existe", t);
    expect(r.status).toBe(404);
    expect(((await r.json()) as any).issue[0].code).toBe("not-found");
  });
});

describe("Questionnaire y QuestionnaireResponse", () => {
  const PSS4 = "https://github.com/CravApp/Gooluco/fhir/Questionnaire/pss4-ema";

  it("las definiciones son públicas", async () => {
    const r = await fhir("/Questionnaire/pss4-ema");
    expect(r.status).toBe(200);
    const q = (await r.json()) as any;
    expect(q.url).toBe(PSS4);
    expect(q.item.filter((i: any) => i.linkId.startsWith("pss4-") && i.type === "choice").length).toBe(4);
    const all = (await (await fhir("/Questionnaire")).json()) as any;
    expect(all.entry.map((e: any) => e.resource.id).sort()).toEqual(["findrisc", "nordico", "pss4-ema"]);
    const byUrl = (await (await fhir(`/Questionnaire?url=${encodeURIComponent(PSS4)}`)).json()) as any;
    expect(byUrl.total).toBe(1);
  });

  it("crea una respuesta PSS-4 y la busca por questionnaire y authored", async () => {
    const t = await loginPkce(ALL, "ana");
    const qr = {
      resourceType: "QuestionnaireResponse",
      questionnaire: PSS4,
      status: "completed",
      subject: { reference: "Patient/demo-ana" },
      authored: "2026-11-25T20:00:00Z",
      item: [
        ...[1, 3, 1, 2].map((v, i) => ({ linkId: `pss4-${i + 1}`, answer: [{ valueCoding: { code: String(v) } }] })),
        { linkId: "pss4-total", answer: [{ valueInteger: 1 + (4 - 3) + (4 - 1) + 2 }] },
        { linkId: "actividad", answer: [{ valueCoding: { code: "trabajando_estudiando" } }] },
        { linkId: "social", answer: [{ valueBoolean: false }] },
      ],
    };
    const c = await fhir("/QuestionnaireResponse", t.access_token, { method: "POST", body: JSON.stringify(qr) });
    expect(c.status).toBe(201);
    const id = ((await c.json()) as any).id;
    const s = (await (await fhir(`/QuestionnaireResponse?questionnaire=${encodeURIComponent(PSS4)}&authored=ge2026-11-25`, t.access_token)).json()) as any;
    expect(s.entry.map((e: any) => e.resource.id)).toContain(id);
    const bad = { ...qr, item: [{ linkId: "inventado" }] };
    expect((await fhir("/QuestionnaireResponse", t.access_token, { method: "POST", body: JSON.stringify(bad) })).status).toBe(400);
    const unk = { ...qr, questionnaire: "https://otro.example/Questionnaire/x" };
    expect((await fhir("/QuestionnaireResponse", t.access_token, { method: "POST", body: JSON.stringify(unk) })).status).toBe(422);
  });

  it("acepta FINDRISC y Nórdico (linkIds anidados)", async () => {
    const t = await backendToken("system/QuestionnaireResponse.crs");
    const base = { resourceType: "QuestionnaireResponse", status: "completed", subject: { reference: "Patient/demo-beto" }, authored: "2026-11-25T13:00:00Z" };
    const f = await fhir("/QuestionnaireResponse", t, {
      method: "POST",
      body: JSON.stringify({ ...base, questionnaire: "https://github.com/CravApp/Gooluco/fhir/Questionnaire/findrisc", item: [{ linkId: "edad", answer: [{ valueInteger: 54 }] }, { linkId: "total", answer: [{ valueInteger: 12 }] }] }),
    });
    expect(f.status).toBe(201);
    const n = await fhir("/QuestionnaireResponse", t, {
      method: "POST",
      body: JSON.stringify({ ...base, questionnaire: "https://github.com/CravApp/Gooluco/fhir/Questionnaire/nordico", item: [{ linkId: "CUELLO", item: [{ linkId: "CUELLO.molestias7d", answer: [{ valueBoolean: true }] }] }] }),
    });
    expect(n.status).toBe(201);
    const s = (await (await fhir("/QuestionnaireResponse?patient=demo-beto", t)).json()) as any;
    expect(s.total).toBeGreaterThanOrEqual(2);
  });
});
