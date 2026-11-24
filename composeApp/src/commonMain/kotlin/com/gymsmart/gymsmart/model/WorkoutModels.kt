package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.round

/**
 * Entrenamiento: días definidos por el usuario y registro de series.
 *
 * La idea de diseño que manda sobre todo lo demás (ver `doc/07-registro-sesiones.md`): el número que
 * la app pide es **el que el usuario mira en el gimnasio**, no el peso total. En barra libre eso es
 * lo que pone a cada lado; en mancuernas, lo que marca una; en máquina y polea, el número de la placa.
 * El total se calcula a partir de ahí ([WorkoutKind.totalKg]) y se muestra como dato secundario.
 */

/** Una serie registrada. [value] está siempre en la unidad del tipo de ejercicio, no en kg totales. */
@Serializable
data class WorkoutSet(
    val value: Double,
    val reps: Int,
    val rir: Int? = null
)

@Serializable
data class WorkoutExercise(
    val id: String,
    val name: String,
    /** Id de [WorkoutKind]. Se decide al crear el ejercicio, no en cada serie. */
    val kind: String = WorkoutKind.BARRA,
    /** Peso de la barra. Solo se usa cuando [kind] es [WorkoutKind.BARRA]. */
    val barKg: Double = 20.0,
    /** Cuánto mueve cada toque de − / +. Lo elige el usuario: cada máquina salta distinto. */
    val stepKg: Double = 2.5,
    /** Las series de la última sesión. De aquí sale el peso que la app propone sola, serie a serie. */
    val lastSession: List<WorkoutSet> = emptyList()
)

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

/** Tipo de ejercicio: decide qué número se pide, en qué unidad y cómo se calcula el peso total. */
data class WorkoutKind(
    val id: String,
    val emoji: String,
    val label: String,
    /** Qué se apunta, en una línea, para la pantalla donde se elige el tipo. */
    val hint: String,
    val unit: String,
    /** Rótulo del número grande. */
    val bigLabel: String,
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
            WorkoutKind(BARRA, "🏋", "Barra libre", "apuntas lo de cada lado",
                "kg/lado", "Lo que pones a cada lado", 2.5, true),
            WorkoutKind(MANCUERNAS, "💪", "Mancuernas", "apuntas una mancuerna",
                "kg/mano", "Peso de cada mancuerna", 1.0, false),
            WorkoutKind(MAQUINA, "⚙", "Máquina", "el número de la placa",
                "kg", "Peso de la máquina", 2.5, false),
            WorkoutKind(POLEA, "🔗", "Polea", "el número de la placa",
                "kg", "Peso de la polea", 2.5, false),
            WorkoutKind(CORPORAL, "🧍", "Peso corporal", "sin peso, o con lastre",
                "kg lastre", "Lastre añadido", 1.25, false)
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

/** Máximo de rayitas que se pintan por ejercicio; el resto se resume con un "+n". */
const val WORKOUT_MAX_LINES = 8

/** Peso total realmente levantado a partir del número que apunta el usuario. */
fun WorkoutExercise.totalKg(value: Double): Double = when (kind) {
    WorkoutKind.BARRA -> round2(barKg + 2 * value)
    WorkoutKind.MANCUERNAS -> round2(2 * value)
    else -> round2(value)
}

/** El camino inverso, para quien prefiera escribir el total en el teclado. */
fun WorkoutExercise.valueFromTotal(total: Double): Double = when (kind) {
    WorkoutKind.BARRA -> maxOf(0.0, round2((total - barKg) / 2))
    WorkoutKind.MANCUERNAS -> maxOf(0.0, round2(total / 2))
    else -> maxOf(0.0, round2(total))
}

fun round2(v: Double): Double = round(v * 100) / 100

/** Formato español: 62,5 — sin ceros de más. */
fun formatKg(v: Double): String {
    val r = round2(v)
    val s = if (abs(r - r.toLong()) < 0.0001) r.toLong().toString()
    else r.toString().trimEnd('0').trimEnd('.')
    return s.replace('.', ',')
}

/** Cómo se lee una serie guardada, en la unidad de su ejercicio. */
fun WorkoutExercise.setLabel(set: WorkoutSet): String = when (kind) {
    WorkoutKind.BARRA -> "${formatKg(set.value)} por lado · ${set.reps} reps"
    WorkoutKind.MANCUERNAS -> "${formatKg(set.value)} kg × 2 · ${set.reps} reps"
    WorkoutKind.CORPORAL ->
        (if (set.value > 0) "+${formatKg(set.value)} kg" else "Peso corporal") + " · ${set.reps} reps"
    else -> "${formatKg(set.value)} kg · ${set.reps} reps"
}

fun rirLabel(rir: Int?): String = if (rir == null) "—" else "$rir" + if (rir >= 4) "+" else ""

/** Volumen (kg movidos) de una lista de series de ese ejercicio. */
fun WorkoutExercise.volume(sets: List<WorkoutSet>): Double =
    sets.sumOf { totalKg(it.value) * it.reps }

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
    val sets: List<WorkoutSet> = emptyList()
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

    /**
     * Volumen total en kg movidos (peso real × repeticiones, sumado). Es la cifra que resume una
     * sesión en una sola línea y la que permite comparar un día con otro.
     */
    val volumeKg: Double
        get() = exercises.sumOf { ex ->
            ex.sets.sumOf { s -> sessionTotalKg(ex.kind, s.value, ex.barKg) * s.reps }
        }
}

/**
 * Mismo calculo que [totalKg], pero a partir del tipo guardado en el historico en vez de un
 * [WorkoutExercise] vivo: una sesion pasada no depende de que el ejercicio siga existiendo.
 */
fun sessionTotalKg(kind: String, value: Double, barKg: Double): Double = when (kind) {
    WorkoutKind.BARRA -> round2(barKg + 2 * value)
    WorkoutKind.MANCUERNAS -> round2(2 * value)
    else -> round2(value)
}
