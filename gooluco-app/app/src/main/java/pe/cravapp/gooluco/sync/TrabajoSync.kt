package pe.cravapp.gooluco.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Worker de subida: solo con red, reintentos con backoff exponencial ante fallos transitorios. */
class TrabajoSync(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val almacen = AlmacenSeguro.get(applicationContext)
        if (!almacen.estado.value.activa || !almacen.estado.value.conectado) return Result.success()
        return try {
            val r = Sincronizador(applicationContext).ejecutar(inputData.getBoolean(K_REINTENTAR, false))
            if (r.transitorio && runAttemptCount < MAX_INTENTOS) Result.retry() else Result.success()
        } catch (e: ErrorSync) {
            almacen.registrarError(e.message ?: "Error de sincronización")
            if (e.transitorio && runAttemptCount < MAX_INTENTOS) Result.retry() else Result.failure()
        } catch (e: Exception) {
            almacen.registrarError("Error inesperado: ${e.message}")
            if (runAttemptCount < MAX_INTENTOS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val K_REINTENTAR = "reintentar_errores"
        const val UNICO = "sync-fhir"
        const val PERIODICO = "sync-fhir-periodico"
        private const val MAX_INTENTOS = 5
    }
}

object ProgramadorSync {
    private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Encola una sincronización inmediata (o al recuperar la red). */
    fun ahora(ctx: Context, reintentarErrores: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<TrabajoSync>()
            .setConstraints(conRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(TrabajoSync.K_REINTENTAR to reintentarErrores))
            .addTag(TrabajoSync.UNICO)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(TrabajoSync.UNICO, ExistingWorkPolicy.REPLACE, req)
    }

    /** Tras guardar un registro local: solo si la sincronización está activa y conectada. */
    fun siActiva(ctx: Context) {
        val e = AlmacenSeguro.get(ctx).estado.value
        if (e.activa && e.conectado) ahora(ctx)
    }

    fun periodica(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<TrabajoSync>(6, TimeUnit.HOURS)
            .setConstraints(conRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(TrabajoSync.PERIODICO, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancelar(ctx: Context) {
        WorkManager.getInstance(ctx).apply { cancelUniqueWork(TrabajoSync.UNICO); cancelUniqueWork(TrabajoSync.PERIODICO) }
    }
}
