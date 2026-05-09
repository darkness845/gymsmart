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
import com.gymsmart.gymsmart.model.RoutePoint
import com.gymsmart.gymsmart.model.RoutePointDto
import com.gymsmart.gymsmart.services.GpsService
import com.gymsmart.gymsmart.services.LocationProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    navController: NavController,
    routeId: String,
    gpsService: GpsService,
    locationProvider: LocationProvider
) {
    val accentYellow = Color(0xFFFFC107)
    val bgCream = Color(0xFFF5F5F5)

    var allPoints by remember { mutableStateOf<List<RoutePointDto>>(emptyList()) }
    var routePoints by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    var completedPoints by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isFollowing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var routeName by remember { mutableStateOf("Ruta") }
    var progress by remember { mutableStateOf(0f) }
    var currentPointIndex by remember { mutableStateOf(0) }
    var showCompletedDialog by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<RoutePoint?>(null) }

    // Cargamos la ruta
    LaunchedEffect(routeId) {
        val routesResult = gpsService.getMyRoutes()
        if (routesResult.isSuccess) {
            routesResult.getOrNull()?.find { it.id == routeId }?.let { routeName = it.name }
        }
        val result = gpsService.getRoutePoints(routeId)
        isLoading = false
        if (result.isSuccess) {
            allPoints = result.getOrDefault(emptyList())
            routePoints = allPoints.map { it.toRoutePoint() }
        } else {
            errorMsg = "No se pudo cargar la ruta"
        }
    }

    // GPS en tiempo real — compara posición actual con puntos de la ruta
    LaunchedEffect(isFollowing) {
        if (!isFollowing || routePoints.isEmpty()) return@LaunchedEffect

        currentPointIndex = 0
        completedPoints = emptyList()
        progress = 0f

        locationProvider.getLocationUpdates().collect { userPoint ->
            userLocation = userPoint

            if (currentPointIndex >= routePoints.size) return@collect

            // Solo miramos el punto actual y el siguiente (máximo 1 de margen)
            val indicesToCheck = listOf(currentPointIndex, currentPointIndex + 1)
                .filter { it < routePoints.size }

            for (idx in indicesToCheck) {
                val distancia = haversine(routePoints[idx], userPoint)
                if (distancia < 20.0) {
                    // Avanzamos hasta ese índice
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

    if (showCompletedDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text("🎉 ¡Ruta completada!", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Has completado la ruta $routeName.")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatDouble(calcularDistanciaTotal(routePoints) / 1000, 2)} km recorridos",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCompletedDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
                ) {
                    Text("¡Genial!", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(routeName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Detalle de ruta", fontSize = 12.sp, color = Color.Gray)
                    }
                },
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
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentYellow)
                    }
                }

                errorMsg != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Text("⚠️ $errorMsg", modifier = Modifier.padding(12.dp), color = Color.Red)
                    }
                }

                else -> {
                    // Stats
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val distanciaTotal = calcularDistanciaTotal(routePoints)
                            val distanciaCompletada = calcularDistanciaTotal(completedPoints)
                            MetricBox("Total", "${formatDouble(distanciaTotal / 1000, 2)} km", Color.Gray)
                            MetricBox("Completado", "${formatDouble(distanciaCompletada / 1000, 2)} km", accentYellow)
                            MetricBox("Progreso", "${(progress * 100).toInt()}%", accentYellow)
                        }
                    }

                    // Barra de progreso
                    if (isFollowing || progress > 0f) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Progreso de la ruta", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    color = accentYellow,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = accentYellow,
                                trackColor = Color.LightGray
                            )
                        }
                    }

                    // Mapa
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
                    ) {
                        MapComponent(
                            points = routePoints,
                            completedPoints = completedPoints,
                            userLocation = userLocation,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Botón iniciar/parar seguimiento
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) Color.Red else accentYellow
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(
                            if (isFollowing) "⏹ Parar seguimiento" else "▶ Iniciar seguimiento",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

private fun <T> List<T>.indexOfMinBy(selector: (T) -> Double): Int {
    if (isEmpty()) return -1
    var minIndex = 0
    var minValue = selector(this[0])
    for (i in 1 until size) {
        val value = selector(this[i])
        if (value < minValue) {
            minValue = value
            minIndex = i
        }
    }
    return minIndex
}

private fun calcularDistanciaTotal(points: List<RoutePoint>): Double {
    var total = 0.0
    for (i in 1 until points.size) total += haversine(points[i - 1], points[i])
    return total
}

private fun RoutePointDto.toRoutePoint() = RoutePoint(
    lat = lat,
    lon = lon,
    timestamp = timestamp,
    speedMs = speedMs,
    altitudeM = altitudeM,
    accuracyM = accuracyM
)