package pe.cravapp.gooluco.domain

enum class Sexo(val etiqueta: String) { MASCULINO("Masculino"), FEMENINO("Femenino") }

enum class Condicion(val etiqueta: String) {
    DM2("Diabetes tipo 2"),
    PREDIABETES("Prediabetes"),
    RIESGO("En riesgo"),
    NO_DEFINIDA("No definida"),
}

enum class AntecedenteFamiliar(val etiqueta: String, val puntos: Int) {
    NO("No", 0),
    SEGUNDO_GRADO("Sí: abuelos, tíos o primos hermanos", 3),
    PRIMER_GRADO("Sí: padres, hermanos o hijos", 5),
}

enum class CategoriaFindrisc(val etiqueta: String, val riesgo10Anios: String) {
    BAJO("Bajo", "~1 de 100"),
    LIGERAMENTE_ELEVADO("Ligeramente elevado", "~1 de 25"),
    MODERADO("Moderado", "~1 de 6"),
    ALTO("Alto", "~1 de 3"),
    MUY_ALTO("Muy alto", "~1 de 2"),
}

data class FindriscEntrada(
    val edad: Int,
    val imc: Double,
    val cinturaCm: Double,
    val sexo: Sexo,
    val actividadDiaria30min: Boolean,
    val verdurasFrutasDiario: Boolean,
    val antihipertensivos: Boolean,
    val glucosaAltaPrevia: Boolean,
    val antecedenteFamiliar: AntecedenteFamiliar,
)

data class FindriscResultado(
    val puntos: List<Int>,
    val total: Int,
    val categoria: CategoriaFindrisc,
    /** Tamizaje positivo con el punto de corte peruano (≥ 11, [R14]). */
    val positivoCortePeru: Boolean,
)

/** FINDRISC, 8 ítems, 0–26 puntos (RF-22, regla A2). */
object Findrisc {
    const val CORTE_PERU = 11

    fun puntosEdad(edad: Int) = when {
        edad < 45 -> 0
        edad <= 54 -> 2
        edad <= 64 -> 3
        else -> 4
    }

    fun puntosImc(imc: Double) = when {
        imc < 25.0 -> 0
        imc <= 30.0 -> 1
        else -> 3
    }

    fun puntosCintura(cinturaCm: Double, sexo: Sexo) = when (sexo) {
        Sexo.MASCULINO -> when { cinturaCm < 94 -> 0; cinturaCm <= 102 -> 3; else -> 4 }
        Sexo.FEMENINO -> when { cinturaCm < 80 -> 0; cinturaCm <= 88 -> 3; else -> 4 }
    }

    fun categoria(total: Int) = when {
        total < 7 -> CategoriaFindrisc.BAJO
        total <= 11 -> CategoriaFindrisc.LIGERAMENTE_ELEVADO
        total <= 14 -> CategoriaFindrisc.MODERADO
        total <= 20 -> CategoriaFindrisc.ALTO
        else -> CategoriaFindrisc.MUY_ALTO
    }

    /** No aplica a pacientes con DM2 diagnosticada. */
    fun aplica(condicion: Condicion) = condicion != Condicion.DM2

    fun evaluar(e: FindriscEntrada): FindriscResultado {
        val p = listOf(
            puntosEdad(e.edad),
            puntosImc(e.imc),
            puntosCintura(e.cinturaCm, e.sexo),
            if (e.actividadDiaria30min) 0 else 2,
            if (e.verdurasFrutasDiario) 0 else 1,
            if (e.antihipertensivos) 2 else 0,
            if (e.glucosaAltaPrevia) 5 else 0,
            e.antecedenteFamiliar.puntos,
        )
        val total = p.sum()
        return FindriscResultado(p, total, categoria(total), total >= CORTE_PERU)
    }
}
