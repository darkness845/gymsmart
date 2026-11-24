package com.gymsmart.gymsmart.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.gymsmart.gymsmart.BarcodeScannerSheet
import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.FoodCollection
import com.gymsmart.gymsmart.model.FoodCollections
import com.gymsmart.gymsmart.model.FoodSearchResponse
import com.gymsmart.gymsmart.model.MealEntry
import com.gymsmart.gymsmart.model.NutritionTargets
import com.gymsmart.gymsmart.model.Nutriments
import com.gymsmart.gymsmart.model.Product
import com.gymsmart.gymsmart.model.estimatedKcalPer100
import com.gymsmart.gymsmart.nutrition.CookingYield
import com.gymsmart.gymsmart.nutrition.cookingYieldById
import com.gymsmart.gymsmart.nutrition.isExcludedFromCooking
import com.gymsmart.gymsmart.nutrition.looksAlreadyCooked
import com.gymsmart.gymsmart.nutrition.GENERIC_BRAND
import com.gymsmart.gymsmart.nutrition.matchingGenerics
import com.gymsmart.gymsmart.nutrition.rankFoodResults
import com.gymsmart.gymsmart.nutrition.WeighBasis
import com.gymsmart.gymsmart.nutrition.findCookingYield
import com.gymsmart.gymsmart.nutrition.per100For
import com.gymsmart.gymsmart.nutrition.rawWeightFor
import com.gymsmart.gymsmart.services.HealthDataProvider
import com.gymsmart.gymsmart.services.NutritionService
import com.gymsmart.gymsmart.services.ProfileService
import com.gymsmart.gymsmart.services.WEARABLE_POLL_INTERVAL_MS
import com.gymsmart.gymsmart.services.WearableEnergy
import com.gymsmart.gymsmart.ui.theme.GymSmartColors
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.serialization.json.Json
import kotlin.time.Clock

enum class MealType(val label: String, val emoji: String) {
    DESAYUNO("Desayuno", "🌅"),
    ALMUERZO("Almuerzo", "🥗"),
    COMIDA  ("Comida",   "🍽️"),
    CENA    ("Cena",     "🌙")
}

private sealed class SheetMode {
    data class Search(val meal: MealType) : SheetMode()
    data class Scanner(val meal: MealType) : SheetMode()
    data class PortionPicker(val meal: MealType, val product: Product, val existingEntry: MealEntry? = null) : SheetMode()
    data class EditEntry(val meal: MealType, val entry: MealEntry) : SheetMode()

    // Colecciones (Tarea B): un grupo de alimentos con su cantidad ya fijada, para añadirlos todos de
    // una vez. Reutilizan FoodSearchSheet/PortionPickerSheet (los mismos composables que el flujo de
    // arriba) con sus propias variantes y su propio `returnMeal`, sin tocar ni una línea de las ramas
    // de arriba: el flujo real de registrar comidas es lo más crítico de la app y no debía arriesgarse
    // por una función secundaria.
    data class CollectionEditor(val draft: FoodCollection, val returnMeal: MealType) : SheetMode()
    data class CollectionSearch(val draft: FoodCollection, val returnMeal: MealType) : SheetMode()
    data class CollectionPortionPicker(val draft: FoodCollection, val returnMeal: MealType, val product: Product) : SheetMode()
}

/** Id corto y único de sobra para una colección de uso personal; mismo patrón que `newId()` en
 *  WorkoutScreen.kt (no se puede reutilizar directamente: es `private` en ese otro archivo). */
private fun newCollectionId(): String =
    Clock.System.now().toEpochMilliseconds().toString(36) + (0..999999).random().toString(36)

/**
 * Estado del buscador de alimentos, hoisteado fuera de `FoodSearchSheet` (ver el comentario junto a
 * su `remember` en `NutritionScreen`). Una sola instancia por pantalla, reseteada solo al pulsar
 * "Añadir" desde una comida, nunca al volver atrás desde el selector de ración.
 */
private class FoodSearchState {
    var tab         by mutableStateOf(0)
    var query       by mutableStateOf("")
    var results     by mutableStateOf<List<Product>>(emptyList())
    var hasSearched by mutableStateOf(false)

    fun reset() {
        tab = 0; query = ""; results = emptyList(); hasSearched = false
    }
}

