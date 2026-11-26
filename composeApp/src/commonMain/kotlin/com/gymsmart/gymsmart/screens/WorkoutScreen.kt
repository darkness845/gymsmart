package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.model.*
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.services.WorkoutService
import com.gymsmart.gymsmart.ui.icons.LineIcons
import com.gymsmart.gymsmart.ui.theme.MelocotonTheme
import com.gymsmart.gymsmart.ui.theme.Palette
import com.gymsmart.gymsmart.ui.theme.SystemBarsAppearance
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Entrenamiento, en el estilo Melocotón y con el flujo aprobado en el simulador (Tarea 7):
 *
 *  1. [DaysView] — tus días.
 *  2. [DayView]  — un día: sus ejercicios (↑ ↓ ✕, tocar para editar), empezar y añadir.
 *  3. [WorkoutLogScreen] — apuntar series.
 *  4. [DoneView] — entreno guardado, con lo que se actualizó también en los días unidos.
 *
 * Los cambios del día se guardan **al momento** (no hay borrador ni botón de guardar día): es lo que
 * el usuario probó y aprobó en el simulador.
 *
 * Van como estado interno y no como rutas porque comparten el mismo plan en memoria y el ida y vuelta
 * es constante; pasar el plan por argumentos de ruta obligaría a serializarlo en cada salto.
 */

private enum class WorkoutView { DAYS, DAY, LOG, DONE }
private enum class WorkoutSheet { NONE, NEW_DAY, ADD, EDIT_EXERCISE, EDIT_DAY }

private val DAY_SUGGESTIONS = listOf(
    "Pecho · Hombro · Tríceps", "Espalda · Bíceps", "Pierna", "Tríceps · Hombro · Bíceps",
    "Torso", "Empuje", "Tirón", "Cuerpo completo"
)

private fun newId(): String =
    Clock.System.now().toEpochMilliseconds().toString(36) +
        (0..999999).random().toString(36)

/** Lo que se enseña al terminar un entreno. */
private class FinishSummary(
    val total: Int,
    val dayName: String,
    val duration: String,
    /** Ejercicio → días unidos donde también se actualizó su "última vez". */
    val shared: List<Pair<String, String>>
)

