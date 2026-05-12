package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.FavoriteRequest
import com.gymsmart.gymsmart.model.MealEntry
import com.gymsmart.gymsmart.model.MealEntryRequest
import com.gymsmart.gymsmart.model.MealEntryWithTarget
import com.gymsmart.gymsmart.model.UserSession
import com.gymsmart.gymsmart.services.NutritionService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun Route.nutritionRoutes(nutritionService: NutritionService) {

    post("/nutrition/add") {
        val session = call.sessions.get<UserSession>()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "No hay sesión")
        val req = call.receive<MealEntryRequest>()
        nutritionService.addMeal(session.userId, req)
        call.respond(HttpStatusCode.Created, "Guardado en Turso")
    }

    get("/nutrition/mine") {
        val session = call.sessions.get<UserSession>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val date = call.request.queryParameters["date"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Falta date")
        val rows = nutritionService.getUserMeals(session.userId, date)
        val response = rows.map { row ->
            val entry = MealEntry(
                id = row[7] ?: "", name = row[0] ?: "",
                grams = row[1]?.toDouble() ?: 0.0,
                kcalPer100 = row[2]?.toDouble() ?: 0.0,
                proteinsPer100 = row[3]?.toDouble() ?: 0.0,
                carbsPer100 = row[4]?.toDouble() ?: 0.0,
                fatPer100 = row[5]?.toDouble() ?: 0.0
            )
            MealEntryWithTarget(entry, row[6] ?: "DESAYUNO")
        }
        call.respond(response)
    }

    delete("/nutrition/delete/{id}") {
        call.sessions.get<UserSession>()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val id = call.parameters["id"]
            ?: return@delete call.respond(HttpStatusCode.BadRequest)
        nutritionService.deleteMeal(id)
        call.respond(HttpStatusCode.OK, "Eliminado")
    }

    put("/nutrition/update/{id}") {
        call.sessions.get<UserSession>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        val id = call.parameters["id"]
            ?: return@put call.respond(HttpStatusCode.BadRequest)
        val req = call.receive<MealEntryRequest>()
        nutritionService.updateMeal(id, req)
        call.respond(HttpStatusCode.OK, "Actualizado")
    }

    // ── Historial ─────────────────────────────────────────────────────────────
    get("/nutrition/history") {
        val session = call.sessions.get<UserSession>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val rows = nutritionService.getFoodHistory(session.userId)
        val response = rows.map { row ->
            mapOf(
                "name"           to (row[0] ?: ""),
                "kcalPer100"     to (row[1] ?: "0"),
                "proteinsPer100" to (row[2] ?: "0"),
                "carbsPer100"    to (row[3] ?: "0"),
                "fatPer100"      to (row[4] ?: "0")
            )
        }
        call.respond(response)
    }

    // ── Favoritos ─────────────────────────────────────────────────────────────
    get("/nutrition/favorites") {
        val session = call.sessions.get<UserSession>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val rows = nutritionService.getFavorites(session.userId)
        val response = rows.map { row ->
            mapOf(
                "name"           to (row[0] ?: ""),
                "kcalPer100"     to (row[1] ?: "0"),
                "proteinsPer100" to (row[2] ?: "0"),
                "carbsPer100"    to (row[3] ?: "0"),
                "fatPer100"      to (row[4] ?: "0")
            )
        }
        call.respond(response)
    }

    post("/nutrition/favorites/add") {
        val session = call.sessions.get<UserSession>()
            ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val req = call.receive<FavoriteRequest>()

        if (nutritionService.isFavorite(session.userId, req.name)) {
            return@post call.respond(HttpStatusCode.OK, "Ya existe en favoritos")
        }

        nutritionService.addFavorite(
            session.userId, req.name, req.kcalPer100,
            req.proteinsPer100, req.carbsPer100, req.fatPer100
        )
        call.respond(HttpStatusCode.Created, "Añadido a favoritos")
    }

    delete("/nutrition/favorites/remove/{name}") {
        val session = call.sessions.get<UserSession>()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val name = call.parameters["name"]
            ?: return@delete call.respond(HttpStatusCode.BadRequest)
        nutritionService.removeFavorite(session.userId, name)
        call.respond(HttpStatusCode.OK, "Eliminado de favoritos")
    }
}