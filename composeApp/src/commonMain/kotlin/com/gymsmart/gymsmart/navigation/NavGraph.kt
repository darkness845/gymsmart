package com.gymsmart.gymsmart.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gymsmart.gymsmart.screens.*
import com.gymsmart.gymsmart.screens.DashboardScreen
import com.gymsmart.gymsmart.screens.NutritionScreen
import com.gymsmart.gymsmart.screens.TrainingScreen
import com.gymsmart.gymsmart.screens.GpsScreen
import com.gymsmart.gymsmart.screens.WeightScreen
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.services.*
import kotlinx.serialization.Serializable
import androidx.navigation.toRoute

sealed class Screen(val route: String) {
    object Login      : Screen("login")
    object Register   : Screen("register")
    object Onboarding : Screen("onboarding")
    object Dashboard  : Screen("dashboard")
    object Nutrition  : Screen("nutrition")
    object Training   : Screen("training")
    object Gps        : Screen("gps")
    object Weight     : Screen("weight")
    object MyRoutes : Screen("my_routes")
    object CommunityRoutes : Screen("community_routes")
}

@Serializable
data class RouteDetailArgs(val routeId: String)

@Composable
fun NavGraph(
    locationProvider: LocationProvider,
    healthDataProvider: HealthDataProvider,
    onRequestLocationPermission: (onResult: (Boolean) -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val authService      = remember { AuthService() }
    val nutritionService = remember { NutritionService(authService.client) }
    val profileService   = remember { ProfileService(authService.client) }
    val gpsService = remember { GpsService(authService.client) }

    val scope = rememberCoroutineScope()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val response = authService.me()
        startDestination = if (response.success) {
            // Usuario logado: comprobar si ya completó el onboarding
            if (profileService.hasProfile()) Screen.Dashboard.route
            else Screen.Onboarding.route
        } else {
            Screen.Login.route
        }
    }

    if (startDestination == null) return

    NavHost(navController = navController, startDestination = startDestination!!) {
        composable(Screen.Login.route) {
            LoginScreen(navController, authService, profileService)
        }
        composable(Screen.Register.route) {
            RegisterScreen(navController, authService)
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                profileService = profileService,
                onComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                navController = navController,
                healthDataProvider = healthDataProvider
            )
        }
        composable(Screen.Nutrition.route) {
            NutritionScreen(navController = navController,
                nutritionService = nutritionService,
                profileService   = profileService)
        }
        composable(Screen.Training.route) {
            TrainingScreen(navController)
        }
        composable(Screen.Gps.route) {
            GpsScreen(
                navController = navController,
                locationProvider = locationProvider,
                onRequestPermission = onRequestLocationPermission,
                gpsService = gpsService
            )
        }
        composable(Screen.Weight.route) {
            WeightScreen(navController)
        }

        composable<RouteDetailArgs> { backStackEntry ->
            val args = backStackEntry.toRoute<RouteDetailArgs>()
            RouteDetailScreen(
                navController = navController,
                routeId = args.routeId,
                gpsService = gpsService,
                locationProvider = locationProvider
            )
        }

        composable(Screen.MyRoutes.route) {
            MyRoutesScreen(
                navController = navController,
                gpsService = gpsService
            )
        }

        composable(Screen.CommunityRoutes.route) {
            CommunityRoutesScreen(navController = navController, gpsService = gpsService)
        }
    }
}