@Composable
fun WorkoutScreen(navController: NavController, workoutService: WorkoutService) {
    MelocotonTheme {
        SystemBarsAppearance(darkStatusIcons = true)
        WorkoutScreenContent(navController, workoutService)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutScreenContent(navController: NavController, workoutService: WorkoutService) {
    val p = Palette.current
    val scope = rememberCoroutineScope()

    var plan by remember { mutableStateOf(WorkoutPlan()) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(WorkoutView.DAYS) }
    var currentDayId by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(WorkoutSheet.NONE) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var done by remember { mutableStateOf<FinishSummary?>(null) }

    val snackbar = remember { SnackbarHostState() }

    // Guardar es "lo que hay en pantalla manda": se manda el plan entero y, si falla la red, se avisa
    // sin perder nada — el estado sigue en memoria y el siguiente guardado lo reintenta completo.
    fun persist(next: WorkoutPlan) {
        plan = next
        scope.launch {
            workoutService.savePlan(next).onFailure { saveFailed = true }.onSuccess { saveFailed = false }
        }
    }

    fun updateDay(dayId: String, f: (WorkoutDay) -> WorkoutDay) =
        persist(plan.copy(days = plan.days.map { if (it.id == dayId) f(it) else it }))

    /** Cambia algo del ejercicio en TODOS los días donde está unido (tipo, barra, salto, opciones). */
    fun patchGroup(groupId: String, f: (WorkoutExercise) -> WorkoutExercise) =
        persist(plan.copy(days = plan.days.map { d ->
            d.copy(exercises = d.exercises.map { if (it.groupId == groupId) f(it) else it })
        }))

    fun linkedDayNames(ex: WorkoutExercise, exceptDayId: String): List<String> =
        plan.days.filter { d -> d.id != exceptDayId && d.exercises.any { it.groupId == ex.groupId } }.map { it.name }

    suspend fun load() {
        workoutService.getPlan()
            .onSuccess { plan = it; loadFailed = false }
            .onFailure { loadFailed = true }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    val session = plan.session
    val liveDay = session?.let { s -> plan.days.firstOrNull { it.id == s.dayId } }
    val currentDay = plan.days.firstOrNull { it.id == currentDayId }

    // Si hay un entrenamiento a medias, se entra directamente a él al abrir la pantalla.
    LaunchedEffect(loading) {
        if (!loading && liveDay != null) { currentDayId = liveDay.id; view = WorkoutView.LOG }
    }

    Scaffold(
        containerColor = p.background,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(snackbarData = data, containerColor = p.hero, contentColor = p.onHero)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = p.accentText)
                }

                loadFailed -> LoadError(
                    onRetry = { loading = true; scope.launch { load() } },
                    onBack = { navController.popBackStack() }
                )

                view == WorkoutView.LOG && liveDay != null -> WorkoutLogScreen(
                    day = liveDay,
                    session = session,
                    saveFailed = saveFailed,
                    linkedDays = { ex -> linkedDayNames(ex, liveDay.id) },
                    onBack = { currentDayId = liveDay.id; view = WorkoutView.DAY },
                    onSessionChange = { updated -> persist(plan.copy(session = updated)) },
                    onPatchGroup = { groupId, f -> patchGroup(groupId, f) },
                    onFinish = { finished ->
                        // Lo de hoy pasa a ser "la última vez" del ejercicio en TODOS los días donde
                        // está unido: de ahí sale el peso que la app propondrá sola la próxima vez.
                        var days = plan.days
                        val shared = mutableListOf<Pair<String, String>>()
                        liveDay.exercises.forEach { ex ->
                            val hechas = finished.sets[ex.id].orEmpty()
                            if (hechas.isEmpty()) return@forEach
                            val others = linkedDayNames(ex, liveDay.id)
                            if (others.isNotEmpty()) shared += ex.name to others.joinToString(", ")
                            days = days.map { d ->
                                d.copy(exercises = d.exercises.map { e ->
                                    if (e.groupId == ex.groupId) e.copy(lastSession = hechas, lastFrom = liveDay.name) else e
                                })
                            }
                        }
                        persist(WorkoutPlan(days = days, session = null))

                        // Y queda guardado como entrenamiento hecho (Tarea E), con una copia del nombre
                        // y el tipo de cada ejercicio, para que editar el plan no reescriba el pasado.
                        val hechos = liveDay.exercises.mapNotNull { ex ->
                            val series = finished.sets[ex.id].orEmpty()
                            if (series.isEmpty()) null else SessionExercise(ex.name, ex.kind, ex.barKg, series)
                        }
                        if (hechos.isNotEmpty()) {
                            val hoy = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                            scope.launch {
                                workoutService.saveSession(
                                    WorkoutSession(date = hoy, dayName = liveDay.name, startedAt = finished.startedAt, exercises = hechos)
                                ).onFailure { saveFailed = true }
                            }
                        }

                        val elapsed = ((Clock.System.now().toEpochMilliseconds() - finished.startedAt) / 1000).coerceAtLeast(0)
                        done = FinishSummary(
                            total = finished.sets.values.sumOf { it.size },
                            dayName = liveDay.name,
                            duration = workoutDuration(elapsed),
                            shared = shared
                        )
                        view = WorkoutView.DONE
                    }
                )

                view == WorkoutView.DONE && done != null -> DoneView(
                    summary = done!!,
                    onBack = { done = null; view = WorkoutView.DAYS }
                )

                view == WorkoutView.DAY && currentDay != null -> DayView(
                    day = currentDay,
                    isLive = session?.dayId == currentDay.id,
                    linkedDays = { ex -> linkedDayNames(ex, currentDay.id) },
                    saveFailed = saveFailed,
                    onBack = { view = WorkoutView.DAYS },
                    onEditDay = { sheet = WorkoutSheet.EDIT_DAY },
                    onOpenExercise = { i -> editIndex = i; sheet = WorkoutSheet.EDIT_EXERCISE },
                    onMove = { from, to ->
                        updateDay(currentDay.id) { d ->
                            d.copy(exercises = d.exercises.toMutableList().also { val t = it[from]; it[from] = it[to]; it[to] = t })
                        }
                    },
                    // Quitar del día solo lo quita de ESTE día; en los días donde esté unido sigue igual.
                    onRemove = { i -> updateDay(currentDay.id) { d -> d.copy(exercises = d.exercises.filterIndexed { j, _ -> j != i }) } },
                    onStart = {
                        if (session == null || session.dayId != currentDay.id) {
                            persist(plan.copy(session = ActiveSession(dayId = currentDay.id, startedAt = Clock.System.now().toEpochMilliseconds())))
                        }
                        view = WorkoutView.LOG
                    },
                    onAdd = { sheet = WorkoutSheet.ADD }
                )

                else -> DaysView(
                    days = plan.days,
                    liveDayId = session?.dayId,
                    liveSets = session?.sets?.values?.sumOf { it.size } ?: 0,
                    saveFailed = saveFailed,
                    onBack = { navController.popBackStack() },
                    onOpen = { day -> currentDayId = day.id; view = WorkoutView.DAY },
                    onNew = { sheet = WorkoutSheet.NEW_DAY },
                    onProgress = { navController.navigate(Screen.Progress.route) }
                )
            }
        }
    }

    // ── Hojas ────────────────────────────────────────────────────────────────
    when (sheet) {
        WorkoutSheet.NEW_DAY -> NewDaySheet(
            onDismiss = { sheet = WorkoutSheet.NONE },
            onCreate = { name ->
                val day = WorkoutDay(id = newId(), name = name, exercises = emptyList())
                persist(plan.copy(days = plan.days + day))
                currentDayId = day.id
                view = WorkoutView.DAY
                sheet = WorkoutSheet.NONE
            }
        )

        WorkoutSheet.ADD -> currentDay?.let { day ->
            val here = day.exercises.map { it.groupId }.toSet()
            val seen = mutableSetOf<String>()
            val available = plan.days.filter { it.id != day.id }.flatMap { d ->
                d.exercises.filter { it.groupId !in here && seen.add(it.groupId) }.map { it to d.name }
            }
            AddExerciseSheet(
                dayName = day.name,
                available = available,
                onDismiss = { sheet = WorkoutSheet.NONE },
                // Unir: el nuevo hereda TODO del otro (tipo, barra, salto, opciones y última sesión) y
                // se queda con su grupo. El nombre de partida es el mismo, pero luego es de cada día.
                onLink = { src ->
                    updateDay(day.id) { d -> d.copy(exercises = d.exercises + src.copy(id = newId(), linkId = src.groupId)) }
                    sheet = WorkoutSheet.NONE
                },
                onAddNew = { name, kindId, uni, ps ->
                    val k = WorkoutKind.of(kindId)
                    val ex = WorkoutExercise(
                        id = newId(), name = name, kind = kindId,
                        barKg = if (k.usesBar) 20.0 else 0.0, stepKg = k.defaultStep,
                        unilateral = uni, perSide = ps
                    )
                    updateDay(day.id) { d -> d.copy(exercises = d.exercises + ex) }
                    sheet = WorkoutSheet.NONE
                }
            )
        }

        WorkoutSheet.EDIT_EXERCISE -> {
            val day = currentDay
            val ex = editIndex?.let { day?.exercises?.getOrNull(it) }
            if (day == null || ex == null) {
                sheet = WorkoutSheet.NONE
            } else {
                EditExerciseSheet(
                    exercise = ex,
                    dayName = day.name,
                    linkedDays = linkedDayNames(ex, day.id),
                    onDismiss = { sheet = WorkoutSheet.NONE },
                    onSave = { name, kindId, uni, ps ->
                        // El nombre es una etiqueta de ESTE día; el tipo y las opciones son del
                        // ejercicio, en todos sus días.
                        persist(plan.copy(days = plan.days.map { d ->
                            d.copy(exercises = d.exercises.map { e ->
                                var y = if (e.groupId == ex.groupId) e.copy(
                                    kind = kindId, unilateral = uni, perSide = ps,
                                    barKg = if (WorkoutKind.of(kindId).usesBar && e.barKg <= 0) 20.0 else e.barKg
                                ) else e
                                if (d.id == day.id && e.id == ex.id) y = y.copy(name = name)
                                y
                            })
                        }))
                        sheet = WorkoutSheet.NONE
                    }
                )
            }
        }

        WorkoutSheet.EDIT_DAY -> currentDay?.let { day ->
            EditDaySheet(
                day = day,
                onDismiss = { sheet = WorkoutSheet.NONE },
                onRename = { name -> updateDay(day.id) { it.copy(name = name) }; sheet = WorkoutSheet.NONE },
                onDelete = {
                    // Si se borra el día que se está entrenando, el entreno a medias se va con él.
                    persist(plan.copy(
                        days = plan.days.filterNot { it.id == day.id },
                        session = plan.session?.takeIf { it.dayId != day.id }
                    ))
                    sheet = WorkoutSheet.NONE
                    view = WorkoutView.DAYS
                    scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar("Día eliminado") }
                }
            )
        }

        WorkoutSheet.NONE -> Unit
    }
}

