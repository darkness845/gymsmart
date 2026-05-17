package com.gymsmart.gymsmart.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymsmart.gymsmart.model.ProfileRequest
import com.gymsmart.gymsmart.services.ProfileService
import com.gymsmart.gymsmart.ui.theme.GymSmartColors
import kotlinx.coroutines.launch

private data class FormState(
    val weightKg:      String   = "",
    val heightCm:      String   = "",
    val age:           String   = "",
    val sex:           String   = "",
    val hasWearable:   Boolean? = null,
    val activityLevel: String   = "",
    val goal:          String   = "",
    val goalRate:      Double   = 0.0
)

private enum class Step { BODY, WEARABLE, ACTIVITY, GOAL, SUMMARY }

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    profileService: ProfileService,
    onComplete: () -> Unit
) {
    var form      by remember { mutableStateOf(FormState()) }
    var step      by remember { mutableStateOf(Step.BODY) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }
    val scope     = rememberCoroutineScope()

    val visibleSteps = if (form.hasWearable == true)
        listOf(Step.BODY, Step.WEARABLE, Step.GOAL, Step.SUMMARY)
    else
        listOf(Step.BODY, Step.WEARABLE, Step.ACTIVITY, Step.GOAL, Step.SUMMARY)

    val stepIndex = visibleSteps.indexOf(step)

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Tu perfil",
                                style = MaterialTheme.typography.headlineLarge,
                                color = GymSmartColors.TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Calculamos tus calorías y macros exactos",
                                style = MaterialTheme.typography.bodySmall,
                                color = GymSmartColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            StepIndicator(stepIndex = stepIndex, total = visibleSteps.size)

            Spacer(Modifier.height(8.dp))

            Text(
                text = when (step) {
                    Step.BODY     -> "Datos físicos"
                    Step.WEARABLE -> "Dispositivo fitness"
                    Step.ACTIVITY -> "Nivel de actividad"
                    Step.GOAL     -> "Objetivo"
                    Step.SUMMARY  -> "Resumen"
                },
                style = MaterialTheme.typography.labelMedium,
                color = GymSmartColors.Primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(250)) togetherWith
                            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(250))
                }
            ) { currentStep ->
                when (currentStep) {
                    Step.BODY     -> StepBody(form)     { form = it }
                    Step.WEARABLE -> StepWearable(form) { form = it }
                    Step.ACTIVITY -> StepActivity(form) { form = it }
                    Step.GOAL     -> StepGoal(form)     { form = it }
                    Step.SUMMARY  -> StepSummary(form)
                }
            }

            errorMsg?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = GymSmartColors.Error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(28.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stepIndex > 0) {
                    OutlinedButton(
                        onClick = { step = visibleSteps[stepIndex - 1]; errorMsg = null },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.5.dp, GymSmartColors.Outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GymSmartColors.TextSecondary)
                    ) { Text("← Atrás", style = MaterialTheme.typography.labelLarge) }
                }

                Button(
                    onClick = {
                        val err = validate(step, form)
                        if (err != null) { errorMsg = err; return@Button }
                        errorMsg = null
                        if (step == Step.SUMMARY) {
                            isLoading = true
                            scope.launch {
                                profileService.saveProfile(
                                    ProfileRequest(
                                        weightKg      = form.weightKg.toDouble(),
                                        heightCm      = form.heightCm.toDouble(),
                                        age           = form.age.toInt(),
                                        sex           = form.sex,
                                        activityLevel = form.activityLevel,
                                        goal          = form.goal,
                                        goalRate      = form.goalRate,
                                        hasWearable   = form.hasWearable == true
                                    )
                                ).onSuccess { onComplete() }
                                    .onFailure { errorMsg = "Error al guardar. Inténtalo de nuevo." }
                                isLoading = false
                            }
                        } else {
                            step = visibleSteps[stepIndex + 1]
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = GymSmartColors.Primary,
                        disabledContainerColor = GymSmartColors.Outline,
                        contentColor           = GymSmartColors.OnPrimary,
                        disabledContentColor   = GymSmartColors.TextDisabled
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = GymSmartColors.OnPrimary, strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (step == Step.SUMMARY) "¡Empezar! 🚀" else "Siguiente →",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(stepIndex: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(if (i == stepIndex) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i <= stepIndex) GymSmartColors.Primary else GymSmartColors.Outline)
            )
            if (i < total - 1) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(if (i < stepIndex) GymSmartColors.Primary else GymSmartColors.Outline)
                )
            }
        }
    }
}

