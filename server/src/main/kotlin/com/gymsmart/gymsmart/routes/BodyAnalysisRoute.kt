package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.BodyAnalysisRequest
import com.gymsmart.gymsmart.model.UserSession
import com.gymsmart.gymsmart.services.OpenAIService
import com.gymsmart.gymsmart.services.ProfileService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("BodyAnalysisRoute")

fun Route.bodyAnalysisRoute(profileService: ProfileService) {

    val openAIService = OpenAIService()

    post("/ai/body-analysis") {

        val session = call.sessions.get<UserSession>()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "No hay sesión")

        // SEC-1 (doc/08-backlog-mejoras.md): antes cualquier usuario logueado podía llamar a esta ruta
        // sin límite, consumiendo créditos de OpenAI gratis. El único control era de cliente
        // (WeightScreen.kt), que no protege nada: una petición directa a la API lo salta sin problema.
        if (!profileService.isPremium(session.userId)) {
            return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere suscripción premium"))
        }

        try {

            val request = call.receive<BodyAnalysisRequest>()

            val result = openAIService.analyzeBody(request.imageBase64)

            call.respond(result)

        } catch (e: Exception) {

            logger.error("Error en /ai/body-analysis", e)

            call.respond(
                mapOf(
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }
}