package com.gymsmart.gymsmart.plugins

import com.gymsmart.gymsmart.routes.*
import com.gymsmart.gymsmart.services.FoodService
import com.gymsmart.gymsmart.services.NutritionService
import com.gymsmart.gymsmart.services.ProfileService
import com.gymsmart.gymsmart.services.UserService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    userService:      UserService,
    nutritionService: NutritionService,
    profileService:   ProfileService
) {
    val foodService = FoodService()

    routing {
        get("/") { call.respondText("GymSmart API running 🏋️") }
        healthRoute()
        foodRoute(foodService)
        gpsRoutes()
        authRoutes(userService)
        nutritionRoutes(nutritionService)
        profileRoutes(profileService)
    }
}