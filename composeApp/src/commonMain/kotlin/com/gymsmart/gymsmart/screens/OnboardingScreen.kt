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
import com.gymsmart.gymsmart.ui.theme.MelocotonTheme
import com.gymsmart.gymsmart.ui.theme.Palette
import com.gymsmart.gymsmart.ui.theme.SystemBarsAppearance
import com.gymsmart.gymsmart.validation.validateAge
import com.gymsmart.gymsmart.validation.validateGoalRate
import com.gymsmart.gymsmart.components.RateSlider
import com.gymsmart.gymsmart.nutrition.ActivityBenchmarks
import com.gymsmart.gymsmart.nutrition.NutritionMath
import com.gymsmart.gymsmart.validation.parseDecimal
import com.gymsmart.gymsmart.validation.validateHeightCm
import com.gymsmart.gymsmart.validation.validateWeightKg
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

/**
 * OnboardingScreen con el estilo Melocotón (Tarea 3). Solo cambia el aspecto: la estructura y la lógica
 * de la pantalla son las de siempre, en [OnboardingScreenContent].
 */
@Composable
fun OnboardingScreen(
    profileService: ProfileService,
    onComplete: () -> Unit
) {
    MelocotonTheme {
        // Fondo crema hasta arriba: la hora y la batería tienen que ir en oscuro.
        SystemBarsAppearance(darkStatusIcons = true)
        OnboardingScreenContent(profileService, onComplete)
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingScreenContent(
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
                                color = Palette.current.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Calculamos tus calorías y macros exactos",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.current.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Palette.current.background
                    )
                )
            }
        },
        containerColor = Palette.current.background
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
                color = Palette.current.accentText,
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
                Text(it, color = Palette.current.danger, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(28.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stepIndex > 0) {
                    OutlinedButton(
                        onClick = { step = visibleSteps[stepIndex - 1]; errorMsg = null },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.5.dp, Palette.current.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.current.textSecondary)
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
                                        weightKg      = parseDecimal(form.weightKg)!!,
                                        heightCm      = parseDecimal(form.heightCm)!!,
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
                        containerColor         = Palette.current.hero,
                        disabledContainerColor = Palette.current.border,
                        contentColor           = Palette.current.onHero,
                        disabledContentColor   = Palette.current.chevron
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Palette.current.onHero, strokeWidth = 2.dp)
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
                    .background(if (i <= stepIndex) Palette.current.accentText else Palette.current.border)
            )
            if (i < total - 1) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(if (i < stepIndex) Palette.current.accentText else Palette.current.border)
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
                Text("Se usa para calcular tu metabolismo basal con precisión.", style = MaterialTheme.typography.bodySmall, color = Palette.current.textSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SexChip("Hombre", "♂", form.sex == "male", Modifier.weight(1f)) { onChange(form.copy(sex = "male")) }
                    SexChip("Mujer",  "♀", form.sex == "female", Modifier.weight(1f)) { onChange(form.copy(sex = "female")) }
                }
            }
        }
        val w = parseDecimal(form.weightKg)
        val h = parseDecimal(form.heightCm)
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
                style = MaterialTheme.typography.bodySmall, color = Palette.current.textSecondary, lineHeight = 18.sp
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
                style = MaterialTheme.typography.bodySmall, color = Palette.current.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp))
            // En este punto del onboarding los datos corporales ya están rellenos (el paso BODY va
            // antes y no deja avanzar sin ellos), así que el BMR se puede calcular y cada nivel
            // puede enseñar sus calorías. Si aun así faltara algo, `hintFor` se queda en los pasos.
            val bmrFormulario = run {
                val peso = parseDecimal(form.weightKg)
                val altura = parseDecimal(form.heightCm)
                val edad = form.age.toIntOrNull()
                if (peso != null && altura != null && edad != null && form.sex.isNotBlank())
                    NutritionMath.bmr(form.sex, peso, altura, edad)
                else null
            }
            listOf(
                Triple("sedentary",   "🪑", "Sedentario\nSin ejercicio, trabajo de escritorio"),
                Triple("light",       "🚶", "Ligero\n1–3 días de ejercicio por semana"),
                Triple("moderate",    "🏃", "Moderado\n3–5 días de ejercicio por semana"),
                Triple("active",      "🏋️", "Activo\n6–7 días de ejercicio intenso"),
                Triple("very_active", "⚡", "Muy activo\nAtleta o trabajo físico intenso")
            ).forEach { (value, emoji, desc) ->
                // Bajo cada nivel, los pasos diarios que le corresponden (Tudor-Locke 2004) y las
                // calorías activas equivalentes para su BMR (ver ActivityBenchmarks): elegir entre
                // "ligero" y "moderado" a ojo es la mayor fuente de error del cálculo, y tanto los
                // pasos como las kcal de la pulsera sí se pueden comprobar.
                val baremo = ActivityBenchmarks.hintFor(value, bmrFormulario)
                val texto = if (baremo == null) desc else desc + "\n≈ " + baremo
                SelectableRow(emoji, texto, form.activityLevel == value) { onChange(form.copy(activityLevel = value)) }
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
                // La opción de "recomposición" se eliminó (decisión del usuario, 2026-11-23): en el
                // cálculo real no era más que un déficit con otro nombre (AUD-7 en
                // doc/11-auditoria-kcal.md). Quedan solo las tres direcciones reales.
                listOf(
                    Triple(NutritionMath.GOAL_LOSE,     "🔥", "Perder grasa\nDéficit calórico controlado"),
                    Triple(NutritionMath.GOAL_GAIN,     "💪", "Ganar músculo\nSuperávit calórico limpio"),
                    Triple(NutritionMath.GOAL_MAINTAIN, "➖", "Mantener peso\nComer en mantenimiento")
                ).forEach { (value, emoji, desc) ->
                    SelectableRow(emoji, desc, form.goal == value) {
                        onChange(form.copy(goal = value, goalRate = NutritionMath.defaultRateFor(value)))
                    }
                }
            }
        }

        val pesoActual = parseDecimal(form.weightKg)
        if (form.goal != NutritionMath.GOAL_MAINTAIN && form.goal.isNotBlank() && pesoActual != null) {
            OnboardingCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardTitle("Ritmo de cambio")
                    Text(
                        // Este bloque solo se muestra cuando el objetivo no es "maintain" (ver el `if`
                        // de arriba), y ya no quedan más objetivos que lose/gain, así que el `when` es
                        // exhaustivo sin necesitar un `else`.
                        when (form.goal) {
                            NutritionMath.GOAL_LOSE -> "Cuanto más lento, más músculo conservas."
                            else                    -> "Cuanto más lento, menos grasa acumulas."
                        },
                        style = MaterialTheme.typography.bodySmall, color = Palette.current.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    // El deslizador necesita el gasto para que su extremo sea el tope **efectivo** y no
                    // prometa ritmos que el tope del 25% o el suelo van a recortar después (AUD-5).
                    // En este paso ya están todos los datos del cuerpo, así que se puede calcular.
                    val alturaActual = parseDecimal(form.heightCm)
                    val edadActual = form.age.toIntOrNull()
                    val gasto = if (alturaActual != null && edadActual != null && form.sex.isNotBlank()) {
                        NutritionMath.tdee(
                            NutritionMath.bmr(form.sex, pesoActual, alturaActual, edadActual),
                            form.activityLevel
                        )
                    } else null
                    val suelo = if (alturaActual != null && edadActual != null && form.sex.isNotBlank()) {
                        NutritionMath.energyAvailabilityFloor(form.sex, pesoActual, alturaActual, edadActual)
                    } else 0.0

                    // Mismo componente y misma fuente de números que ProfileScreen: antes cada
                    // pantalla ofrecía valores distintos (-300/-400/-500 aquí vs -250/-400/-500 allí).
                    RateSlider(
                        goal = form.goal,
                        weightKg = pesoActual,
                        ratePct = form.goalRate,
                        onRateChange = { onChange(form.copy(goalRate = it)) },
                        tdee = gasto,
                        energyFloor = suelo
                    )
                }
            }
        }
    }
}

