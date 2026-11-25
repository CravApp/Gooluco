package pe.cravapp.gooluco.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base local Room. Sin nube. Pendiente (RNF de seguridad): cifrado en reposo con SQLCipher / Keystore.
 */
@Database(
    entities = [
        PerfilEntity::class, TallaHistorialEntity::class, PesajeEntity::class, EmaRespuestaEntity::class,
        FindriscEntity::class, NordicoEntity::class, SlotEntity::class, SyncEstadoEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class BaseDatos : RoomDatabase() {
    abstract fun perfil(): PerfilDao
    abstract fun pesajes(): PesajeDao
    abstract fun ema(): EmaDao
    abstract fun cuestionarios(): CuestionarioDao
    abstract fun agenda(): AgendaDao
    abstract fun sync(): SyncDao
    abstract fun lecturaSync(): LecturaSyncDao

    companion object {
        const val NOMBRE = "equilibra.db"
        @Volatile private var instancia: BaseDatos? = null
        fun get(ctx: Context): BaseDatos = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(ctx.applicationContext, BaseDatos::class.java, NOMBRE)
                .addMigrations(MIGRACION_1_2)
                .build().also { instancia = it }
        }
    }
}

/** v1 → v2: tabla de estado de sincronización FHIR (no toca los datos existentes). */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_estado` (`clave` TEXT NOT NULL, `tipoRecurso` TEXT NOT NULL, `remoteId` TEXT, " +
                "`estado` TEXT NOT NULL, `intentos` INTEGER NOT NULL, `error` TEXT, `actualizadoUtc` INTEGER NOT NULL, PRIMARY KEY(`clave`))"
        )
    }
}
