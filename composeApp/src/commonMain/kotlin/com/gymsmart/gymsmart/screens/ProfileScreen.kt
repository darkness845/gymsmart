package com.gymsmart.gymsmart.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.components.RateSlider
import com.gymsmart.gymsmart.components.RateSliderColors
import com.gymsmart.gymsmart.model.AdaptiveResponse
import com.gymsmart.gymsmart.model.NutritionTargets
import com.gymsmart.gymsmart.model.ProfileRequest
import com.gymsmart.gymsmart.model.SubscriptionStatus
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.nutrition.ActivityBenchmarks
import com.gymsmart.gymsmart.nutrition.NutritionMath
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.services.HealthDataProvider
import com.gymsmart.gymsmart.services.ProfileService
import com.gymsmart.gymsmart.services.SubscriptionService
import com.gymsmart.gymsmart.ui.formatThousands
import com.gymsmart.gymsmart.ui.formatWeight
import com.gymsmart.gymsmart.ui.icons.LineIcons
import com.gymsmart.gymsmart.ui.theme.MelocotonTheme
import com.gymsmart.gymsmart.ui.theme.Palette
import com.gymsmart.gymsmart.ui.theme.SystemBarsAppearance
import com.gymsmart.gymsmart.validation.parseDecimal
import com.gymsmart.gymsmart.validation.validateAge
import com.gymsmart.gymsmart.validation.validateGoalRate
import com.gymsmart.gymsmart.validation.validateHeightCm
import com.gymsmart.gymsmart.validation.validateWeightKg
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

/**
 * Las tres pestañas del perfil. Se guardan por posición para que sobrevivan a girar el móvil.
 *
 * El reparto sigue una idea: arriba lo que se cambia a menudo (peso, objetivo), luego lo que casi no
 * se toca (teléfono, contraseña) y aparte lo que no se edita (la pulsera, que solo se consulta).
 */
private enum class ProfileTab(val label: String) { Body("Cuerpo"), Account("Cuenta"), Wearable("Pulsera") }

