package pe.cravapp.gooluco.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ResumenYTendenciaTest {
    private val d = LocalDate.of(2026, 11, 18)

    @Test fun rf25_estresElevado() {
        val r = ResumenDiario.resumir(
            d, listOf(6, 8, 10),
            listOf(PesajeDia(LocalDateTime.of(2026, 11, 18, 6, 30), 80.0), PesajeDia(LocalDateTime.of(2026, 11, 19, 6, 30), 79.6)),
            lineaBase = 7.0,
        )
        assertEquals(8.0, r.estresMedio!!, 1e-9)
        assertEquals(true, r.estresElevado)
        assertEquals(-0.4, r.delta24h!!, 1e-9)
        assertEquals(CalidadDia.COMPLETO, r.calidad)
    }

    @Test fun rf26_sinPesajeMananaParcial() {
        val r = ResumenDiario.resumir(d, listOf(6, 8), emptyList(), lineaBase = null)
        assertEquals(CalidadDia.PARCIAL, r.calidad)
        assertNull(r.delta24h)
        assertNull(r.estresElevado)
        assertEquals(CalidadDia.INSUFICIENTE, ResumenDiario.resumir(d, emptyList(), emptyList(), null).calidad)
    }

    @Test fun lineaBaseRequiere7Dias() {
        assertNull(ResumenDiario.lineaBase(List(6) { 5.0 }))
        assertEquals(5.0, ResumenDiario.lineaBase(List(7) { 5.0 })!!, 1e-9)
    }

    @Test fun regresionYProyeccion() {
        val pts = (0..6).map { it.toDouble() to 80.0 - 0.1 * it }
        val r = Tendencia.regresion(pts)!!
        assertEquals(-0.1, r.pendiente, 1e-9)
        // último x = 6 → 79.4; +14 días → 78.0
        assertEquals(78.0, Tendencia.proyeccion(pts)!!, 1e-9)
        assertNull(Tendencia.proyeccion(pts.take(3)))
        assertNull(Tendencia.pendiente7d(pts.take(3)))
    }

    @Test fun rf27_spearmanMinimo14() {
        val pares13 = (1..13).map { it.toDouble() to it * 2.0 }
        assertNull(Tendencia.spearman(pares13))
        val pares14 = (1..14).map { it.toDouble() to it * 2.0 }
        assertEquals(1.0, Tendencia.spearman(pares14)!!, 1e-9)
        val inversos = (1..14).map { it.toDouble() to -it.toDouble() }
        assertEquals(-1.0, Tendencia.spearman(inversos)!!, 1e-9)
    }

    @Test fun rangosConEmpates() {
        assertEquals(listOf(1.0, 2.5, 2.5, 4.0), Tendencia.rangos(listOf(1.0, 2.0, 2.0, 3.0)))
    }

    @Test fun nordicoCodificacion() {
        val r = mapOf(
            RegionNordica.CUELLO to RespuestaRegion(true, false, true),
            RegionNordica.ESPALDA_BAJA to RespuestaRegion(true, true, false),
        )
        val back = Nordico.decodificar(Nordico.codificar(r))
        assertEquals(RespuestaRegion(true, false, true), back[RegionNordica.CUELLO])
        assertEquals(RespuestaRegion(true, true, false), back[RegionNordica.ESPALDA_BAJA])
        assertEquals(RespuestaRegion(), back[RegionNordica.RODILLAS])
        assertEquals("Molestia: cuello", Nordico.resumen(r))
        assertEquals("Sin dolor", Nordico.resumen(emptyMap()))
        assertTrue(Nordico.regiones12m(back).size == 2)
        assertNotNull(back)
    }
}
