package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.UserSession
import com.gymsmart.gymsmart.model.WorkoutPlan
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
