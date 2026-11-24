package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.gymsmart.gymsmart.model.*
import com.gymsmart.gymsmart.services.rememberRestNotifier
import com.gymsmart.gymsmart.ui.theme.GymSmartColors
import kotlinx.coroutines.delay
import kotlin.time.Clock

/**
 * Apuntar las series del día abierto.
 *
 * Reglas de diseño que vienen de la maqueta y que conviene no "simplificar" otra vez:
 *  - El número grande es el que el usuario mira (por lado / por mancuerna / placa), no el total.
 *  - El peso lo pone la app sola: la serie N contra la serie N de la última sesión.
 *  - Nunca se supone cuántas series va a hacer: no hay objetivo, solo lo hecho.
 *  - El RIR va en pantalla, a un toque, no escondido.
 */
@Composable
fun WorkoutLogScreen(
    day: WorkoutDay,
    session: ActiveSession,
    saveFailed: Boolean,
    onBack: () -> Unit,
    onSessionChange: (ActiveSession) -> Unit,
    onFinish: (ActiveSession) -> Unit,
    onDayChange: (WorkoutDay) -> Unit
) {
    var selected by remember(day.id) { mutableStateOf(0) }
    val exercise = day.exercises.getOrNull(selected) ?: day.exercises.firstOrNull() ?: return
    val todaySets = session.sets[exercise.id].orEmpty()

    // Lo que se está editando ahora mismo. El peso arranca en la serie que toca de la última vez.
    var editingIndex by remember(day.id) { mutableStateOf<Int?>(null) }
    var value by remember(exercise.id) { mutableStateOf(0.0) }
    var reps by remember(exercise.id) { mutableStateOf(10) }
    var rir by remember(exercise.id) { mutableStateOf<Int?>(null) }
    var seeded by remember(exercise.id) { mutableStateOf(false) }

    var kpadField by remember { mutableStateOf<String?>(null) }
    var showKindSheet by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }

    /** La serie de la última sesión contra la que toca compararse. */
    fun reference(index: Int = todaySets.size): WorkoutSet? = exercise.lastSession.getOrNull(index)

    // Propone lo de la vez anterior; si aquel día no llegó a esa serie, deja lo que haya puesto.
    fun propose() {
        reference()?.let { value = it.value; reps = it.reps }
    }

    LaunchedEffect(exercise.id) {
        if (!seeded) { propose(); seeded = true }
    }

    // Descanso: arranca solo al guardar una serie y sigue contando en negativo pasado el tiempo.
    var restTotal by remember { mutableStateOf(0) }
    var restLeft by remember { mutableStateOf(0) }
    // Aviso del sistema (Tarea C): se programa al guardar la serie para que llegue aunque salgas de
    // la app o se bloquee el movil, que es lo que se hace entre series.
    val restNotifier = rememberRestNotifier()
    var restFinishedPopup by remember { mutableStateOf(false) }
    LaunchedEffect(restTotal) {
        while (restTotal > 0 && restLeft > -600) {
            delay(1000)
            restLeft -= 1
            if (restLeft == 0) {
                // El contador ha llegado a cero con la app delante: se avisa aqui y se cancela la
                // notificacion del sistema, para no dar el mismo aviso dos veces.
                restNotifier.cancel()
                restFinishedPopup = true
            }
        }
    }

    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) { now = Clock.System.now().toEpochMilliseconds(); delay(1000) }
    }

    fun updateSets(newSets: List<WorkoutSet>) {
        onSessionChange(session.copy(sets = session.sets + (exercise.id to newSets)))
    }

    fun saveSet() {
        val rec = WorkoutSet(value = value, reps = reps, rir = rir)
        val idx = editingIndex
        if (idx != null) {
            updateSets(todaySets.mapIndexed { i, s -> if (i == idx) rec else s })
            editingIndex = null
        } else {
            updateSets(todaySets + rec)
            restTotal = REST_SECONDS; restLeft = REST_SECONDS
            restFinishedPopup = false
            restNotifier.schedule(REST_SECONDS)
            exercise.lastSession.getOrNull(todaySets.size + 1)?.let { value = it.value; reps = it.reps }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {

        WorkoutTopBar(
            title = "Entreno",
            subtitle = day.name,
            onBack = onBack,
            trailing = { SessionClock(now - session.startedAt) }
        )
        if (saveFailed) OfflineNotice()

        Column(
            Modifier.weight(1f).workoutPage().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

            Spacer(Modifier.height(14.dp))
            SessionSummary(day, session, selected)

            Spacer(Modifier.height(22.dp))
            SectionLabel("Ejercicios")
            Spacer(Modifier.height(10.dp))
            ExerciseStrip(
                day = day,
                session = session,
                selected = selected,
                onSelect = { selected = it; editingIndex = null }
            )

            Spacer(Modifier.height(16.dp))

            // ── Tarjeta del peso ────────────────────────────────────────────
            val kind = WorkoutKind.of(exercise.kind)
            Column(Modifier.card()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(exercise.name, style = MaterialTheme.typography.headlineMedium,
                        color = GymSmartColors.TextPrimary, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Pill(
                        text = editingIndex?.let { "Editando ${it + 1}" } ?: "Serie ${todaySets.size + 1}",
                        color = GymSmartColors.Primary
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(GymSmartColors.SurfaceElevated)
                            .border(1.dp, GymSmartColors.Outline, RoundedCornerShape(50))
                            .clickable { showKindSheet = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            "${kind.emoji} ${kind.label}" +
                                (if (kind.usesBar) " · barra ${formatKg(exercise.barKg)}" else "") + "  ▾",
                            style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    val ref = reference(editingIndex ?: todaySets.size)
                    Text(
                        when {
                            ref != null ->
                                "Última vez, serie ${(editingIndex ?: todaySets.size) + 1}: " +
                                    "${formatKg(ref.value)} ${if (kind.usesBar) "por lado" else "kg"} × ${ref.reps}"
                            todaySets.isNotEmpty() -> "Anterior: ${exercise.setLabel(todaySets.last())}"
                            exercise.lastSession.isNotEmpty() -> "Serie nueva respecto a la última vez"
                            else -> "Primera vez"
                        },
                        style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary
                    )
                }

                Spacer(Modifier.height(18.dp))
                Text(kind.bigLabel.uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = GymSmartColors.TextSecondary, modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)

                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .clickable { kpadField = "weight" }.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(formatKg(value), fontSize = 56.sp, fontWeight = FontWeight.ExtraBold,
                        color = GymSmartColors.TextPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text(kind.unit, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = GymSmartColors.Primary, modifier = Modifier.padding(bottom = 8.dp))
                    Text(" ✎", fontSize = 12.sp, color = GymSmartColors.TextDisabled,
                        modifier = Modifier.padding(bottom = 10.dp))
                }

                Text(
                    weightSubtitle(exercise, value, reference(editingIndex ?: todaySets.size)),
                    style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepButton("−", formatKg(exercise.stepKg), Modifier.weight(1f)) {
                        value = maxOf(0.0, round2(value - exercise.stepKg))
                    }
                    StepButton("+", formatKg(exercise.stepKg), Modifier.weight(1f)) {
                        value = minOf(MAX_VALUE, round2(value + exercise.stepKg))
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Cuánto suben y bajan − y +. Ponlo igual que " + when (exercise.kind) {
                        WorkoutKind.BARRA -> "los saltos de tus discos (por lado)"
                        WorkoutKind.MANCUERNAS -> "los saltos de tus mancuernas"
                        WorkoutKind.CORPORAL -> "los saltos de tu lastre"
                        WorkoutKind.POLEA -> "los saltos de esta polea"
                        else -> "los saltos de esta máquina"
                    } + ". Para un peso suelto, toca el número y escríbelo.",
                    style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextDisabled
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WORKOUT_STEPS.forEach { s ->
                        ChipButton(formatKg(s), s == exercise.stepKg) {
                            onDayChange(day.copy(exercises = day.exercises.map {
                                if (it.id == exercise.id) it.copy(stepKg = s) else it
                            }))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Tarjeta de repeticiones ─────────────────────────────────────
            Column(Modifier.card()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel("Repeticiones")
                    reference(editingIndex ?: todaySets.size)?.let {
                        Text("última vez ${it.reps}", style = MaterialTheme.typography.labelMedium,
                            color = GymSmartColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepButton("−", null, Modifier.weight(1f)) { reps = maxOf(1, reps - 1) }
                    Column(
                        Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                            .clickable { kpadField = "reps" },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$reps", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                            color = GymSmartColors.TextPrimary)
                        Text("REPS ✎", style = MaterialTheme.typography.labelSmall,
                            color = GymSmartColors.TextSecondary)
                    }
                    StepButton("+", null, Modifier.weight(1f)) { reps = minOf(100, reps + 1) }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WORKOUT_QUICK_REPS.forEach { q ->
                        SquareChip("$q", q == reps, Modifier.weight(1f)) { reps = q }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("RIR", style = MaterialTheme.typography.labelMedium,
                        color = GymSmartColors.TextSecondary, fontWeight = FontWeight.Bold)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        WORKOUT_RIRS.forEach { r ->
                            SquareChip(rirLabel(r), r == rir, Modifier.weight(1f)) { rir = r }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ── Series registradas ──────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Series de este ejercicio", style = MaterialTheme.typography.titleSmall,
                    color = GymSmartColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    if (todaySets.isEmpty()) "ninguna todavía"
                    else "${todaySets.size} ${seriesWord(todaySets.size)} · " +
                        "${formatKg(exercise.volume(todaySets))} kg",
                    style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary
                )
            }
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(GymSmartColors.SurfaceCard)
            ) {
                if (todaySets.isEmpty()) {
                    Text(
                        "Aún no has guardado ninguna serie.\nAjusta el peso y pulsa Guardar serie.",
                        style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextDisabled,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(26.dp)
                    )
                } else {
                    todaySets.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = GymSmartColors.Divider)
                        SetRow(
                            exercise = exercise,
                            set = s,
                            index = i,
                            previous = todaySets.getOrNull(i - 1),
                            editing = editingIndex == i,
                            onEdit = {
                                editingIndex = i; value = s.value; reps = s.reps; rir = s.rir
                            },
                            onDelete = {
                                updateSets(todaySets.filterIndexed { j, _ -> j != i })
                                if (editingIndex == i) editingIndex = null
                                else if ((editingIndex ?: 0) > i) editingIndex = editingIndex!! - 1
                            }
                        )
                    }
                    HorizontalDivider(color = GymSmartColors.Divider)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Volumen del ejercicio", style = MaterialTheme.typography.bodySmall,
                            color = GymSmartColors.TextSecondary)
                        Text("${formatKg(exercise.volume(todaySets))} kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = GymSmartColors.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedButton(
                onClick = { confirmFinish = true },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp)
            ) {
                Text("Terminar entrenamiento", style = MaterialTheme.typography.labelLarge,
                    color = GymSmartColors.TextSecondary)
            }

            Spacer(Modifier.height(24.dp))
        }

        // ── Barra fija de guardar ───────────────────────────────────────────
        Column(
            Modifier.background(GymSmartColors.Background).workoutPage()
                .padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 16.dp)
        ) {

            if (restFinishedPopup) {
                RestFinishedPopup(onDismiss = { restFinishedPopup = false })
                Spacer(Modifier.height(8.dp))
            }

            if (restTotal > 0) {
                RestBar(
                    left = restLeft,
                    total = restTotal,
                    onAdd = {
                        restTotal += 30; restLeft += 30
                        restFinishedPopup = false
                        // Reprograma: si no, el aviso seguiria saltando a la hora vieja.
                        if (restLeft > 0) restNotifier.schedule(restLeft)
                    },
                    onSkip = {
                        restTotal = 0; restLeft = 0
                        restFinishedPopup = false
                        restNotifier.cancel()
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { saveSet() },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (editingIndex != null) GymSmartColors.Warning else GymSmartColors.Primary
                ),
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (editingIndex != null) "Guardar cambios" else "Guardar serie ${todaySets.size + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (editingIndex != null) Color(0xFF2A1E00) else GymSmartColors.OnPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        exercise.setLabel(WorkoutSet(value, reps)) +
                            if (exercise.kind == WorkoutKind.CORPORAL) ""
                            else " · ${formatKg(exercise.totalKg(value))} kg totales",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (editingIndex != null) Color(0xFF2A1E00) else GymSmartColors.OnPrimary
                    )
                }
            }

            if (editingIndex != null) {
                TextButton(onClick = { editingIndex = null; propose() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar edición", color = GymSmartColors.TextSecondary)
                }
            } else {
                val prev = todaySets.lastOrNull() ?: reference()
                if (prev != null && (prev.value != value || prev.reps != reps)) {
                    OutlinedButton(
                        onClick = { value = prev.value; reps = prev.reps },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(46.dp)
                    ) {
                        Text(
                            "Repetir ${formatKg(prev.value)} ${WorkoutKind.of(exercise.kind).unit} × ${prev.reps}",
                            style = MaterialTheme.typography.labelLarge, color = GymSmartColors.TextPrimary
                        )
                    }
                }
            }
        }
    }

    // ── Teclado numérico propio ─────────────────────────────────────────────
    kpadField?.let { field ->
        NumberPadSheet(
            exercise = exercise,
            field = field,
            current = if (field == "weight") value else reps.toDouble(),
            onDismiss = { kpadField = null },
            onAccept = { v ->
                if (field == "weight") value = minOf(MAX_VALUE, maxOf(0.0, v))
                else reps = v.toInt().coerceIn(1, 100)
                kpadField = null
            }
        )
    }

    if (showKindSheet) {
        KindSheet(
            exercise = exercise,
            onDismiss = { showKindSheet = false },
            onChange = { updated ->
                onDayChange(day.copy(exercises = day.exercises.map { if (it.id == updated.id) updated else it }))
            }
        )
    }

    if (confirmFinish) {
        val total = session.sets.values.sumOf { it.size }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            containerColor = GymSmartColors.SurfaceCard,
            title = { Text("¿Terminar el entrenamiento?", color = GymSmartColors.TextPrimary) },
            text = {
                Text(
                    if (total > 0)
                        "$total ${seriesWord(total)} registradas. Se guardan como tu última vez, " +
                            "y la próxima sesión te pondrá estos pesos solos."
                    else "No has registrado ninguna serie todavía.",
                    color = GymSmartColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmFinish = false; onFinish(session) }) {
                    Text("Terminar", color = GymSmartColors.Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) {
                    Text("Seguir", color = GymSmartColors.TextSecondary)
                }
            }
        )
    }
}

private const val REST_SECONDS = 90
private const val MAX_VALUE = 300.0

private fun weightSubtitle(ex: WorkoutExercise, value: Double, ref: WorkoutSet?): String = when (ex.kind) {
    WorkoutKind.BARRA ->
        "= ${formatKg(ex.totalKg(value))} kg en total · barra ${formatKg(ex.barKg)} + ${formatKg(value)} × 2"
    WorkoutKind.MANCUERNAS -> "= ${formatKg(ex.totalKg(value))} kg entre las dos"
    WorkoutKind.CORPORAL ->
        if (value > 0) "Peso corporal + ${formatKg(value)} kg" else "Solo peso corporal"
    else -> {
        if (ref == null) ""
        else {
            val d = round2(value - ref.value)
            when {
                d == 0.0 -> "Igual que la última vez"
                d > 0 -> "+${formatKg(d)} kg respecto a la última vez"
                else -> "${formatKg(d)} kg respecto a la última vez"
            }
        }
    }
}

@Composable
private fun Modifier.card(): Modifier =
    this.fillMaxWidth().clip(MaterialTheme.shapes.large).background(GymSmartColors.SurfaceCard).padding(16.dp)

@Composable
private fun SessionClock(elapsedMs: Long) {
    val s = (elapsedMs / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    val text = (if (h > 0) "$h:${m.toString().padStart(2, '0')}" else "$m") +
        ":${sec.toString().padStart(2, '0')}"
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(GymSmartColors.Success.copy(alpha = 0.12f))
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text("● $text", style = MaterialTheme.typography.labelMedium,
            color = GymSmartColors.Success, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SessionSummary(day: WorkoutDay, session: ActiveSession, selected: Int) {
    val totalSets = session.sets.values.sumOf { it.size }
    val totalVolume = day.exercises.sumOf { ex -> ex.volume(session.sets[ex.id].orEmpty()) }
    val top = day.exercises.flatMap { ex ->
        session.sets[ex.id].orEmpty().map { ex.totalKg(it.value) }
    }.maxOrNull()

    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .background(GymSmartColors.SurfaceCard).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel("Entrenamiento de hoy")
            Pill("${selected + 1} / ${day.exercises.size}", GymSmartColors.Primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(day.name, style = MaterialTheme.typography.headlineMedium, color = GymSmartColors.TextPrimary)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("$totalSets", "Series", Modifier.weight(1f))
            StatTile(formatKg(totalVolume), "kg volumen", Modifier.weight(1f))
            StatTile(top?.let { "${formatKg(it)} kg" } ?: "—", "Serie top", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.small).background(GymSmartColors.SurfaceElevated)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium,
            color = GymSmartColors.TextPrimary, fontWeight = FontWeight.ExtraBold)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = GymSmartColors.TextSecondary)
    }
}

/** Carrusel de ejercicios: una rayita por serie hecha, nunca un objetivo supuesto. */
@Composable
private fun ExerciseStrip(
    day: WorkoutDay,
    session: ActiveSession,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        day.exercises.forEachIndexed { i, ex ->
            val done = session.sets[ex.id].orEmpty().size
            val kind = WorkoutKind.of(ex.kind)
            Column(
                Modifier.width(140.dp).heightIn(min = 92.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (i == selected) GymSmartColors.Primary.copy(alpha = 0.09f)
                        else GymSmartColors.SurfaceCard
                    )
                    .border(
                        1.5.dp,
                        if (i == selected) GymSmartColors.Primary else Color.Transparent,
                        MaterialTheme.shapes.medium
                    )
                    .clickable { onSelect(i) }
                    .padding(11.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${kind.emoji} ${kind.label.uppercase()}",
                        style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextDisabled,
                        maxLines = 1)
                    if (done > 0) Text("$done", style = MaterialTheme.typography.labelSmall,
                        color = GymSmartColors.TextDisabled, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(ex.name, style = MaterialTheme.typography.bodyMedium,
                    color = GymSmartColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                SetLines(done)
            }
        }
    }
}

/** Rayitas: 4 por fila, hasta 8; a partir de ahí un "+n" centrado debajo. */
@Composable
private fun SetLines(done: Int) {
    val shown = minOf(done, WORKOUT_MAX_LINES)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var i = 0
        while (i < shown) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (j in 0 until 4) {
                    if (i + j < shown) {
                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(GymSmartColors.Primary))
                    } else Spacer(Modifier.weight(1f))
                }
            }
            i += 4
        }
        if (done > WORKOUT_MAX_LINES) {
            Text("+${done - WORKOUT_MAX_LINES}", style = MaterialTheme.typography.labelSmall,
                color = GymSmartColors.Primary, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SetRow(
    exercise: WorkoutExercise,
    set: WorkoutSet,
    index: Int,
    previous: WorkoutSet?,
    editing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (editing) GymSmartColors.Warning.copy(alpha = 0.07f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IndexBadge(index + 1)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
            Text(exercise.setLabel(set), style = MaterialTheme.typography.titleSmall,
                color = GymSmartColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                buildString {
                    if (exercise.kind == WorkoutKind.BARRA || exercise.kind == WorkoutKind.MANCUERNAS)
                        append("${formatKg(exercise.totalKg(set.value))} kg totales · ")
                    append("${formatKg(exercise.totalKg(set.value) * set.reps)} kg de volumen")
                    set.rir?.let { append(" · RIR ${rirLabel(it)}") }
                    append(" · toca para editar")
                },
                style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextDisabled
            )
        }
        previous?.let {
            val d = round2(set.value - it.value)
            Pill(
                text = if (d > 0) "+${formatKg(d)}" else if (d < 0) formatKg(d) else "=",
                color = if (d > 0) GymSmartColors.Success else GymSmartColors.TextSecondary
            )
        }
        Box(
            Modifier.size(40.dp).clip(MaterialTheme.shapes.extraSmall).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) { Text("✕", fontSize = 15.sp, color = GymSmartColors.TextDisabled) }
    }
}

@Composable
private fun RestBar(left: Int, total: Int, onAdd: () -> Unit, onSkip: () -> Unit) {
    val over = left <= 0
    val mm = (if (left < 0) -left else left) / 60
    val ss = (if (left < 0) -left else left) % 60
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .border(1.dp, if (over) GymSmartColors.Success else GymSmartColors.Primary, MaterialTheme.shapes.medium)
            .background(GymSmartColors.SurfaceCard).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (over) "+" else "") + "$mm:${ss.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.titleLarge,
                color = if (over) GymSmartColors.Success else GymSmartColors.TextPrimary,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (over) "LISTO PARA LA SIGUIENTE" else "DESCANSO",
                style = MaterialTheme.typography.labelSmall,
                color = GymSmartColors.TextSecondary, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAdd) { Text("+30 s", color = GymSmartColors.TextSecondary) }
            TextButton(onClick = onSkip) { Text("Saltar", color = GymSmartColors.TextSecondary) }
        }
        LinearProgressIndicator(
            progress = { if (over) 1f else (1f - left.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
            color = GymSmartColors.Primary,
            trackColor = GymSmartColors.Outline,
            modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 8.dp)
        )
    }
}

@Composable
private fun StepButton(symbol: String, caption: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(58.dp).clip(MaterialTheme.shapes.medium)
            .background(GymSmartColors.SurfaceElevated)
            .border(1.dp, GymSmartColors.Outline, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(symbol, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GymSmartColors.TextPrimary)
        caption?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextSecondary)
        }
    }
}

@Composable
private fun ChipButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(50))
            .background(
                if (selected) GymSmartColors.Primary.copy(alpha = 0.14f) else GymSmartColors.SurfaceElevated
            )
            .border(1.dp, if (selected) GymSmartColors.Primary else GymSmartColors.Outline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall,
            color = if (selected) GymSmartColors.Primary else GymSmartColors.TextPrimary,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SquareChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).clip(MaterialTheme.shapes.small)
            .background(if (selected) GymSmartColors.Primary else GymSmartColors.SurfaceElevated)
            .border(
                1.dp,
                if (selected) GymSmartColors.Primary else GymSmartColors.Outline,
                MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall,
            color = if (selected) GymSmartColors.OnPrimary else GymSmartColors.TextSecondary,
            fontWeight = FontWeight.Bold)
    }
}

