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
fun RegisterScreen(navController: NavController, authService: AuthService) {

    var name      by remember { mutableStateOf("") }
    var email     by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var errorMsg  by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    var showVerification by remember { mutableStateOf(false) }
    var verifyToken      by remember { mutableStateOf("") }
    var verifyError      by remember { mutableStateOf("") }
    var isVerifying      by remember { mutableStateOf(false) }
    var isResending      by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Colores de TextField reutilizables
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
        if (!showVerification) {

            // ── Paso 1: formulario de registro ───────────────────────────────

            Text(
                "Crear cuenta",
                style = MaterialTheme.typography.headlineLarge,
                color = GymSmartColors.TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Únete a GymSmart",
                style = MaterialTheme.typography.bodyMedium,
                color = GymSmartColors.TextSecondary
            )

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = fieldColors
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = fieldColors
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                        val response = authService.register(name, email, password)
                        if (response.success && response.message == "VERIFY_EMAIL") {
                            showVerification = true
                        } else {
                            errorMsg = response.message
                        }
                        isLoading = false
                    }
                },
                enabled = !isLoading && name.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
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
                    Text("Crear cuenta", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { navController.navigate(Screen.Login.route) }) {
                Text("¿Ya tienes cuenta?  ", color = GymSmartColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Text("Inicia sesión", color = GymSmartColors.Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }

        } else {

            // ── Paso 2: verificar email ───────────────────────────────────────

            // Icono visual de email — simple círculo con acento
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(GymSmartColors.SurfaceCard, shape = MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Text("✉", style = MaterialTheme.typography.headlineLarge, color = GymSmartColors.Primary)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Verifica tu email",
                style = MaterialTheme.typography.headlineMedium,
                color = GymSmartColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Hemos enviado un código a",
                style = MaterialTheme.typography.bodyMedium,
                color = GymSmartColors.TextSecondary
            )
            Text(
                email,
                style = MaterialTheme.typography.bodyMedium,
                color = GymSmartColors.Primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = verifyToken,
                onValueChange = { verifyToken = it; verifyError = "" },
                label = { Text("Código de verificación") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                isError = verifyError.isNotEmpty(),
                colors = fieldColors
            )

            if (verifyError.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    verifyError,
                    color = if (verifyError.contains("reenviado")) GymSmartColors.Success else GymSmartColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        isVerifying = true
                        verifyError = ""
                        val result = authService.verifyEmail(verifyToken.trim())
                        if (result.success) {
                            navController.navigate(Screen.Onboarding.route) {
                                popUpTo(Screen.Register.route) { inclusive = true }
                            }
                        } else {
                            verifyError = result.message
                        }
                        isVerifying = false
                    }
                },
                enabled = verifyToken.isNotBlank() && !isVerifying,
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
                if (isVerifying) {
                    CircularProgressIndicator(
                        color = GymSmartColors.OnPrimary,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Verificar cuenta", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(
                onClick = {
                    scope.launch {
                        isResending = true
                        authService.resendVerification(email)
                        isResending = false
                        verifyError = "Código reenviado, revisa tu bandeja"
                    }
                },
                enabled = !isResending
            ) {
                Text(
                    if (isResending) "Enviando..." else "¿No te llegó? Reenviar código",
                    color = GymSmartColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}