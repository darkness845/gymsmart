package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.BodyAnalysisRequest
import com.gymsmart.gymsmart.model.UserSession
import com.gymsmart.gymsmart.services.OpenAIService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("BodyAnalysisRoute")

fun Route.bodyAnalysisRoute() {

    val openAIService = OpenAIService()

    post("/ai/body-analysis") {

        call.sessions.get<UserSession>()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "No hay sesión")

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