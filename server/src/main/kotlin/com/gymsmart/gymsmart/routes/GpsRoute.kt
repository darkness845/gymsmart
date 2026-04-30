package com.gymsmart.gymsmart.routes

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GpsLocation(
    val lat: Double,
    val lon: Double,
    val city: String = "",
    val country: String = "",
    val source: String = "ip-geolocation"
)

@Serializable
data class IpApiResponse(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val city: String = "",
    val country: String = "",
    val status: String = ""
)

private val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

// Cache para no spamear la API externa
private var cachedLocation: GpsLocation? = null
private var lastFetchTime: Long = 0
private val CACHE_TTL_MS = 30_000L // 30 segundos

fun Route.gpsRoutes() {
    route("/gps") {
        get("/location") {
            val location = getLocation()
            println("📍 GPS → lat=${location.lat}, lon=${location.lon} | ${location.city}, ${location.country}")
            println("   🗺️  Google Maps: https://maps.google.com/?q=${location.lat},${location.lon}")
            call.respond(HttpStatusCode.OK, location)
        }
    }
}

suspend fun getLocation(): GpsLocation {
    val now = System.currentTimeMillis()
    if (cachedLocation != null && (now - lastFetchTime) < CACHE_TTL_MS) {
        return cachedLocation!!
    }

    return try {
        val response: IpApiResponse = httpClient.get("http://ip-api.com/json/") {
            parameter("fields", "status,country,city,lat,lon")
        }.body()

        if (response.status == "success") {
            val location = GpsLocation(
                lat = response.lat,
                lon = response.lon,
                city = response.city,
                country = response.country
            )
            cachedLocation = location
            lastFetchTime = now
            location
        } else {
            // Fallback a Madrid si falla
            GpsLocation(lat = 40.4168, lon = -3.7038, city = "Madrid (fallback)", country = "ES")
        }
    } catch (e: Exception) {
        println("⚠️  Error obteniendo GPS: ${e.message}")
        GpsLocation(lat = 40.4168, lon = -3.7038, city = "Madrid (fallback)", country = "ES")
    }
}