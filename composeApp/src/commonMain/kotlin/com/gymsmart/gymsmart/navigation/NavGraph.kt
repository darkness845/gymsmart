package com.gymsmart.gymsmart.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    object Workout    : Screen("workout")
    object Progress   : Screen("progress")
    object Gps        : Screen("gps")
    object Weight     : Screen("weight")
    object MyRoutes : Screen("my_routes")
    object CommunityRoutes : Screen("community_routes")
    object ForgotPassword : Screen("forgot_password/{fromProfile}/{email}") {
        fun route(fromProfile: Boolean = false, email: String = "") = "forgot_password/$fromProfile/$email"
    }
    object ResetPassword : Screen("reset_password/{token}/{fromProfile}") {
        fun route(token: String, fromProfile: Boolean = false) = "reset_password/$token/$fromProfile"
    }
    object Profile : Screen("profile")
    object Subscription : Screen("subscription")
}

@Serializable
data class RouteDetailArgs(val routeId: String)

@Composable
private fun StartupScreen(showError: Boolean, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.gymsmart.gymsmart.ui.theme.GymSmartColors.Background),
        contentAlignment = Alignment.Center
    ) {
        if (showError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "No se pudo conectar con el servidor",
                    color = com.gymsmart.gymsmart.ui.theme.GymSmartColors.TextPrimary
                )
                Button(onClick = onRetry) {
                    Text("Reintentar")
                }
            }
        } else {
            CircularProgressIndicator(color = com.gymsmart.gymsmart.ui.theme.GymSmartColors.Primary)
        }
    }
}

@Composable
fun NavGraph(
    locationProvider: LocationProvider,
    healthDataProvider: HealthDataProvider,
    onRequestLocationPermission: (callback: (Boolean) -> Unit) -> Unit,
    onRequestCameraPermission: (callback: (Boolean) -> Unit) -> Unit = {}
) {
    val navController = rememberNavController()
    val authService      = remember { AuthService() }
    val nutritionService = remember { NutritionService(authService.client) }
    val profileService   = remember { ProfileService(authService.client) }
    val gpsService = remember { GpsService(authService.client) }
    val workoutService = remember { WorkoutService(authService.client) }

    val scope = rememberCoroutineScope()
    var startDestination by remember { mutableStateOf<String?>(null) }
    var startupError by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableStateOf(0) }
    var sessionExpiredNotice by remember { mutableStateOf(false) }

    // Centraliza el 401: antes cada pantalla se quedaba con datos vacíos sin explicación cuando la
    // sesión caducaba a mitad de uso (RED-3 en doc/08-backlog-mejoras.md). Ahora, en cuanto cualquier
    // llamada autenticada recibe un 401, se manda al login con aviso, desde cualquier pantalla.
    val sessionExpired by authService.sessionExpired.collectAsState()
    LaunchedEffect(sessionExpired) {
        if (sessionExpired && navController.currentDestination?.route != Screen.Login.route) {
            sessionExpiredNotice = true
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
            authService.acknowledgeSessionExpired()
        }
    }

    // Un fallo de red aquí NUNCA debe decidir "no hay sesión" ni "no tiene perfil": antes eso podía
    // expulsar al login a un usuario con sesión válida, o peor, mandarlo a rehacer el onboarding y
    // sobrescribir un perfil real (ver RED-1 en doc/08-backlog-mejoras.md). Ante un fallo de red se
    // muestra un reintento, nunca se adivina el destino.
    LaunchedEffect(retryTrigger) {
        startupError = false
        val response = authService.me()
        when {
            response.networkError -> startupError = true
            !response.success -> startDestination = Screen.Login.route
            else -> {
                // Usuario logado: comprobar si ya completó el onboarding.
                // null (no se pudo saber) nunca se trata como "no tiene perfil" — se asume que sí lo
                // tiene y Dashboard se encarga de mostrar su propio estado de carga/error.
                startDestination =
                    if (profileService.hasProfile() == false) Screen.Onboarding.route
                    else Screen.Dashboard.route
            }
        }
    }

    if (startDestination == null) {
        StartupScreen(showError = startupError, onRetry = { retryTrigger++ })
        return
    }

    NavHost(navController = navController, startDestination = startDestination!!) {
        composable(Screen.Login.route) {
            LoginScreen(
                navController = navController,
                authService = authService,
                profileService = profileService,
                sessionExpiredNotice = sessionExpiredNotice,
                onNoticeShown = { sessionExpiredNotice = false }
            )
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
                healthDataProvider = healthDataProvider,
                authService = authService
            )
        }
        composable(Screen.Nutrition.route) {
            NutritionScreen(
                navController = navController,
                nutritionService = nutritionService,
                profileService = profileService,
                healthDataProvider = healthDataProvider,
                onRequestCameraPermission = onRequestCameraPermission
            )
        }
        composable(Screen.Training.route) {
            TrainingScreen(navController)
        }
        composable(Screen.Workout.route) {
            WorkoutScreen(navController = navController, workoutService = workoutService)
        }
        composable(Screen.Progress.route) {
            ProgressScreen(
                workoutService = workoutService,
                onBack = { navController.popBackStack() }
            )
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
            WeightScreen(navController, authService)
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

        composable(
            route = "forgot_password/{fromProfile}/{email}",
            arguments = listOf(
                navArgument("fromProfile") { type = NavType.StringType; defaultValue = "false" },
                navArgument("email")       { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val fromProfile = backStackEntry.savedStateHandle.get<String>("fromProfile").toBoolean()
            val email       = backStackEntry.savedStateHandle.get<String>("email") ?: ""
            ForgotPasswordScreen(navController, authService, fromProfile, email)
        }

        composable(
            route = "reset_password/{token}/{fromProfile}",
            arguments = listOf(
                navArgument("token")       { type = NavType.StringType },
                navArgument("fromProfile") { type = NavType.StringType; defaultValue = "false" }
            )
        ) { backStackEntry ->
            val token       = backStackEntry.savedStateHandle.get<String>("token") ?: ""
            val fromProfile = backStackEntry.savedStateHandle.get<String>("fromProfile").toBoolean()
            ResetPasswordScreen(navController, authService, token, fromProfile)
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                navController      = navController,
                healthDataProvider = healthDataProvider,
                authService        = authService,
                profileService     = profileService
            )
        }

        composable(Screen.Subscription.route) {
            SubscriptionScreen(navController, authService)
        }
    }
}