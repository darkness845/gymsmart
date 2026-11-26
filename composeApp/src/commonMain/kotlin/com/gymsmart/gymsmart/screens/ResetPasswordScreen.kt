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
import com.gymsmart.gymsmart.ui.theme.MelocotonTheme
import com.gymsmart.gymsmart.ui.theme.Palette
import com.gymsmart.gymsmart.ui.theme.SystemBarsAppearance
import kotlinx.coroutines.launch

/**
 * ResetPasswordScreen con el estilo Melocotón (Tarea 3). Solo cambia el aspecto: la estructura y la lógica
 * de la pantalla son las de siempre, en [ResetPasswordScreenContent].
 */
@Composable
fun ResetPasswordScreen(
    navController: NavController,
    authService:   AuthService,
    token:         String,
    fromProfile:   Boolean = false
) {
    MelocotonTheme {
        // Fondo crema hasta arriba: la hora y la batería tienen que ir en oscuro.
        SystemBarsAppearance(darkStatusIcons = true)
        ResetPasswordScreenContent(navController, authService, token, fromProfile)
    }
}

@Composable
private fun ResetPasswordScreenContent(
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
        focusedBorderColor      = Palette.current.accentText,
        unfocusedBorderColor    = Palette.current.border,
        focusedLabelColor       = Palette.current.accentText,
        unfocusedLabelColor     = Palette.current.textSecondary,
        cursorColor             = Palette.current.accentText,
        focusedTextColor        = Palette.current.textPrimary,
        unfocusedTextColor      = Palette.current.textPrimary,
        focusedContainerColor   = Palette.current.surface,
        unfocusedContainerColor = Palette.current.surface,
        errorBorderColor        = Palette.current.danger,
        errorLabelColor         = Palette.current.danger,
        errorContainerColor     = Palette.current.surface,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.current.background)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (success) {

            // ── Estado de éxito ───────────────────────────────────────────────

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Palette.current.surface, shape = MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", style = MaterialTheme.typography.displayLarge, color = Palette.current.accentText)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "¡Listo!",
                style = MaterialTheme.typography.headlineLarge,
                color = Palette.current.textPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Tu contraseña ha sido actualizada correctamente.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.current.textSecondary
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
                    containerColor = Palette.current.hero,
                    contentColor   = Palette.current.onHero
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
                    .background(Palette.current.surface, shape = MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Text("🔒", style = MaterialTheme.typography.headlineLarge)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Nueva contraseña",
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.current.textPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Introduce tu nueva contraseña",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.current.textSecondary
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
                    color = Palette.current.danger,
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
                    containerColor         = Palette.current.hero,
                    disabledContainerColor = Palette.current.border,
                    contentColor           = Palette.current.onHero,
                    disabledContentColor   = Palette.current.chevron
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Palette.current.onHero,
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