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
 * RegisterScreen con el estilo Melocotón (Tarea 3). Solo cambia el aspecto: la estructura y la lógica
 * de la pantalla son las de siempre, en [RegisterScreenContent].
 */
@Composable
fun RegisterScreen(navController: NavController, authService: AuthService) {
    MelocotonTheme {
        // Fondo crema hasta arriba: la hora y la batería tienen que ir en oscuro.
        SystemBarsAppearance(darkStatusIcons = true)
        RegisterScreenContent(navController, authService)
    }
}

@Composable
private fun RegisterScreenContent(navController: NavController, authService: AuthService) {

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
        if (!showVerification) {

            // ── Paso 1: formulario de registro ───────────────────────────────

            Text(
                "Crear cuenta",
                style = MaterialTheme.typography.headlineLarge,
                color = Palette.current.textPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Únete a GymSmart",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.current.textSecondary
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
                    color = Palette.current.danger,
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
                    Text("Crear cuenta", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { navController.navigate(Screen.Login.route) }) {
                Text("¿Ya tienes cuenta?  ", color = Palette.current.textSecondary, style = MaterialTheme.typography.bodyMedium)
                Text("Inicia sesión", color = Palette.current.accentText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }

        } else {

            // ── Paso 2: verificar email ───────────────────────────────────────

            // Icono visual de email — simple círculo con acento
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Palette.current.surface, shape = MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Text("✉", style = MaterialTheme.typography.headlineLarge, color = Palette.current.accentText)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Verifica tu email",
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.current.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Hemos enviado un código a",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.current.textSecondary
            )
            Text(
                email,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.current.accentText,
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
                    color = if (verifyError.contains("reenviado")) Palette.current.success else Palette.current.danger,
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
                    containerColor         = Palette.current.hero,
                    disabledContainerColor = Palette.current.border,
                    contentColor           = Palette.current.onHero,
                    disabledContentColor   = Palette.current.chevron
                )
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        color = Palette.current.onHero,
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
                    color = Palette.current.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}