@Composable
private fun StepSummary(form: FormState) {
    val weight = parseDecimal(form.weightKg) ?: 0.0
    val height = parseDecimal(form.heightCm) ?: 0.0
    val age    = form.age.toIntOrNull() ?: 0

    // Misma función que usa el servidor, desde `shared`. Antes esto era una copia de la fórmula que
    // ya había divergido de la del servidor (doc/10-fundamento-cientifico.md).
    val result = NutritionMath.computeTargets(
        sex           = form.sex,
        weightKg      = weight,
        heightCm      = height,
        age           = age,
        activityLevel = form.activityLevel,
        goal          = form.goal,
        ratePct       = form.goalRate
    )
    val tdee     = result.tdee
    val target   = result.targetKcal
    val proteinG = result.proteinG
    val fatG     = result.fatG
    val carbsG   = result.carbsG
    val bmi      = result.bmi
    // Objetivo mostrado como banda, no como cifra exacta: "1624 kcal" promete una precisión que
    // ningún componente del cálculo tiene (±300-500 kcal a priori según §1.1/§1.2/§1.3). Ver
    // doc/11-auditoria-kcal.md, "Dato que debería gobernar toda la comunicación de esta funcionalidad".
    val band = NutritionMath.targetBand(target, result.tdeeIsMeasured)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OnboardingCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardTitle("Tu objetivo diario")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Antes, en modo pulsera, esta caja decía "BMR" pero mostraba `tdee` (2166 para un
                    // BMR de 1805): la etiqueta y el número no coincidían (AUD-21). Ahora cada
                    // etiqueta muestra su propio número.
                    KcalBox(
                        label = if (form.hasWearable == true) "BMR" else "TDEE",
                        value = if (form.hasWearable == true) "${result.bmr}" else "$tdee",
                        sub   = if (form.hasWearable == true) "sin actividad" else "mantenimiento",
                        modifier = Modifier.weight(1f),
                        highlight = false
                    )
                    KcalBox("Objetivo", "$target", "${band.first}-${band.last} kcal/día", Modifier.weight(1f), highlight = true)
                }
                HorizontalDivider(color = Palette.current.divider)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroBox("Proteína", "${proteinG}g", Palette.current.macroProtein, Modifier.weight(1f))
                    MacroBox("Carbos",   "${carbsG}g",   Palette.current.macroCarbs,   Modifier.weight(1f))
                    MacroBox("Grasas",   "${fatG}g",     Palette.current.macroFat,     Modifier.weight(1f))
                }
            }
        }

        if (form.hasWearable == true) {
            OnboardingCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardTitle("⌚ Modo pulsera activo")
                    Text(
                        "Tu objetivo base es tu BMR. Las calorías que quemes con tu pulsera se suman automáticamente cada día en la rueda de nutrición, haciendo tu objetivo dinámico y más preciso.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.current.textSecondary, lineHeight = 18.sp
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
                                // Decía "en tiempo real", justo lo que §1.3 prohíbe prometer: el objetivo
                                // usa la media de días **completos** a propósito, para que el presupuesto
                                // no se mueva mientras comes (AUD-21).
                                "• Modo pulsera: BMR + media de kcal activas\n• Media de días completos, no la lectura de hoy\n"
                            else
                                "• Factor de actividad × BMR = TDEE\n") +
                            "• Proteína: ${if (form.goal == NutritionMath.GOAL_LOSE) "2.0" else "1.8"} g/kg\n" +
                            "• Grasa: 25% kcal totales\n" +
                            "• Carbos: resto calórico\n" +
                            // El suelo del BMR se retiró en §7.3; prometerlo era falso (AUD-2).
                            "• Ajuste máximo: 25% de tu gasto\n" +
                            "• Suelo según tu masa magra estimada",
                    style = MaterialTheme.typography.bodySmall, color = Palette.current.textSecondary, lineHeight = 18.sp
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
        color = Palette.current.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Palette.current.textPrimary)
}

