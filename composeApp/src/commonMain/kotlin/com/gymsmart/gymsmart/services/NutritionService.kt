package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.*
import com.gymsmart.gymsmart.nutrition.cookingYieldById
import com.gymsmart.gymsmart.nutrition.findCookingYield
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

class NutritionService(private val httpClient: HttpClient) {

    /**
     * Devuelve el id creado por el servidor, o null si falló. Sin el id real, la comida recién
     * añadida no se puede editar ni borrar hasta recargar la pantalla (ver NUT-2 en el backlog).
     *
     * [clientId] debe ser **el mismo en los reintentos del mismo guardado** (ver [nuevoClientId]):
     * es lo que hace que un fallo de red después de que el INSERT haya entrado no acabe en dos filas.
     */
    suspend fun saveMealRemote(
        entry: MealEntry,
        mealType: String,
        date: String,
        clientId: String? = null
    ): String? {
        return try {
            val response = httpClient.post("${AppConfig.BASE_URL}/nutrition/add") {
                contentType(ContentType.Application.Json)
                setBody(
                    MealEntryRequest(
                        entry.name, entry.grams, entry.kcalPer100,
                        entry.proteinsPer100, entry.carbsPer100, entry.fatPer100,
                        mealType, date, entry.basis, entry.yieldId, entry.yieldFactor,
                        entry.sourceKcalPer100, entry.sourceProteinsPer100,
                        entry.sourceCarbsPer100, entry.sourceFatPer100, clientId
                    )
                )
            }
            if (response.status != HttpStatusCode.Created) return null
            response.body<Map<String, String>>()["id"]
        } catch (e: Exception) { null }
    }

    suspend fun getUserMeals(date: String): List<MealEntryWithTarget>? {
        return try {
            val response = httpClient.get("${AppConfig.BASE_URL}/nutrition/mine") {
                parameter("date", date)
            }
            response.body<List<MealEntryWithTarget>>()
        } catch (e: Exception) { null }
    }

    suspend fun deleteMealRemote(id: String): Boolean {
        return try {
            val response = httpClient.delete("${AppConfig.BASE_URL}/nutrition/delete/$id")
            response.status == HttpStatusCode.OK
        } catch (e: Exception) { false }
    }

