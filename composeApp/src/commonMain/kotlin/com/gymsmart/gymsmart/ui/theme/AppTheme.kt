package com.gymsmart.gymsmart.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Fondos

object GymSmartColors {

    val Background        = Color(0xFF0F1117) // Fondo principal
    val SurfaceCard       = Color(0xFF181C25) // Tarjetas
    val SurfaceElevated   = Color(0xFF202534) // Sheets, dialogs, inputs activos

    // Acento principal — Azul premium

    val Primary           = Color(0xFF4DA3FF) // Azul eléctrico principal
    val PrimaryDim        = Color(0xFF2D6FB3) // Azul oscuro pressed/container
    val OnPrimary         = Color(0xFFFFFFFF) // Texto sobre botones

    // Textos

    val TextPrimary       = Color(0xFFF3F5F7) // Blanco suave
    val TextSecondary     = Color(0xFF98A2B3) // Gris azulado
    val TextDisabled      = Color(0xFF525866) // Disabled

    // Bordes y separadores

    val Outline           = Color(0xFF31384A)
    val Divider           = Color(0xFF232938)

    // Estados

    val Success           = Color(0xFF4ADE80)
    val Warning           = Color(0xFFFBBF24)
    val Error             = Color(0xFFFF5C7A)

    // Macronutrientes

    val MacroProtein      = Color(0xFF4ADE80)
    val MacroCarbs        = Color(0xFF60A5FA)
    val MacroFat          = Color(0xFFFB923C)

    // GPS y rutas

    val RoutePending      = Color(0xFF4DA3FF)
    val RouteCompleted    = Color(0xFFFF9F43)
    val RouteProgress     = Color(0xFF72B7FF)

    // Premium

    val PremiumGold       = Color(0xFFFFC857)
    val PremiumGoldDim    = Color(0xFF9A6B11)

    // BottomBar / TopBar

    val NavBar            = Color(0xFF121620)
}

// Material3 Color Scheme

private val DarkColorScheme = darkColorScheme(
    primary            = GymSmartColors.Primary,
    onPrimary          = GymSmartColors.OnPrimary,
    primaryContainer   = GymSmartColors.PrimaryDim,
    onPrimaryContainer = GymSmartColors.TextPrimary,

    secondary          = GymSmartColors.PremiumGold,
    onSecondary        = GymSmartColors.OnPrimary,

    background         = GymSmartColors.Background,
    onBackground       = GymSmartColors.TextPrimary,

    surface            = GymSmartColors.SurfaceCard,
    onSurface          = GymSmartColors.TextPrimary,
    surfaceVariant     = GymSmartColors.SurfaceElevated,
    onSurfaceVariant   = GymSmartColors.TextSecondary,

    outline            = GymSmartColors.Outline,
    outlineVariant     = GymSmartColors.Divider,

    error              = GymSmartColors.Error,
    onError            = GymSmartColors.TextPrimary,
)

// Tipografía

private val GymSmartTypography = Typography(

    // Dashboard / Hero

    displayLarge = TextStyle(
        fontSize       = 36.sp,
        lineHeight     = 42.sp,
        fontWeight     = FontWeight.ExtraBold,
        letterSpacing  = (-0.5).sp,
        color          = GymSmartColors.TextPrimary,
    ),

    // Títulos pantalla

    headlineLarge = TextStyle(
        fontSize       = 28.sp,
        lineHeight     = 34.sp,
        fontWeight     = FontWeight.Bold,
        letterSpacing  = (-0.3).sp,
        color          = GymSmartColors.TextPrimary,
    ),

    headlineMedium = TextStyle(
        fontSize       = 22.sp,
        lineHeight     = 28.sp,
        fontWeight     = FontWeight.SemiBold,
        color          = GymSmartColors.TextPrimary,
    ),

    // Títulos cards

    titleLarge = TextStyle(
        fontSize       = 18.sp,
        lineHeight     = 24.sp,
        fontWeight     = FontWeight.SemiBold,
        color          = GymSmartColors.TextPrimary,
    ),

    titleMedium = TextStyle(
        fontSize       = 16.sp,
        lineHeight     = 22.sp,
        fontWeight     = FontWeight.Medium,
        color          = GymSmartColors.TextPrimary,
    ),

    titleSmall = TextStyle(
        fontSize       = 14.sp,
        lineHeight     = 20.sp,
        fontWeight     = FontWeight.Medium,
        letterSpacing  = 0.1.sp,
        color          = GymSmartColors.TextSecondary,
    ),

    // Body

    bodyLarge = TextStyle(
        fontSize       = 16.sp,
        lineHeight     = 24.sp,
        fontWeight     = FontWeight.Normal,
        color          = GymSmartColors.TextPrimary,
    ),

    bodyMedium = TextStyle(
        fontSize       = 14.sp,
        lineHeight     = 20.sp,
        fontWeight     = FontWeight.Normal,
        color          = GymSmartColors.TextPrimary,
    ),

    bodySmall = TextStyle(
        fontSize       = 12.sp,
        lineHeight     = 16.sp,
        fontWeight     = FontWeight.Normal,
        color          = GymSmartColors.TextSecondary,
    ),

    // Labels / Chips / Buttons

    labelLarge = TextStyle(
        fontSize       = 14.sp,
        lineHeight     = 20.sp,
        fontWeight     = FontWeight.SemiBold,
        letterSpacing  = 0.4.sp,
        color          = GymSmartColors.TextPrimary,
    ),

    labelMedium = TextStyle(
        fontSize       = 12.sp,
        lineHeight     = 16.sp,
        fontWeight     = FontWeight.Medium,
        letterSpacing  = 0.5.sp,
        color          = GymSmartColors.TextSecondary,
    ),

    labelSmall = TextStyle(
        fontSize       = 10.sp,
        lineHeight     = 14.sp,
        fontWeight     = FontWeight.Medium,
        letterSpacing  = 0.6.sp,
        color          = GymSmartColors.TextDisabled,
    ),
)

// Shapes

private val GymSmartShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Theme principal

@Composable
fun GymSmartTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = GymSmartTypography,
        shapes      = GymSmartShapes,
        content     = content,
    )
}

// Espaciados

object GymSmartSpacing {
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 16.dp
    val lg  = 24.dp
    val xl  = 32.dp
    val xxl = 48.dp
}

// Elevaciones

object GymSmartElevation {
    val card    = 0.dp
    val sheet   = 8.dp
    val dialog  = 16.dp
}