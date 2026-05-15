package com.gymsmart.gymsmart.plugins

import com.gymsmart.gymsmart.routes.*
import com.gymsmart.gymsmart.services.EmailService
import com.gymsmart.gymsmart.services.FoodService
import com.gymsmart.gymsmart.services.GpsService
import com.gymsmart.gymsmart.services.NutritionService
import com.gymsmart.gymsmart.services.ProfileService
import com.gymsmart.gymsmart.services.StripeService
import com.gymsmart.gymsmart.services.UserService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    userService: UserService,
    nutritionService: NutritionService,
    profileService: ProfileService,
    gpsService: GpsService
) {

    val foodService = FoodService()
    val emailService = EmailService()
    val stripeService = StripeService()

    routing {

        get("/") { call.respondText("GymSmart API running 🏋️") }

        healthRoute()
        foodRoute(foodService)
        gpsRoutes(gpsService)
        authRoutes(userService, emailService)
        nutritionRoutes(nutritionService)
        profileRoutes(profileService, userService)
        bodyAnalysisRoute()
        subscriptionRoutes(profileService, stripeService, userService, emailService)
    }
}