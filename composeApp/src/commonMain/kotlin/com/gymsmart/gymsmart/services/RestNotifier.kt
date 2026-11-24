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
 * **Cómo se evita el aviso doble.** Siempre se programa. Si el contador de la pantalla llega a cero
 * con la app delante, la propia pantalla llama a [cancel] y enseña el aviso dentro de la app, así que
 * la notificación del sistema no llega a salir. Si la app estaba en segundo plano no se cancela nada
 * y salta la notificación, que es justo lo que se quiere. En el peor caso salen los dos, que molesta
 * pero no engaña.
 *
 * Solo hace algo real en Android. En el resto de plataformas el gimnasio no es el caso de uso (el
 * escritorio y la web no se llevan al banco de press), así que el aviso de dentro de la app basta.
 */
interface RestNotifier {
    /** Programa el aviso para dentro de [seconds] segundos. Reemplaza cualquier aviso pendiente. */
    fun schedule(seconds: Int)

    /** Cancela el aviso pendiente, si lo hay. Seguro de llamar aunque no haya ninguno. */
    fun cancel()
}

@Composable
expect fun rememberRestNotifier(): RestNotifier
