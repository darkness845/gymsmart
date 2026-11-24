package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.WorkoutPlan
import com.gymsmart.gymsmart.model.WorkoutSession
import java.util.UUID
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

        // Historico de entrenamientos terminados (Tarea E). Aqui SI va una fila por sesion y no un
        // documento unico por usuario como el plan: crecen sin limite con el tiempo y el calendario
        // pide solo un mes, asi que conviene poder filtrar por fecha en la consulta en vez de
        // traerse anos enteros para descartarlos en el movil.
        turso.execute("""
            CREATE TABLE IF NOT EXISTS workout_sessions (
                id         TEXT PRIMARY KEY,
                user_id    TEXT NOT NULL,
                date       TEXT NOT NULL,
                data       TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """)
        runCatching {
            turso.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user_date ON workout_sessions (user_id, date)")
        }
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

    // ── Historico (Tarea E) ───────────────────────────────────────────────────

    /** Guarda un entrenamiento terminado y devuelve su id. */
    suspend fun saveSession(userId: String, session: WorkoutSession): String {
        val id = session.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        turso.execute(
            """INSERT INTO workout_sessions (id, user_id, date, data, created_at) VALUES (?, ?, ?, ?, ?)
               ON CONFLICT(id) DO UPDATE SET data = excluded.data, date = excluded.date""",
            listOf(id, userId, session.date, json.encodeToString(session.copy(id = id)),
                System.currentTimeMillis().toString())
        )
        return id
    }

    /**
     * Sesiones entre dos fechas ISO, ambas incluidas. El calendario pide un mes cada vez; comparar
     * cadenas ISO funciona como comparar fechas porque el formato es de ancho fijo y ordenable.
     */
    suspend fun getSessions(userId: String, from: String, to: String): List<WorkoutSession> {
        val result = turso.execute(
            """SELECT data FROM workout_sessions
               WHERE user_id = ? AND date >= ? AND date <= ?
               ORDER BY date ASC, created_at ASC""",
            listOf(userId, from, to)
        )
        return turso.extractRows(result).mapNotNull { row ->
            val raw = row.firstOrNull() ?: return@mapNotNull null
            runCatching { json.decodeFromString<WorkoutSession>(raw) }.getOrNull()
        }
    }

    suspend fun deleteSession(userId: String, id: String) {
        turso.execute("DELETE FROM workout_sessions WHERE id = ? AND user_id = ?", listOf(id, userId))
    }
}
