package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VisibilityOff
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRoutesScreen(
    navController: NavController,
    gpsService: GpsService
) {
    val accentYellow = Color(0xFFFFC107)
    val bgCream = Color(0xFFF5F5F5)
    val scope = rememberCoroutineScope()

    var routes by remember { mutableStateOf<List<GpsRouteResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = gpsService.getMyRoutes()
        isLoading = false
        if (result.isSuccess) routes = result.getOrDefault(emptyList())
        else errorMsg = "No se pudieron cargar las rutas"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Rutas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = { navController.navigate("community_routes") }) {
                        Text("Comunidad", color = accentYellow, fontWeight = FontWeight.Bold)
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
                            Text("No tienes rutas guardadas", color = Color.Gray, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Graba tu primera ruta en GPS", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(routes, key = { it.id }) { route ->
                            RouteCard(
                                route = route,
                                onClick = {
                                    navController.navigate(RouteDetailArgs(routeId = route.id))
                                },
                                onPublishToggle = {
                                    scope.launch {
                                        if (route.isPublic) gpsService.unpublishRoute(route.id)
                                        else gpsService.publishRoute(route.id)
                                        // Recargamos la lista
                                        val result = gpsService.getMyRoutes()
                                        if (result.isSuccess) routes = result.getOrDefault(emptyList())
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        gpsService.deleteRoute(route.id)
                                        routes = routes.filter { it.id != route.id }
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
private fun RouteCard(
    route: GpsRouteResponse,
    onClick: () -> Unit,
    onPublishToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val accentYellow = Color(0xFFFFC107)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(route.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (route.isPublic) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = accentYellow
                        ) {
                            Text(
                                "Pública",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatDouble(route.distanceMeters / 1000, 2)} km  •  ${formatDuration(route.durationSeconds)}  •  ${route.pointCount} puntos",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            // Botón publicar/despublicar
            IconButton(
                onClick = onPublishToggle,
                enabled = route.distanceMeters >= 100.0
            ) {
                Icon(
                    imageVector = if (route.isPublic)
                        Icons.Default.VisibilityOff
                    else
                        Icons.Default.Public,
                    contentDescription = if (route.isPublic) "Despublicar" else "Publicar",
                    tint = if (route.isPublic) accentYellow
                    else if (route.distanceMeters >= 100.0) Color.Gray
                    else Color.LightGray
                )
            }
            // Botón eliminar
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.LightGray)
            }
        }
    }
}