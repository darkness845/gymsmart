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
import com.gymsmart.gymsmart.ui.theme.MelocotonTheme
import com.gymsmart.gymsmart.ui.theme.Palette
import com.gymsmart.gymsmart.ui.theme.SystemBarsAppearance

/**
 * CommunityRoutesScreen con el estilo Melocotón (Tarea 3). Solo cambia el aspecto: la estructura y la lógica
 * de la pantalla son las de siempre, en [CommunityRoutesScreenContent].
 */
@Composable
fun CommunityRoutesScreen(
    navController: NavController,
    gpsService: GpsService
) {
    MelocotonTheme {
        // Fondo crema hasta arriba: la hora y la batería tienen que ir en oscuro.
        SystemBarsAppearance(darkStatusIcons = true)
        CommunityRoutesScreenContent(navController, gpsService)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityRoutesScreenContent(
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Palette.current.accentText)
                    }
                }
                errorMsg != null -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Palette.current.danger.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "⚠ $errorMsg",
                            modifier = Modifier.padding(12.dp),
                            color = Palette.current.danger,
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
                                color = Palette.current.textSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "¡Sé el primero en publicar una!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.current.chevron
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
        color = Palette.current.surface,
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
                color = Palette.current.textPrimary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricChip("${formatDouble(route.distanceMeters / 1000, 2)} km")
                Text("·", color = Palette.current.divider)
                MetricChip(formatDuration(route.durationSeconds))
                Text("·", color = Palette.current.divider)
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
        color = Palette.current.textSecondary
    )
}