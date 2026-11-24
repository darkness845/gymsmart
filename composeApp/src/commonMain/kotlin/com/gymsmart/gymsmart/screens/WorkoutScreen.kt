package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.model.*
import com.gymsmart.gymsmart.services.WorkoutService
import com.gymsmart.gymsmart.ui.theme.GymSmartColors
import androidx.compose.foundation.BorderStroke
import com.gymsmart.gymsmart.navigation.Screen
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.time.Clock

/**
 * Entrenamiento. Tres pantallas encadenadas, igual que la maqueta aprobada:
 *
 *  1. [DaysView]   — mis días de entrenamiento, el que esté en curso marcado.
 *  2. [EditorView] — definir un día: nombre y qué ejercicios lo forman.
 *  3. [WorkoutLogScreen] — apuntar series, que trabaja sobre los ejercicios del día abierto.
 *
 * Van como estado interno y no como rutas de navegación porque comparten el mismo plan en memoria
 * y el ida y vuelta entre ellas es constante; pasar el plan por argumentos de ruta obligaría a
 * serializarlo en cada salto.
 */

private enum class WorkoutView { DAYS, EDITOR, LOG }

private val DAY_SUGGESTIONS = listOf(
    "Pecho · Hombro · Tríceps", "Espalda · Bíceps", "Pierna",
    "Torso", "Empuje", "Tirón", "Cuerpo completo"
)

private val EXERCISE_SUGGESTIONS = listOf(
    "Press banca", "Press inclinado", "Aperturas", "Cruce de poleas", "Press militar",
    "Elevaciones laterales", "Fondos", "Extensión tríceps", "Dominadas", "Remo",
    "Jalón al pecho", "Curl bíceps", "Sentadilla", "Prensa", "Extensión cuádriceps",
    "Femoral tumbado", "Gemelos", "Peso muerto"
)

