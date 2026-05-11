package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.services.AuthService
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(navController: NavController, authService: AuthService) {
    val background    = Color(0xFFF5F5F5)
    val accent        = Color(0xFFFFC107)
    val textPrimary   = Color(0xFF1C1C1C)
    val textSecondary = Color(0xFF6B6B6B)

    var name      by remember { mutableStateOf("") }
    var email     by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var errorMsg  by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Paso 2: verificación de email
    var showVerification by remember { mutableStateOf(false) }
    var verifyToken      by remember { mutableStateOf("") }
    var verifyError      by remember { mutableStateOf("") }
    var isVerifying      by remember { mutableStateOf(false) }
    var isResending      by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().background(background).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!showVerification) {
            // ── Paso 1: formulario de registro ───────────────────────────────
            Text("Crear cuenta", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = textPrimary)
            Text("Únete a GymSmart", style = MaterialTheme.typography.bodyMedium, color = textSecondary)
            Spacer(Modifier.height(40.dp))

            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Nombre") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(value = email, onValueChange = { email = it },
                label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(value = password, onValueChange = { password = it },
                label = { Text("Contraseña") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(10.dp))

            if (errorMsg.isNotEmpty()) {
                Text(errorMsg, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.Black,
                    modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Crear cuenta", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { navController.navigate(Screen.Login.route) }) {
                Text("¿Ya tienes cuenta? ", color = textSecondary)
                Text("Inicia sesión", color = accent, fontWeight = FontWeight.Bold)
            }

        } else {
            // ── Paso 2: verificar email ───────────────────────────────────────
            Text("Verifica tu email", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = textPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Hemos enviado un código a $email",
                color = textSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = verifyToken,
                onValueChange = { verifyToken = it; verifyError = "" },
                label = { Text("Código de verificación") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                isError = verifyError.isNotEmpty()
            )
            if (verifyError.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(verifyError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        isVerifying = true
                        verifyError = ""
                        val result = authService.verifyEmail(verifyToken.trim())
                        if (result.success) {
                            // Email verificado → ir al onboarding
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                if (isVerifying) CircularProgressIndicator(color = Color.Black,
                    modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Verificar cuenta", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            // Reenviar código
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
                    color = textSecondary
                )
            }
        }
    }
}