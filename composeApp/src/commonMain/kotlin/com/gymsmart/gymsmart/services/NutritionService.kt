package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

class NutritionService(private val httpClient: HttpClient) {

    /** Devuelve el id creado por el servidor, o null si falló. Sin el id real, la comida recién
     * añadida no se puede editar ni borrar hasta recargar la pantalla (ver NUT-2 en el backlog). */
    suspend fun saveMealRemote(entry: MealEntry, mealType: String, date: String): String? {
        return try {
            val response = httpClient.post("${AppConfig.BASE_URL}/nutrition/add") {
                contentType(ContentType.Application.Json)
                setBody(
                    MealEntryRequest(
                        entry.name, entry.grams, entry.kcalPer100,
                        entry.proteinsPer100, entry.carbsPer100, entry.fatPer100,
                        mealType, date
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
                    date

                ))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) { false }
    }

    suspend fun getFoodHistory(): List<Product> {
        return try {
            val response = httpClient.get("${AppConfig.BASE_URL}/nutrition/history")
            val rows = response.body<List<Map<String, String>>>()
            rows.map { row ->
                Product(
                    product_name = row["name"],
                    nutriments = Nutriments(
                        energy_kcal_100g = row["kcalPer100"]?.toDoubleOrNull(),
                        proteins_100g    = row["proteinsPer100"]?.toDoubleOrNull(),
                        carbohydrates_100g = row["carbsPer100"]?.toDoubleOrNull(),
                        fat_100g         = row["fatPer100"]?.toDoubleOrNull()
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
}