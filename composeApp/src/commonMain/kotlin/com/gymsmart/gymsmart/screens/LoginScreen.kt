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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.services.ProfileService
import com.gymsmart.gymsmart.ui.theme.MelocotonTheme
import com.gymsmart.gymsmart.ui.theme.Palette
import com.gymsmart.gymsmart.ui.theme.SystemBarsAppearance
import kotlinx.coroutines.launch

/**
 * LoginScreen con el estilo Melocotón (Tarea 3). Solo cambia el aspecto: la estructura y la lógica
 * de la pantalla son las de siempre, en [LoginScreenContent].
 */
@Composable
fun LoginScreen(
    navController: NavController,
    authService: AuthService,
    profileService: ProfileService,
    sessionExpiredNotice: Boolean = false,
    onNoticeShown: () -> Unit = {}
) {
    MelocotonTheme {
        // Fondo crema hasta arriba: la hora y la batería tienen que ir en oscuro.
        SystemBarsAppearance(darkStatusIcons = true)
        LoginScreenContent(navController, authService, profileService, sessionExpiredNotice, onNoticeShown)
    }
}

@Composable
private fun LoginScreenContent(
    navController: NavController,
    authService: AuthService,
    profileService: ProfileService,
    sessionExpiredNotice: Boolean = false,
    onNoticeShown: () -> Unit = {}
) {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf(if (sessionExpiredNotice) "Tu sesión caducó, inicia sesión de nuevo." else "") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (sessionExpiredNotice) onNoticeShown()
    }

    // Cuenta registrada pero sin verificar: sin esto, el usuario quedaba en un callejón sin salida
    // (ver AUTH-1 en doc/08-backlog-mejoras.md) — no había forma de introducir el código ni de
    // reenviarlo desde el login.
    var showVerification by remember { mutableStateOf(false) }
    var verifyToken by remember { mutableStateOf("") }
    var verifyError by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Palette.current.accentText,
        unfocusedBorderColor = Palette.current.border,
        focusedLabelColor = Palette.current.accentText,
        unfocusedLabelColor = Palette.current.textSecondary,
        cursorColor = Palette.current.accentText,
        focusedTextColor = Palette.current.textPrimary,
        unfocusedTextColor = Palette.current.textPrimary,
        focusedContainerColor = Palette.current.surface,
        unfocusedContainerColor = Palette.current.surface,
    )

    suspend fun goToDashboardOrOnboarding() {
        // null (no se pudo saber, p.ej. fallo de red) nunca se trata como "no tiene perfil": eso
        // mandaría a rehacer el onboarding y el guardado final sobrescribiría un perfil real (ver
        // RED-1 en doc/08-backlog-mejoras.md).
        val destination = if (profileService.hasProfile() == false) Screen.Onboarding.route else Screen.Dashboard.route
        navController.navigate(destination) {
            popUpTo(Screen.Login.route) { inclusive = true }
        }
    }

    if (showVerification) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.current.background)
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Palette.current.surface, shape = MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Text("✉", style = MaterialTheme.typography.headlineLarge, color = Palette.current.accentText)
            }

            Spacer(Modifier.height(24.dp))

            Text("Verifica tu email", style = MaterialTheme.typography.headlineMedium, color = Palette.current.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tu cuenta todavía no está verificada. Introduce el código que te enviamos a",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.current.textSecondary
            )
            Text(email, style = MaterialTheme.typography.bodyMedium, color = Palette.current.accentText, fontWeight = FontWeight.SemiBold)

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
                            goToDashboardOrOnboarding()
                        } else {
                            verifyError = result.message
                        }
                        isVerifying = false
                    }
                },
                enabled = verifyToken.isNotBlank() && !isVerifying,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.current.hero,
                    disabledContainerColor = Palette.current.border,
                    contentColor = Palette.current.onHero,
                    disabledContentColor = Palette.current.chevron
                )
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(color = Palette.current.onHero, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
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

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = { showVerification = false; verifyError = "" }) {
                Text("Volver", color = Palette.current.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.current.background)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Logo / título
        Text(
            text = "GYM",
            style = MaterialTheme.typography.displayLarge,
            color = Palette.current.textPrimary,
            letterSpacing = 4.sp
        )
        Text(
            text = "SMART",
            style = MaterialTheme.typography.displayLarge,
            color = Palette.current.accentText,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Inicia sesión para continuar",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.current.textSecondary
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.current.accentText,
                unfocusedBorderColor = Palette.current.border,
                focusedLabelColor = Palette.current.accentText,
                unfocusedLabelColor = Palette.current.textSecondary,
                cursorColor = Palette.current.accentText,
                focusedTextColor = Palette.current.textPrimary,
                unfocusedTextColor = Palette.current.textPrimary,
                focusedContainerColor = Palette.current.surface,
                unfocusedContainerColor = Palette.current.surface,
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.current.accentText,
                unfocusedBorderColor = Palette.current.border,
                focusedLabelColor = Palette.current.accentText,
                unfocusedLabelColor = Palette.current.textSecondary,
                cursorColor = Palette.current.accentText,
                focusedTextColor = Palette.current.textPrimary,
                unfocusedTextColor = Palette.current.textPrimary,
                focusedContainerColor = Palette.current.surface,
                unfocusedContainerColor = Palette.current.surface,
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (errorMsg.isNotEmpty()) {
            Text(
                text = errorMsg,
                color = Palette.current.danger,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    errorMsg = ""
                    val response = authService.login(email, password)
                    if (response.success) {
                        goToDashboardOrOnboarding()
                    } else if (response.message == "EMAIL_NOT_VERIFIED") {
                        showVerification = true
                    } else {
                        errorMsg = response.message
                    }
                    isLoading = false
                }
            },
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.current.hero,
                disabledContainerColor = Palette.current.border,
                contentColor = Palette.current.onHero,
                disabledContentColor = Palette.current.chevron
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
                    "Iniciar sesión",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TextButton(onClick = {
            navController.navigate(Screen.ForgotPassword.route(fromProfile = false, email = ""))
        }) {
            Text(
                "¿Olvidaste tu contraseña?",
                color = Palette.current.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        TextButton(onClick = { navController.navigate(Screen.Register.route) }) {
            Text(
                "¿No tienes cuenta?  ",
                color = Palette.current.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Regístrate",
                color = Palette.current.accentText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}