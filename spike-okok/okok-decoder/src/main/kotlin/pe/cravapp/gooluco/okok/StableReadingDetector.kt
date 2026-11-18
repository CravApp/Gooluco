package pe.cravapp.gooluco.okok

/**
 * Decide cuándo una trama corresponde a una "lectura estable" (pesaje final) por balanza (MAC).
 *
 * - Si la variante trae flag de estabilidad (C0, V20, V11): la primera trama con flag = 1
 *   después de tramas no estables (o con un peso distinto) se marca como lectura estable.
 *   Las repeticiones del mismo peso estable no se vuelven a marcar.
 * - Si no trae flag (VF0): se marca estable cuando el mismo peso se repite
 *   [repeatsForUnflagged] veces seguidas (heurística del spike, a validar con hardware).
 */
class StableReadingDetector(
    private val repeatsForUnflagged: Int = 3,
    private val toleranceKg: Double = 0.05,
) {
    private data class State(var lastWeight: Double? = null, var repeats: Int = 0, var lastStableWeight: Double? = null)

    private val states = mutableMapOf<String, State>()

    /** Devuelve true si [frame] de la balanza [mac] debe marcarse como lectura estable. */
    fun accept(mac: String, frame: OkokFrame): Boolean {
        val s = states.getOrPut(mac) { State() }
        val w = frame.weightKg
        val same = s.lastWeight?.let { kotlin.math.abs(it - w) <= toleranceKg } ?: false
        s.repeats = if (same) s.repeats + 1 else 1
        s.lastWeight = w

        val isStable = when (frame.stable) {
            true -> true
            false -> false
            null -> s.repeats >= repeatsForUnflagged
        }
        if (!isStable) {
            s.lastStableWeight = null
            return false
        }
        val alreadyReported = s.lastStableWeight?.let { kotlin.math.abs(it - w) <= toleranceKg } ?: false
        if (alreadyReported) return false
        s.lastStableWeight = w
        return true
    }

    fun reset() = states.clear()
}
