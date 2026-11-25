// Definiciones Questionnaire (FHIR R4) de los cuestionarios de Gooluco/Equilibra.
// Textos tomados de la app (feat/app-base, domain/Pss4.kt, Findrisc.kt, Nordico.kt).
// https://hl7.org/fhir/R4/questionnaire.html
export const Q_BASE = "https://github.com/CravApp/Gooluco/fhir/Questionnaire";
const ORDINAL = "http://hl7.org/fhir/StructureDefinition/ordinalValue";

type Json = Record<string, unknown>;

const likert = ["Nunca", "Casi nunca", "A veces", "Bastante a menudo", "Muy a menudo"];
const opts = (labels: string[], system: string) =>
  labels.map((display, i) => ({
    valueCoding: { system, code: String(i), display },
    extension: [{ url: ORDINAL, valueDecimal: i }],
  }));

const PSS4_ITEMS = [
  "En la última hora, ¿con qué frecuencia sintió que no podía controlar las cosas importantes?",
  "En la última hora, ¿con qué frecuencia se sintió seguro de su capacidad para manejar sus problemas?",
  "En la última hora, ¿con qué frecuencia sintió que las cosas le iban bien?",
  "En la última hora, ¿con qué frecuencia sintió que las dificultades se acumulaban tanto que no podía superarlas?",
];
const ACTIVIDADES: [string, string][] = [
  ["trabajando_estudiando", "Trabajando / estudiando"], ["durmiendo", "Durmiendo"], ["descansando", "Descansando / relajándose"],
  ["viendo_videos", "Viendo videos"], ["clase_reunion", "En clase / reunión"], ["comiendo_bebiendo", "Comiendo / bebiendo"],
  ["jugando", "Jugando"], ["conversando", "Conversando"], ["preparandose_dormir", "Preparándose para dormir"],
  ["llamando_mensajes", "Llamando / escribiendo mensajes"], ["recien_despierto", "Recién despierto"], ["manejando", "Manejando"], ["otra", "Otra"],
];
const LUGARES: [string, string][] = [["casa", "Casa"], ["trabajo", "Trabajo"], ["restaurante", "Restaurante"], ["vehiculo", "Vehículo"], ["otro", "Otro"]];

const pss4: Json = {
  resourceType: "Questionnaire",
  id: "pss4-ema",
  url: `${Q_BASE}/pss4-ema`,
  version: "EMA-ESTRES-SOSW 1.0",
  name: "PSS4EMA",
  title: "Estrés percibido (PSS-4, hora previa) + contexto EMA",
  status: "draft",
  subjectType: ["Patient"],
  description:
    "PSS-4 (Cohen) referido a la última hora, 4 ítems Likert 0–4; los ítems 2 y 3 se invierten (4 − x). Total 0–16 (RF-20). " +
    "Contexto del paper SOSW: actividad, lugar y compañía. Traducción provisional (TBD-06).",
  item: [
    ...PSS4_ITEMS.map((text, i) => ({
      linkId: `pss4-${i + 1}`,
      text,
      type: "choice",
      required: true,
      answerOption: opts(likert, `${Q_BASE}/pss4-ema/likert`),
    })),
    { linkId: "pss4-total", text: "Puntaje total PSS-4 (0–16, ítems 2 y 3 invertidos)", type: "integer", readOnly: true },
    { linkId: "actividad", text: "¿Qué estaba haciendo?", type: "choice", answerOption: ACTIVIDADES.map(([code, display]) => ({ valueCoding: { system: `${Q_BASE}/pss4-ema/actividad`, code, display } })) },
    { linkId: "lugar", text: "¿Dónde estaba?", type: "choice", answerOption: LUGARES.map(([code, display]) => ({ valueCoding: { system: `${Q_BASE}/pss4-ema/lugar`, code, display } })) },
    { linkId: "social", text: "¿Estaba acompañado?", type: "boolean" },
  ],
};

