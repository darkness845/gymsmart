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
import com.gymsmart.gymsmart.ui.theme.GymSmartColors
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
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
        focusedBorderColor = GymSmartColors.Primary,
        unfocusedBorderColor = GymSmartColors.Outline,
        focusedLabelColor = GymSmartColors.Primary,
        unfocusedLabelColor = GymSmartColors.TextSecondary,
        cursorColor = GymSmartColors.Primary,
        focusedTextColor = GymSmartColors.TextPrimary,
        unfocusedTextColor = GymSmartColors.TextPrimary,
        focusedContainerColor = GymSmartColors.SurfaceCard,
        unfocusedContainerColor = GymSmartColors.SurfaceCard,
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
                .background(GymSmartColors.Background)
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(GymSmartColors.SurfaceCard, shape = MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Text("✉", style = MaterialTheme.typography.headlineLarge, color = GymSmartColors.Primary)
            }

            Spacer(Modifier.height(24.dp))

            Text("Verifica tu email", style = MaterialTheme.typography.headlineMedium, color = GymSmartColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tu cuenta todavía no está verificada. Introduce el código que te enviamos a",
                style = MaterialTheme.typography.bodyMedium,
                color = GymSmartColors.TextSecondary
            )
            Text(email, style = MaterialTheme.typography.bodyMedium, color = GymSmartColors.Primary, fontWeight = FontWeight.SemiBold)

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
                    containerColor = GymSmartColors.Primary,
                    disabledContainerColor = GymSmartColors.Outline,
                    contentColor = GymSmartColors.OnPrimary,
                    disabledContentColor = GymSmartColors.TextDisabled
                )
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(color = GymSmartColors.OnPrimary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
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

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = { showVerification = false; verifyError = "" }) {
                Text("Volver", color = GymSmartColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GymSmartColors.Background)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Logo / título
        Text(
            text = "GYM",
            style = MaterialTheme.typography.displayLarge,
            color = GymSmartColors.TextPrimary,
            letterSpacing = 4.sp
        )
        Text(
            text = "SMART",
            style = MaterialTheme.typography.displayLarge,
            color = GymSmartColors.Primary,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Inicia sesión para continuar",
            style = MaterialTheme.typography.bodyMedium,
            color = GymSmartColors.TextSecondary
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
                focusedBorderColor = GymSmartColors.Primary,
                unfocusedBorderColor = GymSmartColors.Outline,
                focusedLabelColor = GymSmartColors.Primary,
                unfocusedLabelColor = GymSmartColors.TextSecondary,
                cursorColor = GymSmartColors.Primary,
                focusedTextColor = GymSmartColors.TextPrimary,
                unfocusedTextColor = GymSmartColors.TextPrimary,
                focusedContainerColor = GymSmartColors.SurfaceCard,
                unfocusedContainerColor = GymSmartColors.SurfaceCard,
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
                focusedBorderColor = GymSmartColors.Primary,
                unfocusedBorderColor = GymSmartColors.Outline,
                focusedLabelColor = GymSmartColors.Primary,
                unfocusedLabelColor = GymSmartColors.TextSecondary,
                cursorColor = GymSmartColors.Primary,
                focusedTextColor = GymSmartColors.TextPrimary,
                unfocusedTextColor = GymSmartColors.TextPrimary,
                focusedContainerColor = GymSmartColors.SurfaceCard,
                unfocusedContainerColor = GymSmartColors.SurfaceCard,
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (errorMsg.isNotEmpty()) {
            Text(
                text = errorMsg,
                color = GymSmartColors.Error,
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
                containerColor = GymSmartColors.Primary,
                disabledContainerColor = GymSmartColors.Outline,
                contentColor = GymSmartColors.OnPrimary,
                disabledContentColor = GymSmartColors.TextDisabled
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
                color = GymSmartColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        TextButton(onClick = { navController.navigate(Screen.Register.route) }) {
            Text(
                "¿No tienes cuenta?  ",
                color = GymSmartColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Regístrate",
                color = GymSmartColors.Primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}