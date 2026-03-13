package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.services.FoodService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.foodRoute(foodService: FoodService) {
    route("/food") {
        get("/search") {
            val query = call.request.queryParameters["q"]
            if (query.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Parámetro q requerido"))
                return@get
            }
            val results = foodService.searchFood(query)
            call.respond(results)
        }
    }
}