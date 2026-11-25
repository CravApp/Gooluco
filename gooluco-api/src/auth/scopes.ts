// Scopes SMART App Launch 2.x: <contexto>/<Recurso|*>.<permisos en orden "cruds">
// https://hl7.org/fhir/smart-app-launch/scopes-and-launch-context.html#scopes-for-requesting-fhir-resources
export type ScopeContext = "patient" | "user" | "system";
export type Perm = "c" | "r" | "u" | "d" | "s";

export interface ResourceScope {
  context: ScopeContext;
  type: string; // "Observation" | "*"
  perms: Set<Perm>;
}

const ORDER: Perm[] = ["c", "r", "u", "d", "s"];
const RE = /^(patient|user|system)\/([A-Za-z]+|\*)\.([a-z*]+)$/;

/** Interpreta un scope de recurso v2 (y v1 .read/.write/.* por compatibilidad). Devuelve null si no lo es. */
export function parseResourceScope(scope: string): ResourceScope | null {
  if (scope.includes("?")) return null; // restricciones por parámetros de búsqueda: no soportadas en esta prueba
  const m = RE.exec(scope);
  if (!m) return null;
  const [, context, type, suffix] = m;
  let perms: Perm[];
  if (suffix === "read") perms = ["r", "s"];
  else if (suffix === "write") perms = ["c", "u", "d"];
  else if (suffix === "*") perms = [...ORDER];
  else {
    // v2: subconjunto de "cruds" respetando el orden
    let idx = -1;
    perms = [];
    for (const ch of suffix) {
      const i = ORDER.indexOf(ch as Perm);
      if (i <= idx) return null;
      idx = i;
      perms.push(ch as Perm);
    }
  }
  return { context: context as ScopeContext, type, perms: new Set(perms) };
}

export function formatResourceScope(s: ResourceScope): string {
  return `${s.context}/${s.type}.${ORDER.filter((p) => s.perms.has(p)).join("")}`;
}

/**
 * Concede la intersección entre lo pedido y lo permitido para el cliente.
 * Scopes no-recurso (openid, fhirUser, launch/patient, offline_access…) se conceden si están en la lista permitida.
 */
export function negotiateScopes(requested: string[], allowed: string[]): string[] {
  const allowedRes = allowed.map(parseResourceScope).filter((x): x is ResourceScope => !!x);
  const out: string[] = [];
  for (const req of requested) {
    const r = parseResourceScope(req);
    if (!r) {
      if (allowed.includes(req) && !out.includes(req)) out.push(req);
      continue;
    }
    const push = (type: string, perms: Set<Perm>) => {
      if (!perms.size) return;
      const f = formatResourceScope({ context: r.context, type, perms });
      if (!out.includes(f)) out.push(f);
    };
    if (r.type === "*") {
      // Comodín pedido: se concede por tipo según lo permitido (o comodín si también lo está).
      for (const a of allowedRes) {
        if (a.context !== r.context) continue;
        push(a.type, new Set([...r.perms].filter((p) => a.perms.has(p))));
      }
      continue;
    }
    const perms = new Set<Perm>();
    for (const a of allowedRes) {
      if (a.context !== r.context) continue;
      if (a.type !== "*" && a.type !== r.type) continue;
      for (const p of r.perms) if (a.perms.has(p)) perms.add(p);
    }
    push(r.type, perms);
  }
  return out;
}

export interface AccessContext {
  sub: string;
  clientId: string;
  scopes: string[];
  patient?: string;
  fhirUser?: string;
}

export interface AccessDecision {
  allowed: boolean;
  /** Si no es undefined, el acceso queda limitado al compartimento de este paciente. */
  patientRestriction?: string;
}

/** ¿El token permite la operación `perm` sobre `type`? Las rutas system/* no restringen paciente. */
export function checkAccess(ctx: AccessContext, type: string, perm: Perm): AccessDecision {
  let viaSystem = false;
  let viaPatientOrUser = false;
  for (const s of ctx.scopes) {
    const r = parseResourceScope(s);
    if (!r || (r.type !== "*" && r.type !== type) || !r.perms.has(perm)) continue;
    if (r.context === "system") viaSystem = true;
    else viaPatientOrUser = true;
  }
  if (viaSystem) return { allowed: true };
  if (viaPatientOrUser) {
    // En este servidor de prueba todos los usuarios finales son pacientes: user/* ≡ su propio compartimento.
    if (!ctx.patient) return { allowed: false };
    return { allowed: true, patientRestriction: ctx.patient };
  }
  return { allowed: false };
}
