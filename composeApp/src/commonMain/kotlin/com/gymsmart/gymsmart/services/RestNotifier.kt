package com.gymsmart.gymsmart.services

import androidx.compose.runtime.Composable

/**
 * Avisa de que se acabó el descanso entre series, incluso con la app en segundo plano o el móvil
 * bloqueado (Tarea C).
 *
 * **Por qué no basta con el contador de la pantalla.** El descanso ya se contaba, pero dentro de un
 * `LaunchedEffect` de la tarjeta del ejercicio: si sales de la app a mirar el móvil —que es
 * exactamente lo que se hace entre series— el sistema puede congelar el proceso y nadie avisa. Por
 * eso el aviso se **programa en el sistema operativo** al guardar la serie, en vez de depender de que
 * la app siga viva.
 *
 * **Siempre la notificación del sistema, nunca un aviso dentro de la app** (decisión del usuario,
 * 2026-11-27): quiere enterarse como de un WhatsApp, encendiendo la pantalla, también con la app
 * delante. Antes, si el contador llegaba a cero con la app abierta, la pantalla cancelaba la alarma y
 * pintaba un aviso propio; eso se quitó. Donde no hay notificación del sistema
 * ([showsSystemNotification] = `false`), la pantalla sigue enseñando su aviso.
 *
 * Solo hace algo real en Android. En el resto de plataformas el gimnasio no es el caso de uso (el
 * escritorio y la web no se llevan al banco de press), así que el aviso de dentro de la app basta.
 */
interface RestNotifier {
    /** Programa el aviso para dentro de [seconds] segundos. Reemplaza cualquier aviso pendiente. */
    fun schedule(seconds: Int)

    /** Cancela el aviso pendiente, si lo hay. Seguro de llamar aunque no haya ninguno. */
    fun cancel()

    /** `true` si el aviso sale como notificación del sistema; si no, la pantalla pinta el suyo. */
    val showsSystemNotification: Boolean get() = false

    /**
     * Pide el permiso de notificaciones si hace falta (Android 13+ las trae apagadas). Se llama al
     * abrir la sesión, para que el primer descanso ya avise.
     */
    fun requestPermissionIfNeeded() {}
}

@Composable
expect fun rememberRestNotifier(): RestNotifier