private fun newId(): String =
    Clock.System.now().toEpochMilliseconds().toString(36) +
        (0..999999).random().toString(36)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    navController: NavController,
    workoutService: WorkoutService
) {
    val scope = rememberCoroutineScope()

    var plan by remember { mutableStateOf(WorkoutPlan()) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(WorkoutView.DAYS) }
    var draft by remember { mutableStateOf<WorkoutDay?>(null) }
    var draftIsNew by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }

    // Guardar es "lo que hay en pantalla manda": se manda el plan entero y, si falla la red, se avisa
    // sin perder nada — el estado sigue en memoria y el siguiente guardado lo reintenta completo.
    fun persist(next: WorkoutPlan) {
        plan = next
        scope.launch {
            workoutService.savePlan(next).onFailure { saveFailed = true }.onSuccess { saveFailed = false }
        }
    }

    LaunchedEffect(Unit) {
        workoutService.getPlan()
            .onSuccess { plan = it; loadFailed = false }
            .onFailure { loadFailed = true }
        loading = false
    }

    val session = plan.session
    val liveDay = session?.let { s -> plan.days.firstOrNull { it.id == s.dayId } }

    // Si hay un entrenamiento a medias, se entra directamente a él al abrir la pantalla.
    LaunchedEffect(loading) {
        if (!loading && liveDay != null) view = WorkoutView.LOG
    }

    Scaffold(
        containerColor = GymSmartColors.Background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GymSmartColors.Primary)
                }

                loadFailed -> LoadError(
                    onRetry = {
                        loading = true
                        scope.launch {
                            workoutService.getPlan()
                                .onSuccess { plan = it; loadFailed = false }
                                .onFailure { loadFailed = true }
                            loading = false
                        }
                    },
                    onBack = { navController.popBackStack() }
                )

                view == WorkoutView.LOG && liveDay != null -> WorkoutLogScreen(
                    day = liveDay,
                    session = session,
                    saveFailed = saveFailed,
                    onBack = { view = WorkoutView.DAYS },
                    onSessionChange = { updated -> persist(plan.copy(session = updated)) },
                    onFinish = { finished ->
                        // Lo de hoy pasa a ser "la última vez": de ahí sale el peso que la app
                        // propondrá sola la próxima vez, serie contra serie.
                        val days = plan.days.map { d ->
                            if (d.id != liveDay.id) d
                            else d.copy(exercises = d.exercises.map { ex ->
                                val hechas = finished.sets[ex.id].orEmpty()
                                if (hechas.isEmpty()) ex else ex.copy(lastSession = hechas)
                            })
                        }
                        persist(WorkoutPlan(days = days, session = null))

                        // Y ademas queda guardado como entrenamiento hecho (Tarea E). Antes esto se
                        // perdia: solo sobrevivia "la ultima vez" de cada ejercicio, asi que no habia
                        // forma de mirar atras. Se guarda una copia con el nombre y el tipo de cada
                        // ejercicio dentro, para que editar el plan despues no reescriba el pasado.
                        val hechos = liveDay.exercises.mapNotNull { ex ->
                            val series = finished.sets[ex.id].orEmpty()
                            if (series.isEmpty()) null
                            else SessionExercise(ex.name, ex.kind, ex.barKg, series)
                        }
                        if (hechos.isNotEmpty()) {
                            val hoy = Clock.System.now()
                                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                            scope.launch {
                                workoutService.saveSession(
                                    WorkoutSession(
                                        date = hoy,
                                        dayName = liveDay.name,
                                        startedAt = finished.startedAt,
                                        exercises = hechos
                                    )
                                ).onFailure { saveFailed = true }
                            }
                        }

                        view = WorkoutView.DAYS
                        scope.launch {
                            val total = finished.sets.values.sumOf { it.size }
                            snackbar.showMessage(
                                if (total > 0) "Entrenamiento guardado · $total ${seriesWord(total)}"
                                else "Entrenamiento cerrado sin series"
                            )
                        }
                    },
                    onDayChange = { updatedDay ->
                        persist(plan.copy(days = plan.days.map { if (it.id == updatedDay.id) updatedDay else it }))
                    }
                )

                view == WorkoutView.EDITOR && draft != null -> EditorView(
                    draft = draft!!,
                    isNew = draftIsNew,
                    onChange = { draft = it },
                    onCancel = { draft = null; view = WorkoutView.DAYS },
                    onDelete = {
                        val id = draft!!.id
                        persist(plan.copy(days = plan.days.filterNot { it.id == id }))
                        draft = null; view = WorkoutView.DAYS
                        scope.launch { snackbar.showMessage("Día eliminado") }
                    },
                    onSave = {
                        val d = draft!!
                        when {
                            d.name.isBlank() -> scope.launch { snackbar.showMessage("Ponle nombre al día") }
                            d.exercises.isEmpty() -> scope.launch { snackbar.showMessage("Añade al menos un ejercicio") }
                            else -> {
                                val days = if (plan.days.any { it.id == d.id })
                                    plan.days.map { if (it.id == d.id) d else it } else plan.days + d
                                persist(plan.copy(days = days))
                                draft = null; view = WorkoutView.DAYS
                                scope.launch { snackbar.showMessage("Día guardado") }
                            }
                        }
                    }
                )

                else -> DaysView(
                    days = plan.days,
                    liveDayId = session?.dayId,
                    liveSets = session?.sets?.values?.sumOf { it.size } ?: 0,
                    saveFailed = saveFailed,
                    onBack = { navController.popBackStack() },
                    onNew = {
                        draft = WorkoutDay(id = newId(), name = "", exercises = emptyList())
                        draftIsNew = true; view = WorkoutView.EDITOR
                    },
                    onEdit = { day ->
                        if (day.id == session?.dayId) {
                            scope.launch { snackbar.showMessage("Estás entrenando ese día: termínalo antes de editarlo") }
                        } else {
                            draft = day; draftIsNew = false; view = WorkoutView.EDITOR
                        }
                    },
                    onStart = { day ->
                        if (session == null || session.dayId != day.id) {
                            persist(plan.copy(session = ActiveSession(
                                dayId = day.id,
                                startedAt = Clock.System.now().toEpochMilliseconds()
                            )))
                        }
                        view = WorkoutView.LOG
                    },
                    onProgress = { navController.navigate(Screen.Progress.route) }
                )
            }
        }
    }
}

private suspend fun SnackbarHostState.showMessage(msg: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(msg)
}

