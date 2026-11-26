package com.gymsmart.gymsmart.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Iconos de línea del estilo Melocotón (Tarea 3).
 *
 * Sustituyen a los emoji que usaba la pantalla de inicio (🏋️ 🥗 ⚖️ 📍 🗺️). Es el cambio que más
 * "profesionaliza" una pantalla, más que el color: el emoji se dibuja distinto en cada móvil, no
 * se puede teñir con la paleta y compite con el texto.
 *
 * Los trazos son **exactamente** los de la maqueta aprobada del lienzo de diseño (formato de ruta
 * SVG en una rejilla de 24×24), para que la app y la maqueta no diverjan. Se dibujan con trazo, no
 * con relleno, y se tiñen con `Icon(tint = …)`, así cada pantalla les pone el color de su paleta.
 *
 * Los iconos de los alimentos (Tarea 4) siguen siendo emoji a propósito: ahí sirven para reconocer
 * la comida de un vistazo, que es justo lo que un emoji hace bien.
 */
object LineIcons {
    val Dumbbell: ImageVector by lazy { line("Dumbbell", "M6.5 6.5v11M3.5 9v6M17.5 6.5v11M20.5 9v6M6.5 12h11") }
    val Apple: ImageVector by lazy { line("Apple", "M12 8.5c-1.6-2.2-6.5-2-6.5 3 0 4.6 2.9 9 6.5 9s6.5-4.4 6.5-9c0-5-4.9-5.2-6.5-3zM12 8.5c0-2 .9-3.8 2.8-4.5") }
    val Scale: ImageVector by lazy { line("Scale", "M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zM8 10.5a4 4 0 0 1 8 0M12 10.5l1.6-2") }
    val MapPin: ImageVector by lazy { line("MapPin", "M12 21s-6.5-5.7-6.5-11a6.5 6.5 0 0 1 13 0c0 5.3-6.5 11-6.5 11zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z") }
    val Route: ImageVector by lazy { line("Route", "M6 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM18 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM6 16v-6a4 4 0 0 1 4-4h6M18 8v6a4 4 0 0 1-4 4H8") }
    val Footprints: ImageVector by lazy { line("Footprints", "M8.5 3.5c1.5 0 2.3 1.9 2.3 4.2s-.9 4.8-2.3 4.8-2.3-2.4-2.3-4.8.8-4.2 2.3-4.2zM6.6 15h3.8v1.8a1.9 1.9 0 0 1-3.8 0zM15.5 7.5c1.5 0 2.3 1.9 2.3 4.2s-.9 4.8-2.3 4.8-2.3-2.4-2.3-4.8.8-4.2 2.3-4.2z") }
    val Flame: ImageVector by lazy { line("Flame", "M12 3c.8 3 5 5.2 5 10a5 5 0 0 1-10 0c0-2.1 1-3.6 2.1-4.6.1 1.9 1 3 2.1 3.1-.4-3 .2-5.6.8-8.5z") }
    val Crown: ImageVector by lazy { line("Crown", "M4 18h16M5 15l-1.5-8 5 3.5L12 5l3.5 5.5 5-3.5L19 15z") }
    val ChevronRight: ImageVector by lazy { line("ChevronRight", "M9 6l6 6-6 6", stroke = 2.2f) }
    val ChevronLeft: ImageVector by lazy { line("ChevronLeft", "M15 18l-6-6 6-6", stroke = 2f) }
    val ChevronDown: ImageVector by lazy { line("ChevronDown", "M6 9l6 6 6-6", stroke = 2.2f) }
    val TrendUp: ImageVector by lazy { line("TrendUp", "M3 17l6-6 4 4 8-8M15 7h6v6", stroke = 2f) }
    val Link: ImageVector by lazy { line("Link", "M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7", stroke = 2.2f) }
    val Plus: ImageVector by lazy { line("Plus", "M12 5v14M5 12h14", stroke = 2.4f) }
    val Calendar: ImageVector by lazy { line("Calendar", "M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM4 10h16M8 3v4M16 3v4") }
    /** Triángulo de "empezar", relleno. */
    val Play: ImageVector by lazy {
        ImageVector.Builder("Play", 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes("M8 5.5v13l10.5-6.5z"), fill = SolidColor(Color.Black))
            .build()
    }
    val User: ImageVector by lazy { line("User", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4.5 20a7.5 7.5 0 0 1 15 0", stroke = 1.8f) }
    val Refresh: ImageVector by lazy { line("Refresh", "M20 11a8 8 0 0 0-14.3-4.9L4 8M4 4v4h4M4 13a8 8 0 0 0 14.3 4.9L20 16M20 20v-4h-4", stroke = 1.9f) }
    val Logout: ImageVector by lazy { line("Logout", "M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 16l-4-4 4-4M6 12h10", stroke = 1.9f) }

    private fun line(name: String, pathData: String, stroke: Float = 1.9f): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = null,
            // El color real lo pone el `tint` del Icon; esto solo tiene que ser opaco.
            stroke = SolidColor(Color.Black),
            strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ).build()
}
