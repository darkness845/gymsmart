package com.gymsmart.gymsmart.services

import androidx.compose.runtime.Composable

/**
 * Arranca/para el foreground service que mantiene la grabación de rutas GPS viva cuando la
 * pantalla se apaga o la app pasa a segundo plano. Solo tiene efecto real en Android: en el resto
 * de plataformas el GPS no funciona todavía (ver 01-arquitectura.md §5), así que no hay nada que
 * mantener viva.
 */
interface BackgroundTrackingController {
    fun start()
    fun stop()
}

@Composable
expect fun rememberBackgroundTrackingController(): BackgroundTrackingController