internal fun seriesWord(n: Int) = if (n == 1) "serie" else "series"
private fun exercisesWord(n: Int) = if (n == 1) "ejercicio" else "ejercicios"

private fun workoutDuration(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

// ── Pantalla: tus días ───────────────────────────────────────────────────────

@Composable
private fun DaysView(
    days: List<WorkoutDay>,
    liveDayId: String?,
    liveSets: Int,
    saveFailed: Boolean,
    onBack: () -> Unit,
    onOpen: (WorkoutDay) -> Unit,
    onNew: () -> Unit,
    onProgress: () -> Unit
) {
    val p = Palette.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (saveFailed) OfflineNotice()
        Column(
            Modifier.workoutPage().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleBack(onBack)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Entreno", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, color = p.textPrimary)
                    Text("Tus días de entreno", fontSize = 14.sp, color = p.textSecondary)
                }
            }

            // Acceso al histórico (Tarea E): el progreso se mira aunque hoy no toque entrenar.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface)
                    .border(1.dp, p.border, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClick = onProgress)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(LineIcons.Calendar, null, tint = p.accentText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Ver mi progreso", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.textPrimary, modifier = Modifier.weight(1f))
                Icon(LineIcons.ChevronRight, null, tint = p.chevron, modifier = Modifier.size(16.dp))
            }

            days.forEach { day ->
                val live = day.id == liveDayId
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface)
                        .border(if (live) 1.5.dp else 1.dp, if (live) p.accent else p.border, RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) { onOpen(day) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.hero), contentAlignment = Alignment.Center) {
                        Icon(LineIcons.Dumbbell, null, tint = p.onHeroAccent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(day.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
                        Text(
                            when {
                                live -> "Entrenando · $liveSets ${seriesWord(liveSets)}"
                                day.exercises.isEmpty() -> "Sin ejercicios"
                                else -> "${day.exercises.size} ${exercisesWord(day.exercises.size)}"
                            },
                            fontSize = 12.sp,
                            fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
                            color = if (live) p.accentText else p.textSecondary
                        )
                    }
                    Icon(LineIcons.ChevronRight, null, tint = p.chevron, modifier = Modifier.size(16.dp))
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, Color(0xFFCDBBAD), RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button, onClick = onNew)
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(LineIcons.Plus, null, tint = p.accentText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Crear día", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.accentText)
            }
        }
    }
}