@Composable
private fun StepBody(form: FormState, onChange: (FormState) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OnboardingCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CardTitle("Medidas corporales")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumericField(form.weightKg, "Peso (kg)", "70", Modifier.weight(1f)) { onChange(form.copy(weightKg = it)) }
                    NumericField(form.heightCm, "Altura (cm)", "175", Modifier.weight(1f)) { onChange(form.copy(heightCm = it)) }
                }
                NumericField(form.age, "Edad (años)", "25") { onChange(form.copy(age = it)) }
            }
        }
        OnboardingCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardTitle("Sexo biológico")
                Text("Se usa para calcular tu metabolismo basal con precisión.", style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SexChip("Hombre", "♂", form.sex == "male", Modifier.weight(1f)) { onChange(form.copy(sex = "male")) }
                    SexChip("Mujer",  "♀", form.sex == "female", Modifier.weight(1f)) { onChange(form.copy(sex = "female")) }
                }
            }
        }
        val w = form.weightKg.toDoubleOrNull()
        val h = form.heightCm.toDoubleOrNull()
        if (w != null && h != null && h > 0) BmiCard(bmi = w / ((h / 100.0) * (h / 100.0)))
    }
}

@Composable
private fun StepWearable(form: FormState, onChange: (FormState) -> Unit) {
    OnboardingCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardTitle("¿Tienes pulsera o app fitness?")
            Text(
                "Si tienes un dispositivo como Garmin, Apple Watch, Fitbit o una app como Google Fit que registra tus calorías quemadas, podemos usar esos datos para un objetivo más preciso cada día.",
                style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary, lineHeight = 18.sp
            )
            Spacer(Modifier.height(4.dp))
            SelectableRow("⌚", "Sí, tengo pulsera o app\nUsaré sus datos de calorías quemadas", form.hasWearable == true) {
                onChange(form.copy(hasWearable = true, activityLevel = "wearable"))
            }
            SelectableRow("❌", "No tengo\nCalcularé mi actividad manualmente", form.hasWearable == false) {
                onChange(form.copy(hasWearable = false, activityLevel = ""))
            }
        }
    }
}

@Composable
private fun StepActivity(form: FormState, onChange: (FormState) -> Unit) {
    OnboardingCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardTitle("¿Cuánto te mueves?")
            Text("Determina tu gasto total diario (TDEE = BMR × factor de actividad).",
                style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp))
            listOf(
                Triple("sedentary",   "🪑", "Sedentario\nSin ejercicio, trabajo de escritorio"),
                Triple("light",       "🚶", "Ligero\n1–3 días de ejercicio por semana"),
                Triple("moderate",    "🏃", "Moderado\n3–5 días de ejercicio por semana"),
                Triple("active",      "🏋️", "Activo\n6–7 días de ejercicio intenso"),
                Triple("very_active", "⚡", "Muy activo\nAtleta o trabajo físico intenso")
            ).forEach { (value, emoji, desc) ->
                SelectableRow(emoji, desc, form.activityLevel == value) { onChange(form.copy(activityLevel = value)) }
            }
        }
    }
}

