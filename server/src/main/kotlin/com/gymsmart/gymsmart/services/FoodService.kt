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
            val response = client.get("https://world.openfoodfacts.org/cgi/search.pl") {
                parameter("search_terms", query)
                parameter("search_simple", 1)
                parameter("action", "process")
                parameter("json", 1)
                parameter("page_size", 10)
                parameter("fields", "product_name,image_url,brands,nutriments_estimated,nutriments")
                parameter("lc", "es")
                parameter("user_id", "jony2745")
                parameter("password", "Silimon274")
            }

            if (response.status.value != 200) {
                return FoodSearchResponse(products = emptyList(), count = 0)
            }

            response.body()
        } catch (e: Exception) {
            FoodSearchResponse(products = emptyList(), count = 0)
        }
    }
}