package com.gymsmart.gymsmart.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.model.ProfileRequest
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.services.HealthDataProvider
import com.gymsmart.gymsmart.services.ProfileService
import com.gymsmart.gymsmart.model.NutritionTargets
import kotlinx.coroutines.launch
import kotlin.time.Clock

// ─── Colores ────────────────────────────────────────────────────────────────
private val Background = Color(0xFFF5F3EF)
private val Accent = Color(0xFFFFB800)
private val TextPrimary = Color(0xFF1C1C1C)
private val TextSecondary = Color(0xFF6B6B6B)
private val CardWhite = Color.White
private val DividerColor = Color(0xFFE0E0E0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    healthDataProvider: HealthDataProvider
) {

    var currentPassword by remember { mutableStateOf("") }
    var newPassword     by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPasswords   by remember { mutableStateOf(false) }
    var passwordError   by remember { mutableStateOf<String?>(null) }
    var showPasswordSuccessDialog by remember { mutableStateOf(false) }
    var isSavingPassword by remember { mutableStateOf(false) }

    val authService = remember { AuthService() }
    val profileService = remember { ProfileService(authService.client) }
    val scope = rememberCoroutineScope()

    // Usuario
    var userName by remember { mutableStateOf("Usuario") }
    var userEmail by remember { mutableStateOf("") }

    // Wearable
    var steps by remember { mutableStateOf<Long?>(null) }
    var calories by remember { mutableStateOf<Double?>(null) }

    // Perfil
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }

    var sex by remember { mutableStateOf("male") }
    var activityLevel by remember { mutableStateOf("moderate") }
    var goal by remember { mutableStateOf("maintain") }
    var goalRate by remember { mutableStateOf("0.5") }

    // UI
    var isSaving by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }

    var rateError by remember { mutableStateOf(false) }

    data class OriginalProfile(
        val weight: String, val height: String, val age: String,
        val sex: String, val activityLevel: String, val goal: String, val goalRate: String
    )

    var originalProfile by remember { mutableStateOf<OriginalProfile?>(null) }

    val hasChanges by derivedStateOf {
        originalProfile?.let {
            weight != it.weight || height != it.height || age != it.age ||
                    sex != it.sex || activityLevel != it.activityLevel ||
                    goal != it.goal || goalRate != it.goalRate
        } ?: false
    }

    var showSummaryDialog by remember { mutableStateOf(false) }
    var savedTargets by remember { mutableStateOf<NutritionTargets?>(null) }

    // ────────────────────────────────────────────────────────────────────────
    // Carga inicial
    // ────────────────────────────────────────────────────────────────────────

    LaunchedEffect(Unit) {

        // Usuario
        runCatching {
            authService.me()
        }.onSuccess { response ->
            userName  = response.user?.name  ?: response.message
            userEmail = response.user?.email ?: ""
        }

        // Perfil
        profileService.getProfileResponse()
            .onSuccess { response ->

                val profile = response.profile

                weight = profile.weightKg.toString()
                height = profile.heightCm.toString()
                age = profile.age.toString()
                sex = profile.sex
                activityLevel = profile.activityLevel
                goal = profile.goal
                goalRate = profile.goalRate.toString()

                originalProfile = OriginalProfile(
                    weight        = profile.weightKg.toString(),
                    height        = profile.heightCm.toString(),
                    age           = profile.age.toString(),
                    sex           = profile.sex,
                    activityLevel = profile.activityLevel,
                    goal          = profile.goal,
                    goalRate      = profile.goalRate.toString()
                )
            }

        // Wearable
        runCatching {
            healthDataProvider.getTodaySteps()
        }.onSuccess {
            steps = it
        }

        runCatching {
            healthDataProvider.getTodayActiveCalories()
        }.onSuccess {
            calories = it
        }
    }

    // ────────────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Mi perfil",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            navController.popBackStack()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background
                )
            )
        },
        containerColor = Background
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ────────────────────────────────────────────────────────────────
            // Header
            // ────────────────────────────────────────────────────────────────

            ProfileHeader(
                name = userName,
                email = userEmail
            )

            HorizontalDivider(color = DividerColor)

            // ────────────────────────────────────────────────────────────────
            // Contraseña
            // ────────────────────────────────────────────────────────────────

            HorizontalDivider(color = DividerColor)

            ProfileSection(
                icon  = Icons.Default.Lock,
                title = "Cambiar contraseña"
            ) {
                PasswordField(
                    value         = currentPassword,
                    onValueChange = { currentPassword = it; passwordError = null },
                    label         = "Contraseña actual",
                    show          = showPasswords,
                    onToggleShow  = { showPasswords = !showPasswords }
                )
                PasswordField(
                    value         = newPassword,
                    onValueChange = { newPassword = it; passwordError = null },
                    label         = "Nueva contraseña",
                    show          = showPasswords,
                    onToggleShow  = { showPasswords = !showPasswords }
                )
                PasswordField(
                    value         = confirmPassword,
                    onValueChange = { confirmPassword = it; passwordError = null },
                    label         = "Confirmar nueva contraseña",
                    show          = showPasswords,
                    onToggleShow  = { showPasswords = !showPasswords }
                )

                AnimatedVisibility(visible = passwordError != null) {
                    Text(
                        passwordError ?: "",
                        color    = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            passwordError = null
                            when {
                                currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank() ->
                                    passwordError = "Rellena todos los campos"
                                newPassword != confirmPassword ->
                                    passwordError = "Las contraseñas no coinciden"
                                newPassword.length < 6 ->
                                    passwordError = "Mínimo 6 caracteres"
                                newPassword == currentPassword ->
                                    passwordError = "La nueva contraseña debe ser diferente"
                                else -> {
                                    isSavingPassword = true
                                    val ok = authService.changePassword(currentPassword, newPassword)
                                    if (ok) {
                                        currentPassword = ""
                                        newPassword     = ""
                                        confirmPassword = ""
                                        showPasswordSuccessDialog = true
                                    } else {
                                        passwordError = "La contraseña actual es incorrecta"
                                    }
                                    isSavingPassword = false
                                }
                            }
                        }
                    },
                    enabled  = !isSavingPassword,
                    colors   = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSavingPassword) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            color       = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Actualizar contraseña", color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ────────────────────────────────────────────────────────────────
            // Datos físicos
            // ────────────────────────────────────────────────────────────────

            ProfileSection(
                icon = Icons.Default.FitnessCenter,
                title = "Datos físicos y preferencias"
            ) {

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    ProfileTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = "Peso (kg)",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Decimal
                    )

                    ProfileTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = "Altura (cm)",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Decimal
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    ProfileTextField(
                        value = age,
                        onValueChange = { age = it },
                        label = "Edad",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number
                    )

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            "Sexo",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(Modifier.height(4.dp))

                        SegmentedButtons(
                            options = listOf(
                                "Hombre" to "male",
                                "Mujer" to "female"
                            ),
                            selected = sex,
                            onSelect = { sex = it }
                        )
                    }
                }

                DropdownField(
                    label = "Nivel de actividad",
                    selected = activityLevel,
                    options = buildList {
                        if (steps != null) add("wearable" to "🏃 Usar pulsera (automático)")
                        add("sedentary"   to "Sedentario")
                        add("light"       to "Ligero")
                        add("moderate"    to "Moderado")
                        add("active"      to "Activo")
                        add("very_active" to "Muy activo")
                    },
                    onSelect = { activityLevel = it }
                )

                DropdownField(
                    label = "Objetivo",
                    selected = goal,
                    options = listOf(
                        "lose_fat"    to "Perder peso",
                        "maintain"    to "Mantener",
                        "gain_muscle" to "Ganar músculo"
                    ),
                    onSelect = {
                        val anterior = goal
                        goal = it
                        // Si cambia entre perder/ganar, resetea el ritmo
                        if (anterior != it && it != "maintain") {
                            goalRate = ""
                        }
                    }
                )

                AnimatedVisibility(visible = goal != "maintain") {
                    DropdownField(
                        label = "Ritmo semanal",
                        selected = goalRate,
                        options = when (goal) {
                            "lose_fat" -> listOf(
                                "" to "Selecciona un ritmo...",   // ← opción vacía inicial
                                "-250.0" to "Suave (-250 kcal/día ≈ 0.25 kg/sem)",
                                "-400.0" to "Moderado (-400 kcal/día ≈ 0.5 kg/sem)",
                                "-500.0" to "Agresivo (-500 kcal/día ≈ 1 kg/sem)"
                            )
                            else -> listOf(
                                "" to "Selecciona un ritmo...",   // ← opción vacía inicial
                                "250.0" to "Suave (+250 kcal/día ≈ 0.25 kg/sem)",
                                "400.0" to "Moderado (+400 kcal/día ≈ 0.5 kg/sem)",
                                "500.0" to "Agresivo (+500 kcal/día ≈ 1 kg/sem)"
                            )
                        },
                        onSelect = { goalRate = it }
                    )
                }

                AnimatedVisibility(visible = rateError) {
                    Text(
                        "⚠️ Selecciona un ritmo semanal",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                AnimatedVisibility(
                    visible = saveSuccess
                ) {
                    Text(
                        "✅ Perfil guardado correctamente",
                        color = Color(0xFF388E3C),
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = {

                        scope.launch {

                            if (goal != "maintain" && goalRate.isBlank()) {
                                rateError = true
                                return@launch
                            }
                            rateError = false

                            isSaving = true
                            saveSuccess = false

                            val request = ProfileRequest(
                                weightKg = weight.toDoubleOrNull() ?: 0.0,
                                heightCm = height.toDoubleOrNull() ?: 0.0,
                                age = age.toIntOrNull() ?: 0,
                                sex = sex,
                                activityLevel = activityLevel,
                                goal = goal,
                                goalRate = goalRate.toDoubleOrNull() ?: 0.5,
                                hasWearable = steps != null
                            )

                            profileService.saveProfile(request)
                                .onSuccess { response ->
                                    savedTargets = response.targets
                                    showSummaryDialog = true
                                    originalProfile = OriginalProfile(weight, height, age, sex, activityLevel, goal, goalRate)
                                }

                            isSaving = false
                        }
                    },
                    enabled  = !isSaving && hasChanges,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = Accent,
                        disabledContainerColor = Color(0xFFE0E0E0)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {

                    if (isSaving) {

                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )

                    } else {

                        Text(
                            "Guardar cambios",
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(color = DividerColor)

            // ────────────────────────────────────────────────────────────────
            // Wearable
            // ────────────────────────────────────────────────────────────────

            ProfileSection(
                icon = Icons.Default.Watch,
                title = "Datos de pulsera (hoy)"
            ) {

                WearableStatRow(
                    icon = Icons.Default.DirectionsWalk,
                    label = "Pasos",
                    value = steps?.toString() ?: "—",
                    unit = "pasos"
                )

                WearableStatRow(
                    icon = Icons.Default.LocalFireDepartment,
                    label = "Calorías activas",
                    value = calories?.toInt()?.toString() ?: "—",
                    unit = "kcal"
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text =
                        if (steps != null)
                            "✅ Health Connect conectado"
                        else
                            "⚠️ Health Connect no disponible",

                    fontSize = 12.sp,

                    color =
                        if (steps != null)
                            Color(0xFF388E3C)
                        else
                            TextSecondary
                )
            }

            HorizontalDivider(color = DividerColor)

            ProfileSection(icon = Icons.Default.Star, title = "Suscripción") {
                var subStatus by remember { mutableStateOf<com.gymsmart.gymsmart.model.SubscriptionStatus?>(null) }
                val subService = remember { com.gymsmart.gymsmart.services.SubscriptionService(authService.client) }

                LaunchedEffect(Unit) {
                    subService.getStatus().onSuccess { subStatus = it }
                }

                if (subStatus?.active == true) {
                    val remaining = ((subStatus!!.expiresAt - Clock.System.now().toEpochMilliseconds()) / 60000).coerceAtLeast(0)
                    Text("⭐ Plan Premium activo", fontWeight = FontWeight.Bold, color = Accent)
                    Text("Caduca en $remaining minutos", fontSize = 13.sp, color = TextSecondary)
                } else {
                    Text("Plan actual: Free", color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { navController.navigate(com.gymsmart.gymsmart.navigation.Screen.Subscription.route) },
                        colors  = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape   = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Mejorar a Premium ⭐", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showPasswordSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordSuccessDialog = false },
            containerColor   = Color.White,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔒", fontSize = 36.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Contraseña actualizada",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = TextPrimary
                    )
                }
            },
            text = {
                Text(
                    "Tu contraseña se ha cambiado correctamente. La próxima vez que inicies sesión usa la nueva.",
                    fontSize   = 14.sp,
                    color      = TextSecondary,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick  = { showPasswordSuccessDialog = false },
                    colors   = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Entendido", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showSummaryDialog) {
        savedTargets?.let { targets ->
            AlertDialog(
                onDismissRequest = { showSummaryDialog = false },
                containerColor   = Color.White,
                shape            = RoundedCornerShape(20.dp),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("✅ Perfil actualizado",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp,
                            color      = TextPrimary)
                        Text("Tus nuevos objetivos diarios",
                            fontSize = 13.sp,
                            color    = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        // TDEE y objetivo calórico
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryKcalBox(
                                label    = if (activityLevel == "wearable") "BMR" else "TDEE",
                                value    = "${targets.tdee}",
                                sub      = "mantenimiento",
                                modifier = Modifier.weight(1f)
                            )
                            SummaryKcalBox(
                                label    = "Objetivo",
                                value    = "${targets.targetKcal}",
                                sub      = "kcal/día",
                                highlight = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Macros
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryMacroBox("Proteína", "${targets.proteinG}g", Color(0xFF4CAF50), Modifier.weight(1f))
                            SummaryMacroBox("Carbos",   "${targets.carbsG}g",   Color(0xFF2196F3), Modifier.weight(1f))
                            SummaryMacroBox("Grasas",   "${targets.fatG}g",     Color(0xFFFF5722), Modifier.weight(1f))
                        }

                        // IMC
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚖️ IMC:", fontSize = 13.sp, color = TextSecondary)
                            Text("${targets.bmi}", fontWeight = FontWeight.Bold,
                                fontSize = 14.sp, color = TextPrimary)
                            Text("·", color = TextSecondary)
                            Text(targets.bmiCategory, fontSize = 13.sp,
                                color = when {
                                    targets.bmi < 18.5 -> Color(0xFF2196F3)
                                    targets.bmi < 25.0 -> Color(0xFF4CAF50)
                                    targets.bmi < 30.0 -> Color(0xFFFFA726)
                                    else               -> Color(0xFFEF5350)
                                })
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSummaryDialog = false },
                        colors  = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape   = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Perfecto 💪", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENTES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryKcalBox(
    label: String, value: String, sub: String,
    modifier: Modifier = Modifier, highlight: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlight) Color(0xFFFFF8E1) else Color(0xFFF5F3EF))
            .then(if (highlight) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPrimary)
            Text(sub,   fontSize = 10.sp,  color = TextSecondary)
        }
    }
}

@Composable
private fun SummaryMacroBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
            Text(label, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun ProfileHeader(
    name: String,
    email: String
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Accent)
        ) {

            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Column {

            Text(
                name,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextPrimary
            )

            if (email.isNotBlank()) {

                Text(
                    email,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ProfileSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Icon(
                icon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(20.dp)
            )

            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary
            )
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = CardWhite
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 3.dp
            ),
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            focusedLabelColor = Accent,
            cursorColor = Accent
        )
    )
}

@Composable
private fun SegmentedButtons(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {

        options.forEachIndexed { index, (label, value) ->

            val isSelected = value == selected

            val shape = when (index) {

                0 -> RoundedCornerShape(
                    topStart = 8.dp,
                    bottomStart = 8.dp
                )

                options.lastIndex -> RoundedCornerShape(
                    topEnd = 8.dp,
                    bottomEnd = 8.dp
                )

                else -> RoundedCornerShape(0.dp)
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(
                        if (isSelected) Accent else Color.Transparent,
                        shape
                    )
                    .border(
                        1.dp,
                        Accent,
                        shape
                    )
                    .clickable {
                        onSelect(value)
                    }
            ) {

                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight =
                        if (isSelected)
                            FontWeight.Bold
                        else
                            FontWeight.Normal,

                    color =
                        if (isSelected)
                            Color.Black
                        else
                            TextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {

    var expanded by remember {
        mutableStateOf(false)
    }

    val displayLabel =
        options.firstOrNull {
            it.first == selected
        }?.second ?: selected

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = it
        }
    ) {

        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            label = {
                Text(label)
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                focusedLabelColor = Accent
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {

            options.forEach { (value, text) ->

                DropdownMenuItem(
                    text = {
                        Text(text)
                    },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun WearableStatRow(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Icon(
                icon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(20.dp)
            )

            Text(
                label,
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        Text(
            "$value $unit",
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    show: Boolean,
    onToggleShow: () -> Unit
) {
    OutlinedTextField(
        value               = value,
        onValueChange       = onValueChange,
        label               = { Text(label) },
        singleLine          = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions     = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon        = {
            IconButton(onClick = onToggleShow) {
                Icon(
                    if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        },
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        colors   = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            focusedLabelColor  = Accent,
            cursorColor        = Accent
        )
    )
}