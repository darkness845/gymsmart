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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.ui.theme.GymSmartColors
import kotlinx.coroutines.launch

@Composable
fun ForgotPasswordScreen(
    navController: NavController,
    authService:   AuthService,
    fromProfile:   Boolean = false,
    prefillEmail:  String  = ""
) {

    var email         by remember { mutableStateOf(prefillEmail) }
    var emailSent     by remember { mutableStateOf(false) }
    var token         by remember { mutableStateOf("") }
    var isLoading     by remember { mutableStateOf(false) }
    var errorMsg      by remember { mutableStateOf("") }

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

    LaunchedEffect(prefillEmail) {
        if (prefillEmail.isNotBlank()) {
            isLoading = true
            authService.forgotPassword(prefillEmail)
            emailSent = true
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GymSmartColors.Background)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Icono contextual
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(GymSmartColors.SurfaceCard, shape = MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (!emailSent) "🔑" else "✉",
                style = MaterialTheme.typography.headlineLarge
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            if (!emailSent) "Recuperar contraseña" else "Introduce el código",
            style = MaterialTheme.typography.headlineMedium,
            color = GymSmartColors.TextPrimary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            if (!emailSent) "Introduce tu email y te enviaremos un código"
            else "Revisa tu correo e introduce el código que te hemos enviado",
            style = MaterialTheme.typography.bodyMedium,
            color = GymSmartColors.TextSecondary
        )

        if (emailSent) {
            Spacer(Modifier.height(4.dp))
            Text(
                email,
                style = MaterialTheme.typography.bodyMedium,
                color = GymSmartColors.Primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(32.dp))

        if (!emailSent) {

            // ── Paso 1: introducir email ──────────────────────────────────────

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMsg = "" },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
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
                    scope.launch {
                        isLoading = true
                        errorMsg = ""
                        authService.forgotPassword(email)
                        emailSent = true
                        isLoading = false
                    }
                },
                enabled = email.isNotBlank() && !isLoading,
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
                    Text("Enviar código", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = { navController.popBackStack() }) {
                Text("← Volver", color = GymSmartColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }

        } else {

            // ── Paso 2: introducir código ─────────────────────────────────────

            OutlinedTextField(
                value = token,
                onValueChange = { token = it; errorMsg = "" },
                label = { Text("Código del correo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                isError = errorMsg.isNotEmpty(),
                colors = fieldColors
            )

            if (errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    errorMsg,
                    color = GymSmartColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMsg = ""
                        val result = authService.verifyResetToken(token.trim())
                        if (result.success) {
                            navController.navigate(Screen.ResetPassword.route(token.trim(), fromProfile))
                        } else {
                            errorMsg = result.message
                        }
                        isLoading = false
                    }
                },
                enabled = token.isNotBlank() && !isLoading,
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
                    Text("Verificar código", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (fromProfile) {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("← Volver al perfil", color = GymSmartColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                TextButton(onClick = { emailSent = false; token = ""; errorMsg = "" }) {
                    Text("← Cambiar email", color = GymSmartColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}