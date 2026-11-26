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
    val rir: Int? = null,
    /** A cada lado; null = serie anterior a la opción (se lee según el tipo, en el cliente). */
    val perSide: Boolean? = null,
    /** Unilateral; null = serie anterior a la opción. */
    val unilateral: Boolean? = null
)

@Serializable
data class WorkoutExercise(
    val id: String,
    val name: String,
    val kind: String = "barra",
    val barKg: Double = 20.0,
    val stepKg: Double = 2.5,
    /** Preferencia de entrada: el usuario teclea el total en vez del peso por lado o por mano. */
    val entryTotal: Boolean = false,
    /** Opción por defecto "Unilateral" (para cuando no hay serie anterior). */
    val unilateral: Boolean = false,
    /** Opción por defecto "A cada lado"; null = ejercicio antiguo. */
    val perSide: Boolean? = null,
    /** Grupo de unión entre días (Tarea 7). Vacío = no unido: su grupo es su propio id. */
    val linkId: String = "",
    /** Día en que se hizo la última sesión, para "Última vez · en …". */
    val lastFrom: String = "",
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
    val sets: List<WorkoutSet> = emptyList(),
    /** Copia de cómo se hizo aquel día, para que el volumen pasado no cambie si hoy se edita el plan. */
    val unilateral: Boolean = false
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
