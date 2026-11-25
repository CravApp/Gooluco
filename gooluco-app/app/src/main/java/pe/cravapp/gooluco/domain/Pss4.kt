package pe.cravapp.gooluco.domain

/**
 * PSS-4 referido a la hora previa (RF-20). 4 ítems Likert 0–4; los ítems 2 y 3 se invierten (4 − x).
 * Total 0–16. Textos: adaptación provisional al español (TBD-06: versión validada para Perú).
 */
object Pss4 {
    const val VERSION = "EMA-ESTRES-SOSW 1.0"

    val ITEMS = listOf(
        "En la última hora, ¿con qué frecuencia sintió que no podía controlar las cosas importantes?",
        "En la última hora, ¿con qué frecuencia se sintió seguro de su capacidad para manejar sus problemas?",
        "En la última hora, ¿con qué frecuencia sintió que las cosas le iban bien?",
        "En la última hora, ¿con qué frecuencia sintió que las dificultades se acumulaban tanto que no podía superarlas?",
    )
    val OPCIONES = listOf("Nunca", "Casi nunca", "A veces", "Bastante a menudo", "Muy a menudo")
    private val INVERTIDOS = setOf(1, 2) // índices 0-based de los ítems 2 y 3

    /** Puntajes por ítem ya invertidos. */
    fun puntuados(respuestas: List<Int>): List<Int> {
        require(respuestas.size == 4) { "PSS-4 requiere 4 respuestas" }
        require(respuestas.all { it in 0..4 }) { "Respuestas fuera de 0..4" }
        return respuestas.mapIndexed { i, r -> if (i in INVERTIDOS) 4 - r else r }
    }

    fun total(respuestas: List<Int>): Int = puntuados(respuestas).sum()

    /** Contexto del paper SOSW (RF-20): 13 actividades, 5 lugares, social binario. */
    val ACTIVIDADES = listOf(
        "trabajando_estudiando" to "Trabajando / estudiando",
        "durmiendo" to "Durmiendo",
        "descansando" to "Descansando / relajándose",
        "viendo_videos" to "Viendo videos",
        "clase_reunion" to "En clase / reunión",
        "comiendo_bebiendo" to "Comiendo / bebiendo",
        "jugando" to "Jugando",
        "conversando" to "Conversando",
        "preparandose_dormir" to "Preparándose para dormir",
        "llamando_mensajes" to "Llamando / escribiendo mensajes",
        "recien_despierto" to "Recién despierto",
        "manejando" to "Manejando",
        "otra" to "Otra",
    )
    val LUGARES = listOf(
        "casa" to "Casa",
        "trabajo" to "Trabajo",
        "restaurante" to "Restaurante",
        "vehiculo" to "Vehículo",
        "otro" to "Otro",
    )
}
