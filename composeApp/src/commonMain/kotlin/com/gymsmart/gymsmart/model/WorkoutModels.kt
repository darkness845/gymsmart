package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.round

/**
 * Entrenamiento: días definidos por el usuario y registro de series.
 *
 * La idea de diseño que manda (ver `doc/07-registro-sesiones.md`): el número que la app pide es **el
 * que el usuario mira en el gimnasio**. El total se calcula a partir de ahí y se enseña aparte.
 *
 * ## Las dos opciones de cada serie (Tarea 7, diseño aprobado en el simulador)
 *
 * - **A cada lado** ([WorkoutSet.perSide]): el número apuntado es el de **un lado** y el total es el
 *   doble, más la barra si es barra libre. Apagado, el número apuntado **es** el total. Vale para
 *   cualquier tipo de ejercicio.
 * - **Unilateral** ([WorkoutSet.unilateral]): las repeticiones apuntadas son las de **un lado** y el
 *   volumen cuenta los dos, porque el trabajo se hizo dos veces.
 *
 * Se guardan **en cada serie**, no en el ejercicio: así la próxima vez la app propone la serie con la
 * misma configuración con la que se hizo, y se ve cómo se apuntó. El ejercicio solo guarda las
 * opciones **por defecto** ([WorkoutExercise.perSide], [WorkoutExercise.unilateral]), que mandan cuando
 * todavía no hay serie anterior.
 *
 * ## Ejercicios unidos entre días (Tarea 7)
 *
 * El mismo ejercicio puede estar en varios días ("Extension triceps" en Pecho · Hombro · Tríceps y en
 * Tríceps · Hombro · Bíceps). Están **unidos** si comparten [WorkoutExercise.groupId]. La identidad se
 * **declara** al añadirlo ("unir con…"), nunca se deduce del nombre. Unidos comparten tipo, barra,
 * salto, opciones por defecto y la última sesión; el **nombre es de cada día**.
 */

/** Barra y mancuernas siempre se apuntaron por lado / por mano antes de que existiera "A cada lado". */
fun legacyPerSide(kind: String): Boolean = kind == WorkoutKind.BARRA || kind == WorkoutKind.MANCUERNAS

/** Una serie registrada. [value] es el número apuntado; su significado lo dan [perSide] y el tipo. */
@Serializable
data class WorkoutSet(
    val value: Double,
    val reps: Int,
    val rir: Int? = null,
    /**
     * A cada lado. `null` = serie guardada antes de que existiera la opción: se lee como se apuntaba
     * entonces ([legacyPerSide]), así ningún dato antiguo cambia de significado.
     */
    val perSide: Boolean? = null,
    /** Unilateral. `null` = serie antigua → no unilateral. */
    val unilateral: Boolean? = null
)

fun WorkoutSet.perSideFor(kind: String): Boolean = perSide ?: legacyPerSide(kind)
val WorkoutSet.isUnilateral: Boolean get() = unilateral ?: false

@Serializable
data class WorkoutExercise(
    val id: String,
    /** Nombre en ESTE día. Si está unido, en otro día puede llamarse distinto: es una etiqueta. */
    val name: String,
    /** Id de [WorkoutKind]. */
    val kind: String = WorkoutKind.BARRA,
    /** Peso de la barra. Solo se usa cuando [kind] es [WorkoutKind.BARRA]. */
    val barKg: Double = 20.0,
    /** Cuánto mueve cada toque de − / +. Lo elige el usuario: cada máquina salta distinto. */
    val stepKg: Double = 2.5,
    /** Obsoleto (conmutador de la tarea 6, sustituido por "A cada lado"). Se conserva para leer datos viejos. */
    val entryTotal: Boolean = false,
    /** Opción por defecto "Unilateral", para cuando no hay serie anterior. */
    val unilateral: Boolean = false,
    /** Opción por defecto "A cada lado". `null` = ejercicio antiguo → [legacyPerSide]. */
    val perSide: Boolean? = null,
    /**
     * Grupo de unión. Vacío = el ejercicio no se ha unido a nada y su grupo es su propio [id]; al unir
     * otro a él, el nuevo recibe este id como [linkId]. Ver [groupId].
     */
    val linkId: String = "",
    /** Nombre del día en que se hizo [lastSession], para decir "Última vez · en …" si fue en otro. */
    val lastFrom: String = "",
    /** Las series de la última sesión. De aquí sale el peso que la app propone sola, serie a serie. */
    val lastSession: List<WorkoutSet> = emptyList()
)

/** Identidad compartida por todos los días donde está este ejercicio. */
val WorkoutExercise.groupId: String get() = linkId.ifBlank { id }
val WorkoutExercise.defaultPerSide: Boolean get() = perSide ?: legacyPerSide(kind)

@Serializable
data class WorkoutDay(
    val id: String,
    val name: String,
    val exercises: List<WorkoutExercise> = emptyList()
)

