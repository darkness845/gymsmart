package com.gymsmart.gymsmart

import com.gymsmart.gymsmart.model.UserSession
import com.gymsmart.gymsmart.plugins.*
import com.gymsmart.gymsmart.routes.*
import com.gymsmart.gymsmart.services.TursoService
import com.gymsmart.gymsmart.services.UserService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.routing
import io.ktor.server.sessions.*
import kotlinx.coroutines.runBlocking
import com.gymsmart.gymsmart.routes.getLocation
import com.gymsmart.gymsmart.services.FoodService
import com.gymsmart.gymsmart.services.NutritionService
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import kotlinx.coroutines.*

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureCORS()

    install(Sessions) {
        cookie<UserSession>("gymsmart_session") {
            cookie.httpOnly = true
            cookie.maxAgeInSeconds = 60 * 60 * 24 * 7
            cookie.path = "/"
        }
    }

    val turso = TursoService()
    val userService = UserService(turso)
    val nutritionService = NutritionService(turso)

    runBlocking { userService.initTable() }
    runBlocking { nutritionService.initTable() }  // ← añade esto también

    // Un único routing con todo
    val foodService = FoodService()
    routing {
        get("/") { call.respondText("GymSmart API running 🏋️") }
        healthRoute()
        foodRoute(foodService)
        gpsRoutes()
        authRoutes(userService)
        nutritionRoutes(nutritionService)
    }

    CoroutineScope(Dispatchers.IO).launch {
        println("🛰️  GPS tracker iniciado — actualizando cada 30 segundos")
        while (true) {
            val loc = getLocation()
            println("📍 [GPS] lat=${loc.lat}, lon=${loc.lon} — ${loc.city}, ${loc.country}")
            delay(30_000)
        }
    }
}