package com.gymsmart.gymsmart.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gymsmart.gymsmart.R

/**
 * Su único trabajo es mantener un foreground service con tipo "location" activo mientras se graba
 * una ruta. No recolecta ubicación por sí mismo (eso sigue siendo [LocationService], llamado desde
 * `GpsScreen`): basta con que este servicio esté en marcha para que Android siga entregando
 * actualizaciones de ubicación al proceso de la app con la pantalla apagada o la app en segundo
 * plano, que es justo lo que no pasaba antes (ver GPS-1 en doc/08-backlog-mejoras.md).
 */
class GpsTrackingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification() = run {
        createChannelIfNeeded()
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GymSmart")
            .setContentText("Grabando ruta GPS")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Seguimiento GPS", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private companion object {
        const val CHANNEL_ID = "gps_tracking"
        const val NOTIFICATION_ID = 1001
    }
}
