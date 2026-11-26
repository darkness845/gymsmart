package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.nutrition.ActivityBenchmarks
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.services.HealthDataProvider
import com.gymsmart.gymsmart.services.WEARABLE_POLL_INTERVAL_MS
import com.gymsmart.gymsmart.ui.formatThousands
import com.gymsmart.gymsmart.ui.icons.LineIcons
import com.gymsmart.gymsmart.ui.theme.MelocotonTheme
import com.gymsmart.gymsmart.ui.theme.Palette
import com.gymsmart.gymsmart.ui.theme.SystemBarsAppearance
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Pantalla de inicio, en el estilo Melocotón (Tarea 3, maqueta "M1 · 2e" aprobada por el usuario).
 *
 * Es la primera pantalla migrada a [MelocotonTheme]: no lee nada de `GymSmartColors`, solo
 * [Palette.current]. Estructura, de arriba abajo: cabecera, saludo, bloque de chocolate con las
 * cifras del día, secciones en lista y Planes.
 *
 * Diferencia deliberada con la maqueta: la maqueta pintaba "78 % de 10.000 pasos", pero la app no
 * tiene ningún objetivo de pasos configurado y 10.000 sería una cifra inventada. La barra avanza
 * hacia el **siguiente nivel del mismo baremo que usa el perfil** (Tudor-Locke, tarea 2), que con
 * 7.842 pasos es justo 10.000, así que en el caso de la maqueta se ve idéntico.
 */
@Composable
fun DashboardScreen(
    navController: NavController,
    healthDataProvider: HealthDataProvider,
    authService: AuthService
) {
    val scope = rememberCoroutineScope()

    var todaySteps    by remember { mutableStateOf<Long?>(null) }
    var todayCalories by remember { mutableStateOf<Double?>(null) }
    var menuExpanded  by remember { mutableStateOf(false) }
    var wearableRefreshing by remember { mutableStateOf(false) }

    // WEAR-3 (doc/08-backlog-mejoras.md): antes esto leía una sola vez al entrar y se quedaba
    // congelado el resto del día si el usuario no volvía a abrir el inicio. Se refresca sola cada
    // WEARABLE_POLL_INTERVAL_MS mientras la pantalla está visible.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        while (true) {
            runCatching { healthDataProvider.getTodaySteps() }
                .onSuccess { todaySteps = it }
                .onFailure { println("Pasos: ${it.message}") }
            runCatching { healthDataProvider.getTodayActiveCalories() }
                .onSuccess { todayCalories = it }
                .onFailure { println("Calorías: ${it.message}") }
            kotlinx.coroutines.delay(WEARABLE_POLL_INTERVAL_MS)
        }
    }

    fun refreshWearable() {
        // Fuerza una relectura inmediata de Health Connect tras sincronizar la pulsera a mano, sin
        // esperar al ciclo automático. Igual que el botón de Nutrición.
        scope.launch {
            wearableRefreshing = true
            runCatching { healthDataProvider.getTodaySteps() }.onSuccess { todaySteps = it }
            runCatching { healthDataProvider.getTodayActiveCalories() }.onSuccess { todayCalories = it }
            wearableRefreshing = false
        }
    }

    val now = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }

    MelocotonTheme {
        SystemBarsAppearance(darkStatusIcons = true)
        val p = Palette.current

        Scaffold(containerColor = p.background) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Cabecera ────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "GymSmart",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp,
                        color = p.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    CircleButton(
                        onClick = ::refreshWearable,
                        enabled = !wearableRefreshing,
                        contentDescription = "Actualizar pasos y calorías"
                    ) {
                        if (wearableRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = p.textPrimary
                            )
                        } else {
                            Icon(LineIcons.Refresh, null, tint = p.textPrimary, modifier = Modifier.size(19.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        CircleButton(
                            onClick = { menuExpanded = true },
                            contentDescription = "Menú de cuenta"
                        ) {
                            Icon(LineIcons.User, null, tint = p.textPrimary, modifier = Modifier.size(19.dp))
                        }
                        AccountMenu(
                            expanded = menuExpanded,
                            onDismiss = { menuExpanded = false },
                            onProfile = {
                                menuExpanded = false
                                navController.navigate(Screen.Profile.route)
                            },
                            onLogout = {
                                menuExpanded = false
                                scope.launch {
                                    authService.logout()
                                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                                }
                            }
                        )
                    }
                }

                // ── Saludo ──────────────────────────────────────────────────
                Text(
                    greetingFor(now.hour),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp,
                    lineHeight = 33.sp,
                    color = p.textPrimary
                )

                // ── El día, en el bloque de chocolate ───────────────────────
                TodayHero(date = now, steps = todaySteps, activeKcal = todayCalories)

                // ── Secciones ───────────────────────────────────────────────
                SectionList(
                    rows = listOf(
                        SectionRow("Entreno", LineIcons.Dumbbell) { navController.navigate(Screen.Workout.route) },
                        SectionRow("Nutrición", LineIcons.Apple) { navController.navigate(Screen.Nutrition.route) },
                        SectionRow("Peso", LineIcons.Scale) { navController.navigate(Screen.Weight.route) },
                        SectionRow("GPS", LineIcons.MapPin) { navController.navigate(Screen.Gps.route) },
                        SectionRow("Mis rutas", LineIcons.Route) { navController.navigate(Screen.MyRoutes.route) }
                    )
                )

                // ── Planes ──────────────────────────────────────────────────
                PlansRow(onClick = { navController.navigate(Screen.Subscription.route) })
            }
        }
    }
}

