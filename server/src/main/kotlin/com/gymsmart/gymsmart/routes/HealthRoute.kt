package com.gymsmart.gymsmart.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoute() {
    get("/health") {
        call.respond(mapOf("status" to "ok", "app" to "GymSmart"))
    }
}