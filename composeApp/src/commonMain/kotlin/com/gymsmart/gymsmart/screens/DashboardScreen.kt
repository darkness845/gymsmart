package com.gymsmart.gymsmart.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.services.HealthDataProvider
import com.gymsmart.gymsmart.ui.theme.GymSmartColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    healthDataProvider: HealthDataProvider
) {
    val authService = remember { AuthService() }
    val scope = rememberCoroutineScope()

    var todaySteps    by remember { mutableStateOf<Long?>(null) }
    var todayCalories by remember { mutableStateOf<Double?>(null) }
    var menuExpanded  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        runCatching { healthDataProvider.getTodaySteps() }
            .onSuccess { todaySteps = it }
            .onFailure { println("Pasos: ${it.message}") }
        runCatching { healthDataProvider.getTodayActiveCalories() }
            .onSuccess { todayCalories = it }
            .onFailure { println("Calorías: ${it.message}") }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("Gym", style = MaterialTheme.typography.headlineLarge, color = GymSmartColors.TextPrimary, fontWeight = FontWeight.ExtraBold)
                            Text("Smart", style = MaterialTheme.typography.headlineLarge, color = GymSmartColors.Primary, fontWeight = FontWeight.ExtraBold)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menú", tint = GymSmartColors.TextSecondary)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                containerColor = GymSmartColors.SurfaceElevated,
                                shape = MaterialTheme.shapes.medium,
                                shadowElevation = 8.dp
                            ) {
                                // Header con avatar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = GymSmartColors.Primary.copy(alpha = 0.15f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Text("G", fontWeight = FontWeight.Bold, color = GymSmartColors.Primary)
                                            }
                                        }
                                        Column {
                                            Text(
                                                "GymSmart",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = GymSmartColors.TextPrimary
                                            )
                                            Text(
                                                "Menú de usuario",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GymSmartColors.TextSecondary
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = GymSmartColors.Divider)

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Mi perfil",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = GymSmartColors.TextPrimary
                                        )
                                    },
                                    leadingIcon = {
                                        Surface(
                                            modifier = Modifier.size(32.dp),
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = GymSmartColors.Primary.copy(alpha = 0.12f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Icon(
                                                    Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = GymSmartColors.Primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = { menuExpanded = false; navController.navigate(Screen.Profile.route) },
                                    colors = MenuItemColors(
                                        textColor = GymSmartColors.TextPrimary,
                                        leadingIconColor = GymSmartColors.Primary,
                                        trailingIconColor = GymSmartColors.TextPrimary,
                                        disabledTextColor = GymSmartColors.TextDisabled,
                                        disabledLeadingIconColor = GymSmartColors.TextDisabled,
                                        disabledTrailingIconColor = GymSmartColors.TextDisabled
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                HorizontalDivider(
                                    color = GymSmartColors.Divider,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Cerrar sesión",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = GymSmartColors.Error
                                        )
                                    },
                                    leadingIcon = {
                                        Surface(
                                            modifier = Modifier.size(32.dp),
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = GymSmartColors.Error.copy(alpha = 0.12f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Logout,
                                                    contentDescription = null,
                                                    tint = GymSmartColors.Error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        scope.launch {
                                            authService.logout()
                                            navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                                        }
                                    },
                                    colors = MenuItemColors(
                                        textColor = GymSmartColors.Error,
                                        leadingIconColor = GymSmartColors.Error,
                                        trailingIconColor = GymSmartColors.Error,
                                        disabledTextColor = GymSmartColors.TextDisabled,
                                        disabledLeadingIconColor = GymSmartColors.TextDisabled,
                                        disabledTrailingIconColor = GymSmartColors.TextDisabled
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = GymSmartColors.Background)
                )
            }
        },
        containerColor = GymSmartColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Text("Tu progreso diario", style = MaterialTheme.typography.bodyMedium, color = GymSmartColors.TextSecondary)

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WearableChip(icon = "👟", label = "Pasos", value = todaySteps?.toString() ?: "...")
                WearableChip(icon = "🔥", label = "Kcal activas", value = todayCalories?.toInt()?.toString() ?: "...")
            }

            Spacer(Modifier.height(24.dp))

            AnimatedCard(
                onClick = { navController.navigate(Screen.Subscription.route) },
                height = 110.dp,
                containerColor = GymSmartColors.SurfaceCard,
                borderColor = GymSmartColors.Primary
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Planes", style = MaterialTheme.typography.titleLarge, color = GymSmartColors.TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PlanBadge("Free", GymSmartColors.TextSecondary)
                            PlanBadge("Premium", GymSmartColors.PremiumGold)
                        }
                    }
                    Text("→", fontSize = 22.sp, color = GymSmartColors.Primary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedMiniCard("Nutrición", "🥗", { navController.navigate(Screen.Nutrition.route) }, Modifier.weight(1f))
                    AnimatedMiniCard("Peso",      "⚖️", { navController.navigate(Screen.Weight.route) },    Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedMiniCard("GPS",       "📍", { navController.navigate(Screen.Gps.route) },       Modifier.weight(1f))
                    AnimatedMiniCard("Mis Rutas", "🗺️", { navController.navigate(Screen.MyRoutes.route) },  Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Componentes ───────────────────────────────────────────────────────────────

@Composable
private fun WearableChip(icon: String, label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = GymSmartColors.SurfaceCard
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(icon, fontSize = 14.sp)
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextSecondary)
                Text(value, style = MaterialTheme.typography.labelLarge, color = GymSmartColors.Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PlanBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AnimatedCard(
    onClick: () -> Unit,
    height: Dp,
    containerColor: Color = GymSmartColors.SurfaceCard,
    borderColor: Color = GymSmartColors.Outline,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .scale(scale)
            .clip(MaterialTheme.shapes.medium)
            .background(containerColor)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
fun AnimatedMiniCard(
    title: String,
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f)

    Box(
        modifier = modifier
            .height(90.dp)
            .scale(scale)
            .clip(MaterialTheme.shapes.medium)
            .background(GymSmartColors.SurfaceCard)
            .border(1.dp, GymSmartColors.Outline, MaterialTheme.shapes.medium)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Text(emoji, fontSize = 24.sp)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = GymSmartColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}