@Composable
private fun StepGoal(form: FormState, onChange: (FormState) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OnboardingCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardTitle("¿Cuál es tu objetivo?")
                listOf(
                    Triple("lose_fat",    "🔥", "Perder grasa\nDéficit calórico controlado"),
                    Triple("maintain",    "⚖️", "Mantener peso\nComer en mantenimiento"),
                    Triple("gain_muscle", "💪", "Ganar músculo\nSuperávit calórico limpio")
                ).forEach { (value, emoji, desc) ->
                    SelectableRow(emoji, desc, form.goal == value) {
                        onChange(form.copy(goal = value, goalRate = if (value == "maintain") 0.0 else 0.0))
                    }
                }
            }
        }

        if (form.goal == "lose_fat" || form.goal == "gain_muscle") {
            OnboardingCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardTitle("Ritmo de cambio")
                    Text(
                        if (form.goal == "lose_fat") "Cuanto más lento, más músculo conservas."
                        else "Cuanto más lento, menos grasa acumulas.",
                        style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    val options = if (form.goal == "lose_fat") listOf(
                        -300.0 to "🐢  Lento — déficit de 300 kcal/día",
                        -400.0 to "🏃  Moderado — déficit de 400 kcal/día",
                        -500.0 to "⚡  Rápido — déficit de 500 kcal/día"
                    ) else listOf(
                        300.0 to "🐢  Lento — superávit de 300 kcal/día",
                        400.0 to "🏃  Moderado — superávit de 400 kcal/día",
                        500.0 to "⚡  Rápido — superávit de 500 kcal/día"
                    )
                    options.forEach { (rate, label) ->
                        SelectableRow("", label, form.goalRate == rate) { onChange(form.copy(goalRate = rate)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepSummary(form: FormState) {
    val weight = form.weightKg.toDoubleOrNull() ?: 0.0
    val height = form.heightCm.toDoubleOrNull() ?: 0.0
    val age    = form.age.toIntOrNull() ?: 0

    val bmr = if (form.sex == "male")
        10.0 * weight + 6.25 * height - 5.0 * age + 5.0
    else
        10.0 * weight + 6.25 * height - 5.0 * age - 161.0

    val tdee = if (form.activityLevel == "wearable") bmr.toInt()
    else {
        val multiplier = when (form.activityLevel) {
            "sedentary" -> 1.2; "light" -> 1.375; "moderate" -> 1.55; "active" -> 1.725; else -> 1.9
        }
        (bmr * multiplier).toInt()
    }

    val dailyDelta = form.goalRate.toInt()
    val minKcal    = if (form.sex == "male") 1500 else 1200
    val target     = (tdee + dailyDelta).coerceAtLeast(minKcal)
    val proteinG   = (weight * if (form.goal == "lose_fat") 2.0 else 1.8).toInt()
    val fatG       = (target * 0.25 / 9.0).toInt()
    val carbsG     = ((target - proteinG * 4 - fatG * 9) / 4.0).toInt().coerceAtLeast(0)
    val bmi        = if (height > 0) weight / ((height / 100.0) * (height / 100.0)) else 0.0

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OnboardingCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardTitle("Tu objetivo diario")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KcalBox(
                        label = if (form.hasWearable == true) "BMR" else "TDEE",
                        value = "$tdee",
                        sub   = if (form.hasWearable == true) "base sin actividad" else "mantenimiento",
                        modifier = Modifier.weight(1f),
                        highlight = false
                    )
                    KcalBox("Objetivo", "$target", "kcal/día", Modifier.weight(1f), highlight = true)
                }
                HorizontalDivider(color = GymSmartColors.Divider)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroBox("Proteína", "${proteinG}g", GymSmartColors.MacroProtein, Modifier.weight(1f))
                    MacroBox("Carbos",   "${carbsG}g",   GymSmartColors.MacroCarbs,   Modifier.weight(1f))
                    MacroBox("Grasas",   "${fatG}g",     GymSmartColors.MacroFat,     Modifier.weight(1f))
                }
            }
        }

        if (form.hasWearable == true) {
            OnboardingCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardTitle("⌚ Modo pulsera activo")
                    Text(
                        "Tu objetivo base es tu BMR. Las calorías que quemes con tu pulsera se suman automáticamente cada día en la rueda de nutrición, haciendo tu objetivo dinámico y más preciso.",
                        style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary, lineHeight = 18.sp
                    )
                }
            }
        }

        if (bmi > 0) BmiCard(bmi)

        OnboardingCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CardTitle("Metodología")
                Text(
                    "• Fórmula Mifflin-St Jeor para BMR\n" +
                            (if (form.hasWearable == true)
                                "• Modo pulsera: objetivo base = BMR\n• Pulsera suma kcal activas en tiempo real\n"
                            else
                                "• Factor de actividad × BMR = TDEE\n") +
                            "• Proteína: ${if (form.goal == "lose_fat") "2.0" else "1.8"} g/kg\n" +
                            "• Grasa: 25% kcal totales\n" +
                            "• Carbos: resto calórico\n" +
                            "• Mínimo calórico de seguridad aplicado",
                    style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary, lineHeight = 18.sp
                )
            }
        }
    }
}

// ── Componentes reutilizables ─────────────────────────────────────────────────