/**
 * Perfil, en el estilo Melocotón (Tarea 3). Diseño aprobado por el usuario: mezcla de las maquetas
 * P1 y P4 del lienzo — cabecera de chocolate a sangre (P4) con las tres cifras de la P1 (peso,
 * objetivo, kcal/día) y tres pestañas (P4) con el contenido de las tarjetas de la P1.
 *
 * Toda la lógica es la de antes, sin cambios: los dos guardados (datos personales y datos físicos),
 * el reguardado del perfil cuando cambia la fecha de nacimiento, las validaciones, el autoajuste y el
 * diálogo de resumen. Lo que cambia es solo cómo se presenta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    healthDataProvider: HealthDataProvider,
    authService: AuthService,
    profileService: ProfileService
) {
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
    // Vacío = "sin elegir". El "0.5" anterior era un resto de cuando goalRate eran kg/semana.
    var goalRate      by remember { mutableStateOf("") }
    var isSaving      by remember { mutableStateOf(false) }
    // VAL-1 (doc/08-backlog-mejoras.md): antes solo existía esta validación para el ritmo semanal;
    // peso/altura/edad se mandaban con toDoubleOrNull() ?: 0.0 sin avisar si el campo estaba vacío o
    // mal escrito (p. ej. con coma decimal). Ahora cubre los cuatro campos con la misma validación
    // que ya existía en OnboardingScreen (extraída a validation/ProfileValidation.kt).
    var profileError  by remember { mutableStateOf<String?>(null) }

    // Suscripción
    var subStatus by remember { mutableStateOf<SubscriptionStatus?>(null) }

    // Diálogo resumen
    var showSummaryDialog by remember { mutableStateOf(false) }
    var savedTargets      by remember { mutableStateOf<NutritionTargets?>(null) }
    var adaptive          by remember { mutableStateOf<AdaptiveResponse?>(null) }
    var avgActive         by remember { mutableStateOf<Double?>(null) }

    // Objetivo diario vigente, para la cabecera. Sale de lo último que se sepa, en este orden: lo que
    // devuelve el servidor al cargar, lo que cambie el autoajuste y lo que devuelva un guardado.
    var currentTargetKcal by remember { mutableStateOf<Int?>(null) }

    var tabIndex by rememberSaveable { mutableStateOf(ProfileTab.Body.ordinal) }
    val tab = ProfileTab.entries[tabIndex]

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
            sex = p.sex; activityLevel = p.activityLevel; goal = p.goal
            // Se encaja en lo permitido por si llega un valor fuera de rango (perfil migrado desde
            // el formato antiguo de kcal/día, o un valor tocado a mano en la base de datos).
            goalRate = NutritionMath.clampRate(p.goal, p.goalRate).toString()
            originalProfile = OriginalProfile(weight, height, age, sex, activityLevel, goal, goalRate)
            currentTargetKcal = response.targets.targetKcal
        }
        runCatching { healthDataProvider.getTodaySteps() }.onSuccess { steps = it }
        runCatching { healthDataProvider.getTodayActiveCalories() }.onSuccess { calories = it }
        // Media de días completos, para que el resumen tras guardar coincida con lo que se ve en
        // Nutrición en vez de calcularse con 0 de actividad.
        avgActive = runCatching { healthDataProvider.getEnergySummary() }
            .getOrNull()?.takeIf { it.hasData }?.averageActiveKcal
        SubscriptionService(authService.client).getStatus().onSuccess { subStatus = it }
        // El autoajuste se lanza solo. El servidor aplica un límite de una vez por semana, así que
        // llamarlo en cada entrada a la pantalla no deshace el suavizado.
        // Se le pasa la media de activas, que ya está calculada arriba: sin ella el servidor anclaba el
        // ajuste al respaldo `BMR × 1,2` y empujaba en dirección contraria a la medición (AUD-4).
        profileService.runAdaptiveAdjustment(avgActive).onSuccess {
            adaptive = it
            if (it.adjusted) it.newTargetKcal?.let { kcal -> currentTargetKcal = kcal }
        }
    }

    fun savePersonal() {
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
                        // VAL-2 (doc/08-backlog-mejoras.md): este reguardado del perfil es un
                        // efecto derivado de editar los datos personales (la fecha de
                        // nacimiento cambia la edad), no la acción que el usuario pidió al
                        // pulsar este botón — si peso/altura/ritmo no son válidos ahora
                        // mismo, se omite el reguardado en silencio en vez de mandar ceros al
                        // servidor (antes: `toDoubleOrNull() ?: 0.0`, se saltaba además la
                        // validación del ritmo que sí tiene el botón de guardar físico).
                        val physicalDataValid = calculatedAge in 14..100 &&
                            validateWeightKg(weight) == null &&
                            validateHeightCm(height) == null &&
                            validateGoalRate(goal, goalRate) == null
                        if (physicalDataValid) {
                            age = calculatedAge.toString()
                            val request = ProfileRequest(
                                weightKg = parseDecimal(weight)!!,
                                heightCm = parseDecimal(height)!!,
                                age = calculatedAge, sex = sex,
                                activityLevel = activityLevel, goal = goal,
                                goalRate = if (goal == NutritionMath.GOAL_MAINTAIN) 0.0 else parseDecimal(goalRate)!!,
                                hasWearable = steps != null
                            )
                            profileService.saveProfile(request, avgActive).onSuccess { response ->
                                savedTargets = response.targets
                                currentTargetKcal = response.targets.targetKcal
                                originalProfile = OriginalProfile(weight, height, calculatedAge.toString(), sex, activityLevel, goal, goalRate)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            isSavingPersonal = false
        }
    }

    fun savePhysical() {
        scope.launch {
            val error = validateWeightKg(weight)
                ?: validateHeightCm(height)
                ?: validateAge(age)
                ?: validateGoalRate(goal, goalRate)
            if (error != null) { profileError = error; return@launch }
            profileError = null; isSaving = true
            val request = ProfileRequest(
                weightKg = parseDecimal(weight)!!,
                heightCm = parseDecimal(height)!!,
                age = age.toInt(), sex = sex,
                activityLevel = activityLevel, goal = goal,
                goalRate = if (goal == NutritionMath.GOAL_MAINTAIN) 0.0 else parseDecimal(goalRate)!!,
                hasWearable = steps != null
            )
            profileService.saveProfile(request, avgActive).onSuccess { response ->
                savedTargets = response.targets
                currentTargetKcal = response.targets.targetKcal
                showSummaryDialog = true
                originalProfile = OriginalProfile(weight, height, age, sex, activityLevel, goal, goalRate)
            }
            isSaving = false
        }
    }

    MelocotonTheme {
        // La cabecera de chocolate llega hasta arriba, detrás de la hora: iconos claros ahí. Abajo
        // el fondo es crema: iconos oscuros.
        SystemBarsAppearance(darkStatusIcons = false, darkNavigationIcons = true)
        val p = Palette.current

        Column(
            Modifier
                .fillMaxSize()
                .background(p.background)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
        ) {
            // Las cifras de la cabecera son las GUARDADAS, no las que se están tecleando: si cambiaran
            // con cada tecla, la cabecera diría "79 kg" antes de pulsar Guardar.
            ProfileHero(
                name = userName,
                email = userEmail,
                isPremium = subStatus?.active == true,
                weightLabel = originalProfile?.weight?.let(::formatWeight)?.let { "$it kg" } ?: "—",
                goalLabel = goalShortLabel(originalProfile?.goal),
                kcalLabel = currentTargetKcal?.let { formatThousands(it.toLong()) } ?: "—",
                onBack = { navController.popBackStack() }
            )

            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ProfileTabs(selected = tab, onSelect = { tabIndex = it.ordinal })

                when (tab) {
                    // ── Cuerpo ───────────────────────────────────────────────────
                    ProfileTab.Body -> {
                        AdaptiveCard(adaptive)

                        MeloCard {
                            CardTitle(LineIcons.Dumbbell, "Cuerpo y objetivo")

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MeloField("Peso (kg)", weight, { weight = it }, Modifier.weight(1f), KeyboardType.Decimal)
                                MeloField("Altura (cm)", height, { height = it }, Modifier.weight(1f), KeyboardType.Decimal)
                                MeloField("Edad", age, { age = it }, Modifier.weight(1f), KeyboardType.Number)
                            }

                            FieldLabelled("Sexo") {
                                MeloSegmented(
                                    options = listOf("Hombre" to "male", "Mujer" to "female"),
                                    selected = sex,
                                    onSelect = { sex = it }
                                )
                            }

                            // El baremo se calcula con lo que hay en el formulario, no con los targets
                            // guardados, para que las calorías de cada nivel se actualicen en cuanto el
                            // usuario corrige su peso o su edad. Si el perfil está a medias,
                            // `bmrFormulario` es null y cada nivel enseña solo sus pasos: media ayuda es
                            // mejor que una cifra inventada.
                            val bmrFormulario = run {
                                val peso = parseDecimal(weight)
                                val altura = parseDecimal(height)
                                val edad = age.toIntOrNull()
                                if (peso != null && altura != null && edad != null && sex.isNotBlank())
                                    NutritionMath.bmr(sex, peso, altura, edad)
                                else null
                            }
                            val activityHint: (String) -> String? = { nivel ->
                                if (nivel == NutritionMath.ACTIVITY_WEARABLE) "se mide, no se estima"
                                else ActivityBenchmarks.hintFor(nivel, bmrFormulario)
                            }
                            MeloSelect(
                                label = "Nivel de actividad",
                                selected = activityLevel,
                                options = buildList {
                                    if (steps != null) add(NutritionMath.ACTIVITY_WEARABLE to "Pulsera (automático)")
                                    add("sedentary"   to "Sedentario")
                                    add("light"       to "Ligero")
                                    add("moderate"    to "Moderado")
                                    add("active"      to "Activo")
                                    add("very_active" to "Muy activo")
                                },
                                onSelect = { activityLevel = it },
                                hintFor = activityHint
                            )

                            MeloSelect(
                                label = "Objetivo",
                                selected = goal,
                                // Se eliminó "recomposición" (decisión del usuario, 2026-11-23): en el
                                // cálculo real no era más que un déficit con otro nombre (AUD-7 en
                                // doc/11-auditoria-kcal.md).
                                options = listOf(
                                    NutritionMath.GOAL_LOSE     to "Perder grasa",
                                    NutritionMath.GOAL_GAIN     to "Ganar músculo",
                                    NutritionMath.GOAL_MAINTAIN to "Mantener"
                                ),
                                onSelect = {
                                    goal = it
                                    // Se arranca en el ritmo por defecto del objetivo en vez de vacío:
                                    // con un deslizador no hay estado "sin seleccionar" que tenga
                                    // sentido enseñar.
                                    goalRate = NutritionMath.defaultRateFor(it).toString()
                                }
                            )

                            AnimatedVisibility(visible = goal != NutritionMath.GOAL_MAINTAIN) {
                                // Todo se deriva del peso real con NutritionMath, así que los kg/semana
                                // que se enseñan son los que de verdad produce el déficit mostrado.
                                // Antes la etiqueta estaba escrita a mano y no cuadraba (§3.3).
                                val pesoActual = parseDecimal(weight) ?: 70.0
                                FieldLabelled("Ritmo semanal") {
                                    // El gasto y el suelo se calculan con lo que hay en el formulario
                                    // (no con los targets cargados), para que el deslizador reaccione al
                                    // instante si el usuario cambia peso o actividad. Sin el gasto, su
                                    // extremo prometía ritmos que el tope del 25% recortaba después
                                    // (AUD-5).
                                    val alturaActual = parseDecimal(height)
                                    val edadActual = age.toIntOrNull()
                                    val datosCompletos = alturaActual != null && edadActual != null && sex.isNotBlank()
                                    val gasto = if (datosCompletos) {
                                        NutritionMath.tdee(
                                            NutritionMath.bmr(sex, pesoActual, alturaActual, edadActual),
                                            activityLevel
                                        )
                                    } else null
                                    val suelo = if (datosCompletos) {
                                        NutritionMath.energyAvailabilityFloor(sex, pesoActual, alturaActual, edadActual)
                                    } else 0.0

                                    RateSlider(
                                        goal = goal,
                                        weightKg = pesoActual,
                                        ratePct = parseDecimal(goalRate) ?: NutritionMath.defaultRateFor(goal),
                                        onRateChange = { goalRate = it.toString() },
                                        tdee = gasto,
                                        energyFloor = suelo,
                                        colors = RateSliderColors(
                                            track = p.track,
                                            band = p.success.copy(alpha = 0.55f),
                                            thumb = p.hero,
                                            text = p.textPrimary,
                                            textSecondary = p.textSecondary,
                                            ok = p.success,
                                            warning = p.accentText
                                        )
                                    )
                                }
                            }

                            AnimatedVisibility(visible = profileError != null) {
                                Text(profileError.orEmpty(), color = p.danger, fontSize = 13.sp)
                            }

                            MeloButton(
                                text = "Guardar y recalcular",
                                onClick = ::savePhysical,
                                enabled = !isSaving && hasChanges,
                                loading = isSaving,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // ── Cuenta ───────────────────────────────────────────────────
                    ProfileTab.Account -> {
                        MeloCard {
                            CardTitle(LineIcons.User, "Datos personales")
                            MeloField(
                                "Nombre", userName,
                                { userName = it; personalSuccess = false },
                                Modifier.fillMaxWidth()
                            )
                            MeloField(
                                "Teléfono", userPhone,
                                {
                                    val digits = it.filter { c -> c.isDigit() }
                                    if (digits.length <= 9) { userPhone = digits; personalSuccess = false }
                                },
                                Modifier.fillMaxWidth(),
                                KeyboardType.Phone
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CountrySelect(
                                    selected = userCountry,
                                    onSelect = { userCountry = it; personalSuccess = false },
                                    modifier = Modifier.weight(1f)
                                )
                                MeloField(
                                    "Nacimiento", userBirthDate,
                                    {
                                        val digits = it.filter { c -> c.isDigit() }.take(8)
                                        var valid = true
                                        if (digits.length == 8) {
                                            val year  = digits.substring(4, 8).toIntOrNull() ?: 0
                                            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                                            if (year < today.year - 100 || year > today.year) valid = false
                                        }
                                        if (valid) { userBirthDate = digits; personalSuccess = false }
                                    },
                                    Modifier.weight(1f),
                                    KeyboardType.Number,
                                    placeholder = "DD/MM/AAAA",
                                    visualTransformation = BirthDateVisualTransformation()
                                )
                            }

                            AnimatedVisibility(visible = personalSuccess) {
                                Text("Datos guardados", color = p.success, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MeloButton(
                                    text = "Guardar",
                                    onClick = ::savePersonal,
                                    enabled = !isSavingPersonal && hasPersonalChanges,
                                    loading = isSavingPersonal,
                                    modifier = Modifier.weight(1f)
                                )
                                MeloButton(
                                    text = "Contraseña",
                                    onClick = {
                                        navController.navigate(Screen.ForgotPassword.route(fromProfile = true, email = userEmail))
                                    },
                                    style = ButtonStyle.Outline,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        SubscriptionCard(
                            status = subStatus,
                            onUpgrade = { navController.navigate(Screen.Subscription.route) }
                        )
                    }

                    // ── Pulsera ──────────────────────────────────────────────────
                    ProfileTab.Wearable -> WearableCard(steps = steps, calories = calories)
                }
            }
        }

        // ── Diálogo resumen ──────────────────────────────────────────────────
        if (showSummaryDialog) {
            savedTargets?.let { targets ->
                SummaryDialog(
                    targets = targets,
                    isWearable = activityLevel == NutritionMath.ACTIVITY_WEARABLE,
                    onDismiss = { showSummaryDialog = false }
                )
            }
        }
    }
}

private fun goalShortLabel(goal: String?): String = when (goal) {
    NutritionMath.GOAL_LOSE     -> "Perder"
    NutritionMath.GOAL_GAIN     -> "Ganar"
    NutritionMath.GOAL_MAINTAIN -> "Mantener"
    else                        -> "—"
}

// ─── Cabecera y pestañas ────────────────────────────────────────────────────────

@Composable
private fun ProfileHero(
    name: String,
    email: String,
    isPremium: Boolean,
    weightLabel: String,
    goalLabel: String,
    kcalLabel: String,
    onBack: () -> Unit
) {
    val p = Palette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(p.hero)
            // El chocolate se dibuja detrás de la barra de estado; el contenido empieza debajo.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = p.heroRaised,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Volver" }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(LineIcons.ChevronLeft, null, tint = p.onHero, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                "Mi perfil",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.onHeroSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(p.accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = p.onAccent
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = p.onHero, maxLines = 1)
                if (email.isNotBlank()) Text(email, fontSize = 13.sp, color = p.onHeroSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            HeroPill(if (isPremium) "Premium" else "Free")
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroTile("Peso", weightLabel, Modifier.weight(1f))
            HeroTile("Objetivo", goalLabel, Modifier.weight(1f))
            HeroTile("Kcal/día", kcalLabel, Modifier.weight(1f), accent = true)
        }
    }
}

@Composable
private fun HeroPill(text: String) {
    val p = Palette.current
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = p.onHeroAccent,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(p.heroRaised)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun HeroTile(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val p = Palette.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.heroTile)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, fontSize = 11.sp, color = p.onHeroSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (accent) p.onHeroAccent else p.onHero,
            maxLines = 1
        )
    }
}

@Composable
private fun ProfileTabs(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    val p = Palette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(16.dp))
            .padding(5.dp)
    ) {
        ProfileTab.entries.forEach { t ->
            val isSelected = t == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) p.hero else p.surface)
                    .clickable(role = Role.Tab) { onSelect(t) }
                    .semantics { this.selected = isSelected }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t.label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) p.onHero else p.textSecondary
                )
            }
        }
    }
}

// ─── Tarjetas ───────────────────────────────────────────────────────────────────

/**
 * Autoajuste: compara el gasto estimado por fórmula con el gasto **medido** a partir de la tendencia
 * de peso y de lo comido (doc/10-fundamento-cientifico.md §5).
 *
 * Existe porque hay tres errores independientes que ninguna fórmula resuelve: el ±10% de Mifflin, un
 * multiplicador de actividad mal elegido (hasta ±350 kcal/día, permanente) y la adaptación metabólica,
 * cuyo rango individual va de −337 a +352 kcal/día. Medir el resultado los absorbe los tres.
 *
 * El texto evita deliberadamente presentarlo como "científicamente calculado": el método de
 * reconciliación es convención, no evidencia revisada por pares (§5.4).
 */
