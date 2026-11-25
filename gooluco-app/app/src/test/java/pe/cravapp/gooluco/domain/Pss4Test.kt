package pe.cravapp.gooluco.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class Pss4Test {
    @Test fun rf20_invierteItems2y3() {
        assertEquals(listOf(2, 1, 2, 1), Pss4.puntuados(listOf(2, 3, 2, 1)))
        assertEquals(6, Pss4.total(listOf(2, 3, 2, 1)))
    }

    @Test fun extremos() {
        assertEquals(0, Pss4.total(listOf(0, 4, 4, 0)))
        assertEquals(16, Pss4.total(listOf(4, 0, 0, 4)))
    }

    @Test fun contextoDelPaper() {
        assertEquals(13, Pss4.ACTIVIDADES.size)
        assertEquals(5, Pss4.LUGARES.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rechazaFueraDeRango() { Pss4.total(listOf(0, 5, 0, 0)) }

    @Test(expected = IllegalArgumentException::class)
    fun rechazaCantidadIncorrecta() { Pss4.total(listOf(0, 1, 2)) }
}