internal fun seriesWord(n: Int) = if (n == 1) "serie" else "series"

@Composable
private fun LoadError(onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No se pudieron cargar tus entrenamientos",
                style = MaterialTheme.typography.titleMedium, color = GymSmartColors.TextPrimary,
                textAlign = TextAlign.Center)
            Text("Revisa la conexión e inténtalo de nuevo.",
                style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onRetry,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = GymSmartColors.Primary)
            ) { Text("Reintentar", color = GymSmartColors.OnPrimary, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onBack) { Text("Volver", color = GymSmartColors.TextSecondary) }
        }
    }
}

// ── Pantalla 1: mis entrenamientos ───────────────────────────────────────────

@Composable
private fun DaysView(
    days: List<WorkoutDay>,
    liveDayId: String?,
    liveSets: Int,
    saveFailed: Boolean,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (WorkoutDay) -> Unit,
    onStart: (WorkoutDay) -> Unit,
    onProgress: () -> Unit
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        WorkoutTopBar(
            title = "Mis entrenamientos",
            subtitle = if (days.isEmpty()) "Empieza creando tu primer día"
            else "Elige el día que vas a entrenar",
            onBack = onBack
        )
        if (saveFailed) OfflineNotice()

        // Acceso al historico (Tarea E). Va aqui arriba y no dentro de la lista de dias porque el
        // progreso no depende de tener plan: se mira igual aunque hoy no toque entrenar.
        OutlinedButton(
            onClick = onProgress,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, GymSmartColors.Outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GymSmartColors.TextPrimary)
        ) {
            Text("📅  Ver mi progreso", fontWeight = FontWeight.SemiBold)
        }

        if (days.isEmpty()) {
            EmptyDays(onNew)
            return@Column
        }

        Column(
            Modifier.workoutPage().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            days.forEach { day ->
                val live = day.id == liveDayId
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(GymSmartColors.SurfaceCard)
                        .border(
                            1.5.dp,
                            if (live) GymSmartColors.Success else Color.Transparent,
                            MaterialTheme.shapes.large
                        )
                        .padding(16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            day.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = GymSmartColors.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Pill(
                            text = if (live) "entrenando · $liveSets ${seriesWord(liveSets)}"
                            else "${day.exercises.size} ${if (day.exercises.size == 1) "ejercicio" else "ejercicios"}",
                            color = if (live) GymSmartColors.Success else GymSmartColors.TextSecondary
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (day.exercises.isEmpty()) {
                        Text(
                            "Todavía no tiene ejercicios. Edítalo para añadirlos.",
                            style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextDisabled
                        )
                    } else {
                        FlowChips(day.exercises.map { "${WorkoutKind.of(it.kind).emoji} ${it.name}" })
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onStart(day) },
                            enabled = day.exercises.isNotEmpty(),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = GymSmartColors.Primary),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text(
                                if (live) "Seguir entrenando  →" else "Entrenar  →",
                                style = MaterialTheme.typography.labelLarge,
                                color = GymSmartColors.OnPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { onEdit(day) },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Editar", style = MaterialTheme.typography.labelLarge,
                                color = GymSmartColors.TextSecondary)
                        }
                    }
                }
            }

            DashedButton(text = "+  Crear día de entrenamiento", onClick = onNew)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Lo primero que ve alguien que abre esto por primera vez. Explica de qué va un "día" con un ejemplo
 * concreto, y la única acción posible es el botón: no hay que buscarla entre otras.
 */