// ── Pantalla: un día ─────────────────────────────────────────────────────────

@Composable
private fun DayView(
    day: WorkoutDay,
    isLive: Boolean,
    linkedDays: (WorkoutExercise) -> List<String>,
    saveFailed: Boolean,
    onBack: () -> Unit,
    onEditDay: () -> Unit,
    onOpenExercise: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onStart: () -> Unit,
    onAdd: () -> Unit
) {
    val p = Palette.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (saveFailed) OfflineNotice()
        Column(
            Modifier.workoutPage().weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleBack(onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(day.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, color = p.textPrimary)
                    Text(
                        if (day.exercises.isEmpty()) "Sin ejercicios todavía" else "${day.exercises.size} ${exercisesWord(day.exercises.size)}",
                        fontSize = 13.sp, color = p.textSecondary
                    )
                }
                // Renombrar y borrar el día: no estaba en el simulador, pero la app ya lo hacía.
                Text(
                    "Editar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.accentText,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onEditDay)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                )
            }

            if (day.exercises.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface)
                        .border(1.dp, Color(0xFFCDBBAD), RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Día vacío", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Añade ejercicios. Los que ya haces en otro día puedes unirlos para que recuerden tu peso.",
                        fontSize = 13.sp, lineHeight = 19.sp, color = p.textSecondary, textAlign = TextAlign.Center
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                day.exercises.forEachIndexed { i, ex ->
                    val others = linkedDays(ex)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface)
                            .border(1.dp, p.border, RoundedCornerShape(18.dp))
                            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IndexBadge(i + 1)
                        Spacer(Modifier.width(8.dp))
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button) { onOpenExercise(i) }.padding(vertical = 2.dp)
                        ) {
                            Text(ex.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
                            Text(
                                (listOf(WorkoutKind.of(ex.kind).label) +
                                    (if (ex.unilateral) listOf("unilateral") else emptyList()) +
                                    (if (ex.defaultPerSide) listOf("a cada lado") else emptyList())
                                ).joinToString(" · ") + " · toca para editar",
                                fontSize = 12.sp, color = p.textSecondary
                            )
                            if (others.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                LinkBadge("Unido con " + others.joinToString(", "))
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        SquareIconButton("↑", "Subir", enabled = i > 0) { onMove(i, i - 1) }
                        Spacer(Modifier.width(6.dp))
                        SquareIconButton("↓", "Bajar", enabled = i < day.exercises.lastIndex) { onMove(i, i + 1) }
                        Spacer(Modifier.width(6.dp))
                        SquareIconButton("✕", "Quitar del día", danger = true) { onRemove(i) }
                    }
                }
            }
        }

        Column(
            Modifier.workoutPage().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (day.exercises.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.accent)
                        .clickable(role = Role.Button, onClick = onStart).padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(LineIcons.Play, null, tint = p.onAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isLive) "Continuar entreno" else "Empezar entreno", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = p.onAccent)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.hero)
                    .clickable(role = Role.Button, onClick = onAdd).padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(LineIcons.Plus, null, tint = p.onHero, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Añadir ejercicio", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.onHero)
            }
        }
    }
}

