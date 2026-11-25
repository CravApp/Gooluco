package pe.cravapp.gooluco.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base local Room. Sin nube. Pendiente (RNF de seguridad): cifrado en reposo con SQLCipher / Keystore.
 */
@Database(
    entities = [
        PerfilEntity::class, TallaHistorialEntity::class, PesajeEntity::class, EmaRespuestaEntity::class,
        FindriscEntity::class, NordicoEntity::class, SlotEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BaseDatos : RoomDatabase() {
    abstract fun perfil(): PerfilDao
    abstract fun pesajes(): PesajeDao
    abstract fun ema(): EmaDao
    abstract fun cuestionarios(): CuestionarioDao
    abstract fun agenda(): AgendaDao

    companion object {
        const val NOMBRE = "equilibra.db"
        @Volatile private var instancia: BaseDatos? = null
        fun get(ctx: Context): BaseDatos = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(ctx.applicationContext, BaseDatos::class.java, NOMBRE).build().also { instancia = it }
        }
    }
}
