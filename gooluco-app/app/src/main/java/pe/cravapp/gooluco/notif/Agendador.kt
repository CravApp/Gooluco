package pe.cravapp.gooluco.notif

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pe.cravapp.gooluco.MainActivity
import pe.cravapp.gooluco.R
import pe.cravapp.gooluco.data.BaseDatos
import pe.cravapp.gooluco.domain.Agenda
import pe.cravapp.gooluco.domain.ConfigAgenda
import pe.cravapp.gooluco.domain.SlotAgenda
import pe.cravapp.gooluco.domain.TipoSlot
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Notificaciones locales de la agenda (RF-17, RF-18). Usa alarmas inexactas con ventana de 5 min
 * (no requieren el permiso SCHEDULE_EXACT_ALARM); se reprograman tras reinicio, cambio de hora/zona y actualización.
 */
object Agendador {
    const val CANAL = "agenda"
    const val EXTRA_SLOT = "slot"
    const val EXTRA_TIPO = "tipo"
    const val EXTRA_RECORDATORIO = "recordatorio"
    const val EXTRA_FECHA = "fecha"
    private const val VENTANA_MS = 5 * 60_000L

    fun crearCanal(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Agenda diaria", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Cuestionarios de estrés y recordatorios de pesaje"
            }
        )
    }

    private fun pi(ctx: Context, slot: SlotAgenda, recordatorio: Boolean, fecha: LocalDate?): PendingIntent {
        val i = Intent(ctx, AlarmaReceiver::class.java)
            .putExtra(EXTRA_SLOT, slot.id)
            .putExtra(EXTRA_TIPO, slot.tipo.name)
            .putExtra(EXTRA_RECORDATORIO, recordatorio)
            .putExtra(EXTRA_FECHA, fecha?.toString())
        val code = (slot.id.hashCode() * 2) + if (recordatorio) 1 else 0
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun programar(ctx: Context, slots: List<SlotAgenda>, cfg: ConfigAgenda = ConfigAgenda()) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val ahora = ZonedDateTime.now(cfg.zona)
        for (s in slots) {
            am.cancel(pi(ctx, s, false, null))
            if (!s.activo) continue
            val t = Agenda.proximoDisparo(s, ahora)
            am.setWindow(AlarmManager.RTC_WAKEUP, t.toInstant().toEpochMilli(), VENTANA_MS, pi(ctx, s, false, null))
        }
    }

    fun programarRecordatorio(ctx: Context, slot: SlotAgenda, fecha: LocalDate, cfg: ConfigAgenda = ConfigAgenda()) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val minutos = if (slot.tipo == TipoSlot.EMA) cfg.recordatorioMin else 180
        val t = ZonedDateTime.now(cfg.zona).plusMinutes(minutos)
        am.setWindow(AlarmManager.RTC_WAKEUP, t.toInstant().toEpochMilli(), VENTANA_MS, pi(ctx, slot, true, fecha))
    }

    fun cancelarTodo(ctx: Context, slots: List<SlotAgenda>) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        slots.forEach { am.cancel(pi(ctx, it, false, null)); am.cancel(pi(ctx, it, true, null)) }
    }

    fun notificar(ctx: Context, slotId: String, tipo: TipoSlot, recordatorio: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val abrir = PendingIntent.getActivity(
            ctx, slotId.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_SLOT, slotId).putExtra(EXTRA_TIPO, tipo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val (titulo, texto) = when (tipo) {
            TipoSlot.EMA -> (if (recordatorio) "Recordatorio: " else "") + "¿Cómo va tu estrés?" to
                "Responde 4 preguntas sobre la última hora (menos de 1 minuto)."
            TipoSlot.PESAJE_MANANA -> "Pesaje de la mañana" to "Súbete a la balanza en ayunas para registrar tu peso."
            TipoSlot.PESAJE_NOCHE -> "Pesaje de la noche" to "Registra tu peso de la noche en la balanza."
        }
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_stat_equilibra)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(slotId.hashCode(), n) } catch (_: SecurityException) { }
    }

    /** Lee la agenda de la base y reprograma todo (arranque, cambio de hora, actualización). */
    fun reprogramarDesdeBase(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val db = BaseDatos.get(app)
            if (db.perfil().obtener()?.consentimientoUtc == null) return@launch
            val slots = db.agenda().todos().map { it.aDominio() }
            programar(app, slots)
        }
    }
}

fun pe.cravapp.gooluco.data.SlotEntity.aDominio() =
    SlotAgenda(id, TipoSlot.valueOf(tipo), LocalTime.of(minutoDelDia / 60, minutoDelDia % 60), activo)

/** Dispara la notificación del slot y deja programado el del día siguiente. */
class AlarmaReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val slotId = intent.getStringExtra(Agendador.EXTRA_SLOT) ?: return
        val tipo = intent.getStringExtra(Agendador.EXTRA_TIPO)?.let { TipoSlot.valueOf(it) } ?: return
        val recordatorio = intent.getBooleanExtra(Agendador.EXTRA_RECORDATORIO, false)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = BaseDatos.get(ctx.applicationContext)
                val slot = db.agenda().todos().firstOrNull { it.id == slotId }?.aDominio()
                val hoy = LocalDate.now(ConfigAgenda().zona)
                if (slot != null && slot.activo) {
                    if (!recordatorio) {
                        Agendador.notificar(ctx, slotId, tipo, false)
                        Agendador.programarRecordatorio(ctx, slot, hoy)
                        Agendador.programar(ctx, listOf(slot)) // siguiente día
                    } else {
                        val fecha = intent.getStringExtra(Agendador.EXTRA_FECHA) ?: hoy.toString()
                        val yaHecho = when (tipo) {
                            TipoSlot.EMA -> db.ema().existe(slotId, fecha) > 0
                            else -> db.pesajes().ultimo()?.fechaLocal == fecha // simplificado
                        }
                        if (!yaHecho) Agendador.notificar(ctx, slotId, tipo, true)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** RF-17: reprograma tras reinicio, cambio de hora/zona o actualización de la app. */
class ReprogramarReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Agendador.reprogramarDesdeBase(ctx)
    }
}
