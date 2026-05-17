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
import com.gymsmart.gymsmart.model.RoutePoint
import com.gymsmart.gymsmart.model.RoutePointDto
import com.gymsmart.gymsmart.services.GpsService
import com.gymsmart.gymsmart.services.LocationProvider
import com.gymsmart.gymsmart.ui.theme.GymSmartColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    navController: NavController,
    routeId: String,
    gpsService: GpsService,
    locationProvider: LocationProvider
) {
    var allPoints          by remember { mutableStateOf<List<RoutePointDto>>(emptyList()) }
    var routePoints        by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    var completedPoints    by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    var isLoading          by remember { mutableStateOf(true) }
    var isFollowing        by remember { mutableStateOf(false) }
    var errorMsg           by remember { mutableStateOf<String?>(null) }
    var routeName          by remember { mutableStateOf("Ruta") }
    var progress           by remember { mutableStateOf(0f) }
    var currentPointIndex  by remember { mutableStateOf(0) }
    var showCompletedDialog by remember { mutableStateOf(false) }
    var userLocation       by remember { mutableStateOf<RoutePoint?>(null) }

    LaunchedEffect(routeId) {
        val routesResult = gpsService.getMyRoutes()
        if (routesResult.isSuccess) {
            routesResult.getOrNull()?.find { it.id == routeId }?.let { routeName = it.name }
        }
        val result = gpsService.getRoutePoints(routeId)
        isLoading = false
        if (result.isSuccess) {
            allPoints   = result.getOrDefault(emptyList())
            routePoints = allPoints.map { it.toRoutePoint() }
        } else {
            errorMsg = "No se pudo cargar la ruta"
        }
    }

    LaunchedEffect(isFollowing) {
        if (!isFollowing || routePoints.isEmpty()) return@LaunchedEffect
        currentPointIndex = 0; completedPoints = emptyList(); progress = 0f

        locationProvider.getLocationUpdates().collect { userPoint ->
            userLocation = userPoint
            if (currentPointIndex >= routePoints.size) return@collect
            val indicesToCheck = listOf(currentPointIndex, currentPointIndex + 1)
                .filter { it < routePoints.size }
            for (idx in indicesToCheck) {
                if (haversine(routePoints[idx], userPoint) < 20.0) {
                    currentPointIndex = idx + 1
                    completedPoints = routePoints.subList(0, currentPointIndex)
                    progress = currentPointIndex.toFloat() / routePoints.size.toFloat()
                    break
                }
            }
            if (currentPointIndex >= routePoints.size) {
                isFollowing = false
                showCompletedDialog = true
            }
        }
    }

    // ── Diálogo ruta completada ──────────────────────────────────────────────
    if (showCompletedDialog) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = GymSmartColors.SurfaceElevated,
            shape = MaterialTheme.shapes.large,
            title = {
                Text(
                    "🎉 ¡Ruta completada!",
                    fontWeight = FontWeight.Bold,
                    color = GymSmartColors.TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Has completado la ruta $routeName.",
                        color = GymSmartColors.TextPrimary
                    )
                    Text(
                        "${formatDouble(calcularDistanciaTotal(routePoints) / 1000, 2)} km recorridos",
                        style = MaterialTheme.typography.bodySmall,
                        color = GymSmartColors.TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCompletedDialog = false },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GymSmartColors.RouteCompleted,
                        contentColor   = GymSmartColors.Background
                    )
                ) {
                    Text("¡Genial!", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                routeName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = GymSmartColors.TextPrimary
                            )
                            Text(
                                "Detalle de ruta",
                                style = MaterialTheme.typography.labelMedium,
                                color = GymSmartColors.TextSecondary
                            )
                        }
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
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GymSmartColors.Primary)
                    }
                }
                errorMsg != null -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GymSmartColors.Error.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "⚠ $errorMsg",
                            modifier = Modifier.padding(12.dp),
                            color = GymSmartColors.Error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                else -> {
                    // ── Stats ────────────────────────────────────────────
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GymSmartColors.SurfaceCard,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val distanciaTotal      = calcularDistanciaTotal(routePoints)
                            val distanciaCompletada = calcularDistanciaTotal(completedPoints)
                            RouteMetricItem(
                                label = "Total",
                                value = "${formatDouble(distanciaTotal / 1000, 2)} km",
                                highlight = false
                            )
                            VerticalDivider(modifier = Modifier.height(36.dp), color = GymSmartColors.Divider)
                            RouteMetricItem(
                                label = "Completado",
                                value = "${formatDouble(distanciaCompletada / 1000, 2)} km",
                                highlight = true
                            )
                            VerticalDivider(modifier = Modifier.height(36.dp), color = GymSmartColors.Divider)
                            RouteMetricItem(
                                label = "Progreso",
                                value = "${(progress * 100).toInt()}%",
                                highlight = true
                            )
                        }
                    }

                    // ── Barra de progreso ─────────────────────────────────
                    if (isFollowing || progress > 0f) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Progreso de la ruta",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GymSmartColors.TextSecondary
                                )
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GymSmartColors.RouteProgress
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(MaterialTheme.shapes.extraSmall),
                                color = GymSmartColors.RouteProgress,
                                trackColor = GymSmartColors.Outline
                            )
                        }
                    }

                    // ── Mapa ──────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                    ) {
                        MapComponent(
                            points = routePoints,
                            completedPoints = completedPoints,
                            userLocation = userLocation,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // ── Botón iniciar / parar ─────────────────────────────
                    Button(
                        onClick = {
                            isFollowing = !isFollowing
                            if (!isFollowing) {
                                completedPoints = emptyList()
                                progress = 0f
                                currentPointIndex = 0
                            }
                        },
                        enabled = routePoints.isNotEmpty(),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor         = if (isFollowing) GymSmartColors.Error else GymSmartColors.Primary,
                            contentColor           = GymSmartColors.OnPrimary,
                            disabledContainerColor = GymSmartColors.Outline,
                            disabledContentColor   = GymSmartColors.TextDisabled
                        )
                    ) {
                        Text(
                            if (isFollowing) "⏹  Parar seguimiento" else "▶  Iniciar seguimiento",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun RouteMetricItem(label: String, value: String, highlight: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = GymSmartColors.TextSecondary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) GymSmartColors.RouteProgress else GymSmartColors.TextPrimary
        )
    }
}

private fun calcularDistanciaTotal(points: List<RoutePoint>): Double {
    var total = 0.0
    for (i in 1 until points.size) total += haversine(points[i - 1], points[i])
    return total
}

private fun RoutePointDto.toRoutePoint() = RoutePoint(
    lat = lat, lon = lon, timestamp = timestamp,
    speedMs = speedMs, altitudeM = altitudeM, accuracyM = accuracyM
)