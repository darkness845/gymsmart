package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.model.GpsRouteResponse
import com.gymsmart.gymsmart.navigation.RouteDetailArgs
import com.gymsmart.gymsmart.services.GpsService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityRoutesScreen(
    navController: NavController,
    gpsService: GpsService
) {
    val accentYellow = Color(0xFFFFC107)
    val bgCream = Color(0xFFF5F5F5)

    var routes by remember { mutableStateOf<List<GpsRouteResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = gpsService.getCommunityRoutes()
        isLoading = false
        if (result.isSuccess) routes = result.getOrDefault(emptyList())
        else errorMsg = "No se pudieron cargar las rutas"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rutas de la comunidad", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
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
                routes.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No hay rutas públicas aún", color = Color.Gray, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("¡Sé el primero en publicar una!", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(route.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatDouble(route.distanceMeters / 1000, 2)} km  •  ${formatDuration(route.durationSeconds)}  •  ${route.pointCount} puntos",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}