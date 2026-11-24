package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.UserSession
import com.gymsmart.gymsmart.model.WorkoutPlan
import com.gymsmart.gymsmart.model.WorkoutSession
import com.gymsmart.gymsmart.services.WorkoutService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WorkoutRoute")

/** Límites de cordura: esto es de uso personal, pero el cuerpo lo manda el cliente. */
private const val MAX_DAYS = 30
private const val MAX_EXERCISES_PER_DAY = 40
private const val MAX_SETS_PER_EXERCISE = 60
private const val MAX_NAME_LENGTH = 60

fun Route.workoutRoutes(workoutService: WorkoutService) {
    route("/workouts") {

        get {
            val session = call.sessions.get<UserSession>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "No autenticado")
            call.respond(workoutService.getPlan(session.userId))
        }

        put {
            val session = call.sessions.get<UserSession>()
                ?: return@put call.respond(HttpStatusCode.Unauthorized, "No autenticado")

            val plan = try {
                call.receive<WorkoutPlan>()
            } catch (e: Exception) {
                logger.warn("Plan de entrenamiento mal formado de ${session.userId}: ${e.message}")
                return@put call.respond(HttpStatusCode.BadRequest, "Plan mal formado")
            }

            validate(plan)?.let { return@put call.respond(HttpStatusCode.BadRequest, it) }

            workoutService.savePlan(session.userId, plan)
            call.respond(HttpStatusCode.OK)
        }

        // ── Historico de entrenamientos (Tarea E) ─────────────────────────────

        /** Sesiones de un rango de fechas ISO. El calendario pide un mes cada vez. */
        get("/sessions") {
            val session = call.sessions.get<UserSession>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "No autenticado")
            val from = call.request.queryParameters["from"]
            val to = call.request.queryParameters["to"]
            if (from.isNullOrBlank() || to.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.BadRequest, "Faltan from y to")
            }
            if (!isIsoDate(from) || !isIsoDate(to)) {
                return@get call.respond(HttpStatusCode.BadRequest, "Fechas invalidas")
            }
            call.respond(workoutService.getSessions(session.userId, from, to))
        }

        post("/sessions") {
            val user = call.sessions.get<UserSession>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "No autenticado")

            val body = try {
                call.receive<WorkoutSession>()
            } catch (e: Exception) {
                logger.warn("Sesion mal formada de ${user.userId}: ${e.message}")
                return@post call.respond(HttpStatusCode.BadRequest, "Sesion mal formada")
            }

            validateSession(body)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }

            val id = workoutService.saveSession(user.userId, body)
            call.respond(HttpStatusCode.Created, mapOf("id" to id))
        }

        delete("/sessions/{id}") {
            val user = call.sessions.get<UserSession>()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, "No autenticado")
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Falta el id")
            workoutService.deleteSession(user.userId, id)
            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun validate(plan: WorkoutPlan): String? {
    if (plan.days.size > MAX_DAYS) return "Demasiados días (máximo $MAX_DAYS)"
    plan.days.forEach { day ->
        if (day.name.isBlank()) return "Hay un día sin nombre"
        if (day.name.length > MAX_NAME_LENGTH) return "Nombre de día demasiado largo"
        if (day.exercises.size > MAX_EXERCISES_PER_DAY)
            return "Demasiados ejercicios en '${day.name}'"
        day.exercises.forEach { ex ->
            if (ex.name.isBlank()) return "Hay un ejercicio sin nombre"
            if (ex.name.length > MAX_NAME_LENGTH) return "Nombre de ejercicio demasiado largo"
            if (ex.lastSession.size > MAX_SETS_PER_EXERCISE)
                return "Demasiadas series en '${ex.name}'"
        }
    }
    plan.session?.sets?.values?.forEach { sets ->
        if (sets.size > MAX_SETS_PER_EXERCISE) return "Demasiadas series en una sesión"
    }
    return null
}


/** `2026-11-24`. Se comprueba porque la fecha va directa a la consulta como texto. */
private fun isIsoDate(value: String): Boolean =
    value.length == 10 && value[4] == '-' && value[7] == '-' &&
        value.filterIndexed { i, _ -> i != 4 && i != 7 }.all { it.isDigit() }

/** Mismos limites de cordura que el plan: el cuerpo lo manda el cliente. */
private fun validateSession(s: WorkoutSession): String? {
    if (!isIsoDate(s.date)) return "Fecha invalida"
    if (s.dayName.length > MAX_NAME_LENGTH) return "Nombre del dia demasiado largo"
    if (s.exercises.size > MAX_EXERCISES_PER_DAY) return "Demasiados ejercicios"
    for (ex in s.exercises) {
        if (ex.name.isBlank()) return "Hay un ejercicio sin nombre"
        if (ex.name.length > MAX_NAME_LENGTH) return "Nombre de ejercicio demasiado largo"
        if (ex.sets.size > MAX_SETS_PER_EXERCISE) return "Demasiadas series en '${ex.name}'"
        for (set in ex.sets) {
            if (set.value < 0 || set.value > 1000) return "Peso fuera de rango en '${ex.name}'"
            if (set.reps < 0 || set.reps > 1000) return "Repeticiones fuera de rango en '${ex.name}'"
        }
    }
    return null
}