@Composable
private fun OnboardingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = GymSmartColors.SurfaceCard,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = GymSmartColors.TextPrimary)
}

@Composable
private fun NumericField(
    value: String, label: String, placeholder: String,
    modifier: Modifier = Modifier, onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = GymSmartColors.TextDisabled) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = GymSmartColors.Primary,
            unfocusedBorderColor    = GymSmartColors.Outline,
            focusedLabelColor       = GymSmartColors.Primary,
            unfocusedLabelColor     = GymSmartColors.TextSecondary,
            focusedTextColor        = GymSmartColors.TextPrimary,
            unfocusedTextColor      = GymSmartColors.TextPrimary,
            cursorColor             = GymSmartColors.Primary,
            focusedContainerColor   = GymSmartColors.SurfaceElevated,
            unfocusedContainerColor = GymSmartColors.SurfaceElevated,
        )
    )
}

@Composable
private fun SexChip(
    label: String, emoji: String, selected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) GymSmartColors.SurfaceElevated else GymSmartColors.SurfaceCard)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) GymSmartColors.Primary else GymSmartColors.Outline,
                MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) GymSmartColors.TextPrimary else GymSmartColors.TextSecondary
            )
        }
    }
}

@Composable
private fun SelectableRow(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) GymSmartColors.SurfaceElevated else GymSmartColors.SurfaceCard)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) GymSmartColors.Primary else GymSmartColors.Outline,
                MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (emoji.isNotEmpty()) { Text(emoji, fontSize = 20.sp); Spacer(Modifier.width(12.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            val parts = label.split("\n")
            Text(
                parts[0],
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) GymSmartColors.TextPrimary else GymSmartColors.TextSecondary
            )
            if (parts.size > 1) Text(parts[1], style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary)
        }
        if (selected) {
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).background(GymSmartColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", fontSize = 11.sp, color = GymSmartColors.OnPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BmiCard(bmi: Double) {
    val rounded = (kotlin.math.round(bmi * 10.0)) / 10.0
    val (category, color) = when {
        bmi < 18.5 -> "Bajo peso"    to GymSmartColors.MacroCarbs
        bmi < 25.0 -> "Normopeso"    to GymSmartColors.Success
        bmi < 30.0 -> "Sobrepeso"    to GymSmartColors.Warning
        bmi < 35.0 -> "Obesidad I"   to GymSmartColors.Error
        else       -> "Obesidad II+" to GymSmartColors.Error
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = GymSmartColors.SurfaceCard
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Text("⚖️", fontSize = 20.sp) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Índice de Masa Corporal", style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$rounded", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = GymSmartColors.TextPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text(category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun KcalBox(label: String, value: String, sub: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (highlight) GymSmartColors.SurfaceElevated else GymSmartColors.SurfaceCard)
            .then(if (highlight) Modifier.border(2.dp, GymSmartColors.Primary, MaterialTheme.shapes.small) else Modifier)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextSecondary)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = if (highlight) GymSmartColors.Primary else GymSmartColors.TextPrimary)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextSecondary)
        }
    }
}

@Composable
private fun MacroBox(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.12f))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextSecondary)
        }
    }
}

private fun validate(step: Step, f: FormState): String? = when (step) {
    Step.BODY -> {
        val w = f.weightKg.toDoubleOrNull()
        val h = f.heightCm.toDoubleOrNull()
        val a = f.age.toIntOrNull()
        when {
            w == null || w !in 30.0..300.0  -> "Introduce un peso válido (30–300 kg)"
            h == null || h !in 100.0..250.0 -> "Introduce una altura válida (100–250 cm)"
            a == null || a !in 14..100      -> "Introduce una edad válida (14–100 años)"
            f.sex.isEmpty()                  -> "Selecciona tu sexo biológico"
            else                             -> null
        }
    }
    Step.WEARABLE  -> if (f.hasWearable == null) "Selecciona una opción" else null
    Step.ACTIVITY  -> if (f.activityLevel.isEmpty()) "Selecciona tu nivel de actividad" else null
    Step.GOAL -> when {
        f.goal.isEmpty()                          -> "Selecciona tu objetivo"
        f.goal != "maintain" && f.goalRate == 0.0 -> "Selecciona el ritmo de cambio"
        else                                       -> null
    }
    Step.SUMMARY -> null
}