@Composable
private fun EmptyDays(onNew: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(GymSmartColors.SurfaceCard)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(50))
                    .background(GymSmartColors.Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Text("🏋", fontSize = 28.sp) }

            Spacer(Modifier.height(18.dp))
            Text(
                "Aquí van tus entrenamientos",
                style = MaterialTheme.typography.titleLarge,
                color = GymSmartColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Un día es una rutina tuya, como \"Pecho · Hombro · Tríceps\": le pones nombre y metes " +
                    "los ejercicios que haces, en orden.\n\n" +
                    "Luego, el día que toque, lo abres y solo tienes que ir apuntando las series.",
                style = MaterialTheme.typography.bodyMedium,
                color = GymSmartColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onNew,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = GymSmartColors.Primary),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Crear mi primer día", style = MaterialTheme.typography.titleMedium,
                    color = GymSmartColors.OnPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Pantalla 2: definir el día ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorView(
    draft: WorkoutDay,
    isNew: Boolean,
    onChange: (WorkoutDay) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    var kindTarget by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        WorkoutTopBar(
            title = if (isNew) "Nuevo día" else "Editar día",
            subtitle = "Define qué ejercicios haces",
            onBack = onCancel
        )

        Column(
            Modifier.weight(1f).workoutPage().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

            Spacer(Modifier.height(8.dp))
            SectionLabel("Nombre del día")
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it.take(60))) },
                placeholder = { Text("Pecho · Hombro · Tríceps") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Spacer(Modifier.height(10.dp))
            FlowSelectable(
                options = DAY_SUGGESTIONS,
                selected = draft.name,
                onSelect = { onChange(draft.copy(name = it)) }
            )

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ejercicios", style = MaterialTheme.typography.titleSmall,
                    color = GymSmartColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    if (draft.exercises.isEmpty()) "ninguno todavía"
                    else "${draft.exercises.size} ${if (draft.exercises.size == 1) "ejercicio" else "ejercicios"}",
                    style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))

            draft.exercises.forEachIndexed { i, ex ->
                val kind = WorkoutKind.of(ex.kind)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(GymSmartColors.SurfaceCard)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IndexBadge(i + 1)
                    Spacer(Modifier.width(10.dp))
                    Column(
                        Modifier.weight(1f).clickable { kindTarget = i }
                    ) {
                        Text(ex.name, style = MaterialTheme.typography.titleSmall,
                            color = GymSmartColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "${kind.emoji} ${kind.label}" +
                                (if (kind.usesBar) " · barra ${formatKg(ex.barKg)}" else "") +
                                " · toca para cambiarlo",
                            style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextDisabled
                        )
                    }
                    IconTextButton("↑", enabled = i > 0) {
                        onChange(draft.copy(exercises = draft.exercises.swapped(i, i - 1)))
                    }
                    IconTextButton("↓", enabled = i < draft.exercises.lastIndex) {
                        onChange(draft.copy(exercises = draft.exercises.swapped(i, i + 1)))
                    }
                    IconTextButton("✕", tint = GymSmartColors.Error) {
                        onChange(draft.copy(exercises = draft.exercises.filterIndexed { j, _ -> j != i }))
                    }
                }
            }

            DashedButton(text = "+  Añadir ejercicio", onClick = { adding = true })

            if (!isNew) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(48.dp)
                ) {
                    Text("Eliminar este día", color = GymSmartColors.Error,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Surface(color = GymSmartColors.Background, tonalElevation = 0.dp) {
            Button(
                onClick = onSave,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = GymSmartColors.Primary),
                modifier = Modifier.workoutPage().padding(20.dp).height(56.dp)
            ) {
                Text("Guardar día", style = MaterialTheme.typography.titleMedium,
                    color = GymSmartColors.OnPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (adding) {
        AddExerciseSheet(
            onDismiss = { adding = false },
            onAdd = { ex ->
                onChange(draft.copy(exercises = draft.exercises + ex))
                adding = false
            }
        )
    }

    kindTarget?.let { idx ->
        val ex = draft.exercises.getOrNull(idx)
        if (ex == null) kindTarget = null
        else KindSheet(
            exercise = ex,
            onDismiss = { kindTarget = null },
            onChange = { updated ->
                onChange(draft.copy(exercises = draft.exercises.mapIndexed { j, e -> if (j == idx) updated else e }))
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = GymSmartColors.SurfaceCard,
            title = { Text("¿Eliminar \"${draft.name}\"?", color = GymSmartColors.TextPrimary) },
            text = {
                Text(
                    "Se borra el día con sus ejercicios y el historial de pesos que guardaban.",
                    color = GymSmartColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Eliminar", color = GymSmartColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancelar", color = GymSmartColors.TextSecondary)
                }
            }
        )
    }
}

private fun <T> List<T>.swapped(a: Int, b: Int): List<T> =
    toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }

// ── Hoja: añadir ejercicio (el tipo se decide aquí, una vez) ─────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExerciseSheet(onDismiss: () -> Unit, onAdd: (WorkoutExercise) -> Unit) {
    var picked by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    var kindId by remember { mutableStateOf(WorkoutKind.BARRA) }
    var bar by remember { mutableStateOf(20.0) }
    val name = custom.trim().ifBlank { picked }
    // Igual que en el teclado numérico de WorkoutLogScreen: sin esto la hoja puede quedarse
    // parcialmente expandida. El scroll de la Column ya mitigaba parte del problema, pero el tope de
    // la hoja debe subir del todo igualmente (mismo patrón que la hoja de comidas en NutritionScreen).
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GymSmartColors.SurfaceCard
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)
        ) {
            Text("Añadir ejercicio", style = MaterialTheme.typography.titleMedium,
                color = GymSmartColors.TextPrimary, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(16.dp))
            SectionLabel("Cuál")
            Spacer(Modifier.height(8.dp))
            FlowSelectable(EXERCISE_SUGGESTIONS, picked) { picked = it; custom = "" }
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.take(60); if (it.isNotBlank()) picked = "" },
                placeholder = { Text("…o escribe otro") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Cómo lo haces")
            Spacer(Modifier.height(4.dp))
            WorkoutKind.ALL.forEach { k ->
                KindOption(kind = k, selected = k.id == kindId) {
                    kindId = k.id
                    if (k.usesBar && bar <= 0) bar = 20.0
                }
            }

            if (WorkoutKind.of(kindId).usesBar) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Peso de la barra")
                Spacer(Modifier.height(8.dp))
                FlowSelectable(BAR_OPTIONS.map { "${formatKg(it)} kg" }, "${formatKg(bar)} kg") { label ->
                    bar = BAR_OPTIONS.first { "${formatKg(it)} kg" == label }
                }
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    val k = WorkoutKind.of(kindId)
                    onAdd(
                        WorkoutExercise(
                            id = newId(),
                            name = name,
                            kind = kindId,
                            barKg = if (k.usesBar) bar else 0.0,
                            stepKg = k.defaultStep
                        )
                    )
                },
                enabled = name.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = GymSmartColors.Primary),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Añadir", color = GymSmartColors.OnPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

