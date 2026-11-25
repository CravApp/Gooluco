package pe.cravapp.gooluco.domain

/**
 * Cuestionario Nórdico Musculoesquelético estandarizado (Kuorinka et al., 1987), sección general.
 * No forma parte del SRS v2: lo introduce el diseño «Equilibra». Por cada región:
 * molestias en los últimos 12 meses, impedimento de actividades en 12 meses y molestias en los últimos 7 días.
 */
enum class RegionNordica(val etiqueta: String) {
    CUELLO("Cuello"),
    HOMBROS("Hombros"),
    CODOS("Codos"),
    MUNECAS_MANOS("Muñecas / manos"),
    ESPALDA_ALTA("Espalda alta (dorsal)"),
    ESPALDA_BAJA("Espalda baja (lumbar)"),
    CADERAS_MUSLOS("Caderas / muslos"),
    RODILLAS("Rodillas"),
    TOBILLOS_PIES("Tobillos / pies"),
}

data class RespuestaRegion(
    val molestias12m: Boolean = false,
    val impidio12m: Boolean = false,
    val molestias7d: Boolean = false,
)

object Nordico {
    const val VERSION = "NMQ-general 1.0"

    fun regiones7d(r: Map<RegionNordica, RespuestaRegion>) = r.filterValues { it.molestias7d }.keys.toList()
    fun regiones12m(r: Map<RegionNordica, RespuestaRegion>) = r.filterValues { it.molestias12m }.keys.toList()

    /** Resumen breve para la tarjeta «Nórdico hoy». */
    fun resumen(r: Map<RegionNordica, RespuestaRegion>): String {
        val n = regiones7d(r).size
        return when (n) {
            0 -> "Sin dolor"
            1 -> "Molestia: ${regiones7d(r).first().etiqueta.lowercase()}"
            else -> "Molestias en $n zonas"
        }
    }

    /** Codificación compacta para persistir: "CUELLO:110;HOMBROS:000;…" (12m, impidió, 7d). */
    fun codificar(r: Map<RegionNordica, RespuestaRegion>): String =
        RegionNordica.entries.joinToString(";") { reg ->
            val x = r[reg] ?: RespuestaRegion()
            "${reg.name}:${b(x.molestias12m)}${b(x.impidio12m)}${b(x.molestias7d)}"
        }

    fun decodificar(s: String): Map<RegionNordica, RespuestaRegion> =
        s.split(";").filter { it.contains(":") }.mapNotNull { part ->
            val (k, v) = part.split(":", limit = 2)
            val reg = RegionNordica.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
            if (v.length != 3) return@mapNotNull null
            reg to RespuestaRegion(v[0] == '1', v[1] == '1', v[2] == '1')
        }.toMap()

    private fun b(x: Boolean) = if (x) "1" else "0"
}
