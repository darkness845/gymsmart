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
import androidx.navigation.NavController
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.services.AuthService
import kotlinx.coroutines.launch

@Composable
fun ResetPasswordScreen(navController: NavController, authService: AuthService, token: String) {
    val background    = Color(0xFFF5F3EF)
    val accent        = Color(0xFFFFB800)
    val textPrimary   = Color(0xFF1A1A1A)
    val textSecondary = Color(0xFF888888)

    var newPassword by remember { mutableStateOf("") }
    var confirm     by remember { mutableStateOf("") }
    var isLoading   by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf("") }
    var success     by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().background(background).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Nueva contraseña", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = textPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Introduce tu nueva contraseña",
            color = textSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

        if (success) {
            Text("✅ Contraseña actualizada. Ya puedes iniciar sesión.",
                color = textPrimary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text("Ir al login", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Nueva contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Confirmar contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(10.dp))
            if (errorMsg.isNotEmpty()) {
                Text(errorMsg, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.Black,
                    modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Cambiar contraseña", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}