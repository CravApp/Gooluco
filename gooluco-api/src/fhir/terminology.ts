// Códigos LOINC / UCUM usados por Gooluco (perfil Vital Signs de FHIR R4).
// https://hl7.org/fhir/R4/observation-vitalsigns.html
export const LOINC = "http://loinc.org";
export const UCUM = "http://unitsofmeasure.org";
export const OBS_CATEGORY = "http://terminology.hl7.org/CodeSystem/observation-category";

export interface MeasureDef {
  code: string;
  display: string;
  ucum: string[]; // unidades UCUM aceptadas
  profile?: string;
  vitalSign: boolean;
}

export const MEASURES: Record<string, MeasureDef> = {
  "29463-7": { code: "29463-7", display: "Body weight", ucum: ["kg"], profile: "http://hl7.org/fhir/StructureDefinition/bodyweight", vitalSign: true },
  "8302-2": { code: "8302-2", display: "Body height", ucum: ["cm"], profile: "http://hl7.org/fhir/StructureDefinition/bodyheight", vitalSign: true },
  "39156-5": { code: "39156-5", display: "Body mass index (BMI) [Ratio]", ucum: ["kg/m2"], profile: "http://hl7.org/fhir/StructureDefinition/bmi", vitalSign: true },
  "8280-0": { code: "8280-0", display: "Waist Circumference at umbilicus by Tape measure", ucum: ["cm"], vitalSign: false },
};
