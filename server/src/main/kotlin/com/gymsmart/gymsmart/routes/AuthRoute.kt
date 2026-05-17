package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.*
import com.gymsmart.gymsmart.services.EmailService
import com.gymsmart.gymsmart.services.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable

@Serializable data class ForgotPasswordRequest(val email: String)
@Serializable data class ResetPasswordRequest(val token: String, val newPassword: String = "")
@Serializable data class VerifyEmailRequest(val token: String)
@Serializable data class ResendVerificationRequest(val email: String)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class UpdatePersonalDataRequest(
    val name: String,
    val phone: String,
    val country: String,
    val birthDate: String
)


fun Route.authRoutes(userService: UserService, emailService: EmailService) {

    post("/auth/register") {
        val req = call.receive<RegisterRequest>()
        if (req.name.isBlank() || req.email.isBlank() || req.password.length < 6) {
            call.respond(HttpStatusCode.BadRequest,
                AuthResponse(false, "Datos inválidos. La contraseña debe tener al menos 6 caracteres."))
            return@post
        }
        userService.register(req.name, req.email, req.password)
            .onSuccess { pair ->
                val user  = pair.first
                val token = pair.second
                val emailSent = emailService.sendEmailVerification(req.email, req.name, token)
                println(">>> Email enviado: $emailSent — destino: ${req.email}")
                call.respond(HttpStatusCode.Created, AuthResponse(true, "VERIFY_EMAIL"))
            }
            .onFailure { e ->
                call.respond(HttpStatusCode.Conflict,
                    AuthResponse(false, e.message ?: "Error al registrar"))
            }
    }

    post("/auth/login") {
        val req = call.receive<LoginRequest>()
        userService.login(req.email, req.password)
            .onSuccess { user ->
                call.sessions.set(UserSession(user.id, user.email, user.name))
                call.respond(HttpStatusCode.OK, AuthResponse(true, "Login exitoso", user))
            }
            .onFailure { e ->
                val msg = e.message ?: "Error al hacer login"
                // Código especial para que el cliente sepa que debe verificar
                if (msg == "EMAIL_NOT_VERIFIED") {
                    call.respond(HttpStatusCode.Forbidden, AuthResponse(false, "EMAIL_NOT_VERIFIED"))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, AuthResponse(false, msg))
                }
            }
    }

    post("/auth/logout") {
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.OK, AuthResponse(true, "Sesión cerrada"))
    }

    get("/auth/me") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respond(HttpStatusCode.Unauthorized, AuthResponse(false, "No hay sesión activa"))
        } else {
            val user = userService.findById(session.userId)
                ?: User(session.userId, session.name, session.email)
            call.respond(HttpStatusCode.OK, AuthResponse(true, "Sesión activa", user))
        }
    }

    // Verificar email con código
    post("/auth/verify-email") {
        val req = call.receive<VerifyEmailRequest>()
        userService.verifyEmail(req.token)
            .onSuccess { user ->
                // Iniciamos sesión automáticamente tras verificar
                call.sessions.set(UserSession(user.id, user.email, user.name))
                call.respond(HttpStatusCode.OK, AuthResponse(true, "Email verificado correctamente", user))
            }
            .onFailure { e ->
                call.respond(HttpStatusCode.BadRequest, AuthResponse(false, e.message ?: "Token inválido"))
            }
    }

    // Reenviar código de verificación
    post("/auth/resend-verification") {
        val req = call.receive<ResendVerificationRequest>()
        userService.resendVerificationToken(req.email)
            .onSuccess { (token, name) ->
                emailService.sendEmailVerification(req.email, name, token)
                call.respond(HttpStatusCode.OK, AuthResponse(true, "Código reenviado"))
            }
            .onFailure { e ->
                call.respond(HttpStatusCode.BadRequest, AuthResponse(false, e.message ?: "Error"))
            }
    }

    // Endpoints de reset de contraseña
    post("/auth/forgot-password") {
        val req = call.receive<ForgotPasswordRequest>()
        if (req.email.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, AuthResponse(false, "Email requerido"))
            return@post
        }
        userService.createResetToken(req.email)
            .onSuccess { (token, name) ->
                emailService.sendPasswordReset(req.email, name, token)
            }
        call.respond(HttpStatusCode.OK, AuthResponse(true, "Si el email existe, recibirás un enlace en breve"))
    }

    post("/auth/verify-reset-token") {
        val req = call.receive<ResetPasswordRequest>()
        userService.verifyResetToken(req.token)
            .onSuccess { call.respond(HttpStatusCode.OK, AuthResponse(true, "Token válido")) }
            .onFailure { e -> call.respond(HttpStatusCode.BadRequest, AuthResponse(false, e.message ?: "Token inválido")) }
    }

    post("/auth/reset-password") {
        val req = call.receive<ResetPasswordRequest>()
        if (req.token.isBlank() || req.newPassword.length < 6) {
            call.respond(HttpStatusCode.BadRequest,
                AuthResponse(false, "Token y contraseña (mín. 6 caracteres) requeridos"))
            return@post
        }
        userService.resetPassword(req.token, req.newPassword)
            .onSuccess { call.respond(HttpStatusCode.OK, AuthResponse(true, "Contraseña actualizada correctamente")) }
            .onFailure { e -> call.respond(HttpStatusCode.BadRequest, AuthResponse(false, e.message ?: "Token inválido")) }
    }

    post("/auth/change-password") {
        val session = call.sessions.get<UserSession>()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, AuthResponse(false, "Sin sesión"))

        println(">>> change-password session=$session")

        val req = try {
            call.receive<ChangePasswordRequest>()
        } catch (e: Exception) {
            return@post call.respond(HttpStatusCode.BadRequest, AuthResponse(false, "Cuerpo inválido"))
        }

        if (req.currentPassword.isBlank() || req.newPassword.length < 6) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                AuthResponse(false, "Mínimo 6 caracteres en la nueva contraseña")
            )
        }

        userService.changePassword(session.userId, req.currentPassword, req.newPassword)
            .onSuccess { call.respond(HttpStatusCode.OK, AuthResponse(true, "Contraseña actualizada")) }
            .onFailure { e -> call.respond(HttpStatusCode.BadRequest, AuthResponse(false, e.message ?: "Error")) }
    }

    put("/auth/profile") {
        val session = call.sessions.get<UserSession>()
            ?: return@put call.respond(HttpStatusCode.Unauthorized, AuthResponse(false, "Sin sesión"))

        println(">>> PUT /auth/profile session=$session")

        val req = try {
            call.receive<UpdatePersonalDataRequest>()
        } catch (e: Exception) {
            println(">>> ERROR recibiendo body: ${e.message}")
            return@put call.respond(HttpStatusCode.BadRequest, AuthResponse(false, "Cuerpo inválido"))
        }

        println(">>> req=$req")

        userService.updatePersonalData(session.userId, req.name, req.phone, req.country, req.birthDate)
            .onSuccess {
                println(">>> updatePersonalData OK")
                call.sessions.set(UserSession(session.userId, session.email, req.name))
                call.respond(HttpStatusCode.OK, AuthResponse(true, "Datos actualizados"))
            }
            .onFailure { e ->
                println(">>> updatePersonalData ERROR: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, AuthResponse(false, e.message ?: "Error"))
            }
    }
}