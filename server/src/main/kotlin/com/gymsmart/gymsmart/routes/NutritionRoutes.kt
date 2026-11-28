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
        validateMeal(req)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
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
                yieldId = row.getOrNull(9)?.takeIf { it.isNotBlank() },
                yieldFactor = row.getOrNull(10)?.toDoubleOrNull()
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
        validateMeal(req)?.let { return@put call.respond(HttpStatusCode.BadRequest, it) }
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
                "yieldId"        to (row.getOrNull(6)?.takeIf { it.isNotBlank() } ?: ""),
                // El factor que se aplicó de verdad. Con él, deshacer la conversión no depende de que
                // el catálogo siga teniendo ese id con el mismo valor (doc/13 §2.4, I-3).
                "yieldFactor"    to (row.getOrNull(7)?.takeIf { it.toDoubleOrNull() != null } ?: ""),
                // Y mejor aún: los valores en la base de origen. Con ellos el cliente no deshace
                // nada, solo lee, que es lo único exacto desde que cada macro tiene su propio factor.
                "srcKcalPer100"     to (row.getOrNull(8)?.takeIf { it.toDoubleOrNull() != null } ?: ""),
                "srcProteinsPer100" to (row.getOrNull(9)?.takeIf { it.toDoubleOrNull() != null } ?: ""),
                "srcCarbsPer100"    to (row.getOrNull(10)?.takeIf { it.toDoubleOrNull() != null } ?: ""),
                "srcFatPer100"      to (row.getOrNull(11)?.takeIf { it.toDoubleOrNull() != null } ?: "")
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

/** Las cuatro comidas del día que maneja el cliente. Un valor distinto crea una fila invisible. */
private val MEAL_TYPES = setOf("DESAYUNO", "ALMUERZO", "COMIDA", "CENA")

/** Techo físico de energía: 100 g de grasa pura son 900 kcal. */
private const val MAX_KCAL_PER_100 = 902.0
private const val MAX_NAME_LENGTH = 120

/**
 * Valida todo lo que llega en una comida, no solo la base de pesaje.
 *
 * Antes **no había ninguna comprobación**: se aceptaban `grams` negativos, 0 o 10⁹, `kcalPer100` sin
 * techo, una `date` con cualquier formato y un `mealType` desconocido. La incoherencia era además
 * flagrante, porque `validateCollections` sí comprobaba `0 < grams ≤ 5000` para los ítems de una
 * colección: el límite estaba puesto en la puerta por la que no se escribe en `meals`.
 *
 * Dos de estos casos no son teóricos:
 * - un `mealType` que el cliente no reconoce se **descarta en silencio** al cargar el día
 *   (`MealType.entries.find { ... }` devuelve null y la entrada desaparece), pero el
 *   `SUM(grams * kcal_per_100 / 100)` del servidor la sigue contando: comida invisible que pesa en el
 *   autoajuste de kcal.
 * - una `date` que no parsea hace que ese día se descarte del cálculo del gasto medido.
 *
 * El daño estaba acotado aguas abajo (`reconcile` rechaza un TDEE fuera de 800..6000 y el factor
 * adaptativo está recortado a 0,70..1,30), así que esto no era una vía para destruir el objetivo del
 * usuario; pero sí para ensuciar sus datos sin que nada lo avisara.
 *
 * Devuelve el motivo del rechazo, o `null` si la comida es válida.
 */
private fun validateMeal(req: MealEntryRequest): String? {
    if (req.name.isBlank()) return "La comida necesita un nombre"
    if (req.name.length > MAX_NAME_LENGTH) return "Nombre demasiado largo"

    if (!req.grams.isFinite() || req.grams <= 0.0 || req.grams > MAX_ITEM_GRAMS)
        return "Cantidad fuera de rango (0 a $MAX_ITEM_GRAMS)"

    val porCien = listOf(
        "Energía" to (req.kcalPer100 to MAX_KCAL_PER_100),
        "Proteínas" to (req.proteinsPer100 to 100.0),
        "Hidratos" to (req.carbsPer100 to 100.0),
        "Grasas" to (req.fatPer100 to 100.0)
    )
    porCien.forEach { (etiqueta, par) ->
        val (valor, maximo) = par
        if (!valor.isFinite() || valor < 0.0 || valor > maximo) return "$etiqueta fuera de rango"
    }

    if (req.mealType !in MEAL_TYPES) return "Comida del día inválida"
    if (!esFechaValida(req.date)) return "Fecha inválida (se espera AAAA-MM-DD)"
    if (!isValidBasis(req)) return "Base de pesaje inválida"
    if (req.clientId != null && (req.clientId.isBlank() || req.clientId.length > 64))
        return "Identificador de cliente inválido"
    return null
}

/** `LocalDate.parse` acepta AAAA-MM-DD y rechaza tanto el formato raro como el día inexistente. */
private fun esFechaValida(date: String): Boolean =
    runCatching { java.time.LocalDate.parse(date) }.isSuccess

private fun validateCollections(body: FoodCollections): String? {
    if (body.collections.size > MAX_COLLECTIONS) return "Demasiadas colecciones (máximo $MAX_COLLECTIONS)"
    body.collections.forEach { collection ->
        if (collection.name.isBlank()) return "Hay una colección sin nombre"
        if (collection.name.length > MAX_COLLECTION_NAME_LENGTH) return "Nombre de colección demasiado largo"
        if (collection.items.size > MAX_ITEMS_PER_COLLECTION)
            return "Demasiados alimentos en '${collection.name}' (máximo $MAX_ITEMS_PER_COLLECTION)"
        collection.items.forEach { item ->
            if (item.name.isBlank()) return "Hay un alimento sin nombre en '${collection.name}'"
            if (!item.grams.isFinite() || item.grams <= 0.0 || item.grams > MAX_ITEM_GRAMS)
                return "Cantidad fuera de rango en '${collection.name}'"
            // Los ítems de una colección son MealEntry y llevan base y alimento de referencia, pero
            // aquí no se validaban: PUT /nutrition/collections aceptaba valores que POST
            // /nutrition/add rechaza. Con un basis = "" la app etiquetaba el alimento como "crudo"
            // sin que nadie lo hubiera dicho, y al aplicar la colección el POST daba 400 y el usuario
            // veía "algunos alimentos no se pudieron guardar" sin saber por qué.
            if (item.basis != null && item.basis != "raw" && item.basis != "cooked")
                return "Base de pesaje inválida en '${collection.name}'"
            if (item.yieldId != null && cookingYieldById(item.yieldId) == null)
                return "Alimento de referencia desconocido en '${collection.name}'"
        }
    }
    return null
}