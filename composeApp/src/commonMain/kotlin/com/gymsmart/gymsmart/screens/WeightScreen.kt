package com.gymsmart.gymsmart.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlin.math.max
import kotlin.math.min

data class WeightEntry(
    val day: Int,
    val weight: Float
)

@Composable
fun WeightScreen(navController: NavHostController) {

    var entries by remember { mutableStateOf(listOf<WeightEntry>()) }
    var showDialog by remember { mutableStateOf(false) }
    var inputWeight by remember { mutableStateOf("") }

    val accent = Color(0xFFFFB800)
    val background = Color(0xFFF5F3EF)

    val nextDay = entries.size + 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(16.dp)
    ) {

        // BACK
        TextButton(onClick = { navController.popBackStack() }) {
            Text("← Atrás", color = Color.Black)
        }

        Text(
            "Evolución de peso",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        // BOTÓN AÑADIR DÍA
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Día actual: $nextDay", fontWeight = FontWeight.Bold)

                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Añadir peso", color = Color.Black)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // GRÁFICA
        if (entries.isNotEmpty()) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(10.dp)
            ) {
                Box(Modifier.padding(12.dp)) {

                    Canvas(modifier = Modifier.fillMaxSize()) {

                        val sorted = entries.sortedBy { it.day }

                        val minW = sorted.minOf { it.weight }
                        val maxW = sorted.maxOf { it.weight }
                        val range = max(1f, maxW - minW)

                        val stepX = size.width / max(1, sorted.size - 1)

                        fun y(v: Float): Float {
                            return size.height - ((v - minW) / range) * size.height
                        }

                        val path = Path()

                        sorted.forEachIndexed { index, e ->

                            val x = index * stepX
                            val y = y(e.weight)

                            if (index == 0) {
                                path.moveTo(x, y)
                            } else {
                                val prevX = (index - 1) * stepX
                                val prevY = y(sorted[index - 1].weight)

                                val ctrlX = (prevX + x) / 2

                                path.cubicTo(
                                    ctrlX, prevY,
                                    ctrlX, y,
                                    x, y
                                )
                            }
                        }

                        drawPath(
                            path = path,
                            color = accent,
                            style = Stroke(width = 6f)
                        )

                        // puntos
                        sorted.forEachIndexed { index, e ->
                            val x = index * stepX
                            val y = y(e.weight)

                            drawCircle(
                                color = accent,
                                radius = 6f,
                                center = Offset(x, y)
                            )
                        }
                    }

                    Text(
                        "Progreso",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // LISTA
        entries.sortedByDescending { it.day }.forEach {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Row(
                    Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Día ${it.day}", fontWeight = FontWeight.Bold)
                    Text("${it.weight} kg", fontWeight = FontWeight.Bold, color = accent)
                }
            }
        }
    }

    // DIALOG
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Añadir peso Día $nextDay") },
            text = {
                OutlinedTextField(
                    value = inputWeight,
                    onValueChange = { inputWeight = it },
                    label = { Text("Peso (kg)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val weight = inputWeight.toFloatOrNull()
                        if (weight != null) {
                            entries = entries + WeightEntry(nextDay, weight)
                            inputWeight = ""
                            showDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Guardar", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}