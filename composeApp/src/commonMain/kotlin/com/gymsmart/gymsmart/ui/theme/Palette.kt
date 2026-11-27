package com.gymsmart.gymsmart.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import gymsmart.composeapp.generated.resources.Res
import gymsmart.composeapp.generated.resources.dmsans_bold
import gymsmart.composeapp.generated.resources.dmsans_extrabold
import gymsmart.composeapp.generated.resources.dmsans_medium
import gymsmart.composeapp.generated.resources.dmsans_regular
import gymsmart.composeapp.generated.resources.dmsans_semibold
import org.jetbrains.compose.resources.Font

/**
 * El estilo nuevo de la app (Tarea 3): **Melocotón y chocolate**, la dirección "2e / M1" que eligió
 * el usuario entre las maquetas del lienzo de diseño.
 *
 * ## Por qué una paleta y no constantes
 *
 * El tema anterior (`GymSmartColors`, oscuro, borrado el 2026-11-27 cuando ya no lo leía ninguna
 * pantalla) era un `object` de constantes, y unas constantes no pueden cambiar en tiempo de ejecución:
 * con ese diseño un interruptor claro/oscuro era imposible. Esta paleta va por otro camino:
 *
 * - Los colores tienen **nombre de función, no de aspecto** (`hero`, `accentText`, `textSecondary`),
 *   así una versión oscura puede asignar otro color a cada papel sin tocar las pantallas.
 * - Se leen con [Palette.current] a través de un `CompositionLocal`, que es lo que permite cambiar de
 *   paleta en caliente.
 *
 * **Pendiente**: la variante oscura de Melocotón. No está definida a propósito, porque todavía no se
 * ha diseñado ni aprobado; inventarla aquí sería saltarse el paso que el usuario quiso hacer con
 * maquetas.
 */
@Immutable
data class GymSmartPalette(
    /** Fondo de pantalla. */
    val background: Color,
    /** Tarjetas y listas. */
    val surface: Color,
    /** Campos de texto y huecos hundidos dentro de una tarjeta. */
    val surfaceSunken: Color,
    /** Borde de tarjetas y campos. */
    val border: Color,
    /** Separador entre filas de una misma tarjeta. */
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Bloque protagonista (el chocolate). */
    val hero: Color,
    /** Elementos dentro del bloque protagonista: pistas de barra, píldoras. */
    val heroRaised: Color,
    /** Baldosas de datos dentro del bloque protagonista (las cifras de la cabecera del perfil). */
    val heroTile: Color,
    val onHero: Color,
    val onHeroSecondary: Color,
    /** Texto destacado dentro del bloque protagonista. */
    val onHeroAccent: Color,
    /** Relleno de acento (el melocotón): barras de progreso, botón principal claro. */
    val accent: Color,
    val onAccent: Color,
    /**
     * Acento **para texto e iconos** sobre fondo claro. Es más oscuro que [accent] porque el
     * melocotón de relleno no llega a 4,5:1 de contraste como texto pequeño sobre blanco.
     */
    val accentText: Color,
    /** Fondo suave de acento: círculos de icono, avisos. */
    val accentSoft: Color,
    /** Pista de una barra de progreso sobre fondo claro. */
    val track: Color,
    /** Flecha de "entrar" de las filas: presente pero sin competir con el texto. */
    val chevron: Color,
    val danger: Color,
    /** Estado correcto ("conectado", "guardado"). Verde apagado para no romper la paleta cálida. */
    val success: Color,
    val successSoft: Color,
    /**
     * Macronutrientes: un color por macro, oscurecido respecto al tema antiguo para que se lea como
     * texto sobre blanco y crema (los de antes estaban pensados para fondo negro).
     */
    val macroProtein: Color,
    val macroCarbs: Color,
    val macroFat: Color,
    val isDark: Boolean
)

/**
 * Melocotón en modo claro. Los valores son exactamente los de la maqueta aprobada, y cada pareja
 * texto/fondo se comprobó contra WCAG AA (4,5:1 para texto normal) al diseñarla.
 */
