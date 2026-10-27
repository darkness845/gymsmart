package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * Única instancia de [HttpClient] de toda la app — se crea una sola vez en `NavGraph` y se pasa a las
 * pantallas que lo necesitan. Antes cada pantalla creaba su propio `AuthService()`/`HttpClient`
 * (HTTP-1 en doc/08-backlog-mejoras.md): sin timeouts, sin motor de red determinista en Android (dos
 * en el classpath a la vez), y cada uno con su propio pool de conexiones nunca cerrado.
 */
class AuthService {

    private val _sessionExpired = MutableStateFlow(false)

    /** Se pone a `true` cuando cualquier llamada autenticada (fuera de las rutas de `/auth`) recibe un
     * 401 — la sesión murió mientras se usaba la app (cookie caducada, servidor reiniciado). `NavGraph`
     * lo observa para mandar al login con un aviso en vez de dejar cada pantalla con datos vacíos sin
     * explicación (RED-3). */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    fun acknowledgeSessionExpired() {
        _sessionExpired.value = false
    }

    val client = HttpClient {
        install(ContentNegotiation) { json() }
        install(HttpCookies) {
            storage = createCookiesStorage()
        }
        install(HttpTimeout) {
            // Generosos a propósito: Render (plan gratuito) puede tardar bastante en un cold start.
            requestTimeoutMillis = 45_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 45_000
        }
        HttpResponseValidator {
            validateResponse { response ->
                val path = response.call.request.url.encodedPath
                // Las rutas de auth devuelven 401 como parte de su flujo normal (no hay sesión
                // todavía, credenciales mal), ya gestionado por cada método de esta clase — no es una
                // sesión que "caduca", es que nunca hubo una.
                if (response.status == HttpStatusCode.Unauthorized && !path.startsWith("/auth/")) {
                    _sessionExpired.value = true
                }
            }
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
            // Excepción aquí = la petición ni siquiera tuvo respuesta (red caída, cold start de
            // Render). Es distinto de que el servidor responda "no hay sesión" — no se debe tratar
            // igual (ver RED-1 en doc/08-backlog-mejoras.md).
            AuthResponse(false, "Error de conexión", networkError = true)
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