/**
 * Entrenamiento en curso. Se guarda igual que el resto para que cerrar la app a mitad de sesión
 * (o quedarse sin batería en el gimnasio) no borre las series ya apuntadas.
 */
@Serializable
data class ActiveSession(
    val dayId: String,
    val startedAt: Long,
    /** Series de hoy por ejercicio, indexadas por `WorkoutExercise.id`. */
    val sets: Map<String, List<WorkoutSet>> = emptyMap()
)

@Serializable
data class WorkoutPlan(
    val days: List<WorkoutDay> = emptyList(),
    val session: ActiveSession? = null
)

/** Tipo de ejercicio: qué se mide y cómo se llama el número grande. */
data class WorkoutKind(
    val id: String,
    val label: String,
    /** Una línea bajo el nombre, en la hoja de elegir tipo. */
    val hint: String,
    /** Rótulo del número grande cuando NO es "a cada lado". */
    val bigLabel: String,
    /** Final de la frase "Ponlo igual que …" bajo los saltos. */
    val stepHelp: String,
    val defaultStep: Double,
    val usesBar: Boolean
) {
    companion object {
        const val BARRA = "barra"
        const val MANCUERNAS = "mancuernas"
        const val MAQUINA = "maquina"
        const val POLEA = "polea"
        const val CORPORAL = "corporal"

        val ALL = listOf(
            WorkoutKind(BARRA, "Barra libre", "discos y barra", "PESO TOTAL CON BARRA", "los saltos de tus discos", 2.5, true),
            WorkoutKind(MANCUERNAS, "Mancuernas", "una o dos mancuernas", "PESO DE LAS DOS MANCUERNAS", "los saltos de tus mancuernas", 1.0, false),
            WorkoutKind(MAQUINA, "Máquina", "el número de la placa", "PESO DE LA MÁQUINA", "los saltos de esta máquina", 2.5, false),
            WorkoutKind(POLEA, "Polea", "el número de la placa", "PESO DE LA POLEA", "los saltos de esta polea", 2.5, false),
            WorkoutKind(CORPORAL, "Peso corporal", "sin peso, o con lastre", "LASTRE AÑADIDO", "los saltos de tu lastre", 1.25, false)
        )

        fun of(id: String): WorkoutKind = ALL.firstOrNull { it.id == id } ?: ALL.first()
    }
}

/** Saltos del − / + que puede elegir el usuario para un ejercicio. */
val WORKOUT_STEPS = listOf(0.25, 0.5, 1.0, 1.25, 2.5, 5.0)

/** Repeticiones de acceso rápido. */
val WORKOUT_QUICK_REPS = listOf(5, 6, 8, 10, 12, 15)

/** RIR seleccionables; `null` = no lo apunto. */
val WORKOUT_RIRS: List<Int?> = listOf(null, 0, 1, 2, 3, 4)

/** Máximo de rayitas que se pintan por ejercicio en la tira. */
const val WORKOUT_MAX_LINES = 8

/** Pesos de barra que se pueden elegir. */
val WORKOUT_BARS = listOf(20.0, 15.0, 10.0, 7.5)

// ── Peso total y volumen ─────────────────────────────────────────────────────

/** Peso total realmente movido a partir del número apuntado y de cómo se apuntó. */
fun totalKgOf(kind: String, barKg: Double, value: Double, perSide: Boolean): Double = when {
    kind == WorkoutKind.CORPORAL -> round2(value)
    perSide && kind == WorkoutKind.BARRA -> round2(barKg + 2 * value)
    perSide -> round2(2 * value)
    else -> round2(value)
}

/** El camino inverso: qué número hay que apuntar para mover [total] con esa configuración. */
fun valueForTotal(kind: String, barKg: Double, total: Double, perSide: Boolean): Double = when {
    kind == WorkoutKind.CORPORAL || !perSide -> maxOf(0.0, round2(total))
    kind == WorkoutKind.BARRA -> maxOf(0.0, round2((total - barKg) / 2))
    else -> maxOf(0.0, round2(total / 2))
}

fun WorkoutExercise.totalKg(set: WorkoutSet): Double = totalKgOf(kind, barKg, set.value, set.perSideFor(kind))

/** Volumen (kg movidos). En unilateral cuenta los dos lados: las reps apuntadas son de uno. */
fun WorkoutExercise.volume(sets: List<WorkoutSet>): Double =
    sets.sumOf { totalKg(it) * it.reps * (if (it.isUnilateral) 2 else 1) }

/** Unidad del número grande. */
fun unitFor(kind: String, perSide: Boolean): String = when {
    kind == WorkoutKind.CORPORAL -> "kg lastre"
    perSide && kind == WorkoutKind.MANCUERNAS -> "kg/mano"
    perSide -> "kg/lado"
    else -> "kg"
}