@Composable
private fun AdaptiveCard(adaptive: AdaptiveResponse?) {
    if (adaptive == null) return
    val p = Palette.current

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.accentSoft)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(LineIcons.TrendUp, null, tint = p.accentText, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ajuste según tu progreso real", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
        }
        Spacer(Modifier.height(8.dp))
        if (adaptive.adjusted) {
            val delta = adaptive.deltaKcal ?: 0
            val signo = if (delta >= 0) "+" else "−"
            // `deltaKcal` es ahora el cambio real del **objetivo**. Antes era la diferencia entre
            // gastos mezclados, que podía tener el signo opuesto a lo que el usuario iba a comer:
            // decía "+100 kcal/día" mientras su objetivo bajaba 139 (AUD-4).
            Text(
                "Tu gasto real ronda las ${adaptive.measuredTdee} kcal/día. " +
                    "He ajustado tu objetivo $signo${kotlin.math.abs(delta)} kcal" +
                    (adaptive.newTargetKcal?.let { ", hasta $it" } ?: "") +
                    ", para que tu ritmo se acerque al que elegiste.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = p.heroRaised
            )
        } else {
            Text(
                adaptive.reason ?: "Todavía no hay datos suficientes.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = p.heroRaised
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Registra tus comidas y pésate con regularidad: con eso puedo medir tu gasto " +
                    "real en vez de estimarlo con una fórmula.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = p.heroRaised
            )
        }
    }
}

