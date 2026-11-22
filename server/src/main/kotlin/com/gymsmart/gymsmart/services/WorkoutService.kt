package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.WorkoutPlan
import kotlinx.serialization.json.Json

/**
 * Días de entrenamiento y sesión en curso, un documento JSON por usuario.
 *
 * Se guarda entero en una fila en vez de normalizarlo en tablas (días / ejercicios / series) porque
 * siempre se lee y se escribe completo desde la app, y el servidor no consulta nada por dentro.
 * Si en el futuro hiciera falta estadística por ejercicio en el servidor, habrá que normalizarlo:
 * anotado en `doc/05-deuda-tecnica.md`.
 */
class WorkoutService(private val turso: TursoService) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun initTable() {
        turso.execute("""
            CREATE TABLE IF NOT EXISTS workout_plans (
                user_id    TEXT PRIMARY KEY,
                data       TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """)
    }

    /** Un usuario sin plan todavía devuelve uno vacío, no un error: es su primera vez. */
    suspend fun getPlan(userId: String): WorkoutPlan {
        val result = turso.execute(
            "SELECT data FROM workout_plans WHERE user_id = ?",
            listOf(userId)
        )
        val raw = turso.extractRows(result).firstOrNull()?.firstOrNull() ?: return WorkoutPlan()
        return runCatching { json.decodeFromString<WorkoutPlan>(raw) }.getOrElse { WorkoutPlan() }
    }

    suspend fun savePlan(userId: String, plan: WorkoutPlan) {
        turso.execute(
            """INSERT INTO workout_plans (user_id, data, updated_at) VALUES (?, ?, ?)
               ON CONFLICT(user_id) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at""",
            listOf(userId, json.encodeToString(plan), System.currentTimeMillis().toString())
        )
    }
}
