package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.ui.theme.GymSmartColors
import kotlinx.coroutines.launch

@Composable
fun ResetPasswordScreen(
    navController: NavController,
    authService:   AuthService,
    token:         String,
    fromProfile:   Boolean = false
) {

    var newPassword by remember { mutableStateOf("") }
    var confirm     by remember { mutableStateOf("") }
    var isLoading   by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf("") }
    var success     by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor      = GymSmartColors.Primary,
        unfocusedBorderColor    = GymSmartColors.Outline,
        focusedLabelColor       = GymSmartColors.Primary,
        unfocusedLabelColor     = GymSmartColors.TextSecondary,
        cursorColor             = GymSmartColors.Primary,
        focusedTextColor        = GymSmartColors.TextPrimary,
        unfocusedTextColor      = GymSmartColors.TextPrimary,
        focusedContainerColor   = GymSmartColors.SurfaceCard,
        unfocusedContainerColor = GymSmartColors.SurfaceCard,
        errorBorderColor        = GymSmartColors.Error,
        errorLabelColor         = GymSmartColors.Error,
        errorContainerColor     = GymSmartColors.SurfaceCard,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GymSmartColors.Background)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (success) {

            // ── Estado de éxito ───────────────────────────────────────────────

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(GymSmartColors.SurfaceCard, shape = MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", style = MaterialTheme.typography.displayLarge, color = GymSmartColors.Primary)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "¡Listo!",
                style = MaterialTheme.typography.headlineLarge,
                color = GymSmartColors.TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Tu contraseña ha sido actualizada correctamente.",
                style = MaterialTheme.typography.bodyMedium,
                color = GymSmartColors.TextSecondary
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (fromProfile) {
                        navController.popBackStack(Screen.Profile.route, inclusive = false)
                    } else {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GymSmartColors.Primary,
                    contentColor   = GymSmartColors.OnPrimary
                )
            ) {
                Text(
                    if (fromProfile) "Volver al perfil" else "Ir al login",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }

        } else {

            // ── Formulario nueva contraseña ───────────────────────────────────

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(GymSmartColors.SurfaceCard, shape = MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Text("🔒", style = MaterialTheme.typography.headlineLarge)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Nueva contraseña",
                style = MaterialTheme.typography.headlineMedium,
                color = GymSmartColors.TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Introduce tu nueva contraseña",
                style = MaterialTheme.typography.bodyMedium,
                color = GymSmartColors.TextSecondary
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Nueva contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = fieldColors
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it; errorMsg = "" },
                label = { Text("Confirmar contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                isError = errorMsg.isNotEmpty(),
                colors = fieldColors
            )

            Spacer(Modifier.height(12.dp))

            if (errorMsg.isNotEmpty()) {
                Text(
                    errorMsg,
                    color = GymSmartColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    if (newPassword != confirm) { errorMsg = "Las contraseñas no coinciden"; return@Button }
                    if (newPassword.length < 6) { errorMsg = "Mínimo 6 caracteres"; return@Button }
                    scope.launch {
                        isLoading = true
                        errorMsg = ""
                        val result = authService.resetPassword(token, newPassword)
                        if (result.success) success = true
                        else errorMsg = result.message
                        isLoading = false
                    }
                },
                enabled = newPassword.isNotBlank() && confirm.isNotBlank() && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor         = GymSmartColors.Primary,
                    disabledContainerColor = GymSmartColors.Outline,
                    contentColor           = GymSmartColors.OnPrimary,
                    disabledContentColor   = GymSmartColors.TextDisabled
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = GymSmartColors.OnPrimary,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Cambiar contraseña",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}