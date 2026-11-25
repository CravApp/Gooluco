// Respuestas FHIR (application/fhir+json) y OperationOutcome.
// https://hl7.org/fhir/R4/http.html#mime-type  ·  https://hl7.org/fhir/R4/operationoutcome.html
export const FHIR_JSON = "application/fhir+json; charset=utf-8";

export type IssueType =
  | "invalid" | "structure" | "required" | "value" | "security" | "login" | "forbidden"
  | "not-found" | "not-supported" | "processing" | "exception" | "business-rule";

export interface OperationOutcome {
  resourceType: "OperationOutcome";
  issue: { severity: "fatal" | "error" | "warning" | "information"; code: IssueType; diagnostics?: string; expression?: string[] }[];
}

export function outcome(code: IssueType, diagnostics: string, expression?: string[]): OperationOutcome {
  return { resourceType: "OperationOutcome", issue: [{ severity: "error", code, diagnostics, ...(expression ? { expression } : {}) }] };
}

export function fhirResponse(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": FHIR_JSON, ...headers } });
}

export function fhirError(status: number, code: IssueType, diagnostics: string, headers: Record<string, string> = {}, expression?: string[]): Response {
  return fhirResponse(outcome(code, diagnostics, expression), status, headers);
}