// ── Pantalla: entreno guardado ───────────────────────────────────────────────

@Composable
private fun DoneView(summary: FinishSummary, onBack: () -> Unit) {
    val p = Palette.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.workoutPage().weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(p.hero).padding(horizontal = 20.dp, vertical = 22.dp)) {
                Text("ENTRENO GUARDADO", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = p.onHeroAccent)
                Spacer(Modifier.height(6.dp))
                Text("${summary.total} ${seriesWord(summary.total)}", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, color = p.onHero)
                Text("${summary.dayName} · ${summary.duration}", fontSize = 13.sp, color = p.onHeroSecondary)
            }
            if (summary.shared.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface)
                        .border(1.dp, p.border, RoundedCornerShape(20.dp)).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LineIcons.Link, null, tint = p.accentText, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("También actualizado en otros días", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
                    }
                    Spacer(Modifier.height(10.dp))
                    summary.shared.forEach { (name, days) ->
                        Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = p.textPrimary)); append(name); pop()
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = p.textSecondary)); append(" en $days"); pop()
                            },
                            fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("La próxima vez que entrenes cualquiera de esos días, te propondrá estos pesos.", fontSize = 12.sp, lineHeight = 17.sp, color = p.textSecondary)
                }
            }
        }
        PrimaryButton("Volver a mis días", onBack, Modifier.workoutPage().padding(20.dp))
    }
}

