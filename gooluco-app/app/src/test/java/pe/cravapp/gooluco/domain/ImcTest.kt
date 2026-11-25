package pe.cravapp.gooluco.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImcTest {
    @Test fun rf14_normal() {
        val imc = Imc.calcular(67.05, 165.0)
        assertEquals(24.6, imc, 1e-9)
        assertEquals(ClaseImc.NORMAL, Imc.clasificar(imc))
    }

    @Test fun rf14_obesidadI() {
        val imc = Imc.calcular(85.0, 165.0)
        assertEquals(31.2, imc, 1e-9)
        assertEquals(ClaseImc.OBESIDAD_I, Imc.clasificar(imc))
    }

    @Test fun limitesOms() {
        assertEquals(ClaseImc.BAJO_PESO, Imc.clasificar(18.4))
        assertEquals(ClaseImc.NORMAL, Imc.clasificar(18.5))
        assertEquals(ClaseImc.NORMAL, Imc.clasificar(24.9))
        assertEquals(ClaseImc.SOBREPESO, Imc.clasificar(25.0))
        assertEquals(ClaseImc.SOBREPESO, Imc.clasificar(29.9))
        assertEquals(ClaseImc.OBESIDAD_I, Imc.clasificar(30.0))
        assertEquals(ClaseImc.OBESIDAD_II, Imc.clasificar(35.0))
        assertEquals(ClaseImc.OBESIDAD_III, Imc.clasificar(40.0))
    }

    @Test fun capturaRealOkok() {
        // Peso estable de la captura RC-03 (92,80 kg) con una talla de ejemplo de 170 cm.
        assertEquals(32.1, Imc.calcular(92.80, 170.0), 1e-9)
    }

    @Test fun rf15_contrasteImcExterno() {
        assertTrue(Imc.contrasteAlerta(24.6, 26.0))
        assertFalse(Imc.contrasteAlerta(24.6, 25.0))
        assertFalse(Imc.contrasteAlerta(24.6, 25.1)) // exactamente 0,5: no alerta
    }

    @Test fun rf02_rangoTalla() {
        assertTrue(Imc.tallaValida(100.0)); assertTrue(Imc.tallaValida(230.0))
        assertFalse(Imc.tallaValida(99.9)); assertFalse(Imc.tallaValida(231.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun tallaFueraDeRangoRechaza() { Imc.calcular(70.0, 90.0) }
}
