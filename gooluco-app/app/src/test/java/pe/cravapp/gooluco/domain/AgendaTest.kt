package pe.cravapp.gooluco.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AgendaTest {
    private fun dt(h: Int, m: Int, d: Int = 18) = LocalDateTime.of(2026, 11, d, h, m)

    @Test fun rf16_slotsPorDefecto() {
        assertEquals(4, Agenda.SLOTS_DEFECTO.count { it.tipo == TipoSlot.EMA })
        assertEquals(2, Agenda.SLOTS_DEFECTO.count { it.tipo != TipoSlot.EMA })
    }

    @Test fun rf24_franjas() {
        val pesajes = listOf(
            PesajeDia(dt(6, 10), 80.0), PesajeDia(dt(7, 0), 80.4),
            PesajeDia(dt(20, 0), 81.0), PesajeDia(dt(23, 30), 81.2),
        )
        val (m, n) = ResumenDiario.pesosDelDia(LocalDate.of(2026, 11, 18), pesajes)
        assertEquals(80.0, m!!, 1e-9)
        assertEquals(81.2, n!!, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 17) to Franja.NOCHE, Agenda.franja(dt(0, 40)))
        assertEquals(Franja.OTRA, Agenda.franja(dt(14, 0)).second)
    }

    @Test fun rf18_ventana() {
        val p = dt(13, 0)
        assertTrue(Agenda.dentroDeVentana(p, dt(13, 59), 60))
        assertFalse(Agenda.dentroDeVentana(p, dt(14, 0), 60))
        assertFalse(Agenda.dentroDeVentana(p, dt(12, 59), 60))
        assertEquals("EMA-2", Agenda.emaAbierta(Agenda.SLOTS_DEFECTO, dt(13, 30))?.id)
        assertNull(Agenda.emaAbierta(Agenda.SLOTS_DEFECTO, dt(15, 0)))
    }

    @Test fun rf19_adherencia50() {
        val a = Agenda.adherencia(listOf(EstadoEma.RESPONDIDA, EstadoEma.RESPONDIDA, EstadoEma.OMITIDA, EstadoEma.EXPIRADA))
        assertEquals(0.5, a!!, 1e-9)
        assertNull(Agenda.adherencia(listOf(EstadoEma.CANCELADA)))
    }

    @Test fun proximoDisparo() {
        val lima = ZoneId.of("America/Lima")
        val ahora = ZonedDateTime.of(dt(13, 0), lima)
        val ema2 = Agenda.SLOTS_DEFECTO.first { it.id == "EMA-2" }
        assertEquals(ZonedDateTime.of(dt(13, 0, 19), lima), Agenda.proximoDisparo(ema2, ahora))
        val ema3 = Agenda.SLOTS_DEFECTO.first { it.id == "EMA-3" }
        assertEquals(ZonedDateTime.of(dt(17, 0), lima), Agenda.proximoDisparo(ema3, ahora))
    }

    @Test fun rf21_latenciaNoNegativa() {
        val lima = ZoneId.of("America/Lima")
        assertEquals(451, Agenda.latenciaSeg(ZonedDateTime.of(dt(13, 0), lima), ZonedDateTime.of(dt(13, 7), lima).plusSeconds(31)))
        assertEquals(0, Agenda.latenciaSeg(ZonedDateTime.of(dt(13, 0), lima), ZonedDateTime.of(dt(12, 0), lima)))
    }
}
