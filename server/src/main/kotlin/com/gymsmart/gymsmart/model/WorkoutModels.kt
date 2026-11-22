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
