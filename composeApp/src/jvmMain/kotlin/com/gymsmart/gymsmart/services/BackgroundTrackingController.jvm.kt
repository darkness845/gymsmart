package com.gymsmart.gymsmart.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Desktop no tiene GPS real (ver DesktopLocationProvider), nada que mantener viva en segundo plano.
@Composable
actual fun rememberBackgroundTrackingController(): BackgroundTrackingController =
    remember {
        object : BackgroundTrackingController {
            override fun start() {}
            override fun stop() {}
        }
    }
