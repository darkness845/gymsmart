package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.*
import com.gymsmart.gymsmart.services.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.authRoutes(userService: UserService) {

    post("/auth/register") {
        val req = call.receive<RegisterRequest>()

        if (req.name.isBlank() || req.email.isBlank() || req.password.length < 6) {
            call.respond(
                HttpStatusCode.BadRequest,
                AuthResponse(false, "Datos inválidos. La contraseña debe tener al menos 6 caracteres.")
            )
            return@post
        }

        userService.register(req.name, req.email, req.password)
            .onSuccess { user ->
                call.sessions.set(UserSession(user.id, user.email, user.name))
                call.respond(HttpStatusCode.Created, AuthResponse(true, "Registro exitoso", user))
            }
            .onFailure { e ->
                call.respond(HttpStatusCode.Conflict, AuthResponse(false, e.message ?: "Error al registrar"))
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
                call.respond(HttpStatusCode.Unauthorized, AuthResponse(false, e.message ?: "Error al hacer login"))
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
            val user = User(session.userId, session.name, session.email)
            call.respond(HttpStatusCode.OK, AuthResponse(true, "Sesión activa", user))
        }
    }
}