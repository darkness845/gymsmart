package com.gymsmart.gymsmart.navigation

import androidx.navigation.NavHostController
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gymsmart.gymsmart.screens.DashboardScreen
import com.gymsmart.gymsmart.screens.NutritionScreen
import com.gymsmart.gymsmart.screens.TrainingScreen
import com.gymsmart.gymsmart.screens.GpsScreen // <--- 1. AÑADIDO: Importamos tu pantalla
import com.gymsmart.gymsmart.screens.WeightScreen

sealed class Screen(val route: String) {
    object Weight :Screen("weight")
    object Dashboard : Screen("dashboard")
    object Nutrition : Screen("nutrition")
    object Training : Screen("training")
    object Gps : Screen("gps") // <--- 2. AÑADIDO: Tu ruta "gps"
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController)
        }
        composable(Screen.Nutrition.route) {
            NutritionScreen(navController)
        }
        composable(Screen.Training.route) {
            TrainingScreen(navController)
        }
        // --- NUEVO: WeightScreen ---
        composable(Screen.Weight.route) {
            WeightScreen(navController)
        }

        // ---CÓDIGO DE DANIEL---
        composable(Screen.Gps.route) {
            GpsScreen(navController) // <--- 3. AÑADIDO: Registro de tu pantalla
        }
    }
}