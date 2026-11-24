package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

/**
 * Espejo de `composeApp/.../model/WorkoutModels.kt` (solo la parte serializable).
 * El servidor no interpreta el contenido: lo guarda y lo devuelve tal cual, por usuario.
 */

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
    val kind: String = "barra",
    val barKg: Double = 20.0,
    val stepKg: Double = 2.5,
    val lastSession: List<WorkoutSet> = emptyList()
)

@Serializable
data class WorkoutDay(
    val id: String,
    val name: String,
    val exercises: List<WorkoutExercise> = emptyList()
)

@Serializable
data class ActiveSession(
    val dayId: String,
    val startedAt: Long,
    val sets: Map<String, List<WorkoutSet>> = emptyMap()
)

@Serializable
data class WorkoutPlan(
    val days: List<WorkoutDay> = emptyList(),
    val session: ActiveSession? = null
)

// ── Histórico de entrenamientos (Tarea E) ────────────────────────────────────

/**
 * Copia de un ejercicio tal y como se hizo ese día. Se guarda el nombre y el tipo dentro del
 * registro a propósito: si el plan cambia después, el histórico tiene que seguir leyéndose igual.
 */
@Serializable
data class SessionExercise(
    val name: String,
    val kind: String = "barra",
    val barKg: Double = 20.0,
    val sets: List<WorkoutSet> = emptyList()
)

/** Un entrenamiento terminado. [date] en ISO (`2026-11-24`), que es como se consulta y se agrupa. */
@Serializable
data class WorkoutSession(
    val id: String = "",
    val date: String,
    val dayName: String,
    val startedAt: Long = 0L,
    val exercises: List<SessionExercise> = emptyList()
)
