package com.gymsmart.gymsmart.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.model.SubscriptionStatus
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.services.SubscriptionService
import com.gymsmart.gymsmart.ui.theme.GymSmartColors
import kotlinx.coroutines.launch
import kotlin.time.Clock

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
            override fun originalToTransformed(offset: Int): Int =
                if (offset <= 2) offset else (offset + 1).coerceAtMost(formatted.length)
            override fun transformedToOriginal(offset: Int): Int =
                if (offset <= 2) offset else (offset - 1).coerceAtMost(trimmed.length)
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

    var status      by remember { mutableStateOf<SubscriptionStatus?>(null) }
    var showPayment by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var isLoading   by remember { mutableStateOf(false) }

    var cardName      by remember { mutableStateOf("") }
    var cardNumberRaw by remember { mutableStateOf("") }
    var cardExpiryRaw by remember { mutableStateOf("") }
    var cardCvc       by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        subService.getStatus().onSuccess { status = it }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Suscripción",
                            fontWeight = FontWeight.Bold,
                            color = GymSmartColors.TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = GymSmartColors.TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GymSmartColors.Background
                    )
                )
            }
        },
        containerColor = GymSmartColors.Background
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
                // ── Pantalla de éxito ─────────────────────────────────────
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = GymSmartColors.SurfaceCard,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GymSmartColors.Success,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "¡Pago confirmado!",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium,
                            color = GymSmartColors.TextPrimary
                        )
                        Text(
                            "Tu plan Premium ya está activo.\nHemos enviado un recibo a tu correo.",
                            color = GymSmartColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { navController.popBackStack() },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GymSmartColors.Primary,
                                contentColor   = GymSmartColors.OnPrimary
                            )
                        ) {
                            Text("Volver", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                return@Column
            }

            if (!showPayment) {
                // ── Planes ────────────────────────────────────────────────
                PlanCard(
                    title    = "Free",
                    price    = "Gratis",
                    features = listOf("Registro de peso", "Nutrición", "GPS"),
                    active   = status?.active != true,
                    isPremium = false,
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
                    active    = status?.active == true,
                    isPremium = true,
                    onSelect  = if (status?.active == true) null else { { showPayment = true } }
                )

                status?.let { s ->
                    if (s.active && s.expiresAt > 0) {
                        val minutes = ((s.expiresAt - Clock.System.now().toEpochMilliseconds()) / 60000).coerceAtLeast(0)
                        Text(
                            "⏱ Premium activo — caduca en $minutes min",
                            style = MaterialTheme.typography.bodySmall,
                            color = GymSmartColors.TextSecondary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

            } else {
                // ── Formulario de pago ────────────────────────────────────
                Text(
                    "Datos de pago",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = GymSmartColors.TextPrimary
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = GymSmartColors.SurfaceCard,
                    shape = MaterialTheme.shapes.medium
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
                            placeholder = { Text("4242 4242 4242 4242", color = GymSmartColors.TextDisabled) },
                            visualTransformation = CardNumberVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = GymSmartColors.Primary,
                                unfocusedBorderColor    = GymSmartColors.Outline,
                                focusedLabelColor       = GymSmartColors.Primary,
                                unfocusedLabelColor     = GymSmartColors.TextSecondary,
                                cursorColor             = GymSmartColors.Primary,
                                focusedTextColor        = GymSmartColors.TextPrimary,
                                unfocusedTextColor      = GymSmartColors.TextPrimary,
                                focusedContainerColor   = GymSmartColors.SurfaceElevated,
                                unfocusedContainerColor = GymSmartColors.SurfaceElevated,
                            )
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = cardExpiryRaw,
                                onValueChange = { cardExpiryRaw = it.filter { c -> c.isDigit() }.take(4) },
                                label = { Text("MM/AA") },
                                visualTransformation = ExpiryVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor      = GymSmartColors.Primary,
                                    unfocusedBorderColor    = GymSmartColors.Outline,
                                    focusedLabelColor       = GymSmartColors.Primary,
                                    unfocusedLabelColor     = GymSmartColors.TextSecondary,
                                    cursorColor             = GymSmartColors.Primary,
                                    focusedTextColor        = GymSmartColors.TextPrimary,
                                    unfocusedTextColor      = GymSmartColors.TextPrimary,
                                    focusedContainerColor   = GymSmartColors.SurfaceElevated,
                                    unfocusedContainerColor = GymSmartColors.SurfaceElevated,
                                )
                            )
                            PaymentTextField(
                                value = cardCvc,
                                onValueChange = { if (it.length <= 4) cardCvc = it.filter { c -> c.isDigit() } },
                                label = "CVC",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Surface(
                            color = GymSmartColors.SurfaceElevated,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "🔒 Pago seguro procesado por Stripe",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = GymSmartColors.TextSecondary
                            )
                        }
                    }
                }

                AnimatedVisibility(errorMsg != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GymSmartColors.Error.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            errorMsg ?: "",
                            modifier = Modifier.padding(10.dp),
                            color = GymSmartColors.Error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true; errorMsg = null
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
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = GymSmartColors.Primary,
                        contentColor           = GymSmartColors.OnPrimary,
                        disabledContainerColor = GymSmartColors.Outline,
                        disabledContentColor   = GymSmartColors.TextDisabled
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = GymSmartColors.OnPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Pagar 4,99€", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        showPayment = false
                        scope.launch {
                            subService.getStatus().onSuccess { status = it }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, GymSmartColors.Outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = GymSmartColors.TextSecondary
                    )
                ) {
                    Text("← Cancelar", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String, price: String, features: List<String>,
    active: Boolean, isPremium: Boolean, onSelect: (() -> Unit)?
) {
    val accentColor = if (isPremium) GymSmartColors.PremiumGold else GymSmartColors.TextDisabled

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (active) accentColor.copy(alpha = 0.10f) else GymSmartColors.SurfaceCard,
        shape = MaterialTheme.shapes.large,
        border = if (active) ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(accentColor)
        ) else null
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isPremium) Icon(Icons.Default.Star, null, tint = accentColor)
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = GymSmartColors.TextPrimary)
                }
                Text(price, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = accentColor)
            }

            features.forEach { feature ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✓", color = accentColor, fontWeight = FontWeight.Bold)
                    Text(feature, style = MaterialTheme.typography.bodyMedium, color = GymSmartColors.TextSecondary)
                }
            }

            if (active) {
                Text(
                    "✅ Plan actual",
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (onSelect != null) {
                Button(
                    onClick = onSelect,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GymSmartColors.PremiumGold,
                        contentColor   = GymSmartColors.Background
                    )
                ) {
                    Text("Suscribirse por 4,99€", fontWeight = FontWeight.Bold)
                }
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
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = GymSmartColors.TextDisabled) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = GymSmartColors.Primary,
            unfocusedBorderColor    = GymSmartColors.Outline,
            focusedLabelColor       = GymSmartColors.Primary,
            unfocusedLabelColor     = GymSmartColors.TextSecondary,
            cursorColor             = GymSmartColors.Primary,
            focusedTextColor        = GymSmartColors.TextPrimary,
            unfocusedTextColor      = GymSmartColors.TextPrimary,
            focusedContainerColor   = GymSmartColors.SurfaceElevated,
            unfocusedContainerColor = GymSmartColors.SurfaceElevated,
        )
    )
}