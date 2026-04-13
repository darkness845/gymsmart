package com.gymsmart.gymsmart.plugins

import com.gymsmart.gymsmart.routes.foodRoute
import com.gymsmart.gymsmart.routes.gpsRoute
import com.gymsmart.gymsmart.routes.healthRoute
import com.gymsmart.gymsmart.services.FoodService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val foodService = FoodService()

    routing {
        get("/") {
            call.respondText("GymSmart API running 🏋️")
        }
        healthRoute()
        foodRoute(foodService)
        gpsRoute()
    }
}