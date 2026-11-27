package com.gymsmart.gymsmart.services

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Programa el aviso de fin de descanso con `AlarmManager`, para que llegue aunque la app esté en
 * segundo plano o el móvil bloqueado (Tarea C).
 *
 * **Por qué una alarma y no una corrutina.** La corrutina que ya cuenta el descanso vive en la
 * pantalla; si sales de la app, el sistema puede congelar el proceso y no avisa nadie. La alarma la
 * guarda el sistema, así que sobrevive a eso.
 *
 * **Por qué `ELAPSED_REALTIME_WAKEUP`.** Cuenta desde el arranque del dispositivo y no se ve afectada
 * por cambios de hora ni de zona horaria, que es justo lo que se quiere para "dentro de 90 segundos".
 * Y `WAKEUP` porque un descanso sirve de poco si el aviso espera a que enciendas la pantalla.
 *
 * **Exactitud.** Se usa alarma exacta cuando el sistema la permite. Desde Android 12 hace falta un
 * permiso que el usuario concede a mano, así que si no está concedido se cae a la versión inexacta
 * en vez de reventar: puede retrasarse, pero avisa. Se comprueba en cada llamada y no una sola vez,
 * porque el permiso se puede revocar con la app abierta. Desde 2026-11-27 la app declara también
 * `USE_EXACT_ALARM`, que Android 13+ concede al instalar, así que en la práctica siempre es exacta.
 *
 * **Permiso de notificaciones.** Con `targetSdk` 33+ las notificaciones vienen apagadas hasta que la
 * app las pide, y la app **nunca las pedía**: la alarma saltaba, el receptor llamaba a `notify` y el
 * sistema la tiraba en silencio. Por eso "el aviso no funcionaba". Ahora se piden al abrir la sesión.
 */
@Composable
actual fun rememberRestNotifier(): RestNotifier {
    val context = LocalContext.current.applicationContext
    // Si lo deniega no se insiste: el contador de la pantalla sigue funcionando, y Android deja de
    // enseñar el diálogo tras dos negativas (entonces solo se activa desde Ajustes).
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        AndroidRestNotifier(context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }
}

private class AndroidRestNotifier(
    private val context: Context,
    private val askPermission: () -> Unit
) : RestNotifier {

    override val showsSystemNotification: Boolean get() = true

    override fun requestPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) askPermission()
    }

    /** Quita de la barra el aviso del descanso anterior: al empezar otro o cancelarlo ya no sirve. */
    private fun clearShown() {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(RestAlarmReceiver.NOTIFICATION_ID)
        }
    }

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private fun pendingIntent(): PendingIntent? {
        val intent = Intent(context, RestAlarmReceiver::class.java).setAction(RestAlarmReceiver.ACTION)
        // FLAG_UPDATE_CURRENT con el mismo request code: programar un descanso nuevo reemplaza al
        // anterior en vez de acumular avisos de series ya hechas.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return runCatching {
            PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        }.getOrNull()
    }

    override fun schedule(seconds: Int) {
        if (seconds <= 0) return
        clearShown()
        val manager = alarmManager ?: return
        val pending = pendingIntent() ?: return
        val triggerAt = SystemClock.elapsedRealtime() + seconds * 1000L

        val puedeSerExacta = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)

        runCatching {
            if (puedeSerExacta) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending
                )
            }
        }
    }

    override fun cancel() {
        clearShown()
        val manager = alarmManager ?: return
        pendingIntent()?.let { runCatching { manager.cancel(it) } }
    }

    private companion object {
        /** Único: solo puede haber un descanso en marcha, y uno nuevo pisa al anterior. */
        const val REQUEST_CODE = 2001
    }
}
