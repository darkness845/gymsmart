package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VisibilityOff
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRoutesScreen(
    navController: NavController,
    gpsService: GpsService
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var routes    by remember { mutableStateOf<List<GpsRouteResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = gpsService.getMyRoutes()
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
                            "Mis rutas",
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
                    actions = {
                        TextButton(onClick = { navController.navigate("community_routes") }) {
                            Text(
                                "Comunidad",
                                color = GymSmartColors.Primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GymSmartColors.Background
                    )
                )
            }
        },
        containerColor = GymSmartColors.Background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = GymSmartColors.SurfaceElevated,
                    contentColor = GymSmartColors.TextPrimary,
                    actionColor = GymSmartColors.Primary
                )
            }
        }
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
                                "No tienes rutas guardadas",
                                style = MaterialTheme.typography.titleMedium,
                                color = GymSmartColors.TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Graba tu primera ruta en GPS",
                                style = MaterialTheme.typography.bodySmall,
                                color = GymSmartColors.TextDisabled
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(routes, key = { it.id }) { route ->
                            MyRouteCard(
                                route = route,
                                onClick = {
                                    navController.navigate(RouteDetailArgs(routeId = route.id))
                                },
                                onPublishToggle = {
                                    scope.launch {
                                        if (route.isPublic) gpsService.unpublishRoute(route.id)
                                        else gpsService.publishRoute(route.id)
                                        val result = gpsService.getMyRoutes()
                                        if (result.isSuccess) routes = result.getOrDefault(emptyList())
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        gpsService.deleteRoute(route.id)
                                        routes = routes.filter { it.id != route.id }
                                    }
                                },
                                onPublishBlocked = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "La ruta debe tener al menos 0.1 km para publicarse",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
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
private fun MyRouteCard(
    route: GpsRouteResponse,
    onClick: () -> Unit,
    onPublishToggle: () -> Unit,
    onDelete: () -> Unit,
    onPublishBlocked: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = GymSmartColors.SurfaceCard,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        route.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = GymSmartColors.TextPrimary
                    )
                    if (route.isPublic) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = GymSmartColors.RoutePending.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Pública",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = GymSmartColors.RoutePending
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${formatDouble(route.distanceMeters / 1000, 2)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = GymSmartColors.TextSecondary
                    )
                    Text("·", color = GymSmartColors.Divider)
                    Text(
                        formatDuration(route.durationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = GymSmartColors.TextSecondary
                    )
                    Text("·", color = GymSmartColors.Divider)
                    Text(
                        "${route.pointCount} pts",
                        style = MaterialTheme.typography.bodySmall,
                        color = GymSmartColors.TextSecondary
                    )
                }
            }

            IconButton(
                onClick = {
                    if (route.distanceMeters >= 100.0) onPublishToggle()
                    else onPublishBlocked()
                }
            ) {
                Icon(
                    imageVector = if (route.isPublic) Icons.Default.VisibilityOff else Icons.Default.Public,
                    contentDescription = if (route.isPublic) "Despublicar" else "Publicar",
                    tint = when {
                        route.isPublic              -> GymSmartColors.Primary
                        route.distanceMeters >= 100 -> GymSmartColors.TextSecondary
                        else                        -> GymSmartColors.TextDisabled
                    }
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = GymSmartColors.Error.copy(alpha = 0.6f)
                )
            }
        }
    }
}