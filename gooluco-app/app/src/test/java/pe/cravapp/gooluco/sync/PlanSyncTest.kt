package pe.cravapp.gooluco.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.cravapp.gooluco.data.SyncEstadoEntity
import pe.cravapp.gooluco.domain.EstadoEma
import pe.cravapp.gooluco.fhir.MapeadorFhir
import pe.cravapp.gooluco.fhir.Muestras
import pe.cravapp.gooluco.fhir.Muestras.ZONA

class PlanSyncTest {
    private val m = MapeadorFhir("inst")

    private fun cands(desde: Long = 0L) = PlanSync.candidatos(
        m, ZONA, Muestras.perfil, listOf(Muestras.pesaje()), listOf(Muestras.talla), listOf(Muestras.ema),
        listOf(Muestras.findrisc), listOf(Muestras.nordico), desde,
    )

    private fun est(clave: String, e: EstadoItem) = clave to SyncEstadoEntity(clave, "Observation", "r", e.name, 1, null, 0)

    @Test fun desenlace_por_codigo_http() {
        assertEquals(Desenlace.CREADO, PlanSync.desenlace(201))
        assertEquals(Desenlace.CREADO, PlanSync.desenlace(200))
        assertEquals(Desenlace.NO_AUTORIZADO, PlanSync.desenlace(401))
        listOf(400, 403, 404, 422).forEach { assertEquals(Desenlace.RECHAZADO, PlanSync.desenlace(it)) }
        listOf(408, 429, 500, 502, 503).forEach { assertEquals(Desenlace.REINTENTAR, PlanSync.desenlace(it)) }
    }

    @Test fun candidatos_todos_los_tipos_ordenados() {
        val c = cands()
        assertEquals(listOf("TALLA:1", "PESO:7", "IMC:7", "PSS4:3", "CINTURA:4", "FINDRISC:4", "NORDICO:5"), c.map { it.clave })
        assertEquals("PESO:7", c.first { it.clave == "IMC:7" }.dependeDe)
        assertEquals(setOf("Observation", "QuestionnaireResponse"), c.map { it.tipo }.toSet())
        c.forEach { r -> assertTrue(r.construir("demo-ana", emptyMap())!!.contains("Patient/demo-ana")) }
    }

    @Test fun imc_usa_el_id_remoto_del_peso() {
        val imc = cands().first { it.clave == "IMC:7" }
        assertTrue(imc.construir("demo-ana", mapOf("PESO:7" to "obs-99"))!!.contains("Observation/obs-99"))
    }

    @Test fun desde_activacion_excluye_lo_anterior_pero_mantiene_talla_vigente() {
        val c = cands(desde = Muestras.ts("2026-11-25T10:30:00-05:00"))
        assertEquals(listOf("TALLA:1", "CINTURA:4", "FINDRISC:4", "NORDICO:5"), c.map { it.clave })
    }

    @Test fun ema_no_respondida_no_es_candidata() {
        val c = PlanSync.candidatos(m, ZONA, null, emptyList(), emptyList(), listOf(Muestras.ema.copy(estado = EstadoEma.OMITIDA.name, pssTotal = null)), emptyList(), emptyList(), 0)
        assertTrue(c.isEmpty())
    }

    @Test fun findrisc_sin_perfil_solo_envia_cintura() {
        val c = PlanSync.candidatos(m, ZONA, null, emptyList(), emptyList(), emptyList(), listOf(Muestras.findrisc), emptyList(), 0)
        assertEquals(listOf("CINTURA:4"), c.map { it.clave })
    }

    @Test fun pendientes_excluye_sincronizados_y_errores_salvo_reintento() {
        val c = cands()
        val estados = mapOf(est("PESO:7", EstadoItem.SINCRONIZADO), est("PSS4:3", EstadoItem.ERROR))
        val p = PlanSync.pendientes(c, estados, reintentarErrores = false).map { it.clave }
        assertEquals(listOf("TALLA:1", "IMC:7", "CINTURA:4", "FINDRISC:4", "NORDICO:5"), p)
        assertTrue("PSS4:3" in PlanSync.pendientes(c, estados, reintentarErrores = true).map { it.clave })
        assertEquals(5, PlanSync.contarPendientes(c, estados))
        assertEquals(0, PlanSync.contarPendientes(c, c.associate { est(it.clave, EstadoItem.SINCRONIZADO) }))
    }

    @Test fun edad_en_fecha_del_registro() {
        assertEquals(46, PlanSync.edadEn("1980-06-15", Muestras.ts("2026-11-25T11:00:00-05:00"), ZONA))
        assertEquals(45, PlanSync.edadEn("1980-12-01", Muestras.ts("2026-11-25T11:00:00-05:00"), ZONA))
        assertNull(PlanSync.edadEn("no-fecha", 0, ZONA))
    }
}
