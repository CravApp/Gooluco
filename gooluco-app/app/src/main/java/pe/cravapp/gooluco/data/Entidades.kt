package pe.cravapp.gooluco.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Perfil del paciente activo (RF-01). Una sola fila (id = 1). Solo en el dispositivo. */
@Entity(tableName = "perfil")
data class PerfilEntity(
    @PrimaryKey val id: Int = 1,
    val tallaCm: Double,
    val fechaNacimiento: String, // ISO yyyy-MM-dd
    val sexo: String,            // Sexo.name
    val condicion: String,       // Condicion.name
    val cinturaCm: Double? = null,
    val metaPesoKg: Double? = null,
    val consentimientoUtc: Long? = null, // RF-37
    val balanzaMac: String? = null,      // RF-08
    val balanzaVariante: String? = null,
)

/** Historial de talla (RF-02): cada cambio guarda fecha y autor. */
@Entity(tableName = "talla_historial")
data class TallaHistorialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tallaCm: Double,
    val desdeUtc: Long,
    val autor: String = "paciente",
)

/** Pesaje estable (RF-10). El IMC se calcula con la talla vigente y se guarda junto con ella. */
@Entity(tableName = "pesaje", indices = [Index("timestampUtc")])
data class PesajeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampUtc: Long,
    val zona: String,
    val pesoKg: Double,
    val impedanciaOhm: Double?,
    val unidadOriginal: String?,
    val variante: String,
    val mac: String,
    val rssi: Int?,
    val fuente: String,          // REAL / SIMULADA / MANUAL
    val tramaHex: String,
    val tallaCmUsada: Double?,
    val imc: Double?,
    val claseImc: String?,
    val fechaLocal: String,      // RF-24 (fecha D asignada)
    val franja: String,          // MANANA / NOCHE / OTRA
    val imcExterno: Double? = null, // RF-15: IMC del smartwatch, solo para contraste manual
)

/** Respuesta EMA de estrés (RF-19, RF-21). */
@Entity(tableName = "ema_respuesta", indices = [Index("fechaProgramada")])
data class EmaRespuestaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotId: String,
    val fechaProgramada: String,
    val programadaUtc: Long,
    val respondidaUtc: Long?,
    val latenciaSeg: Long?,
    val estado: String, // EstadoEma.name
    val i1: Int? = null, val i2: Int? = null, val i3: Int? = null, val i4: Int? = null, // ya puntuados (2 y 3 invertidos)
    val pssTotal: Int? = null,
    val actividad: String? = null,
    val lugar: String? = null,
    val social: Boolean? = null,
    val version: String,
)

/** Evaluación FINDRISC (RF-22). */
@Entity(tableName = "findrisc")
data class FindriscEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampUtc: Long,
    val puntos: String, // "2,1,3,2,0,0,0,5"
    val total: Int,
    val categoria: String,
    val imcUsado: Double,
    val cinturaCm: Double,
)

/** Cuestionario Nórdico (diseño Equilibra). */
@Entity(tableName = "nordico")
data class NordicoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampUtc: Long,
    val respuestas: String, // Nordico.codificar
    val version: String,
)

/** Slots de la agenda diaria (RF-16). */
@Entity(tableName = "slot_agenda")
data class SlotEntity(
    @PrimaryKey val id: String,
    val tipo: String,
    val minutoDelDia: Int,
    val activo: Boolean,
)

/** Estado de sincronización de cada recurso FHIR derivado de un registro local (clave «PESO:12», «PSS4:3»…). */
@Entity(tableName = "sync_estado")
data class SyncEstadoEntity(
    @PrimaryKey val clave: String,
    val tipoRecurso: String,
    val remoteId: String?,
    val estado: String, // EstadoItem.name
    val intentos: Int,
    val error: String?,
    val actualizadoUtc: Long,
)
