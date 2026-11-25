package pe.cravapp.gooluco.domain

import kotlin.math.sqrt

data class Recta(val pendiente: Double, val intercepto: Double, val n: Int) {
    fun en(x: Double) = intercepto + pendiente * x
}

object Tendencia {
    /** Regresión lineal por mínimos cuadrados (x en días, y en kg). Null si hay < 2 puntos o x constante. */
    fun regresion(puntos: List<Pair<Double, Double>>): Recta? {
        if (puntos.size < 2) return null
        val n = puntos.size.toDouble()
        val mx = puntos.sumOf { it.first } / n
        val my = puntos.sumOf { it.second } / n
        val sxx = puntos.sumOf { (it.first - mx) * (it.first - mx) }
        if (sxx == 0.0) return null
        val sxy = puntos.sumOf { (it.first - mx) * (it.second - my) }
        val b = sxy / sxx
        return Recta(b, my - b * mx, puntos.size)
    }

    /**
     * Proyección lineal a [dias] días desde el último punto. No es un modelo ML: es una extrapolación
     * honesta de la tendencia local, con mínimo [minPuntos] pesajes.
     */
    fun proyeccion(puntos: List<Pair<Double, Double>>, dias: Int = 14, minPuntos: Int = 4): Double? {
        if (puntos.size < minPuntos) return null
        val r = regresion(puntos) ?: return null
        val ultimo = puntos.maxOf { it.first }
        return Imc.redondear1(r.en(ultimo + dias))
    }

    /** Pendiente a 7 días (kg/día) con ≥ 4 pesajes de mañana (RF-27). */
    fun pendiente7d(pesajesManana: List<Pair<Double, Double>>): Double? =
        if (pesajesManana.size < 4) null else regresion(pesajesManana)?.pendiente

    /** Correlación de Spearman (rangos promedio en empates). Null si < [minPares] pares (RF-27: 14). */
    fun spearman(pares: List<Pair<Double, Double>>, minPares: Int = 14): Double? {
        if (pares.size < minPares) return null
        val rx = rangos(pares.map { it.first })
        val ry = rangos(pares.map { it.second })
        return pearson(rx.zip(ry))
    }

    private fun pearson(p: List<Pair<Double, Double>>): Double? {
        val n = p.size.toDouble()
        val mx = p.sumOf { it.first } / n
        val my = p.sumOf { it.second } / n
        val sxy = p.sumOf { (it.first - mx) * (it.second - my) }
        val sxx = p.sumOf { (it.first - mx) * (it.first - mx) }
        val syy = p.sumOf { (it.second - my) * (it.second - my) }
        if (sxx == 0.0 || syy == 0.0) return null
        return sxy / sqrt(sxx * syy)
    }

    internal fun rangos(v: List<Double>): List<Double> {
        val orden = v.indices.sortedBy { v[it] }
        val r = DoubleArray(v.size)
        var i = 0
        while (i < orden.size) {
            var j = i
            while (j + 1 < orden.size && v[orden[j + 1]] == v[orden[i]]) j++
            val prom = (i + j) / 2.0 + 1.0
            for (k in i..j) r[orden[k]] = prom
            i = j + 1
        }
        return r.toList()
    }
}
