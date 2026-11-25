package pe.cravapp.gooluco.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindriscTest {
    private val casoRf22 = FindriscEntrada(
        edad = 50, imc = 27.0, cinturaCm = 96.0, sexo = Sexo.MASCULINO,
        actividadDiaria30min = false, verdurasFrutasDiario = true, antihipertensivos = false,
        glucosaAltaPrevia = false, antecedenteFamiliar = AntecedenteFamiliar.PRIMER_GRADO,
    )

    @Test fun rf22_casoDelSrs() {
        val r = Findrisc.evaluar(casoRf22)
        assertEquals(listOf(2, 1, 3, 2, 0, 0, 0, 5), r.puntos)
        assertEquals(13, r.total)
        assertEquals(CategoriaFindrisc.MODERADO, r.categoria)
        assertTrue(r.positivoCortePeru)
    }

    @Test fun maximo26() {
        val r = Findrisc.evaluar(
            FindriscEntrada(70, 35.0, 110.0, Sexo.FEMENINO, false, false, true, true, AntecedenteFamiliar.PRIMER_GRADO)
        )
        assertEquals(26, r.total)
        assertEquals(CategoriaFindrisc.MUY_ALTO, r.categoria)
    }

    @Test fun minimo0() {
        val r = Findrisc.evaluar(
            FindriscEntrada(30, 22.0, 70.0, Sexo.FEMENINO, true, true, false, false, AntecedenteFamiliar.NO)
        )
        assertEquals(0, r.total)
        assertEquals(CategoriaFindrisc.BAJO, r.categoria)
        assertFalse(r.positivoCortePeru)
    }

    @Test fun categorias() {
        assertEquals(CategoriaFindrisc.BAJO, Findrisc.categoria(6))
        assertEquals(CategoriaFindrisc.LIGERAMENTE_ELEVADO, Findrisc.categoria(7))
        assertEquals(CategoriaFindrisc.LIGERAMENTE_ELEVADO, Findrisc.categoria(11))
        assertEquals(CategoriaFindrisc.MODERADO, Findrisc.categoria(12))
        assertEquals(CategoriaFindrisc.MODERADO, Findrisc.categoria(14))
        assertEquals(CategoriaFindrisc.ALTO, Findrisc.categoria(15))
        assertEquals(CategoriaFindrisc.ALTO, Findrisc.categoria(20))
        assertEquals(CategoriaFindrisc.MUY_ALTO, Findrisc.categoria(21))
    }

    @Test fun cortePeru11() {
        val base = FindriscEntrada(50, 27.0, 90.0, Sexo.MASCULINO, true, true, false, false, AntecedenteFamiliar.NO)
        // 2 + 1 + 0 + 0 + 0 + 0 + 0 + 0 = 3
        assertFalse(Findrisc.evaluar(base).positivoCortePeru)
        val once = base.copy(glucosaAltaPrevia = true, antecedenteFamiliar = AntecedenteFamiliar.SEGUNDO_GRADO) // 3+5+3 = 11
        assertEquals(11, Findrisc.evaluar(once).total)
        assertTrue(Findrisc.evaluar(once).positivoCortePeru)
    }

    @Test fun puntosPorTramo() {
        assertEquals(0, Findrisc.puntosEdad(44)); assertEquals(2, Findrisc.puntosEdad(45))
        assertEquals(3, Findrisc.puntosEdad(55)); assertEquals(4, Findrisc.puntosEdad(65))
        assertEquals(0, Findrisc.puntosImc(24.9)); assertEquals(1, Findrisc.puntosImc(30.0)); assertEquals(3, Findrisc.puntosImc(30.1))
        assertEquals(3, Findrisc.puntosCintura(80.0, Sexo.FEMENINO)); assertEquals(4, Findrisc.puntosCintura(88.5, Sexo.FEMENINO))
        assertEquals(0, Findrisc.puntosCintura(93.9, Sexo.MASCULINO)); assertEquals(4, Findrisc.puntosCintura(103.0, Sexo.MASCULINO))
    }

    @Test fun noAplicaADm2() {
        assertFalse(Findrisc.aplica(Condicion.DM2))
        assertTrue(Findrisc.aplica(Condicion.PREDIABETES))
    }
}
