package com.gymsmart.gymsmart.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.model.ProfileRequest
import com.gymsmart.gymsmart.model.NutritionTargets
import com.gymsmart.gymsmart.model.SubscriptionStatus
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.services.HealthDataProvider
import com.gymsmart.gymsmart.services.ProfileService
import com.gymsmart.gymsmart.services.SubscriptionService
import com.gymsmart.gymsmart.ui.theme.GymSmartColors
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlin.time.Clock

class BirthDateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = text.text.take(8)
        val formatted = buildString {
            trimmed.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append('/')
                append(c)
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                offset <= 8 -> offset + 2
                else        -> formatted.length
            }.coerceAtMost(formatted.length)

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                offset <= 10 -> offset - 2
                else        -> trimmed.length
            }.coerceAtMost(trimmed.length)
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    healthDataProvider: HealthDataProvider
) {
    val authService    = remember { AuthService() }
    val profileService = remember { ProfileService(authService.client) }
    val scope          = rememberCoroutineScope()

    // Usuario
    var userName      by remember { mutableStateOf("Usuario") }
    var userEmail     by remember { mutableStateOf("") }
    var userPhone     by remember { mutableStateOf("") }
    var userCountry   by remember { mutableStateOf("") }
    var userBirthDate by remember { mutableStateOf("") }
    var isSavingPersonal by remember { mutableStateOf(false) }
    var personalSuccess  by remember { mutableStateOf(false) }

    // Wearable
    var steps    by remember { mutableStateOf<Long?>(null) }
    var calories by remember { mutableStateOf<Double?>(null) }

    // Perfil físico
    var weight        by remember { mutableStateOf("") }
    var height        by remember { mutableStateOf("") }
    var age           by remember { mutableStateOf("") }
    var sex           by remember { mutableStateOf("male") }
    var activityLevel by remember { mutableStateOf("moderate") }
    var goal          by remember { mutableStateOf("maintain") }
    var goalRate      by remember { mutableStateOf("0.5") }
    var isSaving      by remember { mutableStateOf(false) }
    var rateError     by remember { mutableStateOf(false) }

    // Suscripción
    var subStatus by remember { mutableStateOf<SubscriptionStatus?>(null) }

    // Diálogo resumen
    var showSummaryDialog by remember { mutableStateOf(false) }
    var savedTargets      by remember { mutableStateOf<NutritionTargets?>(null) }

    data class OriginalPersonal(val name: String, val phone: String, val country: String, val birthDate: String)
    data class OriginalProfile(val weight: String, val height: String, val age: String,
                               val sex: String, val activityLevel: String, val goal: String, val goalRate: String)

    var originalPersonal by remember { mutableStateOf<OriginalPersonal?>(null) }
    var originalProfile  by remember { mutableStateOf<OriginalProfile?>(null) }

    val hasPersonalChanges by derivedStateOf {
        originalPersonal?.let {
            userName != it.name || userPhone != it.phone ||
                    userCountry != it.country || userBirthDate != it.birthDate
        } ?: false
    }
    val hasChanges by derivedStateOf {
        originalProfile?.let {
            weight != it.weight || height != it.height || age != it.age ||
                    sex != it.sex || activityLevel != it.activityLevel ||
                    goal != it.goal || goalRate != it.goalRate
        } ?: false
    }

    LaunchedEffect(Unit) {
        runCatching { authService.me() }.onSuccess { response ->
            userName      = response.user?.name      ?: response.message
            userEmail     = response.user?.email     ?: ""
            userPhone     = response.user?.phone     ?: ""
            userCountry   = response.user?.country   ?: ""
            userBirthDate = response.user?.birthDate ?: ""
            originalPersonal = OriginalPersonal(userName, userPhone, userCountry, userBirthDate)
        }
        profileService.getProfileResponse().onSuccess { response ->
            val p = response.profile
            weight = p.weightKg.toString(); height = p.heightCm.toString(); age = p.age.toString()
            sex = p.sex; activityLevel = p.activityLevel; goal = p.goal; goalRate = p.goalRate.toString()
            originalProfile = OriginalProfile(weight, height, age, sex, activityLevel, goal, goalRate)
        }
        runCatching { healthDataProvider.getTodaySteps() }.onSuccess { steps = it }
        runCatching { healthDataProvider.getTodayActiveCalories() }.onSuccess { calories = it }
        SubscriptionService(authService.client).getStatus().onSuccess { subStatus = it }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Mi perfil",
                            fontWeight = FontWeight.Bold,
                            color = GymSmartColors.TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
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
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Header ──────────────────────────────────────────────────────
            ProfileHeader(name = userName, email = userEmail)

            HorizontalDivider(color = GymSmartColors.Divider)

            // ── Datos personales ─────────────────────────────────────────────
            ProfileSection(icon = Icons.Default.Person, title = "Datos personales") {

                ProfileTextField(
                    value = userName,
                    onValueChange = { userName = it; personalSuccess = false },
                    label = "Nombre"
                )
                ProfileTextField(
                    value = userPhone,
                    onValueChange = {
                        val digits = it.filter { c -> c.isDigit() }
                        if (digits.length <= 9) { userPhone = digits; personalSuccess = false }
                    },
                    label = "Teléfono",
                    keyboardType = KeyboardType.Phone
                )
                CountryPickerField(
                    selected = userCountry,
                    onSelect = { userCountry = it; personalSuccess = false }
                )
                OutlinedTextField(
                    value = userBirthDate,
                    onValueChange = {
                        val digits = it.filter { c -> c.isDigit() }.take(8)
                        if (digits.length == 8) {
                            val year  = digits.substring(4, 8).toIntOrNull() ?: 0
                            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                            if (year < today.year - 100 || year > today.year) return@OutlinedTextField
                        }
                        userBirthDate = digits; personalSuccess = false
                    },
                    label = { Text("Fecha de nacimiento") },
                    placeholder = { Text("DD/MM/AAAA", color = GymSmartColors.TextDisabled) },
                    visualTransformation = BirthDateVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = GymSmartColors.Primary,
                        unfocusedBorderColor = GymSmartColors.Outline,
                        focusedLabelColor    = GymSmartColors.Primary,
                        unfocusedLabelColor  = GymSmartColors.TextSecondary,
                        cursorColor          = GymSmartColors.Primary,
                        focusedTextColor     = GymSmartColors.TextPrimary,
                        unfocusedTextColor   = GymSmartColors.TextPrimary,
                        focusedContainerColor   = GymSmartColors.SurfaceElevated,
                        unfocusedContainerColor = GymSmartColors.SurfaceElevated,
                    )
                )

                AnimatedVisibility(visible = personalSuccess) {
                    Text(
                        "✅ Datos guardados correctamente",
                        color = GymSmartColors.Success,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            isSavingPersonal = true; personalSuccess = false
                            val ok = authService.updatePersonalData(userName, userPhone, userCountry, userBirthDate)
                            if (ok) {
                                originalPersonal = OriginalPersonal(userName, userPhone, userCountry, userBirthDate)
                                personalSuccess = true
                                if (userBirthDate.length == 8) {
                                    try {
                                        val day   = userBirthDate.substring(0, 2).toInt()
                                        val month = userBirthDate.substring(2, 4).toInt()
                                        val year  = userBirthDate.substring(4, 8).toInt()
                                        val birth = LocalDate(year, month, day)
                                        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                                        var calculatedAge = today.year - birth.year
                                        if (today.month.number < birth.month.number ||
                                            (today.month.number == birth.month.number && today.day < birth.day))
                                            calculatedAge--
                                        if (calculatedAge in 14..100 && weight.isNotBlank() && height.isNotBlank()) {
                                            age = calculatedAge.toString()
                                            val request = ProfileRequest(
                                                weightKg = weight.toDoubleOrNull() ?: 0.0,
                                                heightCm = height.toDoubleOrNull() ?: 0.0,
                                                age = calculatedAge, sex = sex,
                                                activityLevel = activityLevel, goal = goal,
                                                goalRate = if (goal == "maintain") 0.0 else goalRate.toDoubleOrNull() ?: 0.0,
                                                hasWearable = steps != null
                                            )
                                            profileService.saveProfile(request).onSuccess { response ->
                                                savedTargets = response.targets
                                                originalProfile = OriginalProfile(weight, height, calculatedAge.toString(), sex, activityLevel, goal, goalRate)
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            isSavingPersonal = false
                        }
                    },
                    enabled = !isSavingPersonal && hasPersonalChanges,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = GymSmartColors.Primary,
                        disabledContainerColor = GymSmartColors.Outline,
                        contentColor           = GymSmartColors.OnPrimary,
                        disabledContentColor   = GymSmartColors.TextDisabled
                    )
                ) {
                    if (isSavingPersonal) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = GymSmartColors.OnPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Guardar cambios", fontWeight = FontWeight.SemiBold)
                    }
                }

                HorizontalDivider(color = GymSmartColors.Divider)

                // Botón cambiar contraseña
                OutlinedButton(
                    onClick = {
                        navController.navigate(Screen.ForgotPassword.route(fromProfile = true, email = userEmail))
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(GymSmartColors.Outline)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = GymSmartColors.TextSecondary
                    )
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Cambiar contraseña", fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Datos físicos ────────────────────────────────────────────────
            ProfileSection(icon = Icons.Default.FitnessCenter, title = "Datos físicos y preferencias") {

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileTextField(
                        value = weight, onValueChange = { weight = it },
                        label = "Peso (kg)", modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Decimal
                    )
                    ProfileTextField(
                        value = height, onValueChange = { height = it },
                        label = "Altura (cm)", modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Decimal
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileTextField(
                        value = age, onValueChange = { age = it },
                        label = "Edad", modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Sexo",
                            style = MaterialTheme.typography.labelMedium,
                            color = GymSmartColors.TextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        SegmentedButtons(
                            options = listOf("Hombre" to "male", "Mujer" to "female"),
                            selected = sex, onSelect = { sex = it }
                        )
                    }
                }

                DropdownField(
                    label = "Nivel de actividad", selected = activityLevel,
                    options = buildList {
                        if (steps != null) add("wearable" to "🏃 Pulsera (automático)")
                        add("sedentary"   to "Sedentario")
                        add("light"       to "Ligero")
                        add("moderate"    to "Moderado")
                        add("active"      to "Activo")
                        add("very_active" to "Muy activo")
                    },
                    onSelect = { activityLevel = it }
                )

                DropdownField(
                    label = "Objetivo", selected = goal,
                    options = listOf(
                        "lose_fat"    to "Perder peso",
                        "maintain"    to "Mantener",
                        "gain_muscle" to "Ganar músculo"
                    ),
                    onSelect = { goal = it; goalRate = if (it == "maintain") "0.0" else "" }
                )

                AnimatedVisibility(visible = goal != "maintain") {
                    DropdownField(
                        label = "Ritmo semanal", selected = goalRate,
                        options = when (goal) {
                            "lose_fat" -> listOf(
                                ""       to "Selecciona un ritmo...",
                                "-250.0" to "Suave (-250 kcal/día ≈ 0.25 kg/sem)",
                                "-400.0" to "Moderado (-400 kcal/día ≈ 0.5 kg/sem)",
                                "-500.0" to "Agresivo (-500 kcal/día ≈ 1 kg/sem)"
                            )
                            else -> listOf(
                                ""      to "Selecciona un ritmo...",
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
                        color = GymSmartColors.Warning,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            if (goal != "maintain" && goalRate.isBlank()) { rateError = true; return@launch }
                            rateError = false; isSaving = true
                            val request = ProfileRequest(
                                weightKg = weight.toDoubleOrNull() ?: 0.0,
                                heightCm = height.toDoubleOrNull() ?: 0.0,
                                age = age.toIntOrNull() ?: 0, sex = sex,
                                activityLevel = activityLevel, goal = goal,
                                goalRate = if (goal == "maintain") 0.0 else goalRate.toDoubleOrNull() ?: 0.0,
                                hasWearable = steps != null
                            )
                            profileService.saveProfile(request).onSuccess { response ->
                                savedTargets = response.targets
                                showSummaryDialog = true
                                originalProfile = OriginalProfile(weight, height, age, sex, activityLevel, goal, goalRate)
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving && hasChanges,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = GymSmartColors.Primary,
                        disabledContainerColor = GymSmartColors.Outline,
                        contentColor           = GymSmartColors.OnPrimary,
                        disabledContentColor   = GymSmartColors.TextDisabled
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = GymSmartColors.OnPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Guardar cambios", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Wearable ─────────────────────────────────────────────────────
            ProfileSection(icon = Icons.Default.Watch, title = "Datos de pulsera (hoy)") {

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

                Spacer(Modifier.height(2.dp))

                Surface(
                    color = if (steps != null)
                        GymSmartColors.Success.copy(alpha = 0.10f)
                    else
                        GymSmartColors.Warning.copy(alpha = 0.10f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = if (steps != null)
                            "✅  Health Connect conectado"
                        else
                            "⚠️  Health Connect no disponible",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (steps != null) GymSmartColors.Success else GymSmartColors.Warning
                    )
                }
            }

            // ── Suscripción ───────────────────────────────────────────────────
            ProfileSection(icon = Icons.Default.Star, title = "Suscripción") {

                if (subStatus?.active == true) {
                    val remaining = ((subStatus!!.expiresAt - Clock.System.now().toEpochMilliseconds()) / 60000)
                        .coerceAtLeast(0)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⭐", style = MaterialTheme.typography.titleMedium)
                        Column {
                            Text(
                                "Plan Premium activo",
                                fontWeight = FontWeight.Bold,
                                color = GymSmartColors.PremiumGold
                            )
                            Text(
                                "Caduca en $remaining minutos",
                                style = MaterialTheme.typography.bodySmall,
                                color = GymSmartColors.TextSecondary
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Plan actual: Free",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GymSmartColors.TextSecondary
                        )
                        Surface(
                            color = GymSmartColors.Outline,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "FREE",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = GymSmartColors.TextDisabled
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { navController.navigate(Screen.Subscription.route) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GymSmartColors.PremiumGold,
                            contentColor   = GymSmartColors.Background
                        )
                    ) {
                        Text("Mejorar a Premium ⭐", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    // ── Diálogo resumen ──────────────────────────────────────────────────────
    if (showSummaryDialog) {
        savedTargets?.let { targets ->
            AlertDialog(
                onDismissRequest = { showSummaryDialog = false },
                containerColor = GymSmartColors.SurfaceElevated,
                shape = MaterialTheme.shapes.large,
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "✅ Perfil actualizado",
                            fontWeight = FontWeight.Bold,
                            color = GymSmartColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Tus nuevos objetivos diarios",
                            style = MaterialTheme.typography.bodySmall,
                            color = GymSmartColors.TextSecondary
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SummaryKcalBox(
                                label = if (activityLevel == "wearable") "BMR" else "TDEE",
                                value = "${targets.tdee}",
                                sub = "mantenimiento",
                                modifier = Modifier.weight(1f)
                            )
                            SummaryKcalBox(
                                label = "Objetivo",
                                value = "${targets.targetKcal}",
                                sub = "kcal/día",
                                highlight = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = GymSmartColors.Divider)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryMacroBox("Proteína", "${targets.proteinG}g", GymSmartColors.MacroProtein, Modifier.weight(1f))
                            SummaryMacroBox("Carbos",   "${targets.carbsG}g",   GymSmartColors.MacroCarbs,   Modifier.weight(1f))
                            SummaryMacroBox("Grasas",   "${targets.fatG}g",     GymSmartColors.MacroFat,     Modifier.weight(1f))
                        }

                        HorizontalDivider(color = GymSmartColors.Divider)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚖️ IMC:", style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary)
                            Text(
                                "${targets.bmi}",
                                fontWeight = FontWeight.Bold,
                                color = GymSmartColors.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text("·", color = GymSmartColors.TextSecondary)
                            Text(
                                targets.bmiCategory,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    targets.bmi < 18.5 -> GymSmartColors.MacroCarbs
                                    targets.bmi < 25.0 -> GymSmartColors.Success
                                    targets.bmi < 30.0 -> GymSmartColors.Warning
                                    else               -> GymSmartColors.Error
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSummaryDialog = false },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GymSmartColors.Primary,
                            contentColor   = GymSmartColors.OnPrimary
                        )
                    ) {
                        Text("Perfecto 💪", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

// ─── Composables auxiliares ──────────────────────────────────────────────────

@Composable
private fun SummaryKcalBox(
    label: String, value: String, sub: String,
    modifier: Modifier = Modifier, highlight: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = if (highlight) GymSmartColors.Primary.copy(alpha = 0.12f) else GymSmartColors.SurfaceCard,
        shape = MaterialTheme.shapes.small,
        border = if (highlight) ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(GymSmartColors.Primary)
        ) else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextSecondary)
            Text(
                value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                color = if (highlight) GymSmartColors.Primary else GymSmartColors.TextPrimary
            )
            Text(sub, style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextSecondary)
        }
    }
}

@Composable
private fun SummaryMacroBox(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.10f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextSecondary)
        }
    }
}

@Composable
private fun ProfileHeader(name: String, email: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = GymSmartColors.Primary.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = GymSmartColors.Primary
                )
            }
        }
        Column {
            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (email.isNotBlank()) {
                Text(email, style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary)
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = GymSmartColors.Primary, modifier = Modifier.size(18.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = GymSmartColors.TextPrimary)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = GymSmartColors.SurfaceCard,
            shape = MaterialTheme.shapes.medium
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
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = GymSmartColors.Primary,
            unfocusedBorderColor = GymSmartColors.Outline,
            focusedLabelColor    = GymSmartColors.Primary,
            unfocusedLabelColor  = GymSmartColors.TextSecondary,
            cursorColor          = GymSmartColors.Primary,
            focusedTextColor     = GymSmartColors.TextPrimary,
            unfocusedTextColor   = GymSmartColors.TextPrimary,
            focusedContainerColor   = GymSmartColors.SurfaceElevated,
            unfocusedContainerColor = GymSmartColors.SurfaceElevated,
        )
    )
}

@Composable
private fun SegmentedButtons(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (label, value) ->
            val isSelected = value == selected
            val shape = when (index) {
                0               -> MaterialTheme.shapes.extraSmall.copy(
                    topEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
                    bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
                )
                options.lastIndex -> MaterialTheme.shapes.extraSmall.copy(
                    topStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                    bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp)
                )
                else            -> androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
            }
            Surface(
                modifier = Modifier.weight(1f).height(36.dp)
                    .border(1.dp, if (isSelected) GymSmartColors.Primary else GymSmartColors.Outline, shape)
                    .clickable { onSelect(value) },
                color = if (isSelected) GymSmartColors.Primary.copy(alpha = 0.15f) else GymSmartColors.SurfaceCard,
                shape = shape
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) GymSmartColors.Primary else GymSmartColors.TextSecondary
                    )
                }
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
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = options.firstOrNull { it.first == selected }?.second ?: selected

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = GymSmartColors.Primary,
                unfocusedBorderColor = GymSmartColors.Outline,
                focusedLabelColor    = GymSmartColors.Primary,
                unfocusedLabelColor  = GymSmartColors.TextSecondary,
                focusedTextColor     = GymSmartColors.TextPrimary,
                unfocusedTextColor   = GymSmartColors.TextPrimary,
                focusedContainerColor   = GymSmartColors.SurfaceElevated,
                unfocusedContainerColor = GymSmartColors.SurfaceElevated,
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = GymSmartColors.SurfaceElevated
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text, color = GymSmartColors.TextPrimary) },
                    onClick = { onSelect(value); expanded = false },
                    colors = MenuItemColors(
                        textColor = GymSmartColors.TextPrimary,
                        leadingIconColor = GymSmartColors.TextPrimary,
                        trailingIconColor = GymSmartColors.TextPrimary,
                        disabledTextColor = GymSmartColors.TextDisabled,
                        disabledLeadingIconColor = GymSmartColors.TextDisabled,
                        disabledTrailingIconColor = GymSmartColors.TextDisabled,
                    )
                )
            }
        }
    }
}

