package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.TrainingItem
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val client = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

private const val API_URL = "http://localhost:8000"

@Serializable
data class TrainingResponse(val history: List<TrainingItem> = emptyList())

suspend fun getDatosEntrenamiento(): TrainingResponse? {
    return try {
        client.get("$API_URL/").body()
    } catch (e: Exception) {
        println("Error de conexión: ${e.message}")
        null
    }
}