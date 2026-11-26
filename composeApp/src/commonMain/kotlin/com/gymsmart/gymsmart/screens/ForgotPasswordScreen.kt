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
import com.gymsmart.gymsmart.ui.theme.MelocotonTheme
import com.gymsmart.gymsmart.ui.theme.Palette
import com.gymsmart.gymsmart.ui.theme.SystemBarsAppearance
import kotlinx.coroutines.launch

/**
 * ForgotPasswordScreen con el estilo Melocotón (Tarea 3). Solo cambia el aspecto: la estructura y la lógica
 * de la pantalla son las de siempre, en [ForgotPasswordScreenContent].
 */
@Composable
fun ForgotPasswordScreen(
    navController: NavController,
    authService:   AuthService,
    fromProfile:   Boolean = false,
    prefillEmail:  String  = ""
) {
    MelocotonTheme {
        // Fondo crema hasta arriba: la hora y la batería tienen que ir en oscuro.
        SystemBarsAppearance(darkStatusIcons = true)
        ForgotPasswordScreenContent(navController, authService, fromProfile, prefillEmail)
    }
}

@Composable
private fun ForgotPasswordScreenContent(
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
            .background(Palette.current.background)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Icono contextual
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Palette.current.surface, shape = MaterialTheme.shapes.extraLarge),
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
            color = Palette.current.textPrimary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            if (!emailSent) "Introduce tu email y te enviaremos un código"
            else "Revisa tu correo e introduce el código que te hemos enviado",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.current.textSecondary
        )

        if (emailSent) {
            Spacer(Modifier.height(4.dp))
            Text(
                email,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.current.accentText,
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
                    Text("Enviar código", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = { navController.popBackStack() }) {
                Text("← Volver", color = Palette.current.textSecondary, style = MaterialTheme.typography.bodyMedium)
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
                    color = Palette.current.danger,
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
                    Text("Verificar código", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (fromProfile) {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("← Volver al perfil", color = Palette.current.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                TextButton(onClick = { emailSent = false; token = ""; errorMsg = "" }) {
                    Text("← Cambiar email", color = Palette.current.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}