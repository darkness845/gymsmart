package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymsmart.gymsmart.model.*
import com.gymsmart.gymsmart.services.rememberRestNotifier
import com.gymsmart.gymsmart.ui.icons.LineIcons
import com.gymsmart.gymsmart.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.time.Clock

/**
 * Apuntar series, tal cual el simulador aprobado (Tarea 7):
 *
 *  - Cada serie arranca con **lo que se hizo en esa misma serie la última vez**, con la misma
 *    configuración (a cada lado / unilateral) con la que se apuntó, aunque fuera en otro día unido.
 *  - Las comparaciones con la última vez se hacen siempre en **peso total**, para que cuadren aunque
 *    un día se apuntara a cada lado y otro en total.
 *  - "Unilateral" y "A cada lado" son interruptores junto al tipo, iguales para todos los tipos, y se
 *    guardan en la serie.
 *
 * Se conserva todo lo que la pantalla ya hacía: la sesión se guarda en cada cambio (cerrar la app no
 * borra nada), el aviso de fin de descanso del sistema (Tarea C) y el teclado numérico propio.
 */

private const val REST_SECONDS = 90
private const val MAX_VALUE = 300.0
private val SelectedTint = Color(0xFFFBF0E7)
private val AccentDark = Color(0xFF6B4330)