internal val BAR_OPTIONS = listOf(20.0, 15.0, 10.0, 7.5)

/** Hoja de "cómo haces este ejercicio": el tipo y, si procede, la barra. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KindSheet(
    exercise: WorkoutExercise,
    onDismiss: () -> Unit,
    onChange: (WorkoutExercise) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GymSmartColors.SurfaceCard
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)
        ) {
            Text("Cómo haces ${exercise.name}", style = MaterialTheme.typography.titleMedium,
                color = GymSmartColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "Esto se elige una vez. Decide qué número te pide la app en cada serie.",
                style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
            )

            WorkoutKind.ALL.forEach { k ->
                KindOption(kind = k, selected = k.id == exercise.kind) {
                    onChange(
                        exercise.copy(
                            kind = k.id,
                            barKg = if (k.usesBar && exercise.barKg <= 0) 20.0 else exercise.barKg,
                            stepKg = k.defaultStep
                        )
                    )
                }
            }

            if (WorkoutKind.of(exercise.kind).usesBar) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Peso de la barra")
                Spacer(Modifier.height(8.dp))
                FlowSelectable(
                    BAR_OPTIONS.map { "${formatKg(it)} kg" },
                    "${formatKg(exercise.barKg)} kg"
                ) { label ->
                    onChange(exercise.copy(barKg = BAR_OPTIONS.first { "${formatKg(it)} kg" == label }))
                }
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = GymSmartColors.Primary),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Hecho", color = GymSmartColors.OnPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun KindOption(kind: WorkoutKind, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) GymSmartColors.Primary.copy(alpha = 0.09f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) GymSmartColors.Primary else GymSmartColors.Outline,
                MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(kind.emoji, fontSize = 18.sp, modifier = Modifier.width(30.dp))
        Column(Modifier.weight(1f)) {
            Text(kind.label, style = MaterialTheme.typography.titleSmall,
                color = GymSmartColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text(kind.hint, style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary)
        }
        if (selected) Text("✓", color = GymSmartColors.Primary, fontWeight = FontWeight.Bold)
    }
}

// ── Piezas compartidas con la pantalla de apuntar ────────────────────────────

/**
 * Ancho máximo de la columna de contenido. En el móvil no se nota (ocupa todo), pero en Desktop
 * evita que una ventana ancha deje las tarjetas estiradas de lado a lado y el texto ilegible.
 */
