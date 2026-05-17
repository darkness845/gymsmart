package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.model.GpsRouteResponse
import com.gymsmart.gymsmart.navigation.RouteDetailArgs
import com.gymsmart.gymsmart.services.GpsService
import com.gymsmart.gymsmart.ui.theme.GymSmartColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityRoutesScreen(
    navController: NavController,
    gpsService: GpsService
) {
    var routes    by remember { mutableStateOf<List<GpsRouteResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = gpsService.getCommunityRoutes()
        isLoading = false
        if (result.isSuccess) routes = result.getOrDefault(emptyList())
        else errorMsg = "No se pudieron cargar las rutas"
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Rutas de la comunidad",
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                routes.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No hay rutas públicas aún",
                                style = MaterialTheme.typography.titleMedium,
                                color = GymSmartColors.TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "¡Sé el primero en publicar una!",
                                style = MaterialTheme.typography.bodySmall,
                                color = GymSmartColors.TextDisabled
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(routes, key = { it.id }) { route ->
                            CommunityRouteCard(
                                route = route,
                                onClick = {
                                    navController.navigate(RouteDetailArgs(routeId = route.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityRouteCard(
    route: GpsRouteResponse,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = GymSmartColors.SurfaceCard,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                route.name,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = GymSmartColors.TextPrimary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricChip("${formatDouble(route.distanceMeters / 1000, 2)} km")
                Text("·", color = GymSmartColors.Divider)
                MetricChip(formatDuration(route.durationSeconds))
                Text("·", color = GymSmartColors.Divider)
                MetricChip("${route.pointCount} pts")
            }
        }
    }
}

@Composable
private fun MetricChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = GymSmartColors.TextSecondary
    )
}