@Composable
private fun SubscriptionCard(status: SubscriptionStatus?, onUpgrade: () -> Unit) {
    val p = Palette.current
    val active = status?.active == true
    MeloCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Suscripción", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
                Spacer(Modifier.height(2.dp))
                if (active) {
                    val remaining = ((status.expiresAt - Clock.System.now().toEpochMilliseconds()) / 60000)
                        .coerceAtLeast(0)
                    Text("Plan Premium activo · caduca en $remaining minutos", fontSize = 13.sp, color = p.textSecondary)
                } else {
                    Text("Plan actual: Free", fontSize = 13.sp, color = p.textSecondary)
                }
            }
            Text(
                if (active) "PREMIUM" else "FREE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) p.accentText else p.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, if (active) p.accentText else p.border, RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
        if (!active) {
            MeloButton(
                text = "Mejorar a Premium",
                onClick = onUpgrade,
                style = ButtonStyle.Accent,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WearableCard(steps: Long?, calories: Double?) {
    val p = Palette.current
    val connected = steps != null
    MeloCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pulsera hoy", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.textPrimary, modifier = Modifier.weight(1f))
            Text(
                if (connected) "Health Connect conectado" else "Health Connect no disponible",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (connected) p.success else p.accentText,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (connected) p.successSoft else p.accentSoft)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WearableStat("Pasos", steps?.let(::formatThousands) ?: "—", Modifier.weight(1f))
            WearableStat("Kcal activas", calories?.let { formatThousands(it.toLong()) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun WearableStat(label: String, value: String, modifier: Modifier = Modifier) {
    val p = Palette.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.surfaceSunken)
            .padding(12.dp)
    ) {
        Text(label, fontSize = 12.sp, color = p.textSecondary)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp, color = p.textPrimary)
    }
}

// ─── Diálogo de resumen ─────────────────────────────────────────────────────────

@Composable
private fun SummaryDialog(targets: NutritionTargets, isWearable: Boolean, onDismiss: () -> Unit) {
    val p = Palette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        shape = RoundedCornerShape(26.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Perfil actualizado", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = p.textPrimary)
                Text("Tus nuevos objetivos diarios", fontSize = 13.sp, color = p.textSecondary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Antes, en modo pulsera, decía "BMR" pero mostraba `targets.tdee` — la
                    // etiqueta y el número no coincidían (AUD-21). Cada etiqueta su número.
                    SummaryKcalBox(
                        label = if (isWearable) "BMR" else "TDEE",
                        value = formatThousands((if (isWearable) targets.bmr else targets.tdee).toLong()),
                        sub = if (isWearable) "sin actividad" else "mantenimiento",
                        modifier = Modifier.weight(1f)
                    )
                    // Banda en vez de número único: "1624 kcal" promete una precisión que el
                    // cálculo no tiene (ver doc/11-auditoria-kcal.md, sección de la banda).
                    SummaryKcalBox(
                        label = "Objetivo",
                        value = formatThousands(targets.targetKcal.toLong()),
                        sub = run {
                            val band = NutritionMath.targetBand(targets.targetKcal, targets.tdeeIsMeasured)
                            "${band.first}-${band.last} kcal/día"
                        },
                        highlight = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMacroBox("Proteína", "${targets.proteinG} g", Modifier.weight(1f))
                    SummaryMacroBox("Carbos", "${targets.carbsG} g", Modifier.weight(1f))
                    SummaryMacroBox("Grasas", "${targets.fatG} g", Modifier.weight(1f))
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("IMC", fontSize = 13.sp, color = p.textSecondary)
                    Text("${targets.bmi}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
                    Text("·", color = p.textSecondary)
                    Text(
                        targets.bmiCategory,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            targets.bmi < 18.5 -> p.accentText
                            targets.bmi < 25.0 -> p.success
                            targets.bmi < 30.0 -> p.accentText
                            else               -> p.danger
                        }
                    )
                }
            }
        },
        confirmButton = {
            MeloButton(text = "Perfecto", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    )
}

@Composable
private fun SummaryKcalBox(
    label: String, value: String, sub: String,
    modifier: Modifier = Modifier, highlight: Boolean = false
) {
    val p = Palette.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) p.hero else p.surfaceSunken)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 11.sp, color = if (highlight) p.onHeroSecondary else p.textSecondary)
        Text(
            value,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (highlight) p.onHeroAccent else p.textPrimary
        )
        Text(sub, fontSize = 11.sp, color = if (highlight) p.onHeroSecondary else p.textSecondary)
    }
}

