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
            storage = createCookiesStorage()
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

    suspend fun verifyEmail(token: String): AuthResponse {
        return try {
            client.post("$base/auth/verify-email") {
                contentType(ContentType.Application.Json)
                setBody(VerifyEmailRequest(token))
            }.body()
        } catch (e: Exception) {
            AuthResponse(false, "Error de conexión: ${e.message}")
        }
    }

    suspend fun resendVerification(email: String): AuthResponse {
        return try {
            client.post("$base/auth/resend-verification") {
                contentType(ContentType.Application.Json)
                setBody(ResendVerificationRequest(email))
            }.body()
        } catch (e: Exception) {
            AuthResponse(false, "Error de conexión: ${e.message}")
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean {
        return try {
            val response = client.post("${AppConfig.BASE_URL}/auth/change-password") {
                contentType(ContentType.Application.Json)
                setBody(ChangePasswordRequest(currentPassword, newPassword))
            }
            response.status.value == 200
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updatePersonalData(name: String, phone: String, country: String, birthDate: String): Boolean {
        return try {
            println(">>> AuthService.updatePersonalData llamando a $base/auth/profile")
            val response = client.put("$base/auth/profile") {
                contentType(ContentType.Application.Json)
                setBody(UpdatePersonalDataRequest(name, phone, country, birthDate))
            }
            println(">>> AuthService.updatePersonalData status=${response.status.value}")
            response.status.value == 200
        } catch (e: Exception) {
            println(">>> AuthService.updatePersonalData ERROR: ${e.message}")
            false
        }
    }
}

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable data class VerifyEmailRequest(val token: String)
@Serializable data class ResendVerificationRequest(val email: String)
@Serializable
data class ForgotPasswordRequest(val email: String)
@Serializable
data class ResetPasswordRequest(val token: String, val newPassword: String)

@Serializable
data class UpdatePersonalDataRequest(
    val name: String,
    val phone: String,
    val country: String,
    val birthDate: String
)

