package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun WeightScreen(navController: NavHostController) {
    var weightInput by remember { mutableStateOf("") }
    var weights by remember { mutableStateOf(listOf<Float>()) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Registro de peso", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = weightInput,
            onValueChange = { weightInput = it },
            label = { Text("Peso (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            val weightValue = weightInput.toFloatOrNull() ?: return@Button
            weights = weights + weightValue
            weightInput = ""
        }) {
            Text("Añadir peso")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (weights.isNotEmpty()) {
            Text("Tus pesos registrados:")
            weights.forEachIndexed { index, w ->
                Text("Día ${index + 1}: $w kg")
            }

            // Opcional: suavizado simple
            val smoothed = smoothWeights(weights)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Suavizado (media móvil): ${smoothed.joinToString(", ")} kg")
        }
    }
}

// Función de suavizado simple (media móvil de 3 días)
fun smoothWeights(weights: List<Float>): List<Float> {
    val result = mutableListOf<Float>()
    for (i in weights.indices) {
        val start = maxOf(0, i - 2)
        val subList = weights.subList(start, i + 1)
        result.add(subList.average().toFloat())
    }
    return result
}