val MelocotonClaro = GymSmartPalette(
    background      = Color(0xFFF7EEE6),
    surface         = Color(0xFFFFFFFF),
    surfaceSunken   = Color(0xFFFBF7F3),
    border          = Color(0xFFEADCCF),
    divider         = Color(0xFFF3E9E0),
    textPrimary     = Color(0xFF2B1D17),
    textSecondary   = Color(0xFF6E5D53),
    hero            = Color(0xFF3E2A20),
    heroRaised      = Color(0xFF5A3F31),
    heroTile        = Color(0xFF4C362A),
    onHero          = Color(0xFFFBF4EE),
    onHeroSecondary = Color(0xFFD8C3B4),
    onHeroAccent    = Color(0xFFF8D3B6),
    accent          = Color(0xFFE8955E),
    onAccent        = Color(0xFF3E2A20),
    accentText      = Color(0xFFA85A2A),
    accentSoft      = Color(0xFFF8E0CF),
    track           = Color(0xFFF3E4D6),
    chevron         = Color(0xFFB3A296),
    danger          = Color(0xFFB42318),
    success         = Color(0xFF3F6B2E),
    successSoft     = Color(0xFFE3EEDA),
    macroProtein    = Color(0xFF2F7A47),
    macroCarbs      = Color(0xFF2F64A8),
    macroFat        = Color(0xFFB4561C),
    isDark          = false
)

val LocalPalette = staticCompositionLocalOf { MelocotonClaro }

/** Acceso a la paleta activa: `Palette.current.hero`. */
object Palette {
    val current: GymSmartPalette
        @Composable @ReadOnlyComposable
        get() = LocalPalette.current
}

/** DM Sans, la tipografía de la maqueta aprobada. Va incluida en la app (licencia OFL). */
@Composable
fun dmSansFamily(): FontFamily = FontFamily(
    Font(Res.font.dmsans_regular, FontWeight.Normal),
    Font(Res.font.dmsans_medium, FontWeight.Medium),
    Font(Res.font.dmsans_semibold, FontWeight.SemiBold),
    Font(Res.font.dmsans_bold, FontWeight.Bold),
    Font(Res.font.dmsans_extrabold, FontWeight.ExtraBold)
)

/**
 * Tipografía de Material con DM Sans en todos los estilos y **sin color fijado**.
 *
 * Lo segundo importa tanto como lo primero: la tipografía vieja lleva el gris claro del tema oscuro
 * metido dentro de cada estilo, así que cualquier `Text` sin color explícito saldría casi invisible
 * sobre el fondo crema. Sin color, el texto toma el `onSurface`/`onBackground` del esquema claro.
 */
@Composable
private fun melocotonTypography(): Typography {
    val f = dmSansFamily()
    return with(Typography()) {
        copy(
            displayLarge = displayLarge.copy(fontFamily = f),
            displayMedium = displayMedium.copy(fontFamily = f),
            displaySmall = displaySmall.copy(fontFamily = f),
            headlineLarge = headlineLarge.copy(fontFamily = f),
            headlineMedium = headlineMedium.copy(fontFamily = f),
            headlineSmall = headlineSmall.copy(fontFamily = f),
            titleLarge = titleLarge.copy(fontFamily = f),
            titleMedium = titleMedium.copy(fontFamily = f),
            titleSmall = titleSmall.copy(fontFamily = f),
            bodyLarge = bodyLarge.copy(fontFamily = f),
            bodyMedium = bodyMedium.copy(fontFamily = f),
            bodySmall = bodySmall.copy(fontFamily = f),
            labelLarge = labelLarge.copy(fontFamily = f),
            labelMedium = labelMedium.copy(fontFamily = f),
            labelSmall = labelSmall.copy(fontFamily = f)
        )
    }
}

/**
 * Envuelve una pantalla ya migrada al estilo Melocotón. Fija la paleta, un esquema de color claro
 * coherente con ella (para que menús, diálogos y ondas al pulsar no salgan con colores del tema
 * oscuro) y la tipografía DM Sans. Las formas se heredan del tema general.
 */
@Composable
fun MelocotonTheme(content: @Composable () -> Unit) {
    val p = MelocotonClaro
    val scheme = lightColorScheme(
        primary = p.hero,
        onPrimary = p.onHero,
        primaryContainer = p.accentSoft,
        onPrimaryContainer = p.textPrimary,
        secondary = p.accentText,
        onSecondary = p.surface,
        background = p.background,
        onBackground = p.textPrimary,
        surface = p.surface,
        onSurface = p.textPrimary,
        surfaceVariant = p.surfaceSunken,
        onSurfaceVariant = p.textSecondary,
        surfaceContainer = p.surface,
        outline = p.border,
        outlineVariant = p.divider,
        error = p.danger,
        onError = p.surface
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = melocotonTypography(), content = content)
    }
}