internal fun Modifier.workoutPage(): Modifier = this.widthIn(max = 620.dp).fillMaxWidth()

@Composable
internal fun WorkoutTopBar(title: String, subtitle: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Row(
        Modifier.workoutPage().padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
                .background(GymSmartColors.SurfaceCard).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("‹", fontSize = 22.sp, color = GymSmartColors.TextSecondary) }

        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = GymSmartColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = GymSmartColors.TextSecondary, maxLines = 1)
        }
        trailing?.invoke()
    }
  }
    HorizontalDivider(color = GymSmartColors.Divider)
}

@Composable
internal fun OfflineNotice() {
    Text(
        "Sin conexión con el servidor: se guardará en cuanto vuelva.",
        style = MaterialTheme.typography.bodySmall,
        color = GymSmartColors.Warning,
        modifier = Modifier.fillMaxWidth()
            .background(GymSmartColors.Warning.copy(alpha = 0.10f))
            .padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = GymSmartColors.TextSecondary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
internal fun Pill(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun IndexBadge(n: Int) {
    Box(
        Modifier.size(28.dp).clip(MaterialTheme.shapes.extraSmall).background(GymSmartColors.SurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        Text("$n", style = MaterialTheme.typography.labelMedium,
            color = GymSmartColors.TextSecondary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IconTextButton(
    symbol: String,
    enabled: Boolean = true,
    tint: Color = GymSmartColors.TextDisabled,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(38.dp).clip(MaterialTheme.shapes.extraSmall)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 15.sp, color = if (enabled) tint else GymSmartColors.TextDisabled.copy(alpha = 0.3f))
    }
}

@Composable
internal fun DashedButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(MaterialTheme.shapes.medium)
            .border(1.5.dp, GymSmartColors.Outline, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = GymSmartColors.TextSecondary)
    }
}

/** Chips informativos que se van envolviendo en varias filas. */
@Composable
internal fun FlowChips(labels: List<String>) {
    WrapRow(labels.size) { i ->
        Box(
            Modifier.clip(MaterialTheme.shapes.extraSmall).background(GymSmartColors.SurfaceElevated)
                .padding(horizontal = 9.dp, vertical = 6.dp)
        ) {
            Text(labels[i], style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary)
        }
    }
}

/** Chips seleccionables (nombres de día, de ejercicio, barras...). */
@Composable
internal fun FlowSelectable(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    WrapRow(options.size) { i ->
        val o = options[i]
        val on = o == selected
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (on) GymSmartColors.Primary.copy(alpha = 0.14f) else Color.Transparent)
                .border(1.dp, if (on) GymSmartColors.Primary else GymSmartColors.Outline, RoundedCornerShape(50))
                .clickable { onSelect(o) }
                .padding(horizontal = 13.dp, vertical = 11.dp)
        ) {
            Text(o, style = MaterialTheme.typography.bodySmall,
                color = if (on) GymSmartColors.Primary else GymSmartColors.TextSecondary,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

/**
 * Filas que se envuelven, sin depender de FlowRow (que en esta versión de Compose Multiplatform
 * sigue siendo experimental). Reparte en filas de ancho fijo por número de elementos.
 */
@Composable
private fun WrapRow(count: Int, item: @Composable (Int) -> Unit) {
    val perRow = 2
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        var i = 0
        while (i < count) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                for (j in 0 until perRow) {
                    if (i + j < count) Box(Modifier.weight(1f)) { item(i + j) }
                    else Spacer(Modifier.weight(1f))
                }
            }
            i += perRow
        }
    }
}
