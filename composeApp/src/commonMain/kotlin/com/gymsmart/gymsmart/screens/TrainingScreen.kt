package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.components.TrainingCard
import com.gymsmart.gymsmart.components.TrainingTable
import com.gymsmart.gymsmart.model.TrainingItem
import com.gymsmart.gymsmart.services.getDatosEntrenamiento
import kotlinx.coroutines.launch

@Composable
fun TrainingScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var trainings by remember { mutableStateOf<List<TrainingItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
        Button(onClick = { navController.popBackStack() }) {
            Text("← Volver")
        }
        Spacer(Modifier.height(16.dp))
        Text("Panel de Entrenamiento - GYMSMART", fontSize = 24.sp, color = Color(0xFF646CFF))
        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(15.dp)) {
                    Text("📊 Historial de Entrenamientos", fontSize = 16.sp, color = Color.White)
                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMsg = null
                                val data = getDatosEntrenamiento()
                                trainings = data?.history ?: emptyList()
                                if (data == null) errorMsg = "Backend no responde en http://localhost:8000"
                                isLoading = false
                            }
                        }
                    ) {
                        Text(if (isLoading) "Cargando..." else "Cargar Historial")
                    }

                    errorMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color.Red, fontSize = 12.sp)
                    }

                    if (trainings.isNotEmpty()) {
                        TrainingTable(data = trainings)
                    }
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(15.dp)) {
                    Text("🛰️ GPS y ANT+", fontSize = 16.sp, color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "GPS disponible en Android/iOS.\nEn Desktop se puede importar archivos .gpx/.fit",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {}, enabled = false) {
                        Text("Importar .GPX / .FIT (próximamente)")
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrainingCard("Duración", null, "min", Color(0xFF646CFF))
            TrainingCard("Puntos GPS", null, "pts", Color(0xFF4CAF50))
            TrainingCard("Estado", "Listo", "", Color(0xFFFF9800))
        }
    }
}