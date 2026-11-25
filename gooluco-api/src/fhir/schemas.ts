// Esquemas zod (→ OpenAPI 3.1) de los recursos FHIR R4 soportados. Son subconjuntos: el resto
// de elementos FHIR se acepta tal cual (objetos "loose").
import { z } from "@hono/zod-openapi";

export const Coding = z.looseObject({ system: z.string().optional(), code: z.string().optional(), display: z.string().optional() }).openapi("Coding");
export const CodeableConcept = z.looseObject({ coding: z.array(Coding).optional(), text: z.string().optional() }).openapi("CodeableConcept");
export const Reference = z.looseObject({ reference: z.string().optional(), display: z.string().optional() }).openapi("Reference");
export const Quantity = z
  .looseObject({
    value: z.number(),
    unit: z.string().optional(),
    system: z.string().optional().openapi({ example: "http://unitsofmeasure.org" }),
    code: z.string().optional().openapi({ example: "kg" }),
  })
  .openapi("Quantity");
export const Meta = z.looseObject({ versionId: z.string().optional(), lastUpdated: z.string().optional(), profile: z.array(z.string()).optional() }).openapi("Meta");

export const OperationOutcomeSchema = z
  .object({
    resourceType: z.literal("OperationOutcome"),
    issue: z.array(z.object({ severity: z.string(), code: z.string(), diagnostics: z.string().optional(), expression: z.array(z.string()).optional() })),
  })
  .openapi("OperationOutcome");

export const PatientSchema = z
  .looseObject({
    resourceType: z.literal("Patient"),
    id: z.string().optional(),
    meta: Meta.optional(),
    identifier: z.array(z.looseObject({ system: z.string().optional(), value: z.string().optional() })).optional(),
    active: z.boolean().optional(),
    name: z.array(z.looseObject({ family: z.string().optional(), given: z.array(z.string()).optional() })).optional(),
    gender: z.enum(["male", "female", "other", "unknown"]).optional(),
    birthDate: z.string().regex(/^\d{4}(-\d{2}(-\d{2})?)?$/).optional(),
  })
  .openapi("Patient");

export const ObservationSchema = z
  .looseObject({
    resourceType: z.literal("Observation"),
    id: z.string().optional(),
    meta: Meta.optional(),
    status: z.enum(["registered", "preliminary", "final", "amended", "corrected", "cancelled", "entered-in-error", "unknown"]),
    category: z.array(CodeableConcept).optional(),
    code: CodeableConcept,
    subject: Reference,
    effectiveDateTime: z.string().openapi({ example: "2026-11-25T12:05:00Z" }),
    valueQuantity: Quantity.optional(),
    derivedFrom: z.array(Reference).optional(),
    device: Reference.optional(),
    note: z.array(z.looseObject({ text: z.string() })).optional(),
  })
  .openapi("Observation", {
    example: {
      resourceType: "Observation",
      status: "final",
      category: [{ coding: [{ system: "http://terminology.hl7.org/CodeSystem/observation-category", code: "vital-signs" }] }],
      code: { coding: [{ system: "http://loinc.org", code: "29463-7", display: "Body weight" }] },
      subject: { reference: "Patient/demo-ana" },
      effectiveDateTime: "2026-11-25T12:05:00Z",
      valueQuantity: { value: 70.2, unit: "kg", system: "http://unitsofmeasure.org", code: "kg" },
    },
  });

const QRItem = z.looseObject({ linkId: z.string(), text: z.string().optional(), answer: z.array(z.looseObject({})).optional(), item: z.array(z.looseObject({ linkId: z.string() })).optional() });

export const QuestionnaireResponseSchema = z
  .looseObject({
    resourceType: z.literal("QuestionnaireResponse"),
    id: z.string().optional(),
    meta: Meta.optional(),
    questionnaire: z.string().openapi({ example: "https://github.com/CravApp/Gooluco/fhir/Questionnaire/pss4-ema" }),
    status: z.enum(["in-progress", "completed", "amended", "entered-in-error", "stopped"]),
    subject: Reference,
    authored: z.string(),
    item: z.array(QRItem).optional(),
  })
  .openapi("QuestionnaireResponse");

export const QuestionnaireSchema = z.looseObject({ resourceType: z.literal("Questionnaire"), id: z.string(), url: z.string(), status: z.string() }).openapi("Questionnaire");

export const BundleSchema = z
  .object({
    resourceType: z.literal("Bundle"),
    type: z.literal("searchset"),
    total: z.number().int(),
    link: z.array(z.object({ relation: z.string(), url: z.string() })),
    entry: z.array(z.object({ fullUrl: z.string(), resource: z.looseObject({ resourceType: z.string() }), search: z.object({ mode: z.string() }) })),
  })
  .openapi("Bundle");

export const CapabilityStatementSchema = z.looseObject({ resourceType: z.literal("CapabilityStatement"), fhirVersion: z.string() }).openapi("CapabilityStatement");