@Composable
private fun NumericField(
    value: String, label: String, placeholder: String,
    modifier: Modifier = Modifier, onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Palette.current.chevron) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = Palette.current.accentText,
            unfocusedBorderColor    = Palette.current.border,
            focusedLabelColor       = Palette.current.accentText,
            unfocusedLabelColor     = Palette.current.textSecondary,
            focusedTextColor        = Palette.current.textPrimary,
            unfocusedTextColor      = Palette.current.textPrimary,
            cursorColor             = Palette.current.accentText,
            focusedContainerColor   = Palette.current.surfaceSunken,
            unfocusedContainerColor = Palette.current.surfaceSunken,
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
            .background(if (selected) Palette.current.surfaceSunken else Palette.current.surface)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Palette.current.accentText else Palette.current.border,
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
                color = if (selected) Palette.current.textPrimary else Palette.current.textSecondary
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
            .background(if (selected) Palette.current.surfaceSunken else Palette.current.surface)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Palette.current.accentText else Palette.current.border,
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
                color = if (selected) Palette.current.textPrimary else Palette.current.textSecondary
            )
            // Se pintan TODAS las líneas que vengan, no solo la segunda. Antes esto cortaba en
            // parts[1] y descartaba el resto sin avisar, así que el baremo de pasos que se añadía
            // como tercera línea no llegaba a verse nunca.
            parts.drop(1).forEachIndexed { i, linea ->
                Text(
                    linea,
                    style = if (i == 0) MaterialTheme.typography.bodySmall
                            else MaterialTheme.typography.labelSmall,
                    color = Palette.current.textSecondary
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).background(Palette.current.accentText),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", fontSize = 11.sp, color = Palette.current.onHero, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BmiCard(bmi: Double) {
    val rounded = (kotlin.math.round(bmi * 10.0)) / 10.0
    val (category, color) = when {
        bmi < 18.5 -> "Bajo peso"    to Palette.current.macroCarbs
        bmi < 25.0 -> "Normopeso"    to Palette.current.success
        bmi < 30.0 -> "Sobrepeso"    to Palette.current.accentText
        bmi < 35.0 -> "Obesidad I"   to Palette.current.danger
        else       -> "Obesidad II+" to Palette.current.danger
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Palette.current.surface
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
                Text("Índice de Masa Corporal", style = MaterialTheme.typography.bodySmall, color = Palette.current.textSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$rounded", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Palette.current.textPrimary)
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
            .background(if (highlight) Palette.current.surfaceSunken else Palette.current.surface)
            .then(if (highlight) Modifier.border(2.dp, Palette.current.accentText, MaterialTheme.shapes.small) else Modifier)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.current.textSecondary)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = if (highlight) Palette.current.accentText else Palette.current.textPrimary)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Palette.current.textSecondary)
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
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.current.textSecondary)
        }
    }
}

private fun validate(step: Step, f: FormState): String? = when (step) {
    Step.BODY ->
        validateWeightKg(f.weightKg)
            ?: validateHeightCm(f.heightCm)
            ?: validateAge(f.age)
            ?: if (f.sex.isEmpty()) "Selecciona tu sexo biológico" else null
    Step.WEARABLE  -> if (f.hasWearable == null) "Selecciona una opción" else null
    Step.ACTIVITY  -> if (f.activityLevel.isEmpty()) "Selecciona tu nivel de actividad" else null
    Step.GOAL ->
        if (f.goal.isEmpty()) "Selecciona tu objetivo"
        // Se valida el Double directamente con el validador compartido. Antes se traducía 0.0 a ""
        // para reusar la validación de ProfileScreen, pero en mantenimiento 0 % es el único ritmo
        // válido y quedaba rechazado como "sin seleccionar".
        else if (!NutritionMath.isRateValid(f.goal, f.goalRate)) "Selecciona el ritmo de cambio"
        else null
    Step.SUMMARY -> null
}