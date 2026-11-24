package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.FavoriteRequest
import com.gymsmart.gymsmart.nutrition.cookingYieldById
import com.gymsmart.gymsmart.model.FoodCollections
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

/** Límites de cordura para las colecciones: esto es de uso personal, pero el cuerpo lo manda el cliente. */
private const val MAX_COLLECTIONS = 40
private const val MAX_ITEMS_PER_COLLECTION = 30
private const val MAX_COLLECTION_NAME_LENGTH = 40
private const val MAX_ITEM_GRAMS = 5000.0

fun Route.nutritionRoutes(nutritionService: NutritionService) {

    post("/nutrition/add") {
        val session = call.sessions.get<UserSession>()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "No hay sesión")
        val req = call.receive<MealEntryRequest>()
        if (!isValidBasis(req)) {
            return@post call.respond(HttpStatusCode.BadRequest, "Base de pesaje inválida")
        }
        val id = nutritionService.addMeal(session.userId, req)
        call.respond(HttpStatusCode.Created, mapOf("id" to id))
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
                fatPer100 = row[5]?.toDouble() ?: 0.0,
                // Las columnas guardan cadena vacía cuando no hay base (ver NutritionService.addMeal),
                // y son NULL en las comidas registradas antes de que existieran.
                basis = row.getOrNull(8)?.takeIf { it.isNotBlank() },
                yieldId = row.getOrNull(9)?.takeIf { it.isNotBlank() }
            )
            MealEntryWithTarget(entry, row[6] ?: "DESAYUNO")
        }
        call.respond(response)
    }

    delete("/nutrition/delete/{id}") {
        val session = call.sessions.get<UserSession>()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        val id = call.parameters["id"]
            ?: return@delete call.respond(HttpStatusCode.BadRequest)
        nutritionService.deleteMeal(id, session.userId)
        call.respond(HttpStatusCode.OK, "Eliminado")
    }

    put("/nutrition/update/{id}") {
        val session = call.sessions.get<UserSession>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)
        val id = call.parameters["id"]
            ?: return@put call.respond(HttpStatusCode.BadRequest)
        val req = call.receive<MealEntryRequest>()
        if (!isValidBasis(req)) {
            return@put call.respond(HttpStatusCode.BadRequest, "Base de pesaje inválida")
        }
        nutritionService.updateMeal(id, session.userId, req)
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
                "fatPer100"      to (row[4] ?: "0"),
                // Los valores de arriba están en la base con la que se registró la comida. El cliente
                // necesita saber cuál era, y con qué alimento del catálogo se convirtió, para
                // devolverlos a base cruda antes de reutilizarlos; si no, volver a elegir "cocido"
                // sobre un alimento del historial aplicaría el factor dos veces.
                "basis"          to (row.getOrNull(5)?.takeIf { it.isNotBlank() } ?: ""),
                "yieldId"        to (row.getOrNull(6)?.takeIf { it.isNotBlank() } ?: "")
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

    // ── Colecciones (Tarea B) ─────────────────────────────────────────────────
    get("/nutrition/collections") {
        val session = call.sessions.get<UserSession>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        call.respond(nutritionService.getCollections(session.userId))
    }

    put("/nutrition/collections") {
        val session = call.sessions.get<UserSession>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized)

        val body = try {
            call.receive<FoodCollections>()
        } catch (e: Exception) {
            return@put call.respond(HttpStatusCode.BadRequest, "Formato inválido")
        }

        validateCollections(body)?.let { return@put call.respond(HttpStatusCode.BadRequest, it) }

        nutritionService.saveCollections(session.userId, body)
        call.respond(HttpStatusCode.OK)
    }
}

/**
 * La base de pesaje y el alimento de referencia los manda el cliente, así que se comprueban antes de
 * guardarlos: la base solo puede ser "raw" o "cooked", y el id tiene que existir de verdad en el
 * catálogo compartido. Sin esto, un valor cualquiera acabaría en BBDD, se mostraría tal cual en la app
 * y rompería la desconversión del historial.
 */
private fun isValidBasis(req: MealEntryRequest): Boolean {
    if (req.basis != null && req.basis != "raw" && req.basis != "cooked") return false
    if (req.yieldId != null && cookingYieldById(req.yieldId) == null) return false
    return true
}

private fun validateCollections(body: FoodCollections): String? {
    if (body.collections.size > MAX_COLLECTIONS) return "Demasiadas colecciones (máximo $MAX_COLLECTIONS)"
    body.collections.forEach { collection ->
        if (collection.name.isBlank()) return "Hay una colección sin nombre"
        if (collection.name.length > MAX_COLLECTION_NAME_LENGTH) return "Nombre de colección demasiado largo"
        if (collection.items.size > MAX_ITEMS_PER_COLLECTION)
            return "Demasiados alimentos en '${collection.name}' (máximo $MAX_ITEMS_PER_COLLECTION)"
        collection.items.forEach { item ->
            if (item.name.isBlank()) return "Hay un alimento sin nombre en '${collection.name}'"
            if (item.grams <= 0.0 || item.grams > MAX_ITEM_GRAMS)
                return "Cantidad fuera de rango en '${collection.name}'"
        }
    }
    return null
}