@Composable
fun WorkoutLogScreen(
    day: WorkoutDay,
    session: ActiveSession,
    saveFailed: Boolean,
    linkedDays: (WorkoutExercise) -> List<String>,
    onBack: () -> Unit,
    onSessionChange: (ActiveSession) -> Unit,
    onPatchGroup: (String, (WorkoutExercise) -> WorkoutExercise) -> Unit,
    onFinish: (ActiveSession) -> Unit
) {
    val p = Palette.current
    var selected by remember(day.id) { mutableStateOf(0) }
    val exercise = day.exercises.getOrNull(selected) ?: day.exercises.firstOrNull() ?: return
    val kind = WorkoutKind.of(exercise.kind)
    val todaySets = session.sets[exercise.id].orEmpty()

    // Lo que se está apuntando ahora mismo.
    var editingIndex by remember(day.id) { mutableStateOf<Int?>(null) }
    var value by remember(day.id) { mutableStateOf(0.0) }
    var reps by remember(day.id) { mutableStateOf(10) }
    var rir by remember(day.id) { mutableStateOf<Int?>(null) }
    var ps by remember(day.id) { mutableStateOf(exercise.defaultPerSide) }
    var uni by remember(day.id) { mutableStateOf(exercise.unilateral) }

    var pad by remember { mutableStateOf<String?>(null) }
    var buffer by remember { mutableStateOf("") }
    var showKindSheet by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }

    /**
     * Propone la serie [n] de [ex]: la misma serie de la última vez, con su configuración. Si aquel
     * día no llegó a esa serie, se queda el peso que hubiera y se usan [fallbackPs] / [fallbackUni].
     */
    fun propose(ex: WorkoutExercise, n: Int, fallbackPs: Boolean, fallbackUni: Boolean) {
        val r = ex.lastSession.getOrNull(n)
        if (r != null) {
            value = r.value; reps = r.reps; ps = r.perSideFor(ex.kind); uni = r.isUnilateral
        } else {
            ps = fallbackPs; uni = fallbackUni
        }
    }

    // Al entrar a un ejercicio (también al reanudar la sesión): lo de la última vez en la serie que
    // toca; si no hay, las opciones por defecto del ejercicio.
    LaunchedEffect(exercise.id) {
        editingIndex = null; rir = null
        propose(exercise, todaySets.size, exercise.defaultPerSide, exercise.unilateral)
    }

    // Reloj de la sesión y descanso.
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) { while (true) { now = Clock.System.now().toEpochMilliseconds(); delay(1000) } }
    var restStart by remember { mutableStateOf<Long?>(null) }
    var restTotal by remember { mutableStateOf(0) }
    var popupOff by remember { mutableStateOf(false) }
    // Aviso del sistema (Tarea C): se programa al guardar la serie para que llegue aunque se salga
    // de la app o se bloquee el móvil, que es lo que se hace entre series.
    val restNotifier = rememberRestNotifier()
    val restLeft = restStart?.let { restTotal - ((now - it) / 1000).toInt() }
    val resting = restLeft != null && restLeft > -600
    val over = resting && (restLeft ?: 1) <= 0
    // Ya no se cancela la notificación al llegar a cero con la app delante: el usuario la quiere
    // siempre, como un mensaje de WhatsApp. Se pide el permiso al entrar para que el primer descanso
    // ya avise.
    LaunchedEffect(Unit) { restNotifier.requestPermissionIfNeeded() }

    fun updateSets(newSets: List<WorkoutSet>) {
        onSessionChange(session.copy(sets = session.sets + (exercise.id to newSets)))
    }

    val refIndex = editingIndex ?: todaySets.size
    val ref = exercise.lastSession.getOrNull(refIndex)
    val shared = linkedDays(exercise)
    val currentTotal = totalKgOf(exercise.kind, exercise.barKg, value, ps)

    fun saveSet() {
        val rec = WorkoutSet(value = value, reps = reps, rir = rir, perSide = ps, unilateral = uni)
        val idx = editingIndex
        if (idx != null) {
            updateSets(todaySets.mapIndexed { i, s -> if (i == idx) rec else s })
            editingIndex = null
            return
        }
        val newSets = todaySets + rec
        updateSets(newSets)
        restTotal = REST_SECONDS; restStart = Clock.System.now().toEpochMilliseconds(); popupOff = false
        restNotifier.schedule(REST_SECONDS)
        // La siguiente serie arranca en lo que se hizo en esa misma serie la última vez.
        propose(exercise, newSets.size, ps, uni)
        rir = null
    }

    // Diferencia con la última vez, siempre en peso total.
    var diffText = ""
    var diffColor = p.textSecondary
    if (ref != null) {
        val d = round2(currentTotal - exercise.totalKg(ref))
        diffText = if (d == 0.0) "Igual que la última vez" else (if (d > 0) "+${formatKg(d)}" else formatKg(d)) + " kg respecto a la última vez"
        diffColor = when { d > 0 -> p.success; d < 0 -> p.accentText; else -> p.textSecondary }
    } else if (todaySets.isNotEmpty()) {
        diffText = "Anterior: " + exercise.setLabel(todaySets.last())
    }
    if (ps && exercise.kind != WorkoutKind.CORPORAL) diffText += (if (diffText.isNotEmpty()) " · " else "") + "${formatKg(currentTotal)} kg en total"

    val allSets = day.exercises.sumOf { session.sets[it.id].orEmpty().size }
    val allVolume = day.exercises.sumOf { it.volume(session.sets[it.id].orEmpty()) }
    val top = day.exercises.flatMap { e -> session.sets[e.id].orEmpty().map { e.totalKg(it) } }.maxOrNull()

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {

        // ── Barra superior ───────────────────────────────────────────────────
        Row(
            Modifier.workoutPage().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleBack(onBack)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Entreno", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = p.textPrimary)
                Text(day.name, fontSize = 12.sp, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(p.hero).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(p.accent))
                Spacer(Modifier.width(6.dp))
                Text(clockText(((now - session.startedAt) / 1000).coerceAtLeast(0)), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.onHero)
            }
        }
        if (saveFailed) OfflineNotice()

        // ── Zona con scroll ──────────────────────────────────────────────────
        Column(
            Modifier.workoutPage().weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Resumen de hoy
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.hero).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ENTRENAMIENTO DE HOY", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = p.onHeroSecondary, modifier = Modifier.weight(1f))
                    Text(
                        "${selected + 1} / ${day.exercises.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.onHeroAccent,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(p.heroRaised).padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(day.name, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = p.onHero)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("$allSets", "SERIES", Modifier.weight(1f))
                    StatTile(formatKg(kotlin.math.round(allVolume)), "KG VOLUMEN", Modifier.weight(1f))
                    StatTile(top?.let { "${formatKg(it)} kg" } ?: "—", "SERIE TOP", Modifier.weight(1f), accent = true)
                }
            }

            // Ejercicios
            Column {
                MiniTitle("Ejercicios")
                Spacer(Modifier.height(8.dp))
                val stripState = rememberScrollState()
                Row(Modifier.fillMaxWidth().horizontalScroll(stripState), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    day.exercises.forEachIndexed { i, e ->
                        val n = session.sets[e.id].orEmpty().size
                        val isSel = i == selected
                        Column(
                            Modifier.width(132.dp).heightIn(min = 92.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (isSel) SelectedTint else p.surface)
                                .border(1.5.dp, if (isSel) p.accent else p.surface, RoundedCornerShape(16.dp))
                                .clickable(role = Role.Tab) { selected = i }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row {
                                    Text(WorkoutKind.of(e.kind).label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = p.textSecondary, modifier = Modifier.weight(1f), maxLines = 1)
                                    if (n > 0) Text("$n", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = p.textSecondary)
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(e.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp, color = p.textPrimary)
                            }
                            SetLines(n)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalScrollIndicator(stripState)
            }

            // ── Tarjeta de peso ──────────────────────────────────────────────
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(22.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(exercise.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 24.sp, color = p.textPrimary, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    val editing = editingIndex
                    Text(
                        if (editing != null) "Editando ${editing + 1}" else "Serie ${todaySets.size + 1}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (editing != null) p.onAccent else AccentDark,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (editing != null) p.accent else p.accentSoft).padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Tipo + los dos interruptores, SIEMPRE en una línea: el tipo ocupa lo suyo y los dos
                // interruptores se reparten el resto. Encendido = relleno chocolate, sin icono.
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${kind.label} ▾", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.textPrimary, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(p.surfaceSunken).border(1.dp, p.border, RoundedCornerShape(50))
                            .clickable(role = Role.Button) { showKindSheet = true }.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                    FillToggle("Unilateral", uni, Modifier.weight(1f)) { uni = !uni }
                    // Al cambiarlo se convierte el número para que el peso REAL no cambie (75 → 37,5).
                    FillToggle("A cada lado", ps, Modifier.weight(1f)) {
                        val total = totalKgOf(exercise.kind, exercise.barKg, value, ps)
                        ps = !ps
                        value = valueForTotal(exercise.kind, exercise.barKg, total, ps)
                    }
                }
                val notes = buildList {
                    if (ps && exercise.kind != WorkoutKind.CORPORAL) add(
                        if (exercise.kind == WorkoutKind.BARRA) "A cada lado: apuntas lo que pones en un lado; el total suma los dos lados y la barra."
                        else "A cada lado: apuntas el peso de un lado; el total es el doble."
                    )
                    if (uni) add("Unilateral: apunta las repeticiones de UN lado.")
                }
                if (notes.isNotEmpty()) {
                    Spacer(Modifier.height(7.dp))
                    Text(notes.joinToString(" "), fontSize = 11.sp, lineHeight = 16.sp, color = p.textSecondary)
                }

                // De dónde sale lo propuesto
                if (ref != null) {
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.accentSoft).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(LineIcons.Link, null, tint = p.accentText, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                if (shared.isNotEmpty() && exercise.lastFrom.isNotBlank() && exercise.lastFrom != day.name) "Última vez · en ${exercise.lastFrom}" else "Última vez",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentDark
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text("Serie ${refIndex + 1}: ${exercise.setLabel(ref)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
                    }
                } else if (exercise.lastSession.isNotEmpty()) {
                    // Primera vez con el ejercicio: no se dice nada. Solo si ya se hizo y esta serie es nueva.
                    Spacer(Modifier.height(10.dp))
                    val n = exercise.lastSession.size
                    Text(
                        "Serie nueva: la última vez hiciste $n ${seriesWord(n)}.", fontSize = 13.sp, color = p.textSecondary,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surfaceSunken)
                            .border(1.dp, Color(0xFFCDBBAD), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    bigLabelFor(exercise.kind, ps) + if (exercise.kind == WorkoutKind.BARRA) " · BARRA DE ${formatKg(exercise.barKg)} KG" else "",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = p.textSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { pad = "weight"; buffer = "" }.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom
                ) {
                    Text(formatKg(value), fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp, lineHeight = 56.sp, color = p.textPrimary)
                    Text(unitFor(exercise.kind, ps), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.accentText, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
                    Text(" ✎", fontSize = 12.sp, color = p.chevron, modifier = Modifier.padding(bottom = 10.dp))
                }
                if (diffText.isNotEmpty()) {
                    Text(diffText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = diffColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepButton("−", formatKg(exercise.stepKg), "Menos peso", Modifier.weight(1f)) { value = maxOf(0.0, round2(value - exercise.stepKg)) }
                    StepButton("+", formatKg(exercise.stepKg), "Más peso", Modifier.weight(1f)) { value = minOf(MAX_VALUE, round2(value + exercise.stepKg)) }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Cuánto suben y bajan − y +. Ponlo igual que ${kind.stepHelp}${if (ps) " (por lado)" else ""}. Para un peso suelto, toca el número y escríbelo.",
                    fontSize = 11.sp, lineHeight = 16.sp, color = p.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                val stepsState = rememberScrollState()
                Row(Modifier.fillMaxWidth().horizontalScroll(stepsState), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    WORKOUT_STEPS.forEach { st ->
                        ChoiceChip(formatKg(st), st == exercise.stepKg) { onPatchGroup(exercise.groupId) { it.copy(stepKg = st) } }
                    }
                }
                if (stepsState.maxValue > 0) { Spacer(Modifier.height(6.dp)); HorizontalScrollIndicator(stepsState) }
            }

            // ── Tarjeta de repeticiones ──────────────────────────────────────
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(22.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniTitle(if (uni) "Repeticiones por lado" else "Repeticiones", Modifier.weight(1f))
                    ref?.let { Text("última vez ${it.reps}" + if (it.isUnilateral) " por lado" else "", fontSize = 12.sp, color = p.textSecondary) }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepButton("−", null, "Menos repeticiones", Modifier.weight(1f)) { reps = maxOf(1, reps - 1) }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { pad = "reps"; buffer = "" },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$reps", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 42.sp, color = p.textPrimary)
                        Text(if (uni) "REPS / LADO ✎" else "REPS ✎", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = p.textSecondary)
                    }
                    StepButton("+", null, "Más repeticiones", Modifier.weight(1f)) { reps = minOf(100, reps + 1) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WORKOUT_QUICK_REPS.forEach { q -> SquareChoice("$q", q == reps, Modifier.weight(1f).height(40.dp)) { reps = q } }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RIR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.textSecondary)
                    Spacer(Modifier.width(10.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        WORKOUT_RIRS.forEach { r -> SquareChoice(rirLabel(r), r == rir, Modifier.weight(1f).height(36.dp)) { rir = r } }
                    }
                }
            }

            // ── Series de este ejercicio ─────────────────────────────────────
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Series de este ejercicio", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        if (todaySets.isEmpty()) "ninguna todavía"
                        else "${todaySets.size} ${seriesWord(todaySets.size)} · ${formatKg(kotlin.math.round(exercise.volume(todaySets)))} kg",
                        fontSize = 12.sp, color = p.textSecondary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(20.dp))) {
                    if (todaySets.isEmpty()) {
                        Text(
                            "Aún no has guardado ninguna serie.\nAjusta el peso y pulsa Guardar serie.",
                            fontSize = 13.sp, lineHeight = 19.sp, color = p.textSecondary, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(22.dp)
                        )
                    } else {
                        todaySets.forEachIndexed { i, s ->
                            if (i > 0) HorizontalDivider(color = p.divider)
                            val prev = todaySets.getOrNull(i - 1)
                            val d = prev?.let { round2(exercise.totalKg(s) - exercise.totalKg(it)) }
                            Row(
                                Modifier.fillMaxWidth().background(if (editingIndex == i) SelectedTint else p.surface).padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IndexBadge(i + 1)
                                Spacer(Modifier.width(10.dp))
                                Column(
                                    Modifier.weight(1f).clickable(role = Role.Button) {
                                        editingIndex = i; value = s.value; reps = s.reps; rir = s.rir
                                        ps = s.perSideFor(exercise.kind); uni = s.isUnilateral
                                    }
                                ) {
                                    Text(exercise.setLabel(s), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
                                    Text(
                                        buildList {
                                            if (s.perSideFor(exercise.kind) && exercise.kind != WorkoutKind.CORPORAL) add("${formatKg(exercise.totalKg(s))} kg totales")
                                            add("${formatKg(kotlin.math.round(exercise.totalKg(s) * s.reps * (if (s.isUnilateral) 2 else 1)))} kg de volumen")
                                            s.rir?.let { add("RIR ${rirLabel(it)}") }
                                            add("toca para editar")
                                        }.joinToString(" · "),
                                        fontSize = 11.sp, color = p.textSecondary
                                    )
                                }
                                if (d != null) {
                                    Text(
                                        when { d > 0 -> "+${formatKg(d)}"; d < 0 -> formatKg(d); else -> "=" },
                                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                        color = if (d > 0) p.success else p.textSecondary,
                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (d > 0) p.successSoft else p.divider).padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                Box(
                                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) {
                                        updateSets(todaySets.filterIndexed { j, _ -> j != i })
                                        val ed = editingIndex
                                        if (ed == i) editingIndex = null else if (ed != null && ed > i) editingIndex = ed - 1
                                    }.semantics { contentDescription = "Borrar serie" },
                                    contentAlignment = Alignment.Center
                                ) { Text("✕", fontSize = 15.sp, color = p.chevron) }
                            }
                        }
                        HorizontalDivider(color = p.divider)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
                            Text("Volumen del ejercicio", fontSize = 12.sp, color = p.textSecondary, modifier = Modifier.weight(1f))
                            Text("${formatKg(kotlin.math.round(exercise.volume(todaySets)))} kg", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = p.textPrimary)
                        }
                    }
                }
            }

            Text(
                "Terminar entrenamiento", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.textSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { confirmFinish = true }.padding(vertical = 14.dp)
            )
        }

        // ── Barra fija de abajo: aviso, descanso y guardar ───────────────────
        HorizontalDivider(color = p.border)
        Column(
            Modifier.workoutPage().background(p.background).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (over && !popupOff && !restNotifier.showsSystemNotification) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.accent).clickable(role = Role.Button) { popupOff = true }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Descanso terminado", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.onAccent)
                        Text("Toca la siguiente serie", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = p.onAccent)
                    }
                    Text("OK", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.onAccent)
                }
            }
            if (resting && restLeft != null) {
                val restColor = if (over) p.success else p.accent
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface).border(1.5.dp, restColor, RoundedCornerShape(14.dp))
                        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((if (over) "+" else "") + clockText(kotlin.math.abs(restLeft).toLong()), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = restColor)
                        Spacer(Modifier.width(8.dp))
                        Text(if (over) "LISTO PARA LA SIGUIENTE" else "DESCANSO", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = p.textSecondary, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            restTotal += 30; popupOff = false
                            // Reprograma: si no, el aviso seguiría saltando a la hora vieja.
                            val left = restTotal - ((Clock.System.now().toEpochMilliseconds() - (restStart ?: 0L)) / 1000).toInt()
                            if (left > 0) restNotifier.schedule(left)
                        }) { Text("+30 s", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.textSecondary) }
                        TextButton(onClick = { restStart = null; restTotal = 0; popupOff = true; restNotifier.cancel() }) {
                            Text("Saltar", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.textSecondary)
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(end = 6.dp).height(3.dp).clip(RoundedCornerShape(50)).background(p.track)) {
                        val frac = if (over) 1f else (1f - restLeft.toFloat() / restTotal.toFloat()).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(restColor))
                    }
                }
            }
            val editing = editingIndex != null
            Column(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (editing) p.accent else p.hero).clickable(role = Role.Button) { saveSet() }.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(if (editing) "Guardar cambios" else "Guardar serie ${todaySets.size + 1}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (editing) p.onAccent else p.onHero)
                Text(
                    exercise.setLabel(WorkoutSet(value, reps, perSide = ps, unilateral = uni)) +
                        if (ps && exercise.kind != WorkoutKind.CORPORAL) " · ${formatKg(currentTotal)} kg totales" else "",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = (if (editing) p.onAccent else p.onHero).copy(alpha = 0.85f)
                )
            }
        }
    }

    // ── Teclado numérico propio ──────────────────────────────────────────────
    pad?.let { field ->
        NumberPadSheet(
            title = if (field == "weight") exercise.name else "Repeticiones",
            current = if (field == "weight") formatKg(value) else "$reps",
            buffer = buffer,
            unit = if (field == "weight") unitFor(exercise.kind, ps) else "reps",
            hint = when {
                field == "reps" -> if (uni) "Repeticiones de UN lado" else "Repeticiones de esta serie"
                exercise.kind == WorkoutKind.CORPORAL -> "Lastre, 0 si vas a peso corporal"
                ps -> "El peso de UN lado"
                exercise.kind == WorkoutKind.BARRA -> "El total, con la barra incluida"
                else -> "El número que marca la placa"
            },
            allowDecimal = field == "weight",
            onBuffer = { buffer = it },
            onCancel = { pad = null; buffer = "" },
            onDone = {
                val parsed = buffer.replace(',', '.').toDoubleOrNull()
                if (parsed != null) {
                    if (field == "weight") value = minOf(MAX_VALUE, maxOf(0.0, round2(parsed)))
                    else reps = parsed.toInt().coerceIn(1, 100)
                }
                pad = null; buffer = ""
            }
        )
    }

    if (showKindSheet) {
        KindSheet(
            exercise = exercise,
            linkedDays = shared,
            onDismiss = { showKindSheet = false },
            onKind = { k -> onPatchGroup(exercise.groupId) { it.copy(kind = k.id, barKg = if (k.usesBar && it.barKg <= 0) 20.0 else it.barKg) } },
            onBar = { b -> onPatchGroup(exercise.groupId) { it.copy(barKg = b) } }
        )
    }

    if (confirmFinish) {
        val anyShared = day.exercises.any { session.sets[it.id].orEmpty().isNotEmpty() && linkedDays(it).isNotEmpty() }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            containerColor = p.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("¿Terminar el entrenamiento?", fontWeight = FontWeight.ExtraBold, color = p.textPrimary) },
            text = {
                Text(
                    if (allSets > 0)
                        "$allSets ${if (allSets == 1) "serie registrada" else "series registradas"}. Se guardan como tu última vez" +
                            (if (anyShared) ", también en los días unidos," else "") + " y la próxima sesión te pondrá estos pesos solos."
                    else "No has registrado ninguna serie todavía.",
                    color = p.textSecondary
                )
            },
            confirmButton = { PrimaryButton("Terminar", { confirmFinish = false; restNotifier.cancel(); onFinish(session) }, Modifier.width(120.dp), compact = true) },
            dismissButton = { SecondaryButton("Seguir", { confirmFinish = false }, Modifier.width(100.dp), compact = true) }
        )
    }
}

