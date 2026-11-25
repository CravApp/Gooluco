package pe.cravapp.gooluco.fhir

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.cravapp.gooluco.domain.EstadoEma
import pe.cravapp.gooluco.domain.RegionNordica
import pe.cravapp.gooluco.fhir.Muestras.ZONA
import java.io.File

class MapeadorFhirTest {
    private val m = MapeadorFhir("inst-prueba")
    private val pac = "demo-ana"

    private fun obs(o: Observation) = Json.parseToJsonElement(Fhir.json.encodeToString(Observation.serializer(), o)).jsonObject
    private fun qr(q: QuestionnaireResponse) = Json.parseToJsonElement(Fhir.json.encodeToString(QuestionnaireResponse.serializer(), q)).jsonObject
    private fun JsonObject.s(vararg ruta: Any): String = nav(*ruta).jsonPrimitive.content
    private fun JsonObject.nav(vararg ruta: Any): kotlinx.serialization.json.JsonElement =
        ruta.fold(this as kotlinx.serialization.json.JsonElement) { e, k -> if (k is Int) e.jsonArray[k] else e.jsonObject[k as String]!! }
    private fun JsonObject.item(linkId: String) = this["item"]!!.jsonArray.map { it.jsonObject }.first { it["linkId"]!!.jsonPrimitive.content == linkId }
    private fun JsonObject.resp(linkId: String) = item(linkId)["answer"]!!.jsonArray[0].jsonObject

