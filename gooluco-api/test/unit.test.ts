import { describe, expect, it } from "vitest";
import { checkAccess, negotiateScopes, parseResourceScope } from "../src/auth/scopes";
import { parseDateParam, parseToken, patientIdFromRef } from "../src/fhir/search";

describe("scopes SMART v2", () => {
  it("interpreta sufijos cruds y v1", () => {
    expect([...parseResourceScope("patient/Observation.rs")!.perms]).toEqual(["r", "s"]);
    expect([...parseResourceScope("patient/Observation.read")!.perms]).toEqual(["r", "s"]);
    expect([...parseResourceScope("system/*.cruds")!.perms]).toEqual(["c", "r", "u", "d", "s"]);
    expect(parseResourceScope("patient/Observation.sr")).toBeNull(); // orden inválido
    expect(parseResourceScope("patient/Observation.rs?category=laboratory")).toBeNull();
    expect(parseResourceScope("openid")).toBeNull();
  });
  it("negocia la intersección", () => {
    expect(negotiateScopes(["patient/Observation.cruds", "openid", "user/*.rs"], ["patient/Observation.crs", "openid"])).toEqual(["patient/Observation.crs", "openid"]);
    expect(negotiateScopes(["patient/*.rs"], ["patient/Observation.crs", "patient/Patient.rs"])).toEqual(["patient/Observation.rs", "patient/Patient.rs"]);
    expect(negotiateScopes(["system/*.rs"], ["system/*.cruds"])).toEqual(["system/*.rs"]);
  });
  it("checkAccess limita al paciente salvo system/*", () => {
    expect(checkAccess({ sub: "a", clientId: "x", scopes: ["patient/Observation.rs"], patient: "p1" }, "Observation", "r")).toEqual({ allowed: true, patientRestriction: "p1" });
    expect(checkAccess({ sub: "a", clientId: "x", scopes: ["patient/Observation.rs"], patient: "p1" }, "Observation", "c").allowed).toBe(false);
    expect(checkAccess({ sub: "a", clientId: "x", scopes: ["system/*.rs"] }, "Patient", "s")).toEqual({ allowed: true });
    expect(checkAccess({ sub: "a", clientId: "x", scopes: ["patient/Observation.rs"] }, "Observation", "r").allowed).toBe(false);
  });
});

describe("parámetros de búsqueda", () => {
  it("date con precisión y prefijos", () => {
    expect(parseDateParam("2026-11-25")).toEqual({ prefix: "eq", low: "2026-11-25T00:00:00.000Z", high: "2026-11-26T00:00:00.000Z" });
    expect(parseDateParam("ge2026-11")!.high).toBe("2026-12-01T00:00:00.000Z");
    expect(parseDateParam("lt2026-11-25T10:00:00-05:00")!.low).toBe("2026-11-25T15:00:00.000Z");
    expect(parseDateParam("hoy")).toBeNull();
  });
  it("token y referencias", () => {
    expect(parseToken("http://loinc.org|29463-7")).toEqual({ system: "http://loinc.org", code: "29463-7" });
    expect(parseToken("29463-7")).toEqual({ code: "29463-7" });
    expect(patientIdFromRef("Patient/demo-ana")).toBe("demo-ana");
    expect(patientIdFromRef("https://x/fhir/Patient/demo-ana")).toBe("demo-ana");
    expect(patientIdFromRef("Practitioner/1")).toBeNull();
  });
});
