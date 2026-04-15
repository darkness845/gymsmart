package com.gymsmart.gymsmart

import com.gymsmart.gymsmart.model.UserSession
import com.gymsmart.gymsmart.plugins.*
import com.gymsmart.gymsmart.routes.authRoutes
import com.gymsmart.gymsmart.services.TursoService
import com.gymsmart.gymsmart.services.UserService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.routing
import io.ktor.server.sessions.*
import kotlinx.coroutines.runBlocking

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Plugins
    configureSerialization()
    configureCORS()

    // Sesiones con cookie
    install(Sessions) {
        cookie<UserSession>("gymsmart_session") {
            cookie.httpOnly = true
            cookie.maxAgeInSeconds = 60 * 60 * 24 * 7 // 7 días
            cookie.path = "/"
        }
    }

    // Servicios de autenticación
    val turso = TursoService()
    val userService = UserService(turso)

    // Crear tabla users si no existe
    runBlocking { userService.initTable() }

    // Routing
    configureRouting()
    routing {
        authRoutes(userService)
    }
}