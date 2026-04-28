package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay

data class GpsData(
    val lat: Double,
    val lon: Double,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsScreen(navController: NavController) {

    var lat by remember { mutableStateOf("--") }
    var lon by remember { mutableStateOf("--") }
    var status by remember { mutableStateOf("Buscando señal...") }

    val client = remember { HttpClient() }

    // 🔁 Polling automático (FIX incluido)
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val response = client.get("http://localhost:8080/gps")

                if (response.status.value == 200) {
                    val data: GpsData = response.body()

                    lat = data.lat.toString()
                    lon = data.lon.toString()
                    status = "Señal recibida ✅"

                } else {
                    status = "Esperando datos del móvil..."
                }

            } catch (e: Exception) {
                status = "Sin conexión ❌"
            }

            delay(3000)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Telemetría Garmin - Daniel") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 📍 ESTADO GPS
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📍 Estado del GPS", style = MaterialTheme.typography.titleMedium)
                    Text(status, style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Latitud: $lat")
                    Text("Longitud: $lon")
                }
            }

            // ⌚ GARMIN
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⌚ Datos Garmin (ANT+)", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Pulsaciones: -- bpm", style = MaterialTheme.typography.bodyLarge)
                    Text("Cadencia: -- rpm", style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Volver al Menú Principal")
            }
        }
    }
}