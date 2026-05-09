package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.services.FoodService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.foodRoute(foodService: FoodService) {
    route("/food") {

        // Búsqueda por texto
        get("/search") {
            val query = call.request.queryParameters["q"]
            if (query.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Parámetro q requerido"))
                return@get
            }
            call.respond(foodService.searchFood(query))
        }

        // Búsqueda por código de barras EAN-13
        get("/barcode/{code}") {
            val code = call.parameters["code"]
            if (code.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Código requerido"))
                return@get
            }
            val product = foodService.lookupBarcode(code)
            if (product != null) {
                call.respond(product)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Producto no encontrado"))
            }
        }
    }
}