@Composable
private fun WearableStatRow(icon: ImageVector, label: String, value: String, unit: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = GymSmartColors.Primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = GymSmartColors.TextSecondary)
        }
        Text(
            "$value $unit",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            color = GymSmartColors.TextPrimary
        )
    }
}

// ─── Lista de países (sin cambios) ──────────────────────────────────────────
private val COUNTRIES = listOf(
    "Afganistán", "Albania", "Alemania", "Andorra", "Angola", "Antigua y Barbuda",
    "Arabia Saudita", "Argelia", "Argentina", "Armenia", "Australia", "Austria",
    "Azerbaiyán", "Bahamas", "Bahrein", "Bangladesh", "Barbados", "Bélgica",
    "Belice", "Benín", "Bielorrusia", "Bolivia", "Bosnia y Herzegovina", "Botsuana",
    "Brasil", "Brunéi", "Bulgaria", "Burkina Faso", "Burundi", "Bután", "Cabo Verde",
    "Camboya", "Camerún", "Canadá", "Catar", "Chad", "Chile", "China", "Chipre",
    "Colombia", "Comoras", "Corea del Norte", "Corea del Sur", "Costa Rica", "Croacia",
    "Cuba", "Dinamarca", "Dominica", "Ecuador", "Egipto", "El Salvador",
    "Emiratos Árabes Unidos", "Eritrea", "Eslovaquia", "Eslovenia", "España",
    "Estados Unidos", "Estonia", "Etiopía", "Filipinas", "Finlandia", "Fiyi",
    "Francia", "Gabón", "Gambia", "Georgia", "Ghana", "Granada", "Grecia",
    "Guatemala", "Guinea", "Guinea Ecuatorial", "Guinea-Bisáu", "Guyana", "Haití",
    "Honduras", "Hungría", "India", "Indonesia", "Irak", "Irán", "Irlanda",
    "Islandia", "Islas Marshall", "Islas Salomón", "Israel", "Italia", "Jamaica",
    "Japón", "Jordania", "Kazajistán", "Kenia", "Kirguistán", "Kiribati", "Kuwait",
    "Laos", "Lesoto", "Letonia", "Líbano", "Liberia", "Libia", "Liechtenstein",
    "Lituania", "Luxemburgo", "Macedonia del Norte", "Madagascar", "Malasia",
    "Malaui", "Maldivas", "Malí", "Malta", "Marruecos", "Mauricio", "Mauritania",
    "México", "Micronesia", "Moldavia", "Mónaco", "Mongolia", "Montenegro",
    "Mozambique", "Myanmar", "Namibia", "Nauru", "Nepal", "Nicaragua", "Níger",
    "Nigeria", "Noruega", "Nueva Zelanda", "Omán", "Países Bajos", "Pakistán",
    "Palaos", "Palestina", "Panamá", "Papúa Nueva Guinea", "Paraguay", "Perú",
    "Polonia", "Portugal", "Reino Unido", "República Centroafricana",
    "República Checa", "República del Congo", "República Democrática del Congo",
    "República Dominicana", "Ruanda", "Rumanía", "Rusia", "Samoa", "San Cristóbal y Nieves",
    "San Marino", "San Vicente y las Granadinas", "Santa Lucía",
    "Santo Tomé y Príncipe", "Senegal", "Serbia", "Seychelles", "Sierra Leona",
    "Singapur", "Somalia", "Sri Lanka", "Suazilandia", "Sudáfrica", "Sudán",
    "Sudán del Sur", "Suecia", "Suiza", "Surinam", "Tailandia", "Taiwán",
    "Tanzania", "Tayikistán", "Timor Oriental", "Togo", "Tonga",
    "Trinidad y Tobago", "Túnez", "Turkmenistán", "Turquía", "Tuvalu", "Ucrania",
    "Uganda", "Uruguay", "Uzbekistán", "Vanuatu", "Ciudad del Vaticano",
    "Venezuela", "Vietnam", "Yemen", "Yibuti", "Zambia", "Zimbabue"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerField(selected: String, onSelect: (String) -> Unit) {
    var query    by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(query) {
        if (query.isBlank()) COUNTRIES else COUNTRIES.filter { it.contains(query, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it; if (!it) query = "" }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("País") },
            placeholder = { Text("Selecciona un país", color = GymSmartColors.TextDisabled) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = GymSmartColors.Primary,
                unfocusedBorderColor = GymSmartColors.Outline,
                focusedLabelColor    = GymSmartColors.Primary,
                unfocusedLabelColor  = GymSmartColors.TextSecondary,
                focusedTextColor     = GymSmartColors.TextPrimary,
                unfocusedTextColor   = GymSmartColors.TextPrimary,
                focusedContainerColor   = GymSmartColors.SurfaceElevated,
                unfocusedContainerColor = GymSmartColors.SurfaceElevated,
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false; query = "" },
            containerColor = GymSmartColors.SurfaceElevated
        ) {
            filtered.forEach { country ->
                DropdownMenuItem(
                    text = { Text(country, color = GymSmartColors.TextPrimary) },
                    onClick = { onSelect(country); expanded = false; query = "" },
                    colors = MenuItemColors(
                        textColor = GymSmartColors.TextPrimary,
                        leadingIconColor = GymSmartColors.TextPrimary,
                        trailingIconColor = GymSmartColors.TextPrimary,
                        disabledTextColor = GymSmartColors.TextDisabled,
                        disabledLeadingIconColor = GymSmartColors.TextDisabled,
                        disabledTrailingIconColor = GymSmartColors.TextDisabled,
                    )
                )
            }
        }
    }
}