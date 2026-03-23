package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.navigation.Screen

@Composable
fun DashboardScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "GymSmart 🏋️",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Botón de Jonathan: Nutrición
        Button(
            onClick = { navController.navigate(Screen.Nutrition.route) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Nutrición y Calorías")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botón de Jonathan: Entrenamiento
        Button(
            onClick = { navController.navigate(Screen.Training.route) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entrenamiento")
        }

        // --- INICIO DE TU PARTE (DANIEL) ---
        Spacer(modifier = Modifier.height(16.dp)) // Mantenemos el mismo espacio

        Button(
            onClick = { navController.navigate(Screen.Gps.route) },
            modifier = Modifier.fillMaxWidth(),
            // Le damos un color diferente para que sepas cuál es el tuyo (opcional)
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("GPS y Telemetría Garmin")
        }

        Button(onClick = { navController.navigate(Screen.Weight.route) }) {
            Text("Peso")
        }
        // --- FIN DE TU PARTE ---
    }
}