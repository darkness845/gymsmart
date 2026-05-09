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
import com.gymsmart.gymsmart.model.RoutePointDto
import com.gymsmart.gymsmart.model.SaveRouteRequest
import com.gymsmart.gymsmart.services.GpsService
import com.gymsmart.gymsmart.services.LocationProvider
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsScreen(
    navController: NavController,
    locationProvider: LocationProvider,
    onRequestPermission: (onResult: (Boolean) -> Unit) -> Unit,
    gpsService: GpsService
) {
    val accentYellow = Color(0xFFFFC107)
    val bgCream = Color(0xFFF5F5F5)
    val scope = rememberCoroutineScope()

    var activeRoute by remember { mutableStateOf(ActiveRoute()) }
    var startedAt by remember { mutableStateOf(0L) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var routeName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(activeRoute.isRecording) {
        if (activeRoute.isRecording) {
            locationProvider.getLocationUpdates().collect { punto: RoutePoint ->
                if (punto.accuracyM > 25f) return@collect
                if (activeRoute.points.isEmpty()) {
                    activeRoute = activeRoute.copy(points = listOf(punto))
                    return@collect
                }
                val ultimoPunto = activeRoute.points.last()
                val distanciaDesdeUltimo = haversine(ultimoPunto, punto)
                val velocidadKmH = punto.speedMs * 3.6f
                if (velocidadKmH < 0.8f) return@collect
                if (distanciaDesdeUltimo < 5.0 || distanciaDesdeUltimo > 100.0) return@collect
                activeRoute = activeRoute.copy(
                    points = activeRoute.points + punto,
                    distanceMeters = activeRoute.distanceMeters + distanciaDesdeUltimo
                )
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showSaveDialog = false },
            title = { Text("Guardar ruta", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${formatDouble(activeRoute.distanceMeters / 1000, 2)} km  •  ${activeRoute.points.size} puntos",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        label = { Text("Nombre de la ruta") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (saveSuccess) {
                        Text("✅ Ruta guardada correctamente", color = Color(0xFF4CAF50), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (routeName.isBlank()) return@Button
                        isSaving = true
                        scope.launch {
                            val ahora = Clock.System.now().toEpochMilliseconds()
                            val req = SaveRouteRequest(
                                name = routeName.trim(),
                                distanceMeters = activeRoute.distanceMeters,
                                durationSeconds = (ahora - startedAt) / 1000L,
                                startedAt = startedAt,
                                points = activeRoute.points.map { p ->
                                    RoutePointDto(
                                        lat       = p.lat,
                                        lon       = p.lon,
                                        timestamp = p.timestamp,
                                        speedMs   = p.speedMs,
                                        altitudeM = p.altitudeM,
                                        accuracyM = p.accuracyM
                                    )
                                }
                            )
                            val result = gpsService.saveRoute(req)
                            isSaving = false
                            if (result.isSuccess) {
                                saveSuccess = true
                                kotlinx.coroutines.delay(1200)
                                showSaveDialog = false
                                saveSuccess = false
                                routeName = ""
                                activeRoute = ActiveRoute()
                                startedAt = 0L
                            } else {
                                errorMsg = "Error al guardar: ${result.exceptionOrNull()?.message}"
                                showSaveDialog = false
                            }
                        }
                    },
                    enabled = routeName.isNotBlank() && !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = accentYellow)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                    } else {
                        Text("Guardar", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        routeName = ""
                        activeRoute = ActiveRoute()
                        startedAt = 0L
                    },
                    enabled = !isSaving
                ) {
                    Text("Descartar")
                }
            }
        )
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
                                startedAt = Clock.System.now().toEpochMilliseconds()
                                activeRoute = activeRoute.copy(isRecording = true)
                            } else {
                                errorMsg = "Permiso de ubicación denegado"
                            }
                        }
                    } else {
                        activeRoute = activeRoute.copy(isRecording = false)
                        if (activeRoute.points.isNotEmpty()) {
                            showSaveDialog = true
                        }
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
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
            ) {
                MapComponent(
                    points = activeRoute.points,
                    completedPoints = activeRoute.points,
                    userLocation = activeRoute.points.lastOrNull(),
                    modifier = Modifier.fillMaxSize()
                )
            }

            errorMsg?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Text("⚠️ $it", modifier = Modifier.padding(12.dp), color = Color.Red, fontSize = 13.sp)
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
