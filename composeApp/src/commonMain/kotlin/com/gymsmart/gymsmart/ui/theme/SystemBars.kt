package com.gymsmart.gymsmart.ui.theme

import androidx.compose.runtime.Composable

/**
 * Pide iconos oscuros o claros en las barras del sistema mientras la pantalla que lo llama está en
 * composición, y deja como estaban al salir.
 *
 * Hace falta porque la app dibuja de borde a borde (`enableEdgeToEdge`) y el color de esos iconos
 * lo decide el modo del sistema, no el fondo de la pantalla: con el móvil en modo oscuro salen
 * blancos, y sobre el fondo crema del estilo Melocotón serían invisibles. Arriba y abajo se eligen
 * por separado porque una pantalla puede tener la cabecera de chocolate (iconos claros) y el fondo
 * crema abajo (iconos oscuros), como el perfil. Solo tiene efecto en Android.
 *
 * @param darkStatusIcons iconos oscuros en la barra de arriba (hora, batería).
 * @param darkNavigationIcons iconos oscuros en la barra de navegación de abajo.
 */
@Composable
expect fun SystemBarsAppearance(darkStatusIcons: Boolean, darkNavigationIcons: Boolean = darkStatusIcons)
