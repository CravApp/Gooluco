package pe.cravapp.gooluco.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasRiesgoTest {
    @Test fun rf29_casoRf22() {
        val r = ReglasRiesgo.evaluar(
            EntradaRiesgo(Condicion.RIESGO, Sexo.MASCULINO, imc = 27.0, cinturaCm = 96.0, findriscTotal = 13)
        )
        assertEquals(CategoriaFindrisc.MODERADO, r.categoriaFindrisc)
        assertEquals(ClaseImc.SOBREPESO, r.claseImc)
        assertEquals(ClaseCintura.AUMENTADA, r.cintura)
        assertEquals(NivelRiesgo.MODERADO, r.nivel)
        assertFalse(r.derivar)
    }

    @Test fun sinDatos() {
        val r = ReglasRiesgo.evaluar(EntradaRiesgo(Condicion.NO_DEFINIDA, Sexo.FEMENINO))
        assertEquals(NivelRiesgo.SIN_DATOS, r.nivel)
        assertTrue(r.reglas.isEmpty())
    }

    @Test fun rf33_obesidadDeriva() {
        val r = ReglasRiesgo.evaluar(EntradaRiesgo(Condicion.PREDIABETES, Sexo.FEMENINO, imc = 31.2))
        assertEquals(NivelRiesgo.MODERADO, r.nivel)
        assertTrue(r.derivar)
    }

    @Test fun findriscAltoDeriva() {
        val r = ReglasRiesgo.evaluar(EntradaRiesgo(Condicion.RIESGO, Sexo.FEMENINO, imc = 23.0, findriscTotal = 16))
        assertEquals(NivelRiesgo.ALTO, r.nivel)
        assertTrue(r.derivar)
    }

    @Test fun dm2IgnoraFindrisc() {
        val r = ReglasRiesgo.evaluar(EntradaRiesgo(Condicion.DM2, Sexo.MASCULINO, imc = 23.0, findriscTotal = 22))
        assertNull(r.categoriaFindrisc)
        assertEquals(NivelRiesgo.MODERADO, r.nivel)
    }

    @Test fun cinturaMuyAumentada() {
        val r = ReglasRiesgo.evaluar(EntradaRiesgo(Condicion.RIESGO, Sexo.FEMENINO, imc = 22.0, cinturaCm = 90.0))
        assertEquals(ClaseCintura.MUY_AUMENTADA, r.cintura)
        assertEquals(NivelRiesgo.MODERADO, r.nivel)
    }

    @Test fun alertasNoCambianNivelPeroDerivan() {
        val r = ReglasRiesgo.evaluar(
            EntradaRiesgo(
                Condicion.RIESGO, Sexo.MASCULINO, imc = 22.0,
                cambioPeso7dKg = -2.5, diasEstresElevadoConsecutivos = 3, adherenciaEma7d = 0.4,
            )
        )
        assertEquals(NivelRiesgo.BAJO, r.nivel)
        assertEquals(3, r.alertas.size)
        assertTrue(r.derivar)
    }
}
