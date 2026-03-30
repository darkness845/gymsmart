package com.gymsmart.gymsmart.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Modelos USDA
@Serializable
data class UsdaNutrient(
    val nutrientId: Int = 0,
    val nutrientName: String = "",
    val value: Double = 0.0
)

@Serializable
data class UsdaFood(
    val fdcId: Int = 0,
    val description: String = "",
    val brandOwner: String? = null,
    val foodNutrients: List<UsdaNutrient> = emptyList()
)

@Serializable
data class UsdaSearchResponse(
    val foods: List<UsdaFood> = emptyList(),
    val totalHits: Int = 0
)

// Modelos que ya usa tu frontend (los mantenemos igual)
@Serializable
data class Nutriments(
    @SerialName("energy-kcal_100g") val energy_kcal_100g: Double? = null,
    @SerialName("energy-kcal") val energy_kcal: Double? = null,
    val proteins_100g: Double? = null,
    val carbohydrates_100g: Double? = null,
    val fat_100g: Double? = null
)

@Serializable
data class Product(
    val product_name: String? = null,
    val nutriments: Nutriments? = null,
    val image_url: String? = null,
    val brands: String? = null
)

@Serializable
data class FoodSearchResponse(
    val products: List<Product> = emptyList(),
    val count: Int = 0
)

class FoodService {
    private val API_KEY = "1QB9LaZgb5dK5M6y9gK9SCuCtEu3JQXEvQTDeGRq"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 30000
        }
        defaultRequest {
            headers.append("User-Agent", "GymSmart/1.0 (contact@gymsmart.com)")
        }
    }

    suspend fun searchFood(query: String): FoodSearchResponse {
        return try {
            val response = client.get("https://api.nal.usda.gov/fdc/v1/foods/search") {
                parameter("query", query)
                parameter("pageSize", 10)
                parameter("api_key", API_KEY)
                parameter("dataType", "Foundation,SR Legacy")
            }

            println(">>> Status USDA: ${response.status.value}")

            if (response.status.value != 200) {
                return FoodSearchResponse(products = emptyList(), count = 0)
            }

            val usdaResponse = response.body<UsdaSearchResponse>()

            // Convertir respuesta USDA al formato que ya usa tu frontend
            val products = usdaResponse.foods.map { food ->
                val nutrients = food.foodNutrients
                Product(
                    product_name = food.description,
                    brands = food.brandOwner,
                    image_url = null, // USDA no tiene imágenes
                    nutriments = Nutriments(
                        energy_kcal_100g = nutrients.find { it.nutrientId == 1008 }?.value, // kcal
                        proteins_100g = nutrients.find { it.nutrientId == 1003 }?.value,    // proteínas
                        carbohydrates_100g = nutrients.find { it.nutrientId == 1005 }?.value, // carbs
                        fat_100g = nutrients.find { it.nutrientId == 1004 }?.value           // grasa
                    )
                )
            }

            FoodSearchResponse(products = products, count = usdaResponse.totalHits)

        } catch (e: Exception) {
            println(">>> ERROR: ${e::class.simpleName} - ${e.message}")
            FoodSearchResponse(products = emptyList(), count = 0)
        }
    }
}