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
            // Tres desenlaces distintos y tres respuestas distintas: antes todo lo que no fuera un
            // producto válido se contestaba con el mismo 404 "Producto no encontrado", aunque el
            // producto sí estuviera en Open Food Facts y lo que faltara fueran sus datos.
            when (val resultado = foodService.lookupBarcode(code)) {
                is FoodService.BarcodeLookup.Found -> call.respond(resultado.product)
                FoodService.BarcodeLookup.Unusable -> call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    mapOf("error" to "El producto está en Open Food Facts pero sus datos nutricionales no son utilizables")
                )
                FoodService.BarcodeLookup.UpstreamFailed -> call.respond(
                    HttpStatusCode.BadGateway,
                    mapOf("error" to "Open Food Facts no está disponible ahora mismo")
                )
                FoodService.BarcodeLookup.NotFound -> call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Producto no encontrado")
                )
            }
        }
    }
}