const findrisc: Json = {
  resourceType: "Questionnaire",
  id: "findrisc",
  url: `${Q_BASE}/findrisc`,
  version: "1.0",
  name: "FINDRISC",
  title: "FINDRISC — riesgo de diabetes tipo 2 a 10 años",
  status: "draft",
  subjectType: ["Patient"],
  description: "Finnish Diabetes Risk Score, 8 ítems, 0–26 puntos (RF-22). Punto de corte peruano ≥ 11. No aplica a pacientes con DM2 diagnosticada.",
  item: [
    { linkId: "edad", text: "Edad (puntos: <45 → 0; 45–54 → 2; 55–64 → 3; ≥65 → 4)", type: "integer", required: true },
    { linkId: "imc", text: "IMC (kg/m²) calculado con peso OKOK y talla (<25 → 0; 25–30 → 1; >30 → 3)", type: "decimal", required: true },
    { linkId: "cintura", text: "Perímetro de cintura (cm)", type: "decimal", required: true },
    { linkId: "actividad", text: "¿Realiza al menos 30 minutos de actividad física al día?", type: "boolean", required: true },
    { linkId: "verduras", text: "¿Come verduras o frutas todos los días?", type: "boolean", required: true },
    { linkId: "antihipertensivos", text: "¿Toma o ha tomado medicación para la presión arterial?", type: "boolean", required: true },
    { linkId: "glucosa", text: "¿Le han encontrado alguna vez glucosa alta?", type: "boolean", required: true },
    {
      linkId: "familia",
      text: "¿Algún familiar con diabetes?",
      type: "choice",
      required: true,
      answerOption: [
        ["NO", "No", 0],
        ["SEGUNDO_GRADO", "Sí: abuelos, tíos o primos hermanos", 3],
        ["PRIMER_GRADO", "Sí: padres, hermanos o hijos", 5],
      ].map(([code, display, pts]) => ({ valueCoding: { system: `${Q_BASE}/findrisc/familia`, code, display }, extension: [{ url: ORDINAL, valueDecimal: pts }] })),
    },
    { linkId: "total", text: "Puntaje total FINDRISC (0–26)", type: "integer", readOnly: true },
  ],
};

const REGIONES: [string, string][] = [
  ["CUELLO", "Cuello"], ["HOMBROS", "Hombros"], ["CODOS", "Codos"], ["MUNECAS_MANOS", "Muñecas / manos"],
  ["ESPALDA_ALTA", "Espalda alta (dorsal)"], ["ESPALDA_BAJA", "Espalda baja (lumbar)"], ["CADERAS_MUSLOS", "Caderas / muslos"],
  ["RODILLAS", "Rodillas"], ["TOBILLOS_PIES", "Tobillos / pies"],
];

const nordico: Json = {
  resourceType: "Questionnaire",
  id: "nordico",
  url: `${Q_BASE}/nordico`,
  version: "NMQ-general 1.0",
  name: "NordicoMusculoesqueletico",
  title: "Cuestionario Nórdico Musculoesquelético (sección general)",
  status: "draft",
  subjectType: ["Patient"],
  description: "Kuorinka et al., 1987. Por región: molestias en 12 meses, impedimento de actividades en 12 meses y molestias en los últimos 7 días. Introducido por el diseño «Equilibra» (no está en el SRS v2).",
  item: REGIONES.map(([code, text]) => ({
    linkId: code,
    text,
    type: "group",
    item: [
      { linkId: `${code}.molestias12m`, text: "¿Ha tenido molestias en los últimos 12 meses?", type: "boolean" },
      { linkId: `${code}.impidio12m`, text: "¿Le impidieron realizar sus actividades en los últimos 12 meses?", type: "boolean" },
      { linkId: `${code}.molestias7d`, text: "¿Ha tenido molestias en los últimos 7 días?", type: "boolean" },
    ],
  })),
};

export const QUESTIONNAIRES: Json[] = [pss4, findrisc, nordico];

export function findQuestionnaireByCanonical(canonical: string): Json | undefined {
  const [url] = canonical.split("|");
  return QUESTIONNAIRES.find((q) => q.url === url || `Questionnaire/${q.id}` === url);
}