private fun clockText(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val p = Palette.current
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(p.heroTile).padding(vertical = 9.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = if (accent) p.onHeroAccent else p.onHero, maxLines = 1)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = p.onHeroSecondary, maxLines = 1)
    }
}

/** Rayitas: 4 por fila, hasta 8; a partir de ahí un "+n" debajo. */
@Composable
private fun SetLines(done: Int) {
    val p = Palette.current
    val shown = minOf(done, WORKOUT_MAX_LINES)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var i = 0
        while (i < shown) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (j in 0 until 4) {
                    if (i + j < shown) Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.accent))
                    else Spacer(Modifier.weight(1f))
                }
            }
            i += 4
        }
        if (done > WORKOUT_MAX_LINES) {
            Text("+${done - WORKOUT_MAX_LINES}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = p.accentText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Indicador de desplazamiento horizontal: fino, redondeado y en la paleta. Compose no pinta barra de
 * scroll, y sin ella no se ve que la tira de ejercicios sigue hacia la derecha.
 */
@Composable
private fun HorizontalScrollIndicator(state: ScrollState) {
    val p = Palette.current
    if (state.maxValue <= 0 || state.maxValue == Int.MAX_VALUE) return
    BoxWithConstraints(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(p.track)) {
        val viewport = state.viewportSize.toFloat().coerceAtLeast(1f)
        val thumb = (viewport / (viewport + state.maxValue)).coerceIn(0.15f, 1f)
        val offset = (state.value.toFloat() / state.maxValue) * (1f - thumb)
        Box(Modifier.fillMaxHeight().fillMaxWidth(thumb).offset(x = maxWidth * offset).clip(RoundedCornerShape(50)).background(p.accent))
    }
}

/** Interruptor que ocupa el hueco que le toque y nunca salta de línea: el texto se recorta antes. */
@Composable
private fun FillToggle(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = Palette.current
    Box(
        modifier.clip(RoundedCornerShape(50)).background(if (on) p.hero else p.surface)
            .border(1.dp, if (on) p.hero else p.border, RoundedCornerShape(50))
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) p.onHero else p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StepButton(symbol: String, caption: String?, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = Palette.current
    Column(
        modifier.height(58.dp).clip(RoundedCornerShape(16.dp)).background(p.surfaceSunken).border(1.dp, p.border, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(symbol, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, color = p.textPrimary)
        caption?.let { Text(it, fontSize = 11.sp, color = p.textSecondary) }
    }
}

@Composable
private fun SquareChoice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = Palette.current
    Box(
        modifier.clip(RoundedCornerShape(11.dp)).background(if (selected) p.hero else p.surface)
            .border(1.dp, if (selected) p.hero else p.border, RoundedCornerShape(11.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selected) p.onHero else p.textSecondary)
    }
}

// ── Hojas ────────────────────────────────────────────────────────────────────

@Composable
private fun NumberPadSheet(
    title: String,
    current: String,
    buffer: String,
    unit: String,
    hint: String,
    allowDecimal: Boolean,
    onBuffer: (String) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val p = Palette.current
    WorkoutSheetFrame(onCancel, scrollable = false) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(buffer.ifBlank { current }, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp, color = p.textPrimary)
            Text(unit, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.accentText, modifier = Modifier.padding(start = 6.dp, bottom = 9.dp))
        }
        Text(hint, fontSize = 12.sp, color = p.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (allowDecimal) "," else "", "0", "⌫")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (k.isBlank()) Color.Transparent else p.surfaceSunken)
                                .clickable(enabled = k.isNotBlank()) {
                                    onBuffer(
                                        when (k) {
                                            "⌫" -> buffer.dropLast(1)
                                            "," -> if (buffer.contains(',') || buffer.isEmpty()) buffer else "$buffer,"
                                            else -> if (buffer.count { it.isDigit() } < 4) buffer + k else buffer
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) { Text(k, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.textPrimary) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cancelar", onCancel, Modifier.weight(1f))
            PrimaryButton("Hecho", onDone, Modifier.weight(1f))
        }
    }
}

/** "Cómo lo haces": el tipo y, si es barra libre, el peso de la barra. Si está unido, cambia en todos sus días. */
@Composable
private fun KindSheet(
    exercise: WorkoutExercise,
    linkedDays: List<String>,
    onDismiss: () -> Unit,
    onKind: (WorkoutKind) -> Unit,
    onBar: (Double) -> Unit
) {
    val p = Palette.current
    WorkoutSheetFrame(onDismiss) {
        Column {
            Text("Cómo lo haces", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = p.textPrimary)
            Text(
                if (linkedDays.isNotEmpty()) "Unido con ${linkedDays.joinToString(", ")}: el cambio se aplica en los dos."
                else "Unilateral y A cada lado están fuera, junto a este botón: valen para cualquier tipo.",
                fontSize = 12.sp, lineHeight = 17.sp, color = p.textSecondary
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            WorkoutKind.ALL.forEach { k ->
                val sel = k.id == exercise.kind
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (sel) SelectedTint else p.surface)
                        .border(if (sel) 1.5.dp else 1.dp, if (sel) p.accent else p.border, RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { onKind(k) }.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(k.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
                        Text(k.hint, fontSize = 11.sp, color = p.textSecondary)
                    }
                    Box(
                        Modifier.size(18.dp).clip(CircleShape)
                            .border(if (sel) 5.dp else 1.5.dp, if (sel) p.hero else Color(0xFFCDBBAD), CircleShape)
                    )
                }
            }
        }
        if (WorkoutKind.of(exercise.kind).usesBar) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                MiniTitle("Peso de la barra")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    WORKOUT_BARS.forEach { b -> ChoiceChip("${formatKg(b)} kg", b == exercise.barKg) { onBar(b) } }
                }
            }
        }
        PrimaryButton("Listo", onDismiss, Modifier.fillMaxWidth())
    }
}
