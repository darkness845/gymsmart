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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.services.AuthService
import kotlinx.coroutines.launch

@Composable
fun ForgotPasswordScreen(
        navController: NavController,
        authService:   AuthService,
        fromProfile:   Boolean = false,
        prefillEmail:  String  = ""
    ) {

    val background    = Color(0xFFF5F3EF)
    val accent        = Color(0xFFFFB800)
    val textPrimary   = Color(0xFF1A1A1A)
    val textSecondary = Color(0xFF888888)

    var email     by remember { mutableStateOf(prefillEmail) }
    var emailSent by remember { mutableStateOf(false) }
    var token       by remember { mutableStateOf("") }
    var isLoading   by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf("") }
    var verifiedToken by remember { mutableStateOf("") }  // token verificado que pasamos a la siguiente pantalla
    val scope = rememberCoroutineScope()

    LaunchedEffect(prefillEmail) {
        if (prefillEmail.isNotBlank()) {
            isLoading = true
            authService.forgotPassword(prefillEmail)
            emailSent = true
            isLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(background).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (!emailSent) "Recuperar contraseña" else "Introduce el código",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = textPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (!emailSent) "Introduce tu email y te enviaremos un código"
            else "Revisa tu correo e introduce el código que te hemos enviado",
            color = textSecondary, style = MaterialTheme.typography.bodyMedium
        )
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
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(20.dp))
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
                        authService.forgotPassword(email)
                        emailSent = true
                        isLoading = false
                    }
                },
                enabled = email.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.Black,
                    modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Enviar código", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { navController.popBackStack() }) {
                Text("← Volver", color = textSecondary)
            }

        } else {
            // ── Paso 2: introducir código ─────────────────────────────────────
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; errorMsg = "" },
                label = { Text("Código del correo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                isError = errorMsg.isNotEmpty()
            )
            if (errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMsg = ""
                        val result = authService.verifyResetToken(token.trim())
                        if (result.success) {
                            // Token válido → navega a cambiar contraseña pasando el token
                            navController.navigate(Screen.ResetPassword.route(token.trim(), fromProfile))
                        } else {
                            errorMsg = result.message
                        }
                        isLoading = false
                    }
                },
                enabled = token.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.Black,
                    modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Verificar código", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            if (fromProfile) {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("← Volver al perfil", color = textSecondary)
                }
            } else {
                TextButton(onClick = { emailSent = false; token = ""; errorMsg = "" }) {
                    Text("← Cambiar email", color = textSecondary)
                }
            }
        }
    }
}