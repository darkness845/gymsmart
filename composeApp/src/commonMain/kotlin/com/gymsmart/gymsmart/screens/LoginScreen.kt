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
fun LoginScreen(navController: NavController, authService: AuthService, profileService: ProfileService) {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

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
                        val destination = if (profileService.hasProfile()) {
                            Screen.Dashboard.route
                        } else {
                            Screen.Onboarding.route
                        }
                        navController.navigate(destination) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    } else if (response.message == "EMAIL_NOT_VERIFIED") {
                        errorMsg = "Debes verificar tu email. Revisa tu bandeja de entrada."
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