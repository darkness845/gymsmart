package com.gymsmart.gymsmart.navigation

import androidx.navigation.NavHostController
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gymsmart.gymsmart.screens.DashboardScreen
import com.gymsmart.gymsmart.screens.NutritionScreen
import com.gymsmart.gymsmart.screens.TrainingScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Nutrition : Screen("nutrition")
    object Training : Screen("training")
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
    }
}