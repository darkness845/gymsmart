package com.gymsmart.gymsmart.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.SubscriptionStatus
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.services.SubscriptionService
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlin.time.Clock
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private val Accent     = Color(0xFFFFB800)
private val Background = Color(0xFFF5F3EF)
private val TextPrimary   = Color(0xFF1C1C1C)
private val TextSecondary = Color(0xFF6B6B6B)

class CardNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = text.text.take(16)
        val formatted = trimmed.chunked(4).joinToString(" ")
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val spaces = (offset - 1) / 4
                return (offset + spaces).coerceAtMost(formatted.length)
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                val spaces = (offset - 1) / 4
                return (offset - spaces).coerceAtMost(trimmed.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

class ExpiryVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = text.text.take(4)
        val formatted = if (trimmed.length > 2) "${trimmed.take(2)}/${trimmed.drop(2)}" else trimmed
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return if (offset <= 2) offset else (offset + 1).coerceAtMost(formatted.length)
            }
            override fun transformedToOriginal(offset: Int): Int {
                return if (offset <= 2) offset else (offset - 1).coerceAtMost(trimmed.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(navController: NavController) {

    val authService = remember { AuthService() }
    val subService  = remember { SubscriptionService(authService.client) }
    val scope       = rememberCoroutineScope()

    var status       by remember { mutableStateOf<SubscriptionStatus?>(null) }
    var showPayment  by remember { mutableStateOf(false) }
    var showSuccess  by remember { mutableStateOf(false) }
    var errorMsg     by remember { mutableStateOf<String?>(null) }
    var isLoading    by remember { mutableStateOf(false) }

    // Campos tarjeta
    var cardNumber  by remember { mutableStateOf("") }
    var cardExpiry  by remember { mutableStateOf("") }  // MM/YY
    var cardCvc     by remember { mutableStateOf("") }
    var cardName    by remember { mutableStateOf("") }

    var cardNumberRaw by remember { mutableStateOf("") }
    var cardExpiryRaw by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        subService.getStatus().onSuccess { status = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suscripción", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        },
        containerColor = Background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            if (showSuccess) {
                // ── Pantalla de éxito ──────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
                        Text("¡Pago confirmado!", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Tu plan Premium ya está activo.\nHemos enviado un recibo a tu correo.",
                            color = TextSecondary, textAlign = TextAlign.Center)
                        Button(
                            onClick = { navController.popBackStack() },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Volver", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }
                }
                return@Column
            }

            if (!showPayment) {
                // ── Planes ────────────────────────────────────────────────
                PlanCard(
                    title    = "Free",
                    price    = "Gratis",
                    features = listOf("Registro de peso", "Nutrición", "GPS", "Entrenamientos"),
                    active   = status?.plan == "free" || status == null,
                    accent   = Color(0xFF9E9E9E),
                    onSelect = null
                )

                PlanCard(
                    title    = "Premium",
                    price    = "4,99€",
                    features = listOf(
                        "Todo lo del plan Free",
                        "Análisis de físico con IA (Alpha) ✨",
                        "Acceso prioritario a nuevas funciones"
                    ),
                    active   = status?.active == true,
                    accent   = Accent,
                    onSelect = if (status?.active == true) null else {
                        { showPayment = true }
                    }
                )

                status?.let { s ->
                    if (s.active && s.expiresAt > 0) {
                        val remaining = s.expiresAt - Clock.System.now().toEpochMilliseconds()
                        val minutes   = (remaining / 60000).coerceAtLeast(0)
                        Text(
                            "⏱ Premium activo — caduca en $minutes min",
                            fontSize = 12.sp,
                            color    = TextSecondary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

            } else {
                // ── Formulario de pago ────────────────────────────────────
                Text("Datos de pago", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PaymentTextField(cardName, { cardName = it }, "Nombre en la tarjeta")
                        OutlinedTextField(
                            value = cardNumberRaw,
                            onValueChange = { cardNumberRaw = it.filter { c -> c.isDigit() }.take(16) },
                            label = { Text("Número de tarjeta") },
                            placeholder = { Text("4242 4242 4242 4242", color = Color(0xFFBBBBBB)) },
                            visualTransformation = CardNumberVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, focusedLabelColor = Accent)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = cardExpiryRaw,
                                onValueChange = { cardExpiryRaw = it.filter { c -> c.isDigit() }.take(4) },
                                label = { Text("MM/AA") },
                                visualTransformation = ExpiryVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, focusedLabelColor = Accent)
                            )
                            PaymentTextField(
                                value         = cardCvc,
                                onValueChange = { if (it.length <= 4) cardCvc = it.filter { c -> c.isDigit() } },
                                label         = "CVC",
                                keyboardType  = KeyboardType.Number,
                                modifier      = Modifier.weight(1f)
                            )
                        }

                        Text(
                            "🔒 Pago seguro procesado por Stripe",
                            fontSize = 11.sp,
                            color    = TextSecondary
                        )
                    }
                }

                AnimatedVisibility(errorMsg != null) {
                    Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMsg  = null
                            try {
                                val expParts = cardExpiryRaw.chunked(2)
                                subService.pay(
                                    cardNumber = cardNumberRaw,
                                    expMonth   = expParts.getOrNull(0) ?: "",
                                    expYear    = "20${expParts.getOrNull(1) ?: ""}",
                                    cvc        = cardCvc,
                                    cardName   = cardName
                                ).getOrThrow()

                                showSuccess = true
                                subService.getStatus().onSuccess { status = it }
                            } catch (e: Exception) {
                                errorMsg = "Pago rechazado: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading && cardNumberRaw.length == 16 &&
                            cardExpiryRaw.length == 4 && cardCvc.length >= 3 && cardName.isNotBlank(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp
                    )
                    else Text("Pagar 4,99€", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = { showPayment = false }) {
                    Text("← Cancelar", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String, price: String, features: List<String>,
    active: Boolean, accent: Color, onSelect: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (active) accent.copy(alpha = 0.1f) else Color.White
        ),
        elevation = CardDefaults.cardElevation(if (active) 0.dp else 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (title == "Premium") Icon(Icons.Default.Star, null, tint = accent)
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text(price, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = accent)
            }

            features.forEach { feature ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("✓", color = accent, fontWeight = FontWeight.Bold)
                    Text(feature, fontSize = 14.sp, color = TextSecondary)
                }
            }

            if (active) {
                Text("✅ Plan actual", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            } else if (onSelect != null) {
                Button(
                    onClick  = onSelect,
                    colors   = ButtonDefaults.buttonColors(containerColor = accent),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Suscribirse por 4,99€", color = Color.Black, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PaymentTextField(
    value: String, onValueChange: (String) -> Unit, label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = Color(0xFFBBBBBB)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, focusedLabelColor = Accent)
    )
}