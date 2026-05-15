package com.gymsmart.gymsmart

import com.gymsmart.gymsmart.model.UserSession
import com.gymsmart.gymsmart.plugins.*
import com.gymsmart.gymsmart.services.TursoService
import com.gymsmart.gymsmart.services.UserService
import com.gymsmart.gymsmart.services.NutritionService
import com.gymsmart.gymsmart.services.ProfileService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import com.gymsmart.gymsmart.routes.getLocation
import com.gymsmart.gymsmart.services.GpsService

fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 8080  // ← Railway inyecta PORT
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
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

    val turso            = TursoService()
    val userService      = UserService(turso)
    val nutritionService = NutritionService(turso)
    val profileService   = ProfileService(turso)
    val gpsService       = GpsService(turso)

    runBlocking {
        userService.initTable()
        nutritionService.initTable()
        profileService.initTable()
        profileService.initWeightHistoryTable()
        gpsService.initTables()
        profileService.initSubscriptionTable()
    }

    configureRouting(userService, nutritionService, profileService, gpsService)
}