package pe.cravapp.gooluco.fhir

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DTO FHIR R4 mínimos (solo lo que usa Equilibra). Sin HAPI para mantener el APK pequeño.
 * https://hl7.org/fhir/R4/observation.html · https://hl7.org/fhir/R4/questionnaireresponse.html
 */
object Fhir {
    const val LOINC = "http://loinc.org"
    const val UCUM = "http://unitsofmeasure.org"
    const val CATEGORIA_OBS = "http://terminology.hl7.org/CodeSystem/observation-category"
    const val Q_BASE = "https://github.com/CravApp/Gooluco/fhir/Questionnaire"
    const val SID_REGISTRO = "https://github.com/CravApp/Gooluco/fhir/sid/registro-local"

    val json = Json {
        explicitNulls = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}

@Serializable data class Coding(val system: String? = null, val code: String? = null, val display: String? = null)
@Serializable data class CodeableConcept(val coding: List<Coding>? = null, val text: String? = null)
@Serializable data class Reference(val reference: String? = null, val display: String? = null)
@Serializable data class Identifier(val system: String, val value: String)
@Serializable data class Annotation(val text: String)
@Serializable data class Meta(val versionId: String? = null, val lastUpdated: String? = null, val profile: List<String>? = null)

@Serializable
data class Quantity(val value: Double, val unit: String? = null, val system: String? = null, val code: String? = null)

@Serializable
data class Observation(
    val resourceType: String = "Observation",
    val id: String? = null,
    val meta: Meta? = null,
    val identifier: List<Identifier>? = null,
    val status: String = "final",
    val category: List<CodeableConcept>? = null,
    val code: CodeableConcept,
    val subject: Reference,
    val effectiveDateTime: String? = null,
    val valueQuantity: Quantity? = null,
    val derivedFrom: List<Reference>? = null,
    val note: List<Annotation>? = null,
)

@Serializable
data class QRAnswer(
    val valueCoding: Coding? = null,
    val valueInteger: Int? = null,
    val valueDecimal: Double? = null,
    val valueBoolean: Boolean? = null,
)

@Serializable
data class QRItem(val linkId: String, val text: String? = null, val answer: List<QRAnswer>? = null, val item: List<QRItem>? = null)

@Serializable
data class QuestionnaireResponse(
    val resourceType: String = "QuestionnaireResponse",
    val id: String? = null,
    val meta: Meta? = null,
    val identifier: Identifier? = null,
    val questionnaire: String,
    val status: String = "completed",
    val subject: Reference,
    val authored: String,
    val item: List<QRItem>,
)

@Serializable data class BundleEntry<T>(val fullUrl: String? = null, val resource: T)
@Serializable data class Bundle<T>(val resourceType: String = "Bundle", val total: Int? = null, val entry: List<BundleEntry<T>>? = null)

/** Solo para leer el id/diagnóstico de la respuesta del servidor. */
@Serializable data class RecursoMinimo(val resourceType: String? = null, val id: String? = null, val issue: List<Issue>? = null)
@Serializable data class Issue(val severity: String? = null, val code: String? = null, val diagnostics: String? = null)
