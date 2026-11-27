package com.gymsmart.gymsmart.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gymsmart.gymsmart.R

/**
 * Lanza la notificación de "descanso terminado" cuando salta la alarma programada al guardar la
 * serie (Tarea C).
 *
 * Va en un `BroadcastReceiver` y no en una corrutina de la app a propósito: entre serie y serie el
 * móvil se bloquea o te vas a otra app, y el sistema puede congelar el proceso. El receptor lo
 * despierta el sistema, así que el aviso llega igual.
 *
 * Tiene que comportarse como un mensaje de WhatsApp (lo que pidió el usuario el 2026-11-27): suena y
 * vibra, sale flotando arriba si estás usando el móvil, se ve en la pantalla de bloqueo al encenderla
 * y al tocarla vuelve a la app. Por eso el canal es de importancia alta y visibilidad pública.
 */
class RestAlarmReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        createChannelIfNeeded(manager)

        // Tocarla vuelve a la sesión: el intent del lanzador trae al frente la tarea que ya existe en
        // vez de abrir otra copia de la app.
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Descanso terminado")
            .setContentText("A por la siguiente serie")
            // Monocromo: con el icono de la app a color, la barra de estado enseña una mancha blanca.
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // MESSAGE y no ALARM: algunos fabricantes tratan ALARM como un despertador (pantalla
            // completa, sonido de alarma), y lo que se quería es algo como un mensaje.
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setVibrate(VIBRATION)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        // Si el usuario denegó las notificaciones (Android 13+), `notify` lanza SecurityException.
        // No es un fallo que deba tirar la app: el contador de la pantalla sigue funcionando.
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun createChannelIfNeeded(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // El canal viejo se creó sin vibración ni visibilidad en bloqueo, y los ajustes de un canal no
        // se pueden cambiar una vez creado: se borra y se usa uno nuevo.
        runCatching { manager.deleteNotificationChannel(OLD_CHANNEL_ID) }
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Descanso entre series", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisa cuando termina el descanso entre series"
                enableVibration(true)
                vibrationPattern = VIBRATION
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "rest_timer_v2"
        private const val OLD_CHANNEL_ID = "rest_timer"
        private val VIBRATION = longArrayOf(0, 350, 150, 350)
        const val NOTIFICATION_ID = 2001
        const val ACTION = "com.gymsmart.gymsmart.REST_FINISHED"
    }
}