/** Rótulo encima del número grande. */
fun bigLabelFor(kind: String, perSide: Boolean): String = when {
    kind == WorkoutKind.CORPORAL -> "LASTRE AÑADIDO"
    perSide && kind == WorkoutKind.BARRA -> "LO QUE PONES A CADA LADO"
    perSide && kind == WorkoutKind.MANCUERNAS -> "PESO DE CADA MANCUERNA"
    perSide -> "PESO A CADA LADO"
    else -> WorkoutKind.of(kind).bigLabel
}

/** "37,5 kg a cada lado · 11 reps · unilateral". */
fun WorkoutExercise.setLabel(set: WorkoutSet): String = setLabelOf(kind, set)

fun setLabelOf(kind: String, set: WorkoutSet): String {
    val weight = if (kind == WorkoutKind.CORPORAL) {
        if (set.value > 0) "+${formatKg(set.value)} kg" else "Peso corporal"
    } else {
        "${formatKg(set.value)} kg" + if (set.perSideFor(kind)) " a cada lado" else ""
    }
    return "$weight · ${set.reps} reps" + if (set.isUnilateral) " · unilateral" else ""
}

fun round2(v: Double): Double = round(v * 100) / 100

/** Formato español: 62,5 — sin ceros de más. */
fun formatKg(v: Double): String {
    val r = round2(v)
    val s = if (abs(r - r.toLong()) < 0.0001) r.toLong().toString()
    else r.toString().trimEnd('0').trimEnd('.')
    return s.replace('.', ',')
}

fun rirLabel(rir: Int?): String = if (rir == null) "—" else "$rir" + if (rir >= 4) "+" else ""

/**
 * Nombre normalizado para detectar que se está escribiendo un ejercicio que ya existe en otro día:
 * sin mayúsculas, sin tildes y sin espacios de más. Nada de sinónimos — si coincide se pregunta, si no
 * coincide no se pregunta (decisión del usuario).
 */
fun normalizeExerciseName(name: String): String {
    val sb = StringBuilder()
    for (raw in name.lowercase()) {
        val c = when (raw) {
            'á', 'à', 'ä', 'â' -> 'a'; 'é', 'è', 'ë', 'ê' -> 'e'; 'í', 'ì', 'ï', 'î' -> 'i'
            'ó', 'ò', 'ö', 'ô' -> 'o'; 'ú', 'ù', 'ü', 'û' -> 'u'; 'ñ' -> 'n'
            else -> raw
        }
        if (c.isWhitespace()) { if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ') } else sb.append(c)
    }
    return sb.toString().trim()
}

// ── Histórico de entrenamientos (Tarea E) ────────────────────────────────────

/**
 * Un ejercicio tal y como se hizo un día concreto.
 *
 * Es una **copia**, no una referencia al ejercicio del plan: guarda su nombre y su tipo dentro del
 * registro. Si mañana se renombra el ejercicio, se le cambia el tipo o se borra del plan, lo que
 * pasó aquel día tiene que seguir leyéndose igual. Un histórico que cambia al editar el plan no es
 * un histórico.
 */
@Serializable
data class SessionExercise(
    val name: String,
    val kind: String = WorkoutKind.BARRA,
    val barKg: Double = 20.0,
    val sets: List<WorkoutSet> = emptyList(),
    /** Obsoleto: antes la opción iba en el ejercicio; ahora va en cada serie. Para sesiones viejas. */
    val unilateral: Boolean = false
)

/**
 * Un entrenamiento terminado. [date] en formato ISO (`2026-11-24`), que es como se agrupa en el
 * calendario y como lo consulta el servidor.
 */
@Serializable
data class WorkoutSession(
    val id: String = "",
    val date: String,
    val dayName: String,
    val startedAt: Long = 0L,
    val exercises: List<SessionExercise> = emptyList()
) {
    val totalSets: Int get() = exercises.sumOf { it.sets.size }
    val totalReps: Int get() = exercises.sumOf { ex -> ex.sets.sumOf { it.reps } }

    /** Volumen total en kg movidos (peso real × repeticiones, sumado). */
    val volumeKg: Double
        get() = exercises.sumOf { ex ->
            ex.sets.sumOf { s ->
                val lados = if (s.unilateral ?: ex.unilateral) 2 else 1
                sessionTotalKg(ex.kind, s.value, ex.barKg, s.perSideFor(ex.kind)) * s.reps * lados
            }
        }
}

/**
 * Mismo cálculo que [totalKgOf], a partir del tipo guardado en el histórico. Sin [perSide] se usa la
 * lectura antigua del tipo, que es la que tenían las sesiones guardadas antes de la opción.
 */
fun sessionTotalKg(kind: String, value: Double, barKg: Double, perSide: Boolean = legacyPerSide(kind)): Double =
    totalKgOf(kind, barKg, value, perSide)
