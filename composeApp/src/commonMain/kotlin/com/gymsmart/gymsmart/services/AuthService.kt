package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

class AuthService {

    private val client = HttpClient {
        install(ContentNegotiation) { json() }
        install(HttpCookies) {
            storage = createCookiesStorage() // ← expect/actual resuelve la plataforma
        }
    }

    private val base = AppConfig.BASE_URL

    suspend fun register(name: String, email: String, password: String): AuthResponse {
        return try {
            client.post("$base/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(name, email, password))
            }.body()
        } catch (e: Exception) {
            AuthResponse(false, "Error de conexión: ${e.message}")
        }
    }

    suspend fun login(email: String, password: String): AuthResponse {
        return try {
            client.post("$base/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }.body()
        } catch (e: Exception) {
            AuthResponse(false, "Error de conexión: ${e.message}")
        }
    }

    suspend fun logout(): AuthResponse {
        return try {
            client.post("$base/auth/logout").body()
        } catch (e: Exception) {
            AuthResponse(false, "Error de conexión: ${e.message}")
        }
    }

    suspend fun me(): AuthResponse {
        return try {
            client.get("$base/auth/me").body()
        } catch (e: Exception) {
            AuthResponse(false, "Sin sesión")
        }
    }
}