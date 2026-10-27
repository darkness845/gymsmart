package com.gymsmart.gymsmart.services

import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberBackgroundTrackingController(): BackgroundTrackingController {
    val context = LocalContext.current
    return remember {
        object : BackgroundTrackingController {
            override fun start() {
                val intent = Intent(context, GpsTrackingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }

            override fun stop() {
                context.stopService(Intent(context, GpsTrackingService::class.java))
            }
        }
    }
}