@Composable
private fun LoadError(onRetry: () -> Unit, onBack: () -> Unit) {
    val p = Palette.current
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No se pudieron cargar tus entrenamientos", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.textPrimary, textAlign = TextAlign.Center)
            Text("Revisa la conexión e inténtalo de nuevo.", fontSize = 13.sp, color = p.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            PrimaryButton("Reintentar", onRetry, Modifier.fillMaxWidth())
            TextButton(onClick = onBack) { Text("Volver", color = p.textSecondary) }
        }
    }
}

// ── Hoja: nuevo día ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewDaySheet(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val p = Palette.current
    var name by remember { mutableStateOf("") }
    WorkoutSheetFrame(onDismiss) {
        Text("Nuevo día", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = p.textPrimary)
        FieldLabel("Nombre del día")
        WorkoutField(name, { name = it.take(60) }, "Por ejemplo: Tríceps · Hombro · Bíceps")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DAY_SUGGESTIONS.forEach { s ->
                Text(
                    s, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.textSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(p.surfaceSunken)
                        .border(1.dp, p.border, RoundedCornerShape(50)).clickable { name = s }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            SecondaryButton("Cancelar", onDismiss, Modifier.weight(1f))
            PrimaryButton("Crear día", { if (name.isNotBlank()) onCreate(name.trim()) }, Modifier.weight(1.4f), enabled = name.isNotBlank())
        }
    }
}

// ── Hoja: añadir ejercicio ───────────────────────────────────────────────────