/**
 * Teclado numérico propio. Es la pieza que hace que no se abra nunca el teclado del sistema:
 * teclas grandes, coma decimal y, en barra y mancuernas, la opción de escribir el total y que
 * la app haga la conversión inversa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberPadSheet(
    exercise: WorkoutExercise,
    field: String,
    current: Double,
    onDismiss: () -> Unit,
    onAccept: (Double) -> Unit
) {
    val isWeight = field == "weight"
    val kind = WorkoutKind.of(exercise.kind)
    var buffer by remember { mutableStateOf("") }
    var asTotal by remember { mutableStateOf(false) }
    // Sin esto, la hoja podía quedarse en el estado "parcialmente expandida" de Material3 con el
    // botón "Hecho" cortado y sin ninguna forma de llegar a él salvo arrastrar el tope a mano — este
    // Column no tiene scroll, al contrario que el de añadir ejercicio. Mismo patrón que ya usa bien
    // la hoja de comidas en NutritionScreen.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GymSmartColors.SurfaceCard
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            Text(if (isWeight) exercise.name else "Repeticiones",
                style = MaterialTheme.typography.labelMedium, color = GymSmartColors.TextSecondary,
                fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    buffer.ifBlank {
                        if (isWeight) formatKg(if (asTotal) exercise.totalKg(current) else current)
                        else current.toInt().toString()
                    },
                    fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = GymSmartColors.TextPrimary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (!isWeight) "reps" else if (asTotal) "kg totales" else kind.unit,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GymSmartColors.Primary,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }

            Text(
                when {
                    !isWeight -> "Repeticiones de esta serie"
                    asTotal -> "Escribe el total y lo reparte solo"
                    exercise.kind == WorkoutKind.BARRA -> "Lo que pones a CADA lado de la barra"
                    exercise.kind == WorkoutKind.MANCUERNAS -> "Lo que marca UNA mancuerna"
                    exercise.kind == WorkoutKind.CORPORAL -> "Lastre, 0 si vas a peso corporal"
                    else -> "El número que marca la placa"
                },
                style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            if (isWeight && (exercise.kind == WorkoutKind.BARRA || exercise.kind == WorkoutKind.MANCUERNAS)) {
                TextButton(onClick = { asTotal = !asTotal; buffer = "" }) {
                    Text(
                        if (asTotal) "Escribir ${kind.unit}  ⇄" else "Escribir el total  ⇄",
                        color = GymSmartColors.Primary, fontWeight = FontWeight.Bold
                    )
                }
            } else Spacer(Modifier.height(10.dp))

            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9",
                if (isWeight) "," else "", "0", "⌫")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            Box(
                                Modifier.weight(1f).height(58.dp).clip(MaterialTheme.shapes.medium)
                                    .background(
                                        if (k.isBlank()) Color.Transparent else GymSmartColors.SurfaceElevated
                                    )
                                    .clickable(enabled = k.isNotBlank()) {
                                        buffer = when (k) {
                                            "⌫" -> buffer.dropLast(1)
                                            "," -> if (buffer.contains(',') || buffer.isEmpty()) buffer else "$buffer,"
                                            else -> if (buffer.count { it.isDigit() } < 4) buffer + k else buffer
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(k, fontSize = 23.sp, fontWeight = FontWeight.Bold,
                                    color = GymSmartColors.TextPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("Cancelar", color = GymSmartColors.TextSecondary) }

                Button(
                    onClick = {
                        val parsed = buffer.replace(',', '.').toDoubleOrNull()
                        if (parsed == null) onDismiss()
                        else onAccept(
                            if (isWeight && asTotal) exercise.valueFromTotal(parsed) else parsed
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = GymSmartColors.Primary),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("Hecho", color = GymSmartColors.OnPrimary, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/**
 * Aviso de descanso terminado dentro de la app (Tarea C).
 *
 * Aparece cuando el contador llega a cero con la app delante. En ese caso la notificacion del
 * sistema se cancela, asi que este es el unico aviso: por eso ocupa su sitio en la pantalla en vez
 * de ser algo que se pueda pasar por alto, y se cierra tocandolo.
 */
@Composable
private fun RestFinishedPopup(onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = GymSmartColors.Primary,
        modifier = Modifier.fillMaxWidth().clickable { onDismiss() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Descanso terminado",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = GymSmartColors.OnPrimary
                )
                Text(
                    "Toca la siguiente serie",
                    style = MaterialTheme.typography.labelSmall,
                    color = GymSmartColors.OnPrimary.copy(alpha = 0.85f)
                )
            }
            Text(
                "OK",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = GymSmartColors.OnPrimary
            )
        }
    }
}
