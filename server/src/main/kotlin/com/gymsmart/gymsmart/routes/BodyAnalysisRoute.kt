package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.BodyAnalysisRequest
import com.gymsmart.gymsmart.services.OpenAIService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.bodyAnalysisRoute() {

    val openAIService = OpenAIService()

    post("/ai/body-analysis") {

        try {

            val request = call.receive<BodyAnalysisRequest>()

            val result = openAIService.analyzeBody(request.imageBase64)

            call.respond(result)

        } catch (e: Exception) {

            e.printStackTrace()

            call.respond(
                mapOf(
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }
}