@Composable
private fun SummaryMacroBox(label: String, value: String, modifier: Modifier = Modifier) {
    val p = Palette.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.accentSoft)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = p.textPrimary)
        Text(label, fontSize = 11.sp, color = p.heroRaised)
    }
}

// ─── Piezas del formulario (estilo Melocotón) ───────────────────────────────────

@Composable
private fun MeloCard(content: @Composable ColumnScope.() -> Unit) {
    val p = Palette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun CardTitle(icon: ImageVector, title: String) {
    val p = Palette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(p.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.accentText, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.textPrimary)
    }
}

@Composable
private fun FieldLabelled(label: String, content: @Composable () -> Unit) {
    val p = Palette.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.textSecondary)
        content()
    }
}

/**
 * Campo de texto del estilo Melocotón: etiqueta encima y caja hundida, como en la maqueta, en vez del
 * `OutlinedTextField` de Material con la etiqueta flotando en el borde.
 */
@Composable
private fun MeloField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val p = Palette.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = p.textPrimary),
            cursorBrush = SolidColor(p.accentText),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.surfaceSunken)
                        .border(1.dp, if (focused) p.accentText else p.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 13.dp, vertical = 13.dp)
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, fontSize = 14.sp, color = p.chevron, maxLines = 1)
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun MeloSegmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val p = Palette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.divider)
            .padding(4.dp)
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) p.surface else p.divider)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .semantics { this.selected = isSelected }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) p.textPrimary else p.textSecondary
                )
            }
        }
    }
}