private val httpClient = HttpClient {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

// ── Pantalla principal ────────────────────────────────────────────────────────

@OptIn(kotlin.time.ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    navController: NavController,
    nutritionService: NutritionService,
    profileService: ProfileService,
    healthDataProvider: HealthDataProvider,
    onRequestCameraPermission: (callback: (Boolean) -> Unit) -> Unit = {}
) {
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    var selectedDate by remember { mutableStateOf(today) }

    val meals = remember(selectedDate) {
        mapOf(
            MealType.DESAYUNO to mutableStateListOf(),
            MealType.ALMUERZO to mutableStateListOf(),
            MealType.COMIDA   to mutableStateListOf(),
            MealType.CENA     to mutableStateListOf<MealEntry>()
        )
    }

    var sheetMode by remember { mutableStateOf<SheetMode?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Vive a este nivel (no dentro de FoodSearchSheet) a propósito: el buscador se cierra y se vuelve
    // a abrir constantemente (entrar al selector de ración, volver atrás porque no era ese producto).
    // Antes la búsqueda estaba en un `remember` dentro de FoodSearchSheet, y como esa rama del `when`
    // de abajo deja de componerse mientras se ve el selector de ración, Compose la recreaba desde cero
    // al volver: la query y los resultados se perdían aunque la pantalla pareciera "la misma". Se
    // resetea explícitamente solo al pulsar "Añadir" desde una comida nueva, nunca al volver atrás.
    val foodSearchState = remember { FoodSearchState() }
    // Búsqueda independiente para cuando se está construyendo/editando una colección: si compartiera
    // `foodSearchState` con la de arriba, salir de "añadir alimento a la colección" te dejaría la
    // búsqueda normal del día contaminada con lo último que buscaste para la colección.
    val collectionSearchState = remember { FoodSearchState() }

    fun closeSheet() { scope.launch { sheetState.hide() }.invokeOnCompletion { sheetMode = null } }

    // Colecciones (Tarea B): se cargan una vez al entrar y se guardan completas cada vez que cambian,
    // igual que el plan de entrenamiento en WorkoutScreen. Es una biblioteca personal pequeña, no algo
    // que el servidor necesite consultar por dentro.
    var collections by remember { mutableStateOf(FoodCollections()) }
    LaunchedEffect(Unit) { collections = nutritionService.getCollections() }
    fun persistCollections(next: FoodCollections) {
        collections = next
        scope.launch { nutritionService.saveCollections(next) }
    }

    var activeCalories    by remember { mutableStateOf(0.0) }
    var wearableRefreshing by remember { mutableStateOf(false) }
    var hasWearable     by remember { mutableStateOf(false) }
    var wearableEnergy  by remember { mutableStateOf(WearableEnergy.EMPTY) }
    // Los objetivos los calcula entero el servidor con NutritionMath (compartido). Antes esta pantalla
    // reescalaba grasa y carbos por su cuenta con una tercera copia de la fórmula, que podía divergir
    // de la del servidor y la del Onboarding.
    var targets         by remember { mutableStateOf<NutritionTargets?>(null) }
    // Sin esto, si falla la carga del perfil el usuario cuenta todo el día contra valores por defecto
    // creyendo que son sus objetivos reales, sin ninguna señal de que no lo son (NUT-3). Empieza en
    // false y solo pasa a true en el onFailure: así no se confunde "todavía cargando" con "falló de
    // verdad" (antes mostraba el aviso rojo un instante en cada entrada a la pantalla).
    var goalsFailed     by remember { mutableStateOf(false) }

    val kcalGoal    = targets?.targetKcal?.toDouble() ?: 2000.0
    val proteinGoal = targets?.proteinG?.toDouble()   ?: 150.0
    val carbGoal    = targets?.carbsG?.toDouble()     ?: 200.0
    val fatGoal     = targets?.fatG?.toDouble()       ?: 65.0

    val isToday = selectedDate == today

    val totalKcal     by remember(selectedDate) { derivedStateOf { meals.values.flatten().sumOf { it.kcal } } }
    val totalProteins by remember(selectedDate) { derivedStateOf { meals.values.flatten().sumOf { it.proteins } } }
    val totalCarbs    by remember(selectedDate) { derivedStateOf { meals.values.flatten().sumOf { it.carbs } } }
    val totalFat      by remember(selectedDate) { derivedStateOf { meals.values.flatten().sumOf { it.fat } } }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(selectedDate) {
        // La energía de la pulsera se lee ANTES del perfil porque el objetivo depende de ella: el
        // servidor no tiene acceso a Health Connect, así que es el cliente quien aporta la media.
        val energy = runCatching { healthDataProvider.getEnergySummary() }
            .getOrDefault(WearableEnergy.EMPTY)
        wearableEnergy = energy

        profileService.getProfileResponse(
            avgActiveKcal = energy.takeIf { it.hasData }?.averageActiveKcal,
            // El arrastre solo tiene sentido para hoy. Aplicarlo a un día pasado falsearía su
            // objetivo con actividad que no es suya (BUG-N7 de la revisión técnica).
            carryOverKcal = if (isToday) energy.carryOverKcal else 0.0
        )
            .onSuccess { response ->
                targets     = response.targets
                hasWearable = response.profile.activityLevel == "wearable"
                goalsFailed = false
            }
            .onFailure { goalsFailed = true }

        nutritionService.getUserMeals(selectedDate.toString())?.forEach { entryWithTarget ->
            val mealType = MealType.entries.find { it.name == entryWithTarget.mealType }
            if (mealType != null) meals[mealType]?.add(entryWithTarget.entry)
        }
    }

    // La lectura del día en curso ya NO alimenta el objetivo (eso lo hace la media de días completos,
    // ver doc/10-fundamento-cientifico.md §1.3): aquí es solo informativa, para que el usuario vea su
    // actividad de hoy sin que el presupuesto se mueva bajo sus pies mientras come.
    LaunchedEffect(hasWearable) {
        if (!hasWearable) return@LaunchedEffect
        while (true) {
            runCatching { healthDataProvider.getTodayActiveCalories() }.onSuccess { activeCalories = it }
            kotlinx.coroutines.delay(WEARABLE_POLL_INTERVAL_MS)
        }
    }

    // ── Bottom Sheet ──────────────────────────────────────────────────────────
    if (sheetMode != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetMode = null },
            sheetState = sheetState,
            containerColor = GymSmartColors.SurfaceCard,
            shape = MaterialTheme.shapes.large
        ) {
            when (val mode = sheetMode!!) {
                is SheetMode.Scanner -> BarcodeScannerSheet(
                    onBarcodeDetected = { code ->
                        scope.launch {
                            sheetMode = null
                            try {
                                val response = httpClient.get("${AppConfig.BASE_URL}/food/barcode/$code")
                                if (!response.status.isSuccess()) {
                                    snackbarHostState.showSnackbar("Producto no encontrado (código: $code)", duration = SnackbarDuration.Short)
                                    return@launch
                                }
                                val product = Json { ignoreUnknownKeys = true }.decodeFromString<Product>(response.bodyAsText())
                                sheetMode = SheetMode.PortionPicker(mode.meal, product)
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Producto no encontrado (código: $code)", duration = SnackbarDuration.Short)
                            }
                        }
                    },
                    onClose = ::closeSheet
                )
                is SheetMode.Search -> FoodSearchSheet(
                    mealLabel = mode.meal.label,
                    nutritionService = nutritionService,
                    state = foodSearchState,
                    onProductSelected = { product -> sheetMode = SheetMode.PortionPicker(mode.meal, product) },
                    onClose = ::closeSheet,
                    collectionsTab = CollectionsTabConfig(
                        collections = collections,
                        onApply = { collection ->
                            val list = meals[mode.meal]
                            if (list != null) {
                                scope.launch {
                                    var anyFailed = false
                                    for (item in collection.items) {
                                        val newId = nutritionService.saveMealRemote(item, mode.meal.name, selectedDate.toString())
                                        if (newId != null) list.add(item.copy(id = newId)) else anyFailed = true
                                    }
                                    if (anyFailed) {
                                        snackbarHostState.showSnackbar(
                                            "Algunos alimentos de «${collection.name}» no se pudieron guardar.",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                    closeSheet()
                                }
                            }
                        },
                        onCreate = {
                            collectionSearchState.reset()
                            sheetMode = SheetMode.CollectionEditor(
                                FoodCollection(id = newCollectionId(), name = ""), mode.meal
                            )
                        },
                        onEdit = { collection -> sheetMode = SheetMode.CollectionEditor(collection, mode.meal) },
                        onDelete = { collection ->
                            persistCollections(collections.copy(
                                collections = collections.collections.filterNot { it.id == collection.id }
                            ))
                        }
                    )
                )
                is SheetMode.PortionPicker -> PortionPickerSheet(
                    mealLabel = mode.meal.label,
                    product = mode.product,
                    initialGrams = mode.existingEntry?.grams ?: 100.0,
                    nutritionService = nutritionService,
                    initialBasis = mode.existingEntry?.basis,
                    initialYieldId = mode.existingEntry?.yieldId,
                    onConfirm = { built ->
                        val list = meals[mode.meal] ?: return@PortionPickerSheet
                        val entry = built.copy(id = mode.existingEntry?.id ?: "")
                        scope.launch {
                            val newId = nutritionService.saveMealRemote(entry, mode.meal.name, selectedDate.toString())
                            if (newId != null) {
                                // El id lo asigna el servidor: sin él, esta comida no se podría editar
                                // ni borrar hasta recargar la pantalla (ver NUT-2 en el backlog).
                                val savedEntry = entry.copy(id = newId)
                                if (mode.existingEntry != null) {
                                    val idx = list.indexOfFirst { it.id == mode.existingEntry.id }
                                    if (idx >= 0) list[idx] = savedEntry
                                } else list.add(savedEntry)
                                closeSheet()
                            } else snackbarHostState.showSnackbar("Error: No se pudo guardar en la nube.", duration = SnackbarDuration.Short)
                        }
                    },
                    onBack = { sheetMode = SheetMode.Search(mode.meal) },
                    onClose = ::closeSheet
                )
                is SheetMode.EditEntry -> EditEntrySheet(
                    mealLabel = mode.meal.label,
                    entry = mode.entry,
                    onConfirm = { newGrams ->
                        scope.launch {
                            val updatedEntry = mode.entry.copy(grams = newGrams)
                            val success = nutritionService.updateMealRemote(updatedEntry, mode.meal.name, selectedDate.toString())
                            if (success) {
                                val list = meals[mode.meal] ?: return@launch
                                val idx = list.indexOfFirst { it.id == mode.entry.id }
                                if (idx >= 0) list[idx] = updatedEntry
                                closeSheet()
                            } else snackbarHostState.showSnackbar("Error al actualizar.", duration = SnackbarDuration.Short)
                        }
                    },
                    onDelete = {
                        scope.launch {
                            val success = nutritionService.deleteMealRemote(mode.entry.id)
                            if (success) { meals[mode.meal]?.removeAll { it.id == mode.entry.id }; closeSheet() }
                            else snackbarHostState.showSnackbar("Error al eliminar.", duration = SnackbarDuration.Short)
                        }
                    },
                    onClose = ::closeSheet
                )

                // ── Colecciones (Tarea B) ───────────────────────────────────────────
                is SheetMode.CollectionEditor -> CollectionEditorSheet(
                    draft = mode.draft,
                    onNameChange = { name ->
                        sheetMode = SheetMode.CollectionEditor(mode.draft.copy(name = name), mode.returnMeal)
                    },
                    onRemoveItem = { index ->
                        val next = mode.draft.copy(items = mode.draft.items.filterIndexed { i, _ -> i != index })
                        sheetMode = SheetMode.CollectionEditor(next, mode.returnMeal)
                    },
                    onAddFoodClick = {
                        collectionSearchState.reset()
                        sheetMode = SheetMode.CollectionSearch(mode.draft, mode.returnMeal)
                    },
                    onSave = {
                        if (mode.draft.name.isNotBlank() && mode.draft.items.isNotEmpty()) {
                            val existingIdx = collections.collections.indexOfFirst { it.id == mode.draft.id }
                            val nextList = if (existingIdx >= 0) {
                                collections.collections.toMutableList().also { it[existingIdx] = mode.draft }
                            } else {
                                collections.collections + mode.draft
                            }
                            persistCollections(collections.copy(collections = nextList))
                            sheetMode = SheetMode.Search(mode.returnMeal)
                        }
                    },
                    onClose = ::closeSheet
                )
                is SheetMode.CollectionSearch -> FoodSearchSheet(
                    mealLabel = "«${mode.draft.name.ifBlank { "Nueva colección" }}»",
                    nutritionService = nutritionService,
                    state = collectionSearchState,
                    onProductSelected = { product ->
                        sheetMode = SheetMode.CollectionPortionPicker(mode.draft, mode.returnMeal, product)
                    },
                    onClose = ::closeSheet,
                    collectionsTab = null // no tiene sentido anidar colecciones dentro de una colección
                )
                is SheetMode.CollectionPortionPicker -> PortionPickerSheet(
                    mealLabel = "«${mode.draft.name.ifBlank { "Nueva colección" }}»",
                    product = mode.product,
                    initialGrams = 100.0,
                    nutritionService = nutritionService,
                    onConfirm = { item ->
                        val next = mode.draft.copy(items = mode.draft.items + item)
                        sheetMode = SheetMode.CollectionEditor(next, mode.returnMeal)
                    },
                    onBack = { sheetMode = SheetMode.CollectionSearch(mode.draft, mode.returnMeal) },
                    onClose = ::closeSheet
                )
            }
        }
    }

    // ── Contenido ─────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Nutrición",
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
                    actions = {
                        // Permite forzar una relectura inmediata tras sincronizar la pulsera a mano,
                        // en vez de esperar al siguiente ciclo de WEARABLE_POLL_INTERVAL_MS.
                        if (hasWearable) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        wearableRefreshing = true
                                        runCatching { healthDataProvider.getTodayActiveCalories() }
                                            .onSuccess { activeCalories = it }
                                        wearableRefreshing = false
                                    }
                                },
                                enabled = !wearableRefreshing
                            ) {
                                if (wearableRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = GymSmartColors.TextPrimary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Actualizar calorías de la pulsera",
                                        tint = GymSmartColors.TextPrimary
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GymSmartColors.Background
                    )
                )
            }
        },
        containerColor = GymSmartColors.Background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = GymSmartColors.SurfaceElevated,
                    contentColor = GymSmartColors.TextPrimary,
                    actionColor = GymSmartColors.Primary
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(GymSmartColors.Background)
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                WeekDayPicker(
                    today = today,
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it }
                )
            }
            if (goalsFailed) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GymSmartColors.Error.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "⚠ No se pudieron cargar tus objetivos reales. Mostrando valores aproximados.",
                            modifier = Modifier.padding(12.dp),
                            color = GymSmartColors.Error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            // Antes, si el suelo de seguridad recortaba el déficit, no se decía en ningún sitio: el
            // usuario creía ir a -500 kcal/día y en realidad iba a -200, y achacaba a su cuerpo lo
            // que era un recorte de la app (doc/10-fundamento-cientifico.md §7.3).
            // El texto anterior decía "no bajamos de tu metabolismo basal". Ese suelo se retiró en su día
            // (doc/10-fundamento-cientifico.md §7.3) y el objetivo estaba al 90% del BMR justo cuando
            // este aviso aparecía: le afirmaba al usuario lo contrario de lo que hacía el cálculo
            // (AUD-2). Ahora el mensaje nombra el límite que de verdad ha mordido.
            targets?.takeIf { it.rateLimited || it.noSafeDeficit }?.let { t ->
                item {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GymSmartColors.Warning.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            when {
                                // Mismo mecanismo que noSafeDeficit (el objetivo cae a mantenimiento),
                                // pero el motivo es distinto y hay que decirlo con su propio texto: no
                                // es que falte margen de gasto, es que seguir en bajo peso sin
                                // supervisión no tiene cobertura clínica (AUD-8).
                                t.lowBmiGuard ->
                                    "⚠ Tu IMC (${t.bmi}) está en bajo peso. No te doy más déficit: te " +
                                        "dejo en mantenimiento (${t.targetKcal} kcal). Si quieres seguir " +
                                        "perdiendo, habla primero con un profesional."
                                t.noSafeDeficit ->
                                    "⚠ Con tu gasto actual no puedo darte un déficit seguro, así que te " +
                                        "dejo en mantenimiento (${t.targetKcal} kcal). Para perder grasa " +
                                        "desde aquí, lo que toca es aumentar el gasto, no comer menos."
                                t.energyFloorHit ->
                                    "⚠ Tu ritmo se ha limitado por seguridad: no bajamos de ${t.energyFloor} " +
                                        "kcal, el mínimo para tu masa magra estimada. Avanzarás a " +
                                        "${formatRate(t.effectiveRate)} en vez de " +
                                        "${formatRate(t.requestedRate)} por semana."
                                else ->
                                    "⚠ Tu ritmo se ha limitado por seguridad: el ajuste no pasa del " +
                                        "25% de tu gasto. Avanzarás a ${formatRate(t.effectiveRate)} en " +
                                        "vez de ${formatRate(t.requestedRate)} por semana."
                            },
                            modifier = Modifier.padding(12.dp),
                            color = GymSmartColors.Warning,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            // Antes esto se calculaba y se tiraba: `hasData` distinguía "0 kcal" de "no lo sé" pero la
            // interfaz no decía nada cuando era "no lo sé", así que el objetivo caía en silencio al
            // respaldo sedentario (~300 kcal/día menos para alguien activo) sin ninguna explicación —
            // el mismo "déficit encubierto" que motivó toda esta línea de trabajo (AUD-14).
            if (hasWearable && isToday && !wearableEnergy.hasData) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GymSmartColors.Warning.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "⚠ No tengo datos recientes de tu pulsera. Tu objetivo de hoy se calcula " +
                                "como si fueras sedentario, que puede quedarse corto si has hecho " +
                                "ejercicio. Sincroniza la app de tu pulsera y vuelve a entrar.",
                            modifier = Modifier.padding(12.dp),
                            color = GymSmartColors.Warning,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            // Cuadrar las kcal no es lo mismo que ser un reparto razonable: con objetivo bajo y peso
            // alto la proteína podía llegar al 85% de las kcal y los carbohidratos a 0 g, sin que nada
            // lo señalara (AUD-17).
            targets?.takeIf { it.macrosDegraded }?.let {
                item {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GymSmartColors.Warning.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "⚠ Con este objetivo calórico, el reparto de macros no es realista: la " +
                                "proteína y la grasa ocupan casi todas las kcal y apenas queda sitio " +
                                "para carbohidratos. Puede ser momento de revisar el ritmo elegido.",
                            modifier = Modifier.padding(12.dp),
                            color = GymSmartColors.Warning,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                CalorieCard(
                    totalKcal = totalKcal, kcalGoal = kcalGoal,
                    proteins = totalProteins, proteinGoal = proteinGoal,
                    carbs = totalCarbs, carbGoal = carbGoal,
                    fat = totalFat, fatGoal = fatGoal,
                    // Solo informativo, y solo si el día mostrado es hoy.
                    activeCaloriesToday = if (hasWearable && isToday) activeCalories else 0.0
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
            items(MealType.entries) { meal ->
                MealSection(
                    meal = meal,
                    entries = meals[meal] ?: emptyList(),
                    // Pulsar "Añadir" es una sesión de búsqueda nueva: se resetea a propósito. El
                    // "volver" desde el selector de ración (más abajo) NO resetea, para conservar lo
                    // que ya se había buscado.
                    onAddClick = { foodSearchState.reset(); sheetMode = SheetMode.Search(meal) },
                    onScanClick = {
                        onRequestCameraPermission { granted ->
                            if (granted) sheetMode = SheetMode.Scanner(meal)
                            else scope.launch { snackbarHostState.showSnackbar("Permiso de cámara denegado") }
                        }
                    },
                    onEntryClick = { entry -> sheetMode = SheetMode.EditEntry(meal, entry) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** Ritmo en % de peso corporal por semana, con signo, para mensajes al usuario. */
private fun formatRate(ratePct: Double): String {
    val abs = com.gymsmart.gymsmart.nutrition.NutritionMath.formatOneDecimal(kotlin.math.abs(ratePct))
    return if (ratePct < 0) "-$abs%" else "+$abs%"
}

// ── Rueda de calorías ─────────────────────────────────────────────────────────

@Composable
private fun CalorieCard(
    totalKcal: Double, kcalGoal: Double,
    proteins: Double, proteinGoal: Double,
    carbs: Double, carbGoal: Double,
    fat: Double, fatGoal: Double,
    /**
     * Actividad de hoy según la pulsera. **No se suma al objetivo**: el objetivo ya viene completo del
     * servidor, calculado con la media de días completos. Antes se sumaba aquí la lectura en curso, que
     * va siempre por debajo y hacía que el usuario comiera de menos sin saberlo.
     */
    activeCaloriesToday: Double = 0.0
) {
    val realGoal = kcalGoal

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = GymSmartColors.SurfaceCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Rueda — aquí sí usamos el gradiente permitido
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(130.dp)) {
                    val stroke = 16.dp.toPx()
                    val inset  = stroke / 2
                    val arc    = Size(size.width - stroke, size.height - stroke)

                    // Fondo del ring
                    drawArc(
                        color = GymSmartColors.SurfaceElevated,
                        startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, topLeft = Offset(inset, inset),
                        size = arc, style = Stroke(stroke, cap = StrokeCap.Butt)
                    )

                    val proteinKcal  = proteins * 4.0
                    val carbsKcal    = carbs    * 4.0
                    val fatKcal      = fat      * 9.0
                    val totalKcalRaw = proteinKcal + carbsKcal + fatKcal
                    val totalCapped  = totalKcalRaw.coerceAtMost(realGoal)
                    val totalSweep   = ((totalCapped / realGoal) * 360.0).toFloat()
                    val proteinSweep = if (totalKcalRaw > 0) (proteinKcal / totalKcalRaw).toFloat() * totalSweep else 0f
                    val carbsSweep   = if (totalKcalRaw > 0) (carbsKcal   / totalKcalRaw).toFloat() * totalSweep else 0f
                    val fatSweep     = if (totalKcalRaw > 0) (fatKcal     / totalKcalRaw).toFloat() * totalSweep else 0f

                    if (proteinSweep > 0f) drawArc(GymSmartColors.MacroProtein, -90f, proteinSweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Butt))
                    if (carbsSweep   > 0f) drawArc(GymSmartColors.MacroCarbs,   -90f + proteinSweep, carbsSweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Butt))
                    if (fatSweep     > 0f) drawArc(GymSmartColors.MacroFat,     -90f + proteinSweep + carbsSweep, fatSweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Butt))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${totalKcal.toInt()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = GymSmartColors.TextPrimary)
                    Text("/ ${realGoal.toInt()} kcal", style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextSecondary)
                    if (activeCaloriesToday > 0) {
                        Text("${activeCaloriesToday.toInt()} kcal 🔥 hoy", fontSize = 9.sp, color = GymSmartColors.Warning, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroRow("Proteínas", proteins, proteinGoal, GymSmartColors.MacroProtein)
                MacroRow("Carbs",     carbs,    carbGoal,    GymSmartColors.MacroCarbs)
                MacroRow("Grasas",    fat,      fatGoal,     GymSmartColors.MacroFat)
            }
        }
    }
}

@Composable
private fun MacroRow(label: String, value: Double, goal: Double, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary, modifier = Modifier.width(65.dp))
        Text("${value.toInt()} / ${goal.toInt()}g", style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

// ── Sección de comida ─────────────────────────────────────────────────────────

@Composable
private fun MealSection(
    meal: MealType,
    entries: List<MealEntry>,
    onAddClick: () -> Unit,
    onScanClick: (() -> Unit)? = null,
    onEntryClick: (MealEntry) -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        color = GymSmartColors.SurfaceCard
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(meal.emoji, fontSize = 18.sp)
                    Text(meal.label, style = MaterialTheme.typography.titleMedium, color = GymSmartColors.TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entries.isNotEmpty()) {
                        Text("${entries.sumOf { it.kcal }.toInt()} kcal",
                            style = MaterialTheme.typography.labelMedium, color = GymSmartColors.MacroFat, fontWeight = FontWeight.SemiBold)
                    }
                    if (onScanClick != null) {
                        Box(
                            modifier = Modifier.size(30.dp).clip(MaterialTheme.shapes.extraSmall).background(GymSmartColors.MacroCarbs),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = onScanClick, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear", tint = GymSmartColors.TextPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.size(30.dp).clip(MaterialTheme.shapes.extraSmall).background(GymSmartColors.Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onAddClick, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Añadir", tint = GymSmartColors.OnPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            entries.forEach { entry ->
                HorizontalDivider(color = GymSmartColors.Divider, modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable { onEntryClick(entry) }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = GymSmartColors.TextPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            "${entry.grams.toInt()}g${basisSuffix(entry)} · P: ${entry.proteins.toInt()}g · C: ${entry.carbs.toInt()}g · G: ${entry.fat.toInt()}g",
                            style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary
                        )
                    }
                    Text("${entry.kcal.toInt()} kcal", style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ── Sheet: Búsqueda ───────────────────────────────────────────────────────────

@Composable
private fun FoodSearchSheet(
    mealLabel: String,
    nutritionService: NutritionService,
    // Query, resultados, pestaña y si ya se buscó viven en `state`, hoisteado a NutritionScreen: esta
    // hoja se destruye y se recrea cada vez que se entra al selector de ración y se vuelve, y un
    // `remember` normal aquí dentro no sobrevive a eso (ver el comentario junto al `remember` de
    // `foodSearchState`). History/favoritos/loading/error sí se quedan locales: se recargan sin coste
    // cada vez y no son "dónde estabas", así que no hace falta conservarlos.
    state: FoodSearchState,
    onProductSelected: (Product) -> Unit,
    onClose: () -> Unit,
    // null = no mostrar la pestaña de colecciones. Se usa así cuando esta misma hoja se reutiliza
    // para buscar alimentos DENTRO de una colección (Tarea B): no tiene sentido anidar colecciones.
    collectionsTab: CollectionsTabConfig? = null
) {
    val scope = rememberCoroutineScope()
    var selectedTab by state::tab
    val tabs = if (collectionsTab != null) listOf("Buscar", "Historial", "Favoritos", "Colecciones")
               else listOf("Buscar", "Historial", "Favoritos")
    var query     by state::query
    var results   by state::results
    var history   by remember { mutableStateOf<List<Product>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<Product>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }
    var hasSearched by state::hasSearched

    LaunchedEffect(Unit) {
        history   = nutritionService.getFoodHistory()
        favorites = nutritionService.getFavorites()
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        SheetHandle()
        SheetHeader(title = "Añadir a $mealLabel", subtitle = "Elige el alimento", onClose = onClose)
        Spacer(Modifier.height(12.dp))

        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(GymSmartColors.SurfaceElevated)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (selectedTab == index) GymSmartColors.SurfaceCard else androidx.compose.ui.graphics.Color.Transparent)
                        .border(if (selectedTab == index) 1.dp else 0.dp, if (selectedTab == index) GymSmartColors.Primary else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.extraSmall)
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == index) GymSmartColors.Primary else GymSmartColors.TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (selectedTab) {
            0 -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("ej: arroz, pollo...", color = GymSmartColors.TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = GymSmartColors.TextSecondary) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = ""; results = emptyList() }) {
                                Icon(Icons.Default.Close, null, tint = GymSmartColors.TextSecondary)
                            }
                        },
                        modifier = Modifier.weight(1f), singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        colors = sheetFieldColors()
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true; errorMsg = null; results = emptyList(); hasSearched = false
                                try {
                                    val response = httpClient.get("${AppConfig.BASE_URL}/food/search") { parameter("q", query) }
                                    if (!response.status.isSuccess()) {
                                        errorMsg = "No se pudo buscar. Inténtalo de nuevo."
                                    } else {
                                        val products = Json { ignoreUnknownKeys = true }
                                            .decodeFromString<FoodSearchResponse>(response.bodyAsText()).products
                                        // Sin kcal/100g ni macros no hay nada que registrar: se filtran para
                                        // no poder guardar una comida de 0 kcal (ver NUT-4 en el backlog).
                                        val branded = products.filter { it.nutriments.estimatedKcalPer100() > 0.0 }
                                        // Los genéricos de la USDA se mezclan con los productos de marca y
                                        // todo se ordena por lo que se parece a lo buscado: Open Food Facts
                                        // devolvía "tortitas de arroz" antes que el arroz (tarea H).
                                        results = rankFoodResults(
                                            query = query,
                                            generics = matchingGenerics(query),
                                            branded = branded.map { it.product_name to it },
                                            toGeneric = { g ->
                                                Product(
                                                    product_name = g.name,
                                                    brands = GENERIC_BRAND,
                                                    nutriments = Nutriments(
                                                        energy_kcal_100g = g.kcalPer100,
                                                        proteins_100g = g.proteinsPer100,
                                                        carbohydrates_100g = g.carbsPer100,
                                                        fat_100g = g.fatPer100
                                                    )
                                                )
                                            }
                                        )
                                    }
                                } catch (e: Exception) {
                                    errorMsg = "No se pudo buscar. Comprueba tu conexión."
                                } finally {
                                    isLoading = false
                                    hasSearched = true
                                }
                            }
                        },
                        enabled = query.isNotBlank() && !isLoading,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GymSmartColors.Primary,
                            contentColor = GymSmartColors.OnPrimary,
                            disabledContainerColor = GymSmartColors.Outline
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Text(if (isLoading) "..." else "Buscar", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                errorMsg?.let { Text(it, color = GymSmartColors.Error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(4.dp))
                if (hasSearched && errorMsg == null && results.isEmpty()) {
                    EmptySheetState("Sin resultados con datos nutricionales para \"$query\"")
                } else {
                    ProductList(products = results, onProductSelected = onProductSelected)
                }
            }
            1 -> {
                if (history.isEmpty()) EmptySheetState("Aún no has añadido ningún alimento")
                else ProductList(products = history, onProductSelected = onProductSelected)
            }
            2 -> {
                if (favorites.isEmpty()) EmptySheetState("No tienes favoritos. Pulsa ❤️ en un alimento para añadirlo.")
                else ProductList(products = favorites, onProductSelected = onProductSelected)
            }
            3 -> if (collectionsTab != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = collectionsTab.onCreate,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Nueva colección")
                    }
                    if (collectionsTab.collections.collections.isEmpty()) {
                        EmptySheetState(
                            "Crea una colección con los alimentos que sueles repetir (p. ej. tu " +
                                "desayuno) para añadirlos todos de una vez."
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            collectionsTab.collections.collections.forEach { collection ->
                                CollectionCard(
                                    collection = collection,
                                    onApply = { collectionsTab.onApply(collection) },
                                    onEdit = { collectionsTab.onEdit(collection) },
                                    onDelete = { collectionsTab.onDelete(collection) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Agrupa lo que necesita la pestaña "Colecciones" de [FoodSearchSheet]: pasarlo como un solo
 *  parámetro nulable es más claro que cinco parámetros sueltos, y `null` deshabilita la pestaña. */
private class CollectionsTabConfig(
    val collections: FoodCollections,
    val onApply: (FoodCollection) -> Unit,
    val onCreate: () -> Unit,
    val onEdit: (FoodCollection) -> Unit,
    val onDelete: (FoodCollection) -> Unit
)

@Composable
private fun CollectionCard(
    collection: FoodCollection,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onApply),
        shape = MaterialTheme.shapes.small,
        color = GymSmartColors.SurfaceElevated
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    collection.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GymSmartColors.TextPrimary
                )
                val totalKcal = collection.items.sumOf { it.kcal }
                val count = collection.items.size
                Text(
                    "$count ${if (count == 1) "alimento" else "alimentos"} · ${totalKcal.toInt()} kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = GymSmartColors.TextSecondary
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar colección", tint = GymSmartColors.TextSecondary)
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar colección", tint = GymSmartColors.Error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar «${collection.name}»?") },
            text = { Text("Esto no borra las comidas que ya hayas añadido con ella, solo la colección.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Eliminar", color = GymSmartColors.Error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

// ── Sheet: Editor de colección ────────────────────────────────────────────────

@Composable
private fun CollectionEditorSheet(
    draft: FoodCollection,
    onNameChange: (String) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onAddFoodClick: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        SheetHandle()
        SheetHeader(
            title = "Colección",
            subtitle = "Los alimentos que quieras añadir todos de una vez",
            onClose = onClose
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = draft.name,
            onValueChange = { onNameChange(it.take(40)) },
            label = { Text("Nombre (ej. Desayuno)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = sheetFieldColors()
        )

        Spacer(Modifier.height(16.dp))

        if (draft.items.isEmpty()) {
            EmptySheetState("Todavía no tiene alimentos. Pulsa «Añadir alimento».")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.items.forEachIndexed { index, item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = GymSmartColors.SurfaceElevated
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GymSmartColors.TextPrimary
                                )
                                Text(
                                    "${item.grams.toInt()} g${basisSuffix(item)} · ${item.kcal.toInt()} kcal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GymSmartColors.TextSecondary
                                )
                            }
                            IconButton(onClick = { onRemoveItem(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Quitar", tint = GymSmartColors.Error)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onAddFoodClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Añadir alimento")
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSave,
            enabled = draft.name.isNotBlank() && draft.items.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = GymSmartColors.Primary,
                disabledContainerColor = GymSmartColors.Outline
            )
        ) {
            Text("Guardar colección", color = GymSmartColors.OnPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptySheetState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = GymSmartColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

/** Gramos que se muestran por defecto en la lista y con los que se abre el selector. */
private const val DEFAULT_GRAMS = 100

@Composable
private fun ProductList(products: List<Product>, onProductSelected: (Product) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(products) { product ->
            // Las kcal se muestran para la cantidad por defecto, no por 100 g: es el número que el
            // usuario va a registrar si lo toca, y así la lista se lee de un vistazo.
            val kcal = product.nutriments.estimatedKcalPer100() / 100.0 * DEFAULT_GRAMS
            val brand = product.brands?.takeIf { it.isNotBlank() }
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onProductSelected(product) },
                shape = MaterialTheme.shapes.small,
                color = GymSmartColors.SurfaceElevated
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            product.product_name ?: "Sin nombre",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = GymSmartColors.TextPrimary
                        )
                        if (brand != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                brand,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (brand == GENERIC_BRAND) GymSmartColors.Primary
                                        else GymSmartColors.TextSecondary
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$DEFAULT_GRAMS g",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GymSmartColors.TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${kcal.toInt()} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = GymSmartColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// ── Sheet: Selector de porción ────────────────────────────────────────────────

/**
 * Selector de cantidad. Construye él mismo la [MealEntry] en vez de devolver solo los gramos: es el
 * único sitio que conoce la base de pesaje elegida y los valores por 100 g ya convertidos a ella
 * (Tarea G), así que dejar que cada sitio que lo llama los recalculase sería la forma de que acabaran
 * divergiendo. Quien lo llama solo decide qué hacer con la entrada (guardarla, o meterla en una
 * colección) y le pone el id si hace falta.
 */
@Composable
private fun PortionPickerSheet(
    mealLabel: String, product: Product, initialGrams: Double,
    nutritionService: NutritionService,
    initialBasis: String? = null,
    initialYieldId: String? = null,
    onConfirm: (MealEntry) -> Unit, onBack: () -> Unit, onClose: () -> Unit
) {
    var gramsText by remember { mutableStateOf(initialGrams.toInt().toString()) }
    val grams = gramsText.toDoubleOrNull() ?: 0.0

    // Factor crudo→cocinado. Primero se intenta reconocer el alimento por su nombre; si no se
    // reconoce, el usuario puede elegirlo a mano, que es justo el dato que faltaba. Lo elegido a mano
    // manda sobre lo automático.
    val autoYield = remember(product.product_name) { findCookingYield(product.product_name) }
    // El id guardado manda sobre el nombre al reabrir una comida ya registrada.
    val pickedYield = cookingYieldById(initialYieldId) ?: autoYield

    // Suelo de cordura sobre las kcal del propio producto. Atrapa lo que el nombre no atrapa: un
    // "Arroz basmati" de 158 kcal/100 g es un vasito ya cocido, no arroz en seco. Se aplica también
    // al alimento elegido a mano, que si no sería la puerta por la que colar justo estos casos.
    val sourceKcalPer100 = product.nutriments.estimatedKcalPer100()
    val looksCooked = pickedYield?.looksAlreadyCooked(sourceKcalPer100) == true
    val cookingYield = if (looksCooked) null else pickedYield

    // Si el nombre delata que el producto ya viene cocinado o es un preparado, tampoco se ofrece nada.
    val excludedByName = remember(product.product_name) { isExcludedFromCooking(product.product_name) }
    val alreadyCooked = excludedByName || looksCooked

    var basis by remember(product.product_name) {
        mutableStateOf(
            when {
                initialBasis == "cooked" -> WeighBasis.COOKED
                initialBasis == "raw" -> WeighBasis.RAW
                // Sin base previa, se sigue el uso real de cada alimento: la carne se pesa en crudo,
                // la pasta y el arroz ya cocidos.
                else -> autoYield?.defaultBasis ?: WeighBasis.RAW
            }
        )
    }
    // Si se deselecciona el alimento de referencia, la base tiene que volver a crudo: quedarse en
    // "cocinado" sin factor mostraría los valores crudos etiquetados como cocinados.
    if (cookingYield == null && basis == WeighBasis.COOKED) basis = WeighBasis.RAW

    val n = product.nutriments
    // Los datos de origen están en base cruda (o en seco); se convierten a la base que haya elegido
    // el usuario antes de calcular cualquier total.
    fun per100(valueRaw: Double): Double = cookingYield?.per100For(valueRaw, basis) ?: valueRaw
    val kcalPer100     = per100(n.estimatedKcalPer100())
    val proteinsPer100 = per100(n?.proteins_100g ?: 0.0)
    val carbsPer100    = per100(n?.carbohydrates_100g ?: 0.0)
    val fatPer100      = per100(n?.fat_100g ?: 0.0)

    val basisLabel = cookingYield?.let {
        if (basis == WeighBasis.RAW) it.rawLabel else it.cookedLabel
    } ?: ""

    val kcal     = kcalPer100     / 100.0 * grams
    val proteins = proteinsPer100 / 100.0 * grams
    val carbs    = carbsPer100    / 100.0 * grams
    val fat      = fatPer100      / 100.0 * grams

    val scope = rememberCoroutineScope()
    var isFavorite by remember { mutableStateOf(false) }

    LaunchedEffect(product.product_name) {
        isFavorite = nutritionService.getFavorites().any { it.product_name == product.product_name }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        SheetHandle()

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.product_name ?: "Sin nombre", style = MaterialTheme.typography.titleLarge, color = GymSmartColors.TextPrimary)
                Text(
                    if (cookingYield == null) "Datos por ${grams.toInt()} g"
                    else "Datos por ${grams.toInt()} g - peso ${basisLabel.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = GymSmartColors.TextSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        scope.launch {
                            if (isFavorite) { nutritionService.removeFavorite(product.product_name ?: ""); isFavorite = false }
                            else { nutritionService.addFavorite(product); isFavorite = true }
                        }
                    },
                    modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.extraSmall)
                        .background(if (isFavorite) GymSmartColors.Error.copy(alpha = 0.12f) else GymSmartColors.SurfaceElevated)
                ) { Text(if (isFavorite) "❤️" else "🤍", fontSize = 18.sp) }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.extraSmall).background(GymSmartColors.SurfaceElevated)
                ) { Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = GymSmartColors.TextPrimary, modifier = Modifier.size(18.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Cantidad",
                    style = MaterialTheme.typography.labelMedium,
                    color = GymSmartColors.TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { if (it.length <= 5) gramsText = it.filter { c -> c.isDigit() } },
                    placeholder = { Text("Cantidad", color = GymSmartColors.TextSecondary) },
                    suffix = { Text("g", color = GymSmartColors.TextSecondary, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = sheetFieldColors(),
                    textStyle = LocalTextStyle.current.copy(color = GymSmartColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                )
            }

            // Mismo aspecto que el campo de al lado, pero es un boton: se pulsa y alterna la base.
            // Solo aparece cuando el alimento tiene factor; si no, la cantidad ocupa todo el ancho.
            if (cookingYield != null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Tipo de peso",
                        style = MaterialTheme.typography.labelMedium,
                        color = GymSmartColors.TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(MaterialTheme.shapes.small)
                            .border(1.dp, GymSmartColors.Outline, MaterialTheme.shapes.small)
                            .clickable {
                                basis = if (basis == WeighBasis.RAW) WeighBasis.COOKED else WeighBasis.RAW
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            basisLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = GymSmartColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(shape = MaterialTheme.shapes.medium, color = GymSmartColors.SurfaceElevated, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (cookingYield == null) "Valores nutricionales por ${grams.toInt()} g"
                    else "Valores nutricionales por ${grams.toInt()} g (${basisLabel.lowercase()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = GymSmartColors.TextSecondary
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    NutrientPreview("Calorías",  "${kcal.toInt()}",     "kcal", GymSmartColors.Primary)
                    NutrientPreview("Proteínas", "${proteins.toInt()}", "g",    GymSmartColors.MacroProtein)
                    NutrientPreview("Carbs",     "${carbs.toInt()}",    "g",    GymSmartColors.MacroCarbs)
                    NutrientPreview("Grasas",    "${fat.toInt()}",      "g",    GymSmartColors.MacroFat)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 100, 150, 200).forEach { preset ->
                val isSelected = gramsText == preset.toString()
                OutlinedButton(
                    onClick = { gramsText = preset.toString() },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isSelected) GymSmartColors.Primary else GymSmartColors.TextSecondary,
                        containerColor = if (isSelected) GymSmartColors.Primary.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent
                    ),
                    border = BorderStroke(1.dp, if (isSelected) GymSmartColors.Primary else GymSmartColors.Outline)
                ) { Text("${preset}g", style = MaterialTheme.typography.labelMedium) }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, GymSmartColors.Outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GymSmartColors.TextSecondary)
            ) { Text("← Volver") }
            Button(
                onClick = {
                    if (grams > 0) onConfirm(
                        MealEntry(
                            name = product.product_name ?: "Sin nombre",
                            grams = grams,
                            kcalPer100 = kcalPer100,
                            proteinsPer100 = proteinsPer100,
                            carbsPer100 = carbsPer100,
                            fatPer100 = fatPer100,
                            // Solo se guardan cuando de verdad se pudo elegir: si el alimento no tiene
                            // factor, decir "crudo" sería afirmar algo que no se sabe.
                            basis = cookingYield?.let { if (basis == WeighBasis.COOKED) "cooked" else "raw" },
                            yieldId = cookingYield?.id
                        )
                    )
                },
                enabled = grams > 0, modifier = Modifier.weight(2f), shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = GymSmartColors.Primary, contentColor = GymSmartColors.OnPrimary, disabledContainerColor = GymSmartColors.Outline)
            ) { Text("Añadir a $mealLabel", fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * Sufijo con la base de pesaje para mostrar junto a los gramos: " en seco", " cocido"... Cadena vacía
 * si la comida no tiene base registrada, que es el caso de todo lo que se registró antes de la Tarea G
 * y de los alimentos sin factor. Los nombres los da el catálogo, para que diga "en seco" en el arroz y
 * "crudo" en el pollo.
 */
private fun basisSuffix(entry: MealEntry): String {
    val basis = entry.basis ?: return ""
    // El id guardado manda sobre el nombre: el alimento de referencia pudo elegirse a mano, y entonces
    // el nombre del producto no lo encuentra en el catálogo.
    val cookingYield = cookingYieldById(entry.yieldId) ?: findCookingYield(entry.name) ?: return ""
    return " " + (if (basis == "cooked") cookingYield.cookedLabel else cookingYield.rawLabel).lowercase()
}

// ── Sheet: Editar entrada ─────────────────────────────────────────────────────

@Composable
private fun EditEntrySheet(
    mealLabel: String, entry: MealEntry,
    onConfirm: (Double) -> Unit, onDelete: () -> Unit, onClose: () -> Unit
) {
    var gramsText by remember { mutableStateOf(entry.grams.toInt().toString()) }
    val grams = gramsText.toDoubleOrNull() ?: 0.0
    val kcal     = entry.kcalPer100     / 100.0 * grams
    val proteins = entry.proteinsPer100 / 100.0 * grams
    val carbs    = entry.carbsPer100    / 100.0 * grams
    val fat      = entry.fatPer100      / 100.0 * grams

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        SheetHandle()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(entry.name, style = MaterialTheme.typography.titleLarge, color = GymSmartColors.TextPrimary)
                Text("Editar porción en $mealLabel", style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.extraSmall).background(GymSmartColors.Error.copy(alpha = 0.12f))) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = GymSmartColors.Error, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.extraSmall).background(GymSmartColors.SurfaceElevated)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = GymSmartColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = gramsText,
            onValueChange = { if (it.length <= 5) gramsText = it.filter { c -> c.isDigit() } },
            label = { Text("Cantidad (g)") },
            suffix = { Text("g", color = GymSmartColors.TextSecondary, fontWeight = FontWeight.Bold) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
            colors = sheetFieldColors(),
            textStyle = LocalTextStyle.current.copy(color = GymSmartColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        )

        Spacer(Modifier.height(16.dp))

        Surface(shape = MaterialTheme.shapes.medium, color = GymSmartColors.SurfaceElevated, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Valores para ${grams.toInt()}g${basisSuffix(entry)}", style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    NutrientPreview("Calorías",  "${kcal.toInt()}",     "kcal", GymSmartColors.Primary)
                    NutrientPreview("Proteínas", "${proteins.toInt()}", "g",    GymSmartColors.MacroProtein)
                    NutrientPreview("Carbs",     "${carbs.toInt()}",    "g",    GymSmartColors.MacroCarbs)
                    NutrientPreview("Grasas",    "${fat.toInt()}",      "g",    GymSmartColors.MacroFat)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 100, 150, 200).forEach { preset ->
                val isSelected = gramsText == preset.toString()
                OutlinedButton(
                    onClick = { gramsText = preset.toString() }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.extraSmall,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isSelected) GymSmartColors.Primary else GymSmartColors.TextSecondary, containerColor = if (isSelected) GymSmartColors.Primary.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent),
                    border = BorderStroke(1.dp, if (isSelected) GymSmartColors.Primary else GymSmartColors.Outline)
                ) { Text("${preset}g", style = MaterialTheme.typography.labelMedium) }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { if (grams > 0) onConfirm(grams) }, enabled = grams > 0,
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = GymSmartColors.Primary, contentColor = GymSmartColors.OnPrimary, disabledContainerColor = GymSmartColors.Outline)
        ) { Text("Guardar cambios", fontWeight = FontWeight.Bold) }
    }
}

// ── Componentes reutilizables ─────────────────────────────────────────────────

@Composable
private fun SheetHandle() {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(GymSmartColors.Outline))
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String, onClose: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, color = GymSmartColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GymSmartColors.TextSecondary)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.extraSmall).background(GymSmartColors.SurfaceElevated)) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = GymSmartColors.TextPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NutrientPreview(label: String, value: String, unit: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextSecondary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = GymSmartColors.TextSecondary)
    }
}

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekDayPicker(today: LocalDate, selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val dayLabels = listOf("L", "M", "M", "J", "V", "S", "D")
    val weekCount = 5
    val initialPage = 2
    val pagerState = rememberPagerState(initialPage = initialPage) { weekCount }
    val currentMonday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { pageIndex ->
        val weekOffset = pageIndex - initialPage
        val monday = currentMonday.plus(DatePeriod(days = weekOffset * 7))
        val weekDays = (0..6).map { monday.plus(DatePeriod(days = it)) }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            weekDays.forEachIndexed { index, date ->
                val isSelected = date == selectedDate
                val isToday    = date == today
                val diffDays   = (date.toEpochDays() - today.toEpochDays()).toInt()
                val inRange    = diffDays in -14..14

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(enabled = inRange) { onDateSelected(date) }
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) GymSmartColors.Primary else androidx.compose.ui.graphics.Color.Transparent)
                            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, GymSmartColors.Primary, CircleShape) else Modifier)
                    ) {
                        Text(
                            dayLabels[index], style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isSelected -> GymSmartColors.OnPrimary
                                isToday    -> GymSmartColors.Primary
                                !inRange   -> GymSmartColors.TextDisabled
                                else       -> GymSmartColors.TextSecondary
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> GymSmartColors.TextPrimary
                            !inRange   -> GymSmartColors.TextDisabled
                            else       -> GymSmartColors.TextPrimary
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.size(5.dp).clip(CircleShape).background(
                            when {
                                isSelected || isToday -> GymSmartColors.Primary
                                !inRange -> androidx.compose.ui.graphics.Color.Transparent
                                else -> GymSmartColors.TextDisabled
                            }
                        )
                    )
                }
            }
        }
    }
}

fun Double.round1(): String = (kotlin.math.round(this * 10) / 10.0).toString()