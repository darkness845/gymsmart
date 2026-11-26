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
import com.gymsmart.gymsmart.services.RecoverableRoute
import com.gymsmart.gymsmart.services.RouteRecoveryStore
import com.gymsmart.gymsmart.services.rememberBackgroundTrackingController
import com.gymsmart.gymsmart.ui.theme.MelocotonTheme
import com.gymsmart.gymsmart.ui.theme.Palette
import com.gymsmart.gymsmart.ui.theme.SystemBarsAppearance
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.time.Clock

/**
 * GpsScreen con el estilo Melocotón (Tarea 3). Solo cambia el aspecto: la estructura y la lógica
 * de la pantalla son las de siempre, en [GpsScreenContent].
 */
@Composable
fun GpsScreen(
    navController: NavController,
    locationProvider: LocationProvider,
    onRequestPermission: (onResult: (Boolean) -> Unit) -> Unit,
    gpsService: GpsService
) {
    MelocotonTheme {
        // Fondo crema hasta arriba: la hora y la batería tienen que ir en oscuro.
        SystemBarsAppearance(darkStatusIcons = true)
        GpsScreenContent(navController, locationProvider, onRequestPermission, gpsService)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GpsScreenContent(
    navController: NavController,
    locationProvider: LocationProvider,
    onRequestPermission: (onResult: (Boolean) -> Unit) -> Unit,
    gpsService: GpsService
) {
    val scope = rememberCoroutineScope()
    val recoveryStore = remember { RouteRecoveryStore() }
    val backgroundTracking = rememberBackgroundTrackingController()

    // Red de seguridad: si esta pantalla se destruye mientras se grababa (el usuario navega fuera),
    // no debe quedar un foreground service huérfano con su notificación. Parar un servicio que ya
    // está parado no hace nada, así que es seguro llamarlo siempre.
    DisposableEffect(Unit) {
        onDispose { backgroundTracking.stop() }
    }

    var activeRoute by remember { mutableStateOf(ActiveRoute()) }
    var startedAt by remember { mutableStateOf(0L) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var routeName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }

    // Si Android mató el proceso a mitad de una grabación anterior, ofrece recuperarla en vez de
    // perderla sin más (ver GPS-3 en doc/08-backlog-mejoras.md).
    var recoveredRoute by remember { mutableStateOf<RecoverableRoute?>(null) }
    LaunchedEffect(Unit) {
        recoveryStore.load()?.let { if (it.points.isNotEmpty()) recoveredRoute = it }
    }

    LaunchedEffect(activeRoute.isRecording) {
        if (activeRoute.isRecording) {
            // Arranca el foreground service ANTES de pedir las actualizaciones de ubicación: sin
            // esto, Android corta o estrangula el GPS en cuanto la pantalla se apaga o la app pasa
            // a segundo plano (ver GPS-1 en doc/08-backlog-mejoras.md).
            backgroundTracking.start()
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
                if (distanciaDesdeUltimo < 5.0) return@collect
                if (distanciaDesdeUltimo > 100.0) {
                    // Hueco de señal (túnel, app en segundo plano...): ese salto no es distancia
                    // real recorrida, así que no se suma al total, PERO se actualiza el punto de
                    // referencia. Si no se hiciera esto, el punto de referencia se queda congelado
                    // y todos los puntos siguientes también quedan a >100m de él, descartándose en
                    // cascada para el resto de la grabación (ver GPS-2 en doc/08-backlog-mejoras.md).
                    activeRoute = activeRoute.copy(points = activeRoute.points + punto)
                    return@collect
                }
                activeRoute = activeRoute.copy(
                    points = activeRoute.points + punto,
                    distanceMeters = activeRoute.distanceMeters + distanciaDesdeUltimo
                )
                // Guardado periódico (no en cada punto, para no escribir a disco cada segundo) por
                // si el proceso muere a mitad de la grabación.
                if (activeRoute.points.size % 10 == 0) {
                    recoveryStore.save(
                        RecoverableRoute(activeRoute.points, activeRoute.distanceMeters, startedAt)
                    )
                }
            }
        } else {
            backgroundTracking.stop()
        }
    }

    // --- Diálogo de guardado ---
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showSaveDialog = false },
            containerColor = Palette.current.surface,
            shape = MaterialTheme.shapes.large,
            title = {
                Text(
                    "Guardar ruta",
                    fontWeight = FontWeight.Bold,
                    color = Palette.current.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = Palette.current.surface,
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
                                color = Palette.current.accentText,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${activeRoute.points.size} puntos",
                                color = Palette.current.textSecondary,
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
                            focusedBorderColor = Palette.current.accentText,
                            unfocusedBorderColor = Palette.current.border,
                            focusedLabelColor = Palette.current.accentText,
                            unfocusedLabelColor = Palette.current.textSecondary,
                            cursorColor = Palette.current.accentText,
                            focusedTextColor = Palette.current.textPrimary,
                            unfocusedTextColor = Palette.current.textPrimary,
                            focusedContainerColor = Palette.current.surface,
                            unfocusedContainerColor = Palette.current.surface,
                        )
                    )
                    if (saveSuccess) {
                        Text(
                            "✅ Ruta guardada correctamente",
                            color = Palette.current.success,
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
                                recoveryStore.clear()
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
                        containerColor = Palette.current.hero,
                        disabledContainerColor = Palette.current.border,
                        contentColor = Palette.current.onHero,
                        disabledContentColor = Palette.current.chevron
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Palette.current.onHero
                        )
                    } else {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        recoveryStore.clear()
                        showSaveDialog = false
                        routeName = ""
                        activeRoute = ActiveRoute()
                        startedAt = 0L
                    },
                    enabled = !isSaving
                ) {
                    Text("Descartar", color = Palette.current.textSecondary)
                }
            }
        )
    }

    // --- Diálogo de recuperación de una ruta sin guardar ---
    recoveredRoute?.let { recovered ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = Palette.current.surface,
            shape = MaterialTheme.shapes.large,
            title = { Text("Ruta sin guardar", fontWeight = FontWeight.Bold, color = Palette.current.textPrimary) },
            text = {
                Text(
                    "Encontramos una ruta de ${formatDouble(recovered.distanceMeters / 1000, 2)} km que no llegó a guardarse. ¿Quieres seguir grabándola o descartarla?",
                    color = Palette.current.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeRoute = ActiveRoute(
                            points = recovered.points,
                            distanceMeters = recovered.distanceMeters,
                            isRecording = true
                        )
                        startedAt = recovered.startedAt
                        recoveredRoute = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Palette.current.hero,
                        contentColor = Palette.current.onHero
                    )
                ) {
                    Text("Seguir grabando", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    recoveryStore.clear()
                    recoveredRoute = null
                }) {
                    Text("Descartar", color = Palette.current.textSecondary)
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
                            color = Palette.current.textPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Volver",
                                tint = Palette.current.textPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Palette.current.background
                    )
                )
            }
        },
        containerColor = Palette.current.background
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
                color = Palette.current.surface,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Distancia",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.current.textSecondary
                    )
                    Text(
                        "${formatDouble(activeRoute.distanceMeters / 1000, 2)} km",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Palette.current.textPrimary
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Palette.current.divider)
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
                            color = Palette.current.divider
                        )
                        MetricItem(
                            label = "Altitud",
                            value = "${formatDouble(activeRoute.points.lastOrNull()?.altitudeM ?: 0.0, 0)}",
                            unit = "m"
                        )
                        VerticalDivider(
                            modifier = Modifier.height(40.dp),
                            color = Palette.current.divider
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
                    color = Palette.current.danger.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "⚠ $it",
                        modifier = Modifier.padding(12.dp),
                        color = Palette.current.danger,
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
                        if (activeRoute.points.isNotEmpty()) {
                            // Guardado final por si el guardado periódico (cada 10 puntos) se quedó
                            // corto respecto a los últimos puntos registrados antes de parar.
                            recoveryStore.save(RecoverableRoute(activeRoute.points, activeRoute.distanceMeters, startedAt))
                            showSaveDialog = true
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeRoute.isRecording) Palette.current.danger else Palette.current.hero,
                    contentColor = Palette.current.onHero,
                    disabledContainerColor = Palette.current.border,
                    disabledContentColor = Palette.current.chevron
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
            color = Palette.current.textSecondary
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Palette.current.textPrimary
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.current.textSecondary,
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