/**
 * Añadir ejercicio. La hoja NO se desplaza: solo la lista de "ya lo haces en otro día" tiene scroll
 * propio, así el nombre, el tipo, las opciones y el botón están siempre a la vista.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExerciseSheet(
    dayName: String,
    available: List<Pair<WorkoutExercise, String>>,
    onDismiss: () -> Unit,
    onLink: (WorkoutExercise) -> Unit,
    onAddNew: (name: String, kind: String, unilateral: Boolean, perSide: Boolean) -> Unit
) {
    val p = Palette.current
    var typed by remember { mutableStateOf("") }
    var kindId by remember { mutableStateOf(WorkoutKind.MAQUINA) }
    var uni by remember { mutableStateOf(false) }
    var ps by remember { mutableStateOf(false) }
    var dismissedFor by remember { mutableStateOf("") }

    val typedN = normalizeExerciseName(typed)
    // Misma regla que se acordó: sin mayúsculas, sin tildes y sin espacios de más. Nada de sinónimos.
    val suggestion = if (typedN.isNotEmpty() && typedN != dismissedFor)
        available.firstOrNull { normalizeExerciseName(it.first.name) == typedN } else null

    WorkoutSheetFrame(onDismiss, scrollable = false) {
        Column {
            Text("Añadir ejercicio", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = p.textPrimary)
            Text("a $dayName", fontSize = 13.sp, color = p.textSecondary)
        }

        if (available.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MiniTitle("Ya lo haces en otro día", Modifier.weight(1f))
                Text("${available.size} ${exercisesWord(available.size)}", fontSize = 11.sp, color = p.textSecondary)
            }
            Column(
                Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 262.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                available.forEach { (ex, fromDay) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surfaceSunken)
                            .border(1.dp, p.border, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { onLink(ex) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(LineIcons.Link, null, tint = p.accentText, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ex.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
                            Text("$fromDay · ${WorkoutKind.of(ex.kind).label}", fontSize = 11.sp, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Unir", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.onHero,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(p.hero).padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = p.divider)
                Text("  o uno nuevo  ", fontSize = 12.sp, color = p.textSecondary)
                HorizontalDivider(Modifier.weight(1f), color = p.divider)
            }
        }

        FieldLabel("Nombre")
        WorkoutField(typed, { typed = it.take(60) }, "Escribe el ejercicio")

        suggestion?.let { (ex, fromDay) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.accentSoft).padding(horizontal = 13.dp, vertical = 11.dp)) {
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append("Ya tienes ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)); append("«${ex.name}»"); pop()
                        append(" en $fromDay. ¿Es el mismo ejercicio?")
                    },
                    fontSize = 13.sp, lineHeight = 19.sp, color = p.hero
                )
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Sí, unirlos", { onLink(ex) }, Modifier.weight(1.2f), compact = true)
                    SecondaryButton("No, es otro", { dismissedFor = typedN }, Modifier.weight(1f), compact = true)
                }
            }
        }

        MiniTitle("Cómo lo haces")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            WorkoutKind.ALL.forEach { k -> ChoiceChip(k.label, k.id == kindId) { kindId = k.id } }
        }
        MiniTitle("Opciones")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ChoiceChip("Unilateral", uni) { uni = !uni }
            ChoiceChip("A cada lado", ps) { ps = !ps }
        }

        PrimaryButton(
            "Añadir como nuevo",
            { if (typed.isNotBlank()) onAddNew(typed.trim(), kindId, uni, ps) },
            Modifier.fillMaxWidth(),
            enabled = typed.isNotBlank()
        )
    }
}

// ── Hoja: editar ejercicio ───────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditExerciseSheet(
    exercise: WorkoutExercise,
    dayName: String,
    linkedDays: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, kind: String, unilateral: Boolean, perSide: Boolean) -> Unit
) {
    val p = Palette.current
    var name by remember(exercise.id) { mutableStateOf(exercise.name) }
    var kindId by remember(exercise.id) { mutableStateOf(exercise.kind) }
    var uni by remember(exercise.id) { mutableStateOf(exercise.unilateral) }
    var ps by remember(exercise.id) { mutableStateOf(exercise.defaultPerSide) }

    WorkoutSheetFrame(onDismiss) {
        Column {
            Text("Editar ejercicio", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = p.textPrimary)
            Text("en $dayName", fontSize = 13.sp, color = p.textSecondary)
        }
        FieldLabel("Nombre")
        WorkoutField(name, { name = it.take(60) }, "Nombre del ejercicio")
        MiniTitle("Cómo lo haces")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            WorkoutKind.ALL.forEach { k -> ChoiceChip(k.label, k.id == kindId) { kindId = k.id } }
        }
        MiniTitle("Opciones")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ChoiceChip("Unilateral", uni) { uni = !uni }
            ChoiceChip("A cada lado", ps) { ps = !ps }
        }
        if (linkedDays.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.accentSoft).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Icon(LineIcons.Link, null, tint = p.accentText, modifier = Modifier.size(13.dp).padding(top = 2.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Unido con ${linkedDays.joinToString(", ")}. El nombre cambia solo aquí; el tipo y las opciones, en los dos.",
                    fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFF6B4330)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 2.dp)) {
            SecondaryButton("Cancelar", onDismiss, Modifier.weight(1f))
            PrimaryButton("Guardar", { if (name.isNotBlank()) onSave(name.trim(), kindId, uni, ps) }, Modifier.weight(1.4f), enabled = name.isNotBlank())
        }
    }
}

// ── Hoja: editar día (renombrar / borrar) ────────────────────────────────────

@Composable
private fun EditDaySheet(day: WorkoutDay, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    val p = Palette.current
    var name by remember(day.id) { mutableStateOf(day.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    WorkoutSheetFrame(onDismiss) {
        Text("Editar día", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = p.textPrimary)
        FieldLabel("Nombre del día")
        WorkoutField(name, { name = it.take(60) }, "Nombre del día")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cancelar", onDismiss, Modifier.weight(1f))
            PrimaryButton("Guardar", { if (name.isNotBlank()) onRename(name.trim()) }, Modifier.weight(1.4f), enabled = name.isNotBlank())
        }
        Text(
            "Eliminar este día", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.danger, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFFF1D5CF), RoundedCornerShape(14.dp))
                .clickable(role = Role.Button) { confirmDelete = true }.padding(vertical = 13.dp)
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = p.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("¿Eliminar «${day.name}»?", fontWeight = FontWeight.ExtraBold, color = p.textPrimary) },
            text = {
                Text(
                    "Se borra el día con sus ejercicios. Si alguno está unido con otro día, en el otro sigue igual.",
                    color = p.textSecondary
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar", color = p.danger, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = p.textSecondary) } }
        )
    }
}

// ── Piezas compartidas con la pantalla de apuntar ────────────────────────────

/** Ancho máximo del contenido: en el móvil ocupa todo; en escritorio evita tarjetas estiradas. */
internal fun Modifier.workoutPage(): Modifier = this.widthIn(max = 620.dp).fillMaxWidth()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutSheetFrame(onDismiss: () -> Unit, scrollable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val p = Palette.current
    // Sin `skipPartiallyExpanded` la hoja puede quedarse a medias con los botones cortados.
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = p.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = p.border) }
    ) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
            content = content
        )
    }
}

