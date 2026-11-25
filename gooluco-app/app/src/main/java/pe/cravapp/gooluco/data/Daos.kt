package pe.cravapp.gooluco.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PerfilDao {
    @Query("SELECT * FROM perfil WHERE id = 1") fun observar(): Flow<PerfilEntity?>
    @Query("SELECT * FROM perfil WHERE id = 1") suspend fun obtener(): PerfilEntity?
    @Upsert suspend fun guardar(p: PerfilEntity)
    @Insert suspend fun registrarTalla(t: TallaHistorialEntity)
}

@Dao
interface PesajeDao {
    @Query("SELECT * FROM pesaje ORDER BY timestampUtc DESC") fun observarTodos(): Flow<List<PesajeEntity>>
    @Query("SELECT * FROM pesaje ORDER BY timestampUtc DESC LIMIT 1") suspend fun ultimo(): PesajeEntity?
    @Insert suspend fun insertar(p: PesajeEntity): Long
    @Query("UPDATE pesaje SET imcExterno = :imc WHERE id = :id") suspend fun fijarImcExterno(id: Long, imc: Double?)
    @Query("DELETE FROM pesaje WHERE id = :id") suspend fun borrar(id: Long)
}

@Dao
interface EmaDao {
    @Query("SELECT * FROM ema_respuesta ORDER BY programadaUtc DESC") fun observarTodas(): Flow<List<EmaRespuestaEntity>>
    @Query("SELECT COUNT(*) FROM ema_respuesta WHERE slotId = :slot AND fechaProgramada = :fecha")
    suspend fun existe(slot: String, fecha: String): Int
    @Insert suspend fun insertar(e: EmaRespuestaEntity)
}

@Dao
interface CuestionarioDao {
    @Query("SELECT * FROM findrisc ORDER BY timestampUtc DESC") fun observarFindrisc(): Flow<List<FindriscEntity>>
    @Insert suspend fun insertarFindrisc(f: FindriscEntity)
    @Query("SELECT * FROM nordico ORDER BY timestampUtc DESC") fun observarNordico(): Flow<List<NordicoEntity>>
    @Insert suspend fun insertarNordico(n: NordicoEntity)
}

@Dao
interface AgendaDao {
    @Query("SELECT * FROM slot_agenda ORDER BY minutoDelDia, id") fun observar(): Flow<List<SlotEntity>>
    @Query("SELECT * FROM slot_agenda ORDER BY minutoDelDia, id") suspend fun todos(): List<SlotEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun guardar(s: List<SlotEntity>)
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_estado") fun observar(): Flow<List<SyncEstadoEntity>>
    @Query("SELECT * FROM sync_estado") suspend fun todos(): List<SyncEstadoEntity>
    @Upsert suspend fun guardar(s: SyncEstadoEntity)
    @Query("DELETE FROM sync_estado") suspend fun borrarTodo()
}

/** Consultas de solo lectura para armar la cola de sincronización. */
@Dao
interface LecturaSyncDao {
    @Query("SELECT * FROM pesaje") suspend fun pesajes(): List<PesajeEntity>
    @Query("SELECT * FROM talla_historial") suspend fun tallas(): List<TallaHistorialEntity>
    @Query("SELECT * FROM talla_historial") fun observarTallas(): Flow<List<TallaHistorialEntity>>
    @Query("SELECT * FROM ema_respuesta") suspend fun emas(): List<EmaRespuestaEntity>
    @Query("SELECT * FROM findrisc") suspend fun findrisc(): List<FindriscEntity>
    @Query("SELECT * FROM nordico") suspend fun nordico(): List<NordicoEntity>
}
