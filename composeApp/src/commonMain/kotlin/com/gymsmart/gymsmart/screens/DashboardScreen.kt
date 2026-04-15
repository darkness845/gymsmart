package com.gymsmart.gymsmart.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.services.AuthService
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(navController: NavController) {

    val background = Color(0xFFF5F5F5)
    val accent = Color(0xFFFFC107)
    val textPrimary = Color(0xFF1C1C1C)
    val textSecondary = Color(0xFF6B6B6B)

    val authService = remember { AuthService() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(20.dp)
    ) {

        Spacer(modifier = Modifier.height(12.dp))

        // Título + botón logout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GymSmart",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Text(
                    text = "Tu progreso diario",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary
                )
            }

            IconButton(onClick = {
                scope.launch {
                    authService.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Cerrar sesión",
                    tint = textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 🔥 CARD PRINCIPAL
        AnimatedCard(
            onClick = { navController.navigate(Screen.Training.route) },
            backgroundColor = accent,
            height = 130.dp
        ) {
            Column {
                Text(
                    "Entrenamiento",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    "Empieza tu rutina",
                    color = Color.Black.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                AnimatedMiniCard(
                    title = "Nutrición",
                    onClick = { navController.navigate(Screen.Nutrition.route) },
                    modifier = Modifier.weight(1f)
                )

                AnimatedMiniCard(
                    title = "Peso",
                    onClick = { navController.navigate(Screen.Weight.route) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                AnimatedMiniCard(
                    title = "GPS",
                    onClick = { navController.navigate(Screen.Gps.route) },
                    modifier = Modifier.weight(1f)
                )

                AnimatedMiniCard(
                    title = "Progreso",
                    onClick = { },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AnimatedCard(
    onClick: () -> Unit,
    backgroundColor: Color,
    height: Dp,
    content: @Composable ColumnScope.() -> Unit
) {

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(if (pressed) 0.96f else 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .scale(scale)
            .shadow(
                elevation = if (pressed) 4.dp else 10.dp,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}

@Composable
fun AnimatedMiniCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(if (pressed) 0.95f else 1f)

    Card(
        modifier = modifier
            .height(120.dp)
            .scale(scale)
            .shadow(
                elevation = if (pressed) 3.dp else 6.dp,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1C1C)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Abrir",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9E9E9E)
            )
        }
    }
}