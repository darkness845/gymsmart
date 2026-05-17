package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.MapComponent
import com.gymsmart.gymsmart.model.ActiveRoute
import com.gymsmart.gymsmart.model.RoutePoint
import com.gymsmart.gymsmart.model.RoutePointDto
import com.gymsmart.gymsmart.model.SaveRouteRequest
import com.gymsmart.gymsmart.services.GpsService
import com.gymsmart.gymsmart.services.LocationProvider
import com.gymsmart.gymsmart.ui.theme.GymSmartColors
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsScreen(
    navController: NavController,
    locationProvider: LocationProvider,
    onRequestPermission: (onResult: (Boolean) -> Unit) -> Unit,
    gpsService: GpsService
) {
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

    // --- Diálogo de guardado ---
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showSaveDialog = false },
            containerColor = GymSmartColors.SurfaceElevated,
            shape = MaterialTheme.shapes.large,
            title = {
                Text(
                    "Guardar ruta",
                    fontWeight = FontWeight.Bold,
                    color = GymSmartColors.TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = GymSmartColors.SurfaceCard,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${formatDouble(activeRoute.distanceMeters / 1000, 2)} km",
                                color = GymSmartColors.Primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${activeRoute.points.size} puntos",
                                color = GymSmartColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    OutlinedTextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        label = { Text("Nombre de la ruta") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GymSmartColors.Primary,
                            unfocusedBorderColor = GymSmartColors.Outline,
                            focusedLabelColor = GymSmartColors.Primary,
                            unfocusedLabelColor = GymSmartColors.TextSecondary,
                            cursorColor = GymSmartColors.Primary,
                            focusedTextColor = GymSmartColors.TextPrimary,
                            unfocusedTextColor = GymSmartColors.TextPrimary,
                            focusedContainerColor = GymSmartColors.SurfaceCard,
                            unfocusedContainerColor = GymSmartColors.SurfaceCard,
                        )
                    )
                    if (saveSuccess) {
                        Text(
                            "✅ Ruta guardada correctamente",
                            color = GymSmartColors.Success,
                            style = MaterialTheme.typography.bodySmall
                        )
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
                                        lat = p.lat,
                                        lon = p.lon,
                                        timestamp = p.timestamp,
                                        speedMs = p.speedMs,
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
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GymSmartColors.Primary,
                        disabledContainerColor = GymSmartColors.Outline,
                        contentColor = GymSmartColors.OnPrimary,
                        disabledContentColor = GymSmartColors.TextDisabled
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = GymSmartColors.OnPrimary
                        )
                    } else {
                        Text("Guardar", fontWeight = FontWeight.Bold)
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
                    Text("Descartar", color = GymSmartColors.TextSecondary)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Ruta GPS",
                            fontWeight = FontWeight.Bold,
                            color = GymSmartColors.TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Volver",
                                tint = GymSmartColors.TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GymSmartColors.Background
                    )
                )
            }
        },
        containerColor = GymSmartColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // --- Tarjeta de métricas ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GymSmartColors.SurfaceCard,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Distancia",
                        style = MaterialTheme.typography.labelMedium,
                        color = GymSmartColors.TextSecondary
                    )
                    Text(
                        "${formatDouble(activeRoute.distanceMeters / 1000, 2)} km",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = GymSmartColors.TextPrimary
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = GymSmartColors.Divider)
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricItem(
                            label = "Velocidad",
                            value = "${formatFloat(activeRoute.points.lastOrNull()?.speedMs?.times(3.6f) ?: 0f, 1)}",
                            unit = "km/h"
                        )
                        VerticalDivider(
                            modifier = Modifier.height(40.dp),
                            color = GymSmartColors.Divider
                        )
                        MetricItem(
                            label = "Altitud",
                            value = "${formatDouble(activeRoute.points.lastOrNull()?.altitudeM ?: 0.0, 0)}",
                            unit = "m"
                        )
                        VerticalDivider(
                            modifier = Modifier.height(40.dp),
                            color = GymSmartColors.Divider
                        )
                        MetricItem(
                            label = "Puntos",
                            value = "${activeRoute.points.size}",
                            unit = ""
                        )
                    }
                }
            }

            // --- Mapa ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                MapComponent(
                    points = activeRoute.points,
                    completedPoints = activeRoute.points,
                    userLocation = activeRoute.points.lastOrNull(),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // --- Error ---
            errorMsg?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = GymSmartColors.Error.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "⚠ $it",
                        modifier = Modifier.padding(12.dp),
                        color = GymSmartColors.Error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // --- Botón iniciar / parar ---
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
                        if (activeRoute.points.isNotEmpty()) showSaveDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeRoute.isRecording) GymSmartColors.Error else GymSmartColors.Primary,
                    contentColor = GymSmartColors.OnPrimary,
                    disabledContainerColor = GymSmartColors.Outline,
                    disabledContentColor = GymSmartColors.TextDisabled
                ),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    if (activeRoute.isRecording) "Parar ruta" else "Iniciar ruta",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

// --- Componente auxiliar de métrica ---
@Composable
private fun MetricItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = GymSmartColors.TextSecondary
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = GymSmartColors.TextPrimary
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = GymSmartColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

// --- Funciones de cálculo (sin cambios) ---
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