    suspend fun updateMealRemote(entry: MealEntry, mealType: String, date: String): Boolean {
        return try {
            val response = httpClient.put("${AppConfig.BASE_URL}/nutrition/update/${entry.id}") {
                contentType(ContentType.Application.Json)
                setBody(MealEntryRequest(
                    entry.name,
                    entry.grams,
                    entry.kcalPer100,
                    entry.proteinsPer100,
                    entry.carbsPer100,
                    entry.fatPer100,
                    mealType,
                    date,
                    entry.basis,
                    entry.yieldId
                ))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) { false }
    }

    /**
     * Historial de alimentos ya usados.
     *
     * Devuelve los valores **en base cruda**, aunque la comida se registrase pesada en cocido. Toda la
     * app da por hecho que los `nutriments` de un [Product] están en base cruda y que la conversión la
     * hace el selector de cantidad; el historial sale de la tabla `meals`, donde lo guardado ya está
     * convertido a la base que eligió el usuario. Sin deshacer esa conversión aquí, reutilizar un
     * alimento del historial y volver a marcarlo como cocido aplicaría el factor dos veces (Tarea G).
     */
    suspend fun getFoodHistory(): List<Product> {
        return try {
            val response = httpClient.get("${AppConfig.BASE_URL}/nutrition/history")
            val rows = response.body<List<Map<String, String>>>()
            rows.map { row ->
                val name = row["name"]

                // Camino bueno: los valores en la base de origen vienen guardados, así que no hay
                // nada que deshacer. Es lo único exacto desde que cada macro tiene su propio factor
                // de conversión — multiplicar los cuatro por el factor de la energía dejaría la
                // proteína de un muslo de pollo un 15 % por debajo de su valor crudo.
                val origen = listOfNotNull(
                    row["srcKcalPer100"]?.toDoubleOrNull(),
                    row["srcProteinsPer100"]?.toDoubleOrNull(),
                    row["srcCarbsPer100"]?.toDoubleOrNull(),
                    row["srcFatPer100"]?.toDoubleOrNull()
                ).takeIf { it.size == 4 && it.all { v -> v.isFinite() && v >= 0.0 } }
                if (origen != null) {
                    return@map Product(
                        product_name = name,
                        nutriments = Nutriments(
                            energy_kcal_100g = origen[0],
                            proteins_100g = origen[1],
                            carbohydrates_100g = origen[2],
                            fat_100g = origen[3]
                        )
                    )
                }

                // Respaldo para las comidas registradas antes de que existieran esas columnas: se
                // deshace con el factor, como se hacía. Es aproximado en los macros y exacto en las
                // kcal, y es lo mejor que se puede hacer con lo que hay guardado.
                val toRaw = if (row["basis"] == "cooked") {
                    // **El factor guardado manda sobre todo lo demás.** Es el que se aplicó de verdad
                    // al registrar esta comida, así que deshacer con él es exacto aunque el catálogo
                    // haya cambiado de valor o haya perdido ese id. Sin esto, corregir un factor
                    // reescribía lo que el historial devolvía para comidas ya registradas, y un id
                    // borrado hacía que el `?: 1.0` devolviera valores cocidos como si fueran crudos
                    // —y al volver a marcar "cocido", el factor se aplicaba dos veces (doc/13 §2.4).
                    //
                    // El catálogo queda solo como respaldo para las comidas anteriores a la columna.
                    row["yieldFactor"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
                        ?: (cookingYieldById(row["yieldId"]) ?: findCookingYield(name))?.factor
                        ?: 1.0
                } else 1.0
                Product(
                    product_name = name,
                    nutriments = Nutriments(
                        energy_kcal_100g = row["kcalPer100"]?.toDoubleOrNull()?.times(toRaw),
                        proteins_100g    = row["proteinsPer100"]?.toDoubleOrNull()?.times(toRaw),
                        carbohydrates_100g = row["carbsPer100"]?.toDoubleOrNull()?.times(toRaw),
                        fat_100g         = row["fatPer100"]?.toDoubleOrNull()?.times(toRaw)
                    )
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getFavorites(): List<Product> {
        return try {
            val response = httpClient.get("${AppConfig.BASE_URL}/nutrition/favorites")
            val rows = response.body<List<Map<String, String>>>()
            rows.map { row ->
                Product(
                    product_name = row["name"],
                    nutriments = Nutriments(
                        energy_kcal_100g = row["kcalPer100"]?.toDoubleOrNull(),
                        proteins_100g = row["proteinsPer100"]?.toDoubleOrNull(),
                        carbohydrates_100g = row["carbsPer100"]?.toDoubleOrNull(),
                        fat_100g = row["fatPer100"]?.toDoubleOrNull()
                    )
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun addFavorite(product: Product): Boolean {
        return try {
            val response = httpClient.post("${AppConfig.BASE_URL}/nutrition/favorites/add") {
                contentType(ContentType.Application.Json)
                setBody(FavoriteBody(
                    name           = product.product_name ?: "",
                    kcalPer100     = product.nutriments.estimatedKcalPer100(),
                    proteinsPer100 = product.nutriments?.proteins_100g ?: 0.0,
                    carbsPer100    = product.nutriments?.carbohydrates_100g ?: 0.0,
                    fatPer100      = product.nutriments?.fat_100g ?: 0.0
                ))
            }
            response.status == HttpStatusCode.Created
        } catch (e: Exception) {
            println("❌ addFavorite error: ${e.message}")
            false
        }
    }

    suspend fun removeFavorite(name: String): Boolean {
        return try {
            val encodedName = name.encodeURLPath()
            val response = httpClient.delete(
                "${AppConfig.BASE_URL}/nutrition/favorites/remove/$encodedName"
            )
            response.status == HttpStatusCode.OK
        } catch (e: Exception) { false }
    }

    // ── Colecciones (Tarea B) ─────────────────────────────────────────────────

    suspend fun getCollections(): FoodCollections {
        return try {
            val response = httpClient.get("${AppConfig.BASE_URL}/nutrition/collections")
            if (response.status.isSuccess()) response.body() else FoodCollections()
        } catch (e: Exception) { FoodCollections() }
    }

    suspend fun saveCollections(collections: FoodCollections): Boolean {
        return try {
            val response = httpClient.put("${AppConfig.BASE_URL}/nutrition/collections") {
                contentType(ContentType.Application.Json)
                setBody(collections)
            }
            response.status.isSuccess()
        } catch (e: Exception) { false }
    }
}