@Composable
internal fun CircleBack(onClick: () -> Unit) {
    val p = Palette.current
    Surface(
        onClick = onClick, shape = CircleShape, color = p.surface, border = BorderStroke(1.dp, p.border),
        modifier = Modifier.size(44.dp).semantics { contentDescription = "Volver" }
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(LineIcons.ChevronLeft, null, tint = p.textPrimary, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
internal fun MiniTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Palette.current.textSecondary, modifier = modifier)
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.current.textSecondary)
}

/** Píldora seleccionable: chocolate encendida, blanca con borde apagada. */
@Composable
internal fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = Palette.current
    Box(
        modifier.clip(RoundedCornerShape(50)).background(if (selected) p.hero else p.surface)
            .border(1.dp, if (selected) p.hero else p.border, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) p.onHero else p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun LinkBadge(text: String) {
    val p = Palette.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(p.accentSoft).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(LineIcons.Link, null, tint = p.accentText, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B4330))
    }
}

@Composable
internal fun IndexBadge(n: Int) {
    val p = Palette.current
    Box(Modifier.size(28.dp).clip(CircleShape).background(p.accentSoft), contentAlignment = Alignment.Center) {
        Text("$n", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4330))
    }
}

@Composable
private fun SquareIconButton(symbol: String, description: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val p = Palette.current
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
            .background(if (danger) p.surface else p.surfaceSunken)
            .border(1.dp, if (danger) Color(0xFFF1D5CF) else p.border, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol, fontSize = if (danger) 14.sp else 16.sp, fontWeight = FontWeight.ExtraBold,
            color = when { danger -> p.danger; enabled -> p.textPrimary; else -> Color(0xFFD9C9BC) }
        )
    }
}

/** Campo de texto del estilo Melocotón: caja hundida, borde terracota al escribir. */
@Composable
internal fun WorkoutField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val p = Palette.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interaction,
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = p.textPrimary),
        cursorBrush = SolidColor(p.accentText),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surfaceSunken)
                    .border(1.dp, if (focused) p.accentText else p.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 13.dp, vertical = 13.dp)
            ) {
                if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = p.chevron, maxLines = 1)
                inner()
            }
        }
    )
}

@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, compact: Boolean = false) {
    val p = Palette.current
    Box(
        modifier.clip(RoundedCornerShape(if (compact) 11.dp else 14.dp))
            .background(if (enabled) p.hero else p.divider)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = if (compact) 10.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold, color = if (enabled) p.onHero else p.textSecondary)
    }
}

@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val p = Palette.current
    Box(
        modifier.clip(RoundedCornerShape(if (compact) 11.dp else 14.dp)).background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(if (compact) 11.dp else 14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = if (compact) 10.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.SemiBold, color = p.textPrimary)
    }
}

@Composable
internal fun OfflineNotice() {
    val p = Palette.current
    Text(
        "Sin conexión con el servidor: se guardará en cuanto vuelva.",
        fontSize = 12.sp, color = p.accentText,
        modifier = Modifier.fillMaxWidth().background(p.accentSoft).padding(horizontal = 20.dp, vertical = 8.dp)
    )
}
