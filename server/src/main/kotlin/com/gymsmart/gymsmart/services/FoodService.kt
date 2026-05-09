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

// ── Modelos Open Food Facts ────────────────────────────────────────────────────

@Serializable
data class Nutriments(
    @SerialName("energy-kcal_100g") val energy_kcal_100g: Double? = null,
    @SerialName("energy-kcal")      val energy_kcal: Double? = null,
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

// Respuesta de búsqueda por texto
@Serializable
data class FoodSearchResponse(
    val products: List<Product> = emptyList(),
    val count: Int = 0
)

// Respuesta de búsqueda por código de barras
@Serializable
data class BarcodeResponse(
    val status: Int = 0,          // 1 = encontrado, 0 = no encontrado
    val product: Product? = null
)

class FoodService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
            socketTimeoutMillis  = 30000
        }
        defaultRequest {
            headers.append("User-Agent", "GymSmart/1.0 (contact@gymsmart.com)")
        }
    }

    // ── Búsqueda por texto (prioriza ES) ──────────────────────────────────────
    suspend fun searchFood(query: String): FoodSearchResponse {
        return try {
            // Primero intenta en el índice español (más resultados Hacendado, Carrefour, Aldi)
            val esResponse = fetchSearch(query, "world", "countries_tags_en" to "spain")
            if (esResponse.products.isNotEmpty()) return esResponse

            // Fallback: índice global sin filtro de país
            fetchSearch(query, "world")

        } catch (e: Exception) {
            println(">>> OFF searchFood error: ${e.message}")
            FoodSearchResponse()
        }
    }

    private suspend fun fetchSearch(
        query: String,
        subdomain: String,
        extraParam: Pair<String, String>? = null
    ): FoodSearchResponse {
        val response = client.get("https://$subdomain.openfoodfacts.org/cgi/search.pl") {
            parameter("search_terms", query)
            parameter("search_simple", 1)
            parameter("action", "process")
            parameter("json", 1)
            parameter("page_size", 20)
            parameter("fields", "product_name,brands,nutriments,image_url")
            extraParam?.let { (k, v) -> parameter(k, v) }
        }
        return if (response.status.value == 200) response.body() else FoodSearchResponse()
    }

    // ── Búsqueda por código de barras EAN-13 ─────────────────────────────────
    suspend fun lookupBarcode(code: String): Product? {
        return try {
            val response = client.get(
                "https://world.openfoodfacts.org/api/v2/product/$code"
            ) {
                parameter("fields", "product_name,brands,nutriments,image_url")
            }
            if (response.status.value != 200) return null
            val body = response.body<BarcodeResponse>()
            if (body.status == 1) body.product else null
        } catch (e: Exception) {
            println(">>> OFF barcode error: ${e.message}")
            null
        }
    }
}