    private fun comprobarSignoVital(j: JsonObject, loinc: String, unidad: String) {
        assertEquals("Observation", j.s("resourceType"))
        assertEquals("final", j.s("status"))
        assertEquals("http://terminology.hl7.org/CodeSystem/observation-category", j.s("category", 0, "coding", 0, "system"))
        assertEquals("vital-signs", j.s("category", 0, "coding", 0, "code"))
        assertEquals("http://loinc.org", j.s("code", "coding", 0, "system"))
        assertEquals(loinc, j.s("code", "coding", 0, "code"))
        assertEquals("Patient/demo-ana", j.s("subject", "reference"))
        assertEquals("http://unitsofmeasure.org", j.s("valueQuantity", "system"))
        assertEquals(unidad, j.s("valueQuantity", "code"))
        assertEquals(Fhir.SID_REGISTRO, j.s("identifier", 0, "system"))
        assertTrue(j.s("effectiveDateTime").matches(Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d-05:00""")))
    }

    @Test fun peso_29463_7_en_kg_con_zona_y_nota_simulada() {
        val j = obs(m.peso(Muestras.pesaje(), pac))
        comprobarSignoVital(j, "29463-7", "kg")
        assertEquals(82.46, j.nav("valueQuantity", "value").jsonPrimitive.double, 0.0)
        assertEquals("2026-11-25T07:05:30-05:00", j.s("effectiveDateTime"))
        assertEquals("inst-prueba/PESO:7", j.s("identifier", 0, "value"))
        assertTrue(j.s("note", 0, "text").contains("SIMULADO"))
    }

    @Test fun peso_real_sin_nota() {
        assertNull(obs(m.peso(Muestras.pesaje(fuente = "REAL"), pac))["note"])
    }

    @Test fun imc_39156_5_con_derivedFrom_del_peso_remoto() {
        val j = obs(m.imc(Muestras.pesaje(), pac, "abc123")!!)
        comprobarSignoVital(j, "39156-5", "kg/m2")
        assertEquals(30.3, j.nav("valueQuantity", "value").jsonPrimitive.double, 0.0)
        assertEquals("Observation/abc123", j.s("derivedFrom", 0, "reference"))
        assertNull(obs(m.imc(Muestras.pesaje(), pac, null)!!)["derivedFrom"])
        assertNull(m.imc(Muestras.pesaje().copy(imc = null), pac, null))
    }

    @Test fun talla_8302_2_y_cintura_8280_0_en_cm() {
        val t = obs(m.talla(Muestras.talla, pac, ZONA))
        comprobarSignoVital(t, "8302-2", "cm")
        assertEquals(165.0, t.nav("valueQuantity", "value").jsonPrimitive.double, 0.0)
        val c = obs(m.cintura(Muestras.findrisc, pac, ZONA))
        comprobarSignoVital(c, "8280-0", "cm")
        assertEquals(95.0, c.nav("valueQuantity", "value").jsonPrimitive.double, 0.0)
    }

    @Test fun pss4_envia_opciones_marcadas_sin_invertir_y_total_5_como_postman() {
        val j = qr(m.pss4(Muestras.ema, pac, ZONA)!!)
        assertEquals("QuestionnaireResponse", j.s("resourceType"))
        assertEquals("completed", j.s("status"))
        assertEquals("https://github.com/CravApp/Gooluco/fhir/Questionnaire/pss4-ema", j.s("questionnaire"))
        assertEquals("Patient/demo-ana", j.s("subject", "reference"))
        assertEquals(listOf("1", "3", "2", "1"), (1..4).map { j.resp("pss4-$it")["valueCoding"]!!.jsonObject["code"]!!.jsonPrimitive.content })
        assertEquals("https://github.com/CravApp/Gooluco/fhir/Questionnaire/pss4-ema/likert", j.resp("pss4-1")["valueCoding"]!!.jsonObject["system"]!!.jsonPrimitive.content)
        assertEquals(5, j.resp("pss4-total")["valueInteger"]!!.jsonPrimitive.int)
        assertEquals("trabajando_estudiando", j.resp("actividad")["valueCoding"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals("casa", j.resp("lugar")["valueCoding"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertFalse(j.resp("social")["valueBoolean"]!!.jsonPrimitive.boolean)
        assertEquals(listOf("pss4-1", "pss4-2", "pss4-3", "pss4-4", "pss4-total", "actividad", "lugar", "social"), j["item"]!!.jsonArray.map { it.jsonObject["linkId"]!!.jsonPrimitive.content })
    }

    @Test fun pss4_omitida_no_se_envia() {
        assertNull(m.pss4(Muestras.ema.copy(estado = EstadoEma.OMITIDA.name), pac, ZONA))
    }

    @Test fun findrisc_reconstruye_respuestas_desde_puntos() {
        val j = qr(m.findrisc(Muestras.findrisc, 46, pac, ZONA))
        assertEquals("https://github.com/CravApp/Gooluco/fhir/Questionnaire/findrisc", j.s("questionnaire"))
        assertEquals(46, j.resp("edad")["valueInteger"]!!.jsonPrimitive.int)
        assertEquals(30.3, j.resp("imc")["valueDecimal"]!!.jsonPrimitive.double, 0.0)
        assertEquals(95.0, j.resp("cintura")["valueDecimal"]!!.jsonPrimitive.double, 0.0)
        assertTrue(j.resp("actividad")["valueBoolean"]!!.jsonPrimitive.boolean)
        assertFalse(j.resp("verduras")["valueBoolean"]!!.jsonPrimitive.boolean)
        assertFalse(j.resp("antihipertensivos")["valueBoolean"]!!.jsonPrimitive.boolean)
        assertFalse(j.resp("glucosa")["valueBoolean"]!!.jsonPrimitive.boolean)
        assertEquals("SEGUNDO_GRADO", j.resp("familia")["valueCoding"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals(13, j.resp("total")["valueInteger"]!!.jsonPrimitive.int)
        assertEquals(
            listOf("edad", "imc", "cintura", "actividad", "verduras", "antihipertensivos", "glucosa", "familia", "total"),
            j["item"]!!.jsonArray.map { it.jsonObject["linkId"]!!.jsonPrimitive.content },
        )
    }

    @Test fun nordico_un_grupo_por_region_con_linkIds_anidados() {
        val j = qr(m.nordico(Muestras.nordico, pac, ZONA))
        assertEquals("https://github.com/CravApp/Gooluco/fhir/Questionnaire/nordico", j.s("questionnaire"))
        val grupos = j["item"] as JsonArray
        assertEquals(RegionNordica.entries.map { it.name }, grupos.map { it.jsonObject["linkId"]!!.jsonPrimitive.content })
        val cuello = j.item("CUELLO")["item"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("CUELLO.molestias12m", "CUELLO.impidio12m", "CUELLO.molestias7d"), cuello.map { it["linkId"]!!.jsonPrimitive.content })
        assertEquals(listOf(true, false, true), cuello.map { it["answer"]!!.jsonArray[0].jsonObject["valueBoolean"]!!.jsonPrimitive.boolean })
        val rodillas = j.item("RODILLAS")["item"]!!.jsonArray.map { it.jsonObject["answer"]!!.jsonArray[0].jsonObject["valueBoolean"]!!.jsonPrimitive.boolean }
        assertEquals(listOf(false, false, false), rodillas)
    }

    @Test fun nunca_envia_mac_trama_impedancia_ni_rssi() {
        val todo = listOf(
            Fhir.json.encodeToString(Observation.serializer(), m.peso(Muestras.pesaje(), pac)),
            Fhir.json.encodeToString(Observation.serializer(), m.imc(Muestras.pesaje(), pac, "x")!!),
        ).joinToString("\n")
        listOf(Muestras.MAC_FALSA, Muestras.TRAMA_FALSA, "543.2", "impedan", "tramaHex", "mac", "rssi", "-61").forEach {
            assertFalse("no debe aparecer '$it'", todo.contains(it, ignoreCase = true))
        }
    }

    @Test fun fecha_hora_con_offset_y_redondeo() {
        assertEquals("2026-11-25T07:05:30-05:00", MapeadorFhir.fechaHora(Muestras.ts("2026-11-25T12:05:30.999Z"), ZONA))
        assertEquals("2026-11-25T07:05:30-05:00", MapeadorFhir.fechaHora(Muestras.ts("2026-11-25T12:05:30Z"), "Zona/Inexistente"))
        assertEquals(82.46, MapeadorFhir.redondear(82.456, 2), 0.0)
    }

    /** Escribe los JSON de ejemplo (paciente demo-ana) en build/fhir-ejemplos para la prueba e2e con curl. */
    @Test fun escribir_ejemplos_para_e2e() {
        val dir = File("build/fhir-ejemplos").apply { mkdirs() }
        fun o(n: String, x: Observation) = File(dir, "$n.json").writeText(Fhir.json.encodeToString(Observation.serializer(), x))
        fun q(n: String, x: QuestionnaireResponse) = File(dir, "$n.json").writeText(Fhir.json.encodeToString(QuestionnaireResponse.serializer(), x))
        o("1-peso", m.peso(Muestras.pesaje(), pac))
        o("2-imc", m.imc(Muestras.pesaje(), pac, null)!!)
        o("3-talla", m.talla(Muestras.talla, pac, ZONA))
        o("4-cintura", m.cintura(Muestras.findrisc, pac, ZONA))
        q("5-pss4", m.pss4(Muestras.ema, pac, ZONA)!!)
        q("6-findrisc", m.findrisc(Muestras.findrisc, 46, pac, ZONA))
        q("7-nordico", m.nordico(Muestras.nordico, pac, ZONA))
        assertEquals(7, dir.listFiles()!!.count { it.name.endsWith(".json") })
    }
}
