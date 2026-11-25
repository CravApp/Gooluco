package pe.cravapp.gooluco.fhir

import pe.cravapp.gooluco.data.EmaRespuestaEntity
import pe.cravapp.gooluco.data.FindriscEntity
import pe.cravapp.gooluco.data.NordicoEntity
import pe.cravapp.gooluco.data.PerfilEntity
import pe.cravapp.gooluco.data.PesajeEntity
import pe.cravapp.gooluco.data.TallaHistorialEntity
import pe.cravapp.gooluco.domain.EstadoEma
import pe.cravapp.gooluco.domain.Nordico
import pe.cravapp.gooluco.domain.RegionNordica
import pe.cravapp.gooluco.domain.RespuestaRegion
import java.time.OffsetDateTime

/** Datos FICTICIOS para pruebas (MAC, trama e impedancia inventadas para verificar que nunca se envían). */
object Muestras {
    const val ZONA = "America/Lima"
    const val MAC_FALSA = "AA:BB:CC:DD:EE:FF"
    const val TRAMA_FALSA = "C0FFEE00DEADBEEF"
    const val IMPEDANCIA_FALSA = 543.21

    fun ts(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    val perfil = PerfilEntity(tallaCm = 165.0, fechaNacimiento = "1980-06-15", sexo = "FEMENINO", condicion = "OBESIDAD", balanzaMac = MAC_FALSA)
    val talla = TallaHistorialEntity(id = 1, tallaCm = 165.0, desdeUtc = ts("2026-11-01T08:00:00-05:00"))

    fun pesaje(id: Long = 7, iso: String = "2026-11-25T07:05:30-05:00", fuente: String = "SIMULADA") = PesajeEntity(
        id = id, timestampUtc = ts(iso), zona = ZONA, pesoKg = 82.456, impedanciaOhm = IMPEDANCIA_FALSA,
        unidadOriginal = "kg", variante = "C0", mac = MAC_FALSA, rssi = -61, fuente = fuente, tramaHex = TRAMA_FALSA,
        tallaCmUsada = 165.0, imc = 30.287, claseImc = "OBESIDAD_I", fechaLocal = "2026-11-25", franja = "MANANA",
    )

    // Marcadas 1,3,2,1 (como el ejemplo de Postman); Room guarda 2 y 3 invertidos → 1,1,2,1 = 5.
    val ema = EmaRespuestaEntity(
        id = 3, slotId = "s1", fechaProgramada = "2026-11-25", programadaUtc = ts("2026-11-25T10:00:00-05:00"),
        respondidaUtc = ts("2026-11-25T10:03:00-05:00"), latenciaSeg = 180, estado = EstadoEma.RESPONDIDA.name,
        i1 = 1, i2 = 1, i3 = 2, i4 = 1, pssTotal = 5, actividad = "trabajando_estudiando", lugar = "casa", social = false, version = "PSS-4 1.0",
    )

    // edad 46 → 2, IMC 30.3 → 3, cintura 95 (F) → 4, actividad sí → 0, verduras no → 1, antihipertensivos no → 0, glucosa no → 0, familia 2.º grado → 3
    val findrisc = FindriscEntity(
        id = 4, timestampUtc = ts("2026-11-25T11:00:00-05:00"), puntos = "2,3,4,0,1,0,0,3", total = 13,
        categoria = "MODERADO", imcUsado = 30.287, cinturaCm = 95.0,
    )

    val nordico = NordicoEntity(
        id = 5, timestampUtc = ts("2026-11-25T12:00:00-05:00"),
        respuestas = Nordico.codificar(
            mapOf(
                RegionNordica.CUELLO to RespuestaRegion(molestias12m = true, impidio12m = false, molestias7d = true),
                RegionNordica.ESPALDA_BAJA to RespuestaRegion(molestias12m = true, impidio12m = true, molestias7d = false),
            ),
        ),
        version = Nordico.VERSION,
    )
}