/**
 * Selector desplegable del estilo Melocotón. Enseña la opción elegida y, si la tiene, su línea de
 * ayuda debajo — así el nivel de actividad dice sus pasos y kcal sin tener que abrir el menú.
 *
 * La ayuda por opción existe porque el nivel de actividad necesita explicar cada escalón (tarea 2);
 * dentro del menú se ve para todas, que es donde sirve para comparar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeloSelect(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    hintFor: (String) -> String? = { null }
) {
    val p = Palette.current
    var expanded by remember { mutableStateOf(false) }
    val selectedText = options.firstOrNull { it.first == selected }?.second ?: selected
    val selectedHint = hintFor(selected)

    FieldLabelled(label) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.surfaceSunken)
                    .border(1.dp, if (expanded) p.accentText else p.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(selectedText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.textPrimary)
                    if (selectedHint != null) {
                        Text(selectedHint, fontSize = 11.sp, color = p.textSecondary)
                    }
                }
                Icon(LineIcons.ChevronDown, null, tint = p.textSecondary, modifier = Modifier.size(16.dp))
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = p.surface,
                shape = RoundedCornerShape(16.dp)
            ) {
                options.forEach { (value, text) ->
                    val hint = hintFor(value)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text,
                                    fontSize = 14.sp,
                                    fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Medium,
                                    color = p.textPrimary
                                )
                                if (hint != null) Text(hint, fontSize = 11.sp, color = p.textSecondary)
                            }
                        },
                        onClick = { onSelect(value); expanded = false }
                    )
                }
            }
        }
    }
}

private enum class ButtonStyle { Primary, Outline, Accent }

@Composable
private fun MeloButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    style: ButtonStyle = ButtonStyle.Primary
) {
    val p = Palette.current
    val (container, content) = when (style) {
        ButtonStyle.Primary -> p.hero to p.onHero
        ButtonStyle.Accent  -> p.accent to p.onAccent
        ButtonStyle.Outline -> p.surface to p.textPrimary
    }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(14.dp),
        border = if (style == ButtonStyle.Outline) BorderStroke(1.dp, p.border) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            // Deshabilitado = "no hay nada que guardar todavía". Se ve apagado pero se lee.
            disabledContainerColor = p.divider,
            disabledContentColor = p.textSecondary
        ),
        contentPadding = PaddingValues(vertical = 14.dp),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
        } else {
            Text(
                text,
                fontSize = 14.sp,
                fontWeight = if (style == ButtonStyle.Outline) FontWeight.SemiBold else FontWeight.Bold
            )
        }
    }
}

// ─── Lista de países ────────────────────────────────────────────────────────────
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

/** Selector de país, con el mismo aspecto que [MeloSelect]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySelect(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = Palette.current
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("País", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.textSecondary)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.surfaceSunken)
                    .border(1.dp, if (expanded) p.accentText else p.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 13.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selected.ifBlank { "Elegir" },
                    fontSize = 14.sp,
                    color = if (selected.isBlank()) p.chevron else p.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(LineIcons.ChevronDown, null, tint = p.textSecondary, modifier = Modifier.size(16.dp))
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = p.surface,
                shape = RoundedCornerShape(16.dp)
            ) {
                COUNTRIES.forEach { country ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                country,
                                fontSize = 14.sp,
                                fontWeight = if (country == selected) FontWeight.Bold else FontWeight.Normal,
                                color = p.textPrimary
                            )
                        },
                        onClick = { onSelect(country); expanded = false }
                    )
                }
            }
        }
    }
}
