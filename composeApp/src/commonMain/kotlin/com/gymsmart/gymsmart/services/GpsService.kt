package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
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

class GpsService {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun getCurrentLocation(): Result<GpsLocation> {
        return try {
            val location: GpsLocation = client.get("${AppConfig.BASE_URL}/gps/location").body()
            Result.success(location)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}