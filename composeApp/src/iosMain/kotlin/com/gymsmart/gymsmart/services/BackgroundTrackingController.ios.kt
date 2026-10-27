package com.gymsmart.gymsmart.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// iOS todavía no tiene un LocationProvider real (ver 01-arquitectura.md §5).
@Composable
actual fun rememberBackgroundTrackingController(): BackgroundTrackingController =
    remember {
        object : BackgroundTrackingController {
            override fun start() {}
            override fun stop() {}
        }
    }