// ── Bloque del día ──────────────────────────────────────────────────────────────

@Composable
private fun TodayHero(date: LocalDateTime, steps: Long?, activeKcal: Double?) {
    val p = Palette.current
    val level = steps?.let { ActivityBenchmarks.levelForSteps(it) }
    val next = steps?.let { ActivityBenchmarks.nextLevelThreshold(it) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(p.hero)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(longDate(date), fontSize = 13.sp, color = p.onHeroSecondary, modifier = Modifier.weight(1f))
            // Es el nivel que corresponde a los pasos de HOY, no el del perfil: un día suelto no
            // es tu nivel de actividad. De ahí que solo aparezca si hay dato.
            level?.let { ActivityBenchmarks.labelFor(it) }?.let { label ->
                Pill(label, background = p.heroRaised, color = p.onHeroAccent)
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HeroStat(
                icon = LineIcons.Footprints,
                label = "Pasos",
                value = steps?.let(::formatThousands) ?: "—",
                modifier = Modifier.weight(1f)
            )
            HeroStat(
                icon = LineIcons.Flame,
                label = "Kcal activas",
                value = activeKcal?.let { formatThousands(it.toLong()) } ?: "—",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(18.dp))
        val progress = when {
            steps == null -> 0f
            next == null -> 1f
            else -> (steps.toFloat() / next).coerceIn(0f, 1f)
        }
        ProgressTrack(progress = progress, track = p.heroRaised, fill = p.accent)

        Spacer(Modifier.height(7.dp))
        Text(
            when {
                steps == null -> "Sin datos de la pulsera todavía"
                next == null -> "Nivel máximo de actividad hoy"
                else -> {
                    val nextLabel = ActivityBenchmarks.labelFor(ActivityBenchmarks.levelForSteps(next)) ?: ""
                    "${(progress * 100).toInt()} % · faltan ${formatThousands(next - steps)} pasos para $nextLabel"
                }
            },
            fontSize = 12.sp,
            color = p.onHeroSecondary
        )
    }
}

@Composable
private fun HeroStat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val p = Palette.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = p.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, color = p.onHeroSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.2).sp,
            lineHeight = 38.sp,
            color = p.onHero
        )
    }
}

// ── Secciones ───────────────────────────────────────────────────────────────────

private class SectionRow(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun SectionList(rows: List<SectionRow>) {
    val p = Palette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(22.dp))
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = p.divider, thickness = 1.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = row.onClick)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(p.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(row.icon, null, tint = p.accentText, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(13.dp))
                Text(
                    row.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = p.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(LineIcons.ChevronRight, null, tint = p.chevron, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PlansRow(onClick: () -> Unit) {
    val p = Palette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(LineIcons.Crown, null, tint = p.accentText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text("Planes", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.textPrimary, modifier = Modifier.weight(1f))
        Text("Free", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.textSecondary)
        Spacer(Modifier.width(8.dp))
        Pill("Premium", background = p.accentText, color = p.surface, bold = true)
    }
}

// ── Menú de cuenta ──────────────────────────────────────────────────────────────

@Composable
private fun AccountMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val p = Palette.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(p.hero),
                contentAlignment = Alignment.Center
            ) {
                Text("G", fontWeight = FontWeight.ExtraBold, color = p.onHeroAccent)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("GymSmart", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
                Text("Tu cuenta", fontSize = 12.sp, color = p.textSecondary)
            }
        }
        HorizontalDivider(color = p.divider)
        DropdownMenuItem(
            text = { Text("Mi perfil", fontSize = 14.sp, color = p.textPrimary) },
            leadingIcon = { Icon(LineIcons.User, null, tint = p.accentText, modifier = Modifier.size(18.dp)) },
            onClick = onProfile
        )
        DropdownMenuItem(
            text = { Text("Cerrar sesión", fontSize = 14.sp, color = p.danger) },
            leadingIcon = { Icon(LineIcons.Logout, null, tint = p.danger, modifier = Modifier.size(18.dp)) },
            onClick = onLogout
        )
    }
}

// ── Piezas pequeñas ─────────────────────────────────────────────────────────────

/** Botón redondo de 48 dp: el mínimo de toque que recomienda Android, aunque se vea compacto. */
@Composable
private fun CircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val p = Palette.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = p.surface,
        border = BorderStroke(1.dp, p.border),
        // El icono de dentro va sin descripción (null): la lleva el botón entero, que es lo que
        // el lector de pantalla enfoca.
        modifier = Modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun Pill(text: String, background: Color, color: Color, bold: Boolean = false) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun ProgressTrack(progress: Float, track: Color, fill: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .clip(RoundedCornerShape(50))
                .background(fill)
        )
    }
}

// ── Texto ───────────────────────────────────────────────────────────────────────

private fun greetingFor(hour: Int): String = when (hour) {
    in 6..13 -> "Buenos días"
    in 14..20 -> "Buenas tardes"
    else -> "Buenas noches"
}

private val DIAS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
)

/** "Jueves, 26 de noviembre". */
private fun longDate(d: LocalDateTime): String =
    "${DIAS[d.dayOfWeek.ordinal]}, ${d.day} de ${MESES[d.month.ordinal]}"

