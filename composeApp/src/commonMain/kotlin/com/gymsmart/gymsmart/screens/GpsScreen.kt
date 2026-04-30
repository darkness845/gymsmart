package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.MapComponent
import com.gymsmart.gymsmart.model.ActiveRoute
import com.gymsmart.gymsmart.model.RoutePoint
import com.gymsmart.gymsmart.services.LocationProvider
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsScreen(
    navController: NavController,
    locationProvider: LocationProvider,          // ← minúscula
    onRequestPermission: (onResult: (Boolean) -> Unit) -> Unit
) {
    val accentYellow = Color(0xFFFFC107)
    val bgCream = Color(0xFFF5F5F5)

    var activeRoute by remember { mutableStateOf(ActiveRoute()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeRoute.isRecording) {
        if (activeRoute.isRecording) {
            locationProvider.getLocationUpdates().collect { punto: RoutePoint ->

                // 1. FILTRO DE PRECISIÓN (Calidad de señal)
                // Si la precisión es peor de 25m, ignoramos el punto totalmente.
                if (punto.accuracyM > 25f) return@collect

                // 2. CASO DEL PRIMER PUNTO
                if (activeRoute.points.isEmpty()) {
                    activeRoute = activeRoute.copy(
                        points = listOf(punto)
                    )
                    return@collect
                }

                // 3. CÁLCULO DE DIFERENCIAS
                val ultimoPunto = activeRoute.points.last()
                val distanciaDesdeUltimo = haversine(ultimoPunto, punto)
                val velocidadKmH = punto.speedMs * 3.6f

                // 4. FILTROS DE MOVIMIENTO REAL (Evitar saltos locos o ruido)
                // Ignoramos si:
                // - El usuario está casi parado (menos de 0.8 km/h)
                // - La distancia es demasiado pequeña (ruido estático < 5m)
                // - La distancia es humanamente imposible (> 100m en un segundo)
                if (velocidadKmH < 0.8f) return@collect
                if (distanciaDesdeUltimo < 5.0 || distanciaDesdeUltimo > 100.0) return@collect

                // 5. ACTUALIZACIÓN FINAL
                // Si ha pasado todos los filtros anteriores, guardamos el punto.
                activeRoute = activeRoute.copy(
                    points = activeRoute.points + punto,
                    distanceMeters = activeRoute.distanceMeters + distanciaDesdeUltimo
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ruta GPS", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = bgCream
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Distancia", fontSize = 13.sp, color = Color.Gray)
                    Text(
                        "${formatDouble(activeRoute.distanceMeters / 1000, 2)} km",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricBox("Puntos", "${activeRoute.points.size}", accentYellow)
                        MetricBox(
                            "Velocidad",
                            "${formatFloat(activeRoute.points.lastOrNull()?.speedMs?.times(3.6f) ?: 0f, 1)} km/h",
                            accentYellow
                        )
                        MetricBox(
                            "Altitud",
                            "${formatDouble(activeRoute.points.lastOrNull()?.altitudeM ?: 0.0, 0)} m",
                            accentYellow
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (!activeRoute.isRecording) {
                        onRequestPermission { granted ->
                            if (granted) {
                                errorMsg = null
                                activeRoute = activeRoute.copy(isRecording = true)
                            } else {
                                errorMsg = "Permiso de ubicación denegado"
                            }
                        }
                    } else {
                        activeRoute = activeRoute.copy(isRecording = false)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeRoute.isRecording) Color.Red else accentYellow
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    if (activeRoute.isRecording) "⏹ Parar ruta" else "▶ Iniciar ruta",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Esto hace que el mapa crezca y ocupe el resto de la pantalla
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
            ) {
                MapComponent(
                    points = activeRoute.points,
                    modifier = Modifier.fillMaxSize()
                )
            }

            errorMsg?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Text(
                        "⚠️ $it",
                        modifier = Modifier.padding(12.dp),
                        color = Color.Red,
                        fontSize = 13.sp
                    )
                }
            }

            if (activeRoute.points.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Últimos puntos", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        activeRoute.points.takeLast(5).reversed().forEach { point ->
                            Text(
                                "📍 ${formatDouble(point.lat, 5)}, ${formatDouble(point.lon, 5)}  •  ${formatFloat(point.speedMs * 3.6f, 1)} km/h",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatDouble(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = round(value * factor) / factor
    val intPart = rounded.toLong()
    return if (decimals == 0) {
        "$intPart"
    } else {
        val decPart = ((rounded - intPart) * factor).toLong()
            .let { abs(it).toString().padStart(decimals, '0') }
        "$intPart.$decPart"
    }
}

private fun formatFloat(value: Float, decimals: Int): String =
    formatDouble(value.toDouble(), decimals)

fun calcularDistancia(points: List<RoutePoint>, nuevo: RoutePoint): Double {
    if (points.isEmpty()) return 0.0
    return calcularDistanciaTotal(points) + haversine(points.last(), nuevo)
}

private fun calcularDistanciaTotal(points: List<RoutePoint>): Double {
    var total = 0.0
    for (i in 1 until points.size) total += haversine(points[i - 1], points[i])
    return total
}

internal fun haversine(a: RoutePoint, b: RoutePoint): Double {
    val R = 6371000.0
    val dLat = (b.lat - a.lat) * PI / 180.0
    val dLon = (b.lon - a.lon) * PI / 180.0
    val lat1 = a.lat * PI / 180.0
    val lat2 = b.lat * PI / 180.0
    val x = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * R * atan2(sqrt(x), sqrt(1 - x))
}

@Composable
private fun MetricBox(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
    }
}