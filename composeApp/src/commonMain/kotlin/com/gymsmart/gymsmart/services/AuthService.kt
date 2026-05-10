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
import kotlinx.serialization.Serializable

class AuthService {

    val client = HttpClient {
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

    // Añade estos dos métodos al final de la clase, antes del cierre

    suspend fun forgotPassword(email: String): AuthResponse {
        return try {
            client.post("$base/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordRequest(email))
            }.body()
        } catch (e: Exception) {
            AuthResponse(false, "Error de conexión: ${e.message}")
        }
    }

    suspend fun resetPassword(token: String, newPassword: String): AuthResponse {
        return try {
            client.post("$base/auth/reset-password") {
                contentType(ContentType.Application.Json)
                setBody(ResetPasswordRequest(token, newPassword))
            }.body()
        } catch (e: Exception) {
            AuthResponse(false, "Error de conexión: ${e.message}")
        }
    }

    suspend fun verifyResetToken(token: String): AuthResponse {
        return try {
            client.post("$base/auth/verify-reset-token") {
                contentType(ContentType.Application.Json)
                setBody(ResetPasswordRequest(token, ""))
            }.body()
        } catch (e: Exception) {
            AuthResponse(false, "Error de conexión: ${e.message}")
        }
    }
}

@Serializable
data class ForgotPasswordRequest(val email: String)
@Serializable
data class ResetPasswordRequest(val token: String, val newPassword: String)