package com.gymsmart.gymsmart.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
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
 * El canal se crea con importancia alta porque el aviso solo sirve si suena en el momento: un
 * descanso que te enteras diez minutos después no es un descanso.
 */
class RestAlarmReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        createChannelIfNeeded(manager)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Descanso terminado")
            .setContentText("Toca la siguiente serie")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        // Si el usuario denegó las notificaciones (Android 13+), `notify` lanza SecurityException.
        // No es un fallo que deba tirar la app: el contador de la pantalla sigue funcionando.
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun createChannelIfNeeded(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Descanso entre series", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Avisa cuando termina el descanso entre series" }
        )
    }

    companion object {
        const val CHANNEL_ID = "rest_timer"
        const val NOTIFICATION_ID = 2001
        const val ACTION = "com.gymsmart.gymsmart.REST_FINISHED"
    }
}
