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
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Caché en memoria con TTL para respuestas de Open Food Facts (OFF-1 en doc/08-backlog-mejoras.md).
 * Todas las peticiones salen de la única IP de Render, compartida por todos los usuarios; OFF limita
 * `search.pl` de forma agresiva por IP, así que una búsqueda repetida (el mismo alimento, buscado por
 * distintos usuarios o el mismo usuario varias veces) no debería generar una petición nueva cada vez.
 * Los datos de un producto apenas cambian día a día, así que un TTL largo no sacrifica nada real.
 * No es LRU de verdad (innecesario a esta escala: app de uso personal) — simplemente, si se supera el
 * límite de entradas, se limpian primero las caducadas y si no basta se vacía del todo.
 */
private class TtlCache<K, V>(private val ttlMillis: Long, private val maxEntries: Int) {
    private data class Entry<V>(val value: V, val expiresAt: Long)
    private val map = ConcurrentHashMap<K, Entry<V>>()

    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: K, value: V) {
        if (map.size >= maxEntries) {
            val now = System.currentTimeMillis()
            map.entries.removeAll { it.value.expiresAt < now }
            if (map.size >= maxEntries) map.clear()
        }
        map[key] = Entry(value, System.currentTimeMillis() + ttlMillis)
    }
}

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

    private val logger = LoggerFactory.getLogger(FoodService::class.java)

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

    private val searchCache = TtlCache<String, FoodSearchResponse>(
        ttlMillis = 24 * 60 * 60 * 1000L,
        maxEntries = 500
    )
    private val barcodeCache = TtlCache<String, Product?>(
        ttlMillis = 24 * 60 * 60 * 1000L,
        maxEntries = 500
    )

    // ── Búsqueda por texto ──────────────────────────────────────
    suspend fun searchFood(query: String): FoodSearchResponse {
        val key = query.trim().lowercase()
        searchCache.get(key)?.let { return it }

        val result = try {
            // Primero intenta en el índice español
            val esResponse = fetchSearch(query, "world", "countries_tags_en" to "spain")
            if (esResponse.products.isNotEmpty()) esResponse

            // Fallback: índice global sin filtro de país
            else fetchSearch(query, "world")

        } catch (e: Exception) {
            logger.warn("Error en searchFood (Open Food Facts): {}", e.message)
            FoodSearchResponse()
        }

        // No cachear un resultado vacío: puede ser un fallo transitorio de OFF, no "este alimento no
        // existe" — cachearlo congelaría ese falso negativo durante todo el TTL.
        if (result.products.isNotEmpty()) searchCache.put(key, result)
        return result
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
        barcodeCache.get(code)?.let { return it }

        val product = try {
            val response = client.get(
                "https://world.openfoodfacts.org/api/v2/product/$code"
            ) {
                parameter("fields", "product_name,brands,nutriments,image_url")
            }
            if (response.status.value != 200) null
            else {
                val body = response.body<BarcodeResponse>()
                if (body.status == 1) body.product else null
            }
        } catch (e: Exception) {
            logger.warn("Error en barcode lookup (Open Food Facts): {}", e.message)
            null
        }

        // Mismo criterio que searchFood: un fallo de red no es "código inexistente", no se cachea.
        if (product != null) barcodeCache.put(code, product)
        return product
    }
}