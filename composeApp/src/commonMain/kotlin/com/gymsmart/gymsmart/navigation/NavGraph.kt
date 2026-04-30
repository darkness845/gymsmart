package com.gymsmart.gymsmart.navigation

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gymsmart.gymsmart.screens.*
import com.gymsmart.gymsmart.screens.DashboardScreen
import com.gymsmart.gymsmart.screens.NutritionScreen
import com.gymsmart.gymsmart.screens.TrainingScreen
import com.gymsmart.gymsmart.screens.GpsScreen
import com.gymsmart.gymsmart.screens.WeightScreen
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.services.*
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Dashboard : Screen("dashboard")
    object Nutrition : Screen("nutrition")
    object Training : Screen("training")
    object Gps : Screen("gps")
    object Weight : Screen("weight")
}

@Composable
fun NavGraph(
    locationProvider: LocationProvider,
    onRequestLocationPermission: (onResult: (Boolean) -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val authService = remember { AuthService() }

    val nutritionService = remember { NutritionService(authService.client) }

    val scope = rememberCoroutineScope()

    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            val response = authService.me()
            startDestination = if (response.success) {
                Screen.Dashboard.route
            } else {
                Screen.Login.route
            }
        }
    }

    if (startDestination == null) return

    NavHost(navController = navController, startDestination = startDestination!!) {
        composable(Screen.Login.route) {
            LoginScreen(navController, authService)
        }
        composable(Screen.Register.route) {
            RegisterScreen(navController, authService)
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController)
        }
        composable(Screen.Nutrition.route) {
            NutritionScreen(
                navController = navController,
                nutritionService = nutritionService
            )
        }
        composable(Screen.Training.route) {
            TrainingScreen(navController)
        }
        composable(Screen.Gps.route) {
            GpsScreen(
                navController = navController,
                locationProvider = locationProvider,
                onRequestPermission = onRequestLocationPermission
            )
        }
        composable(Screen.Weight.route) {
            WeightScreen(navController)
        }
    }
}