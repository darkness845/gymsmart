package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.gymsmart.gymsmart.ui.icons.LineIcons
import com.gymsmart.gymsmart.ui.formatThousands
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.gymsmart.gymsmart.nutrition.foodIconFor
import com.gymsmart.gymsmart.nutrition.per100For
import com.gymsmart.gymsmart.nutrition.rawWeightFor
import com.gymsmart.gymsmart.services.HealthDataProvider
import com.gymsmart.gymsmart.services.NutritionService
import com.gymsmart.gymsmart.services.ProfileService
import com.gymsmart.gymsmart.services.WEARABLE_POLL_INTERVAL_MS
import com.gymsmart.gymsmart.services.WearableEnergy
import com.gymsmart.gymsmart.ui.theme.MelocotonTheme
import com.gymsmart.gymsmart.ui.theme.Palette
import com.gymsmart.gymsmart.ui.theme.SystemBarsAppearance
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

/**
 * NutritionScreen con el estilo Melocotón (Tarea 3). Solo cambia el aspecto: la estructura y la lógica
 * de la pantalla son las de siempre, en [NutritionScreenContent].
 */
@Composable
fun NutritionScreen(
    navController: NavController,
    nutritionService: NutritionService,
    profileService: ProfileService,
    healthDataProvider: HealthDataProvider,
    onRequestCameraPermission: (callback: (Boolean) -> Unit) -> Unit = {}
) {
    MelocotonTheme {
        // Fondo crema hasta arriba: la hora y la batería tienen que ir en oscuro.
        SystemBarsAppearance(darkStatusIcons = true)
        NutritionScreenContent(navController, nutritionService, profileService, healthDataProvider, onRequestCameraPermission)
    }
}

@OptIn(kotlin.time.ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
private fun NutritionScreenContent(
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
            containerColor = Palette.current.surface,
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
            // Cabecera de la maqueta N3: atrás redondo, título con la fecha del día que se mira y, si hay
            // pulsera, el refresco redondo. Sin TopAppBar: así el aspecto es el mismo que en el resto.
            Row(
                Modifier.fillMaxWidth().background(Palette.current.background).statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleBack { navController.popBackStack() }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nutrición", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Palette.current.textPrimary)
                    Text(nutritionLongDate(selectedDate), fontSize = 12.sp, color = Palette.current.textSecondary)
                }
                // Permite forzar una relectura inmediata tras sincronizar la pulsera a mano,
                // en vez de esperar al siguiente ciclo de WEARABLE_POLL_INTERVAL_MS.
                if (hasWearable) {
                    Surface(
                        onClick = {
                            scope.launch {
                                wearableRefreshing = true
                                runCatching { healthDataProvider.getTodayActiveCalories() }
                                    .onSuccess { activeCalories = it }
                                wearableRefreshing = false
                            }
                        },
                        enabled = !wearableRefreshing,
                        shape = CircleShape,
                        color = Palette.current.surface,
                        border = BorderStroke(1.dp, Palette.current.border),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (wearableRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.current.textPrimary)
                            } else {
                                Icon(LineIcons.Refresh, contentDescription = "Actualizar calorías de la pulsera", tint = Palette.current.textPrimary, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
        },
        containerColor = Palette.current.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Palette.current.hero,
                    contentColor = Palette.current.onHero,
                    actionColor = Palette.current.onHeroAccent
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.current.background)
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
                        color = Palette.current.danger.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "⚠ No se pudieron cargar tus objetivos reales. Mostrando valores aproximados.",
                            modifier = Modifier.padding(12.dp),
                            color = Palette.current.danger,
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
                        color = Palette.current.accentText.copy(alpha = 0.12f),
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
                            color = Palette.current.accentText,
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
                        color = Palette.current.accentText.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "⚠ No tengo datos recientes de tu pulsera. Tu objetivo de hoy se calcula " +
                                "como si fueras sedentario, que puede quedarse corto si has hecho " +
                                "ejercicio. Sincroniza la app de tu pulsera y vuelve a entrar.",
                            modifier = Modifier.padding(12.dp),
                            color = Palette.current.accentText,
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
                        color = Palette.current.accentText.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "⚠ Con este objetivo calórico, el reparto de macros no es realista: la " +
                                "proteína y la grasa ocupan casi todas las kcal y apenas queda sitio " +
                                "para carbohidratos. Puede ser momento de revisar el ritmo elegido.",
                            modifier = Modifier.padding(12.dp),
                            color = Palette.current.accentText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                BudgetCard(
                    totalKcal = totalKcal, kcalGoal = kcalGoal,
                    proteins = totalProteins, proteinGoal = proteinGoal,
                    carbs = totalCarbs, carbGoal = carbGoal,
                    fat = totalFat, fatGoal = fatGoal,
                    isToday = isToday,
                    // Solo informativo, y solo si el día mostrado es hoy.
                    activeCaloriesToday = if (hasWearable && isToday) activeCalories else 0.0
                )
            }
            item {
                MiniTitle("Lo que has comido", Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp))
            }
            itemsIndexed(MealType.entries) { index, meal ->
                MealTimelineItem(
                    meal = meal,
                    entries = meals[meal] ?: emptyList(),
                    isLast = index == MealType.entries.lastIndex,
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
            }
        }
    }
}

/** Ritmo en % de peso corporal por semana, con signo, para mensajes al usuario. */
private fun formatRate(ratePct: Double): String {
    val abs = com.gymsmart.gymsmart.nutrition.NutritionMath.formatOneDecimal(kotlin.math.abs(ratePct))
    return if (ratePct < 0) "-$abs%" else "+$abs%"
}

// ── Presupuesto del día (maqueta N3) ──────────────────────────────────────────

/**
 * Lo comido contra el objetivo en una sola barra partida por macros (cada tramo, las kcal que aporta
 * ese macro), con lo que queda a la derecha. Sustituye a la rueda: se lee de un vistazo y deja más
 * sitio a las comidas.
 */
@Composable
private fun BudgetCard(
    totalKcal: Double, kcalGoal: Double,
    proteins: Double, proteinGoal: Double,
    carbs: Double, carbGoal: Double,
    fat: Double, fatGoal: Double,
    isToday: Boolean,
    /**
     * Actividad de hoy según la pulsera. **No se suma al objetivo**: el objetivo ya viene completo del
     * servidor, calculado con la media de días completos. Antes se sumaba aquí la lectura en curso, que
     * va siempre por debajo y hacía que el usuario comiera de menos sin saberlo.
     */
    activeCaloriesToday: Double = 0.0
) {
    val p = Palette.current
    val goal = kcalGoal.coerceAtLeast(1.0)
    val left = (kcalGoal - totalKcal).toLong()
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface)
            .border(1.dp, p.border, RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                MiniTitle(if (isToday) "Presupuesto de hoy" else "Presupuesto del día")
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                    Text(formatThousands(totalKcal.toLong()), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, color = p.textPrimary)
                    Text(" / ${formatThousands(kcalGoal.toLong())} kcal", fontSize = 14.sp, color = p.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                // Pasarse no es un error que haya que gritar: se dice cuánto, en el mismo sitio.
                Text(formatThousands(kotlin.math.abs(left)), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (left >= 0) p.accentText else p.danger)
                Text(if (left >= 0) "quedan" else "de más", fontSize = 12.sp, color = p.textSecondary)
            }
        }
        // Una barra, tres tramos: lo que aporta cada macro sobre el objetivo. Si se pasa, se llena.
        val pk = proteins * 4; val ck = carbs * 4; val fk = fat * 9
        val scale = if (pk + ck + fk > goal) goal / (pk + ck + fk) else 1.0
        val fr = listOf(pk, ck, fk).map { (it * scale / goal).toFloat() }
        val rest = (1f - fr.sum()).coerceAtLeast(0f)
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(p.track)) {
            listOf(p.macroProtein, p.macroCarbs, p.macroFat).forEachIndexed { i, c ->
                if (fr[i] > 0.001f) Box(Modifier.weight(fr[i]).fillMaxHeight().background(c))
            }
            if (rest > 0.001f) Spacer(Modifier.weight(rest))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MacroLegend(proteins, proteinGoal, "proteínas", p.macroProtein)
            MacroLegend(carbs, carbGoal, "carbs", p.macroCarbs)
            MacroLegend(fat, fatGoal, "grasas", p.macroFat)
        }
        if (activeCaloriesToday > 0) {
            HorizontalDivider(color = p.divider, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(LineIcons.Flame, null, tint = p.accentText, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    buildAnnotatedString {
                        append("La pulsera suma ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = p.textPrimary)) { append("${activeCaloriesToday.toInt()} kcal") }
                        append(" de actividad hoy")
                    },
                    fontSize = 12.sp, color = p.textSecondary
                )
            }
        }
    }
}

@Composable
private fun MacroLegend(value: Double, goal: Double, label: String, color: androidx.compose.ui.graphics.Color) {
    val p = Palette.current
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Column {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = p.textPrimary)) { append("${value.toInt()}") }
                    append(" / ${goal.toInt()} g")
                },
                fontSize = 12.sp, color = p.textSecondary
            )
            Text(label, fontSize = 12.sp, color = p.textSecondary)
        }
    }
}

// ── Comida en la línea del día (maqueta N3, sin horas) ────────────────────────

@Composable
private fun MealTimelineItem(
    meal: MealType,
    entries: List<MealEntry>,
    isLast: Boolean,
    onAddClick: () -> Unit,
    onScanClick: (() -> Unit)? = null,
    onEntryClick: (MealEntry) -> Unit
) {
    val p = Palette.current
    val filled = entries.isNotEmpty()
    val dashColor = androidx.compose.ui.graphics.Color(0xFFCDBBAD)
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(IntrinsicSize.Min)) {
        // La línea: un círculo por comida (relleno si ya hay algo, punteado si no) unidos por un trazo.
        Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (filled) p.hero else androidx.compose.ui.graphics.Color.Transparent)
                    .then(
                        if (filled) Modifier else Modifier.drawBehind {
                            drawCircle(
                                color = dashColor, radius = size.minDimension / 2 - 1.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) { Text(meal.emoji, fontSize = 17.sp) }
            if (!isLast) Box(Modifier.padding(vertical = 4.dp).width(2.dp).weight(1f).background(p.border))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 4.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(meal.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.textPrimary, modifier = Modifier.weight(1f))
                if (filled) {
                    Text("${entries.sumOf { it.kcal }.toInt()} kcal", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    if (onScanClick != null) { MealIconButton(Icons.Default.QrCodeScanner, "Escanear", p.track, p.textPrimary, onScanClick); Spacer(Modifier.width(6.dp)) }
                    MealIconButton(Icons.Default.Add, "Añadir a ${meal.label.lowercase()}", p.hero, p.onHero, onAddClick)
                }
            }
            if (filled) {
                Column(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface)
                        .border(1.dp, p.border, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    entries.forEachIndexed { i, entry ->
                        if (i > 0) HorizontalDivider(color = p.divider)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onEntryClick(entry) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(foodIconFor(entry.name), fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.textPrimary)
                                Text("${entry.grams.toInt()} g${basisSuffix(entry)}", fontSize = 12.sp, color = p.textSecondary)
                            }
                            Text("${entry.kcal.toInt()} kcal", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.textSecondary)
                        }
                    }
                }
            } else {
                // Comida vacía: el botón grande punteado invita a añadir; el escáner, al lado.
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(15.dp)).background(p.surface)
                            .drawBehind {
                                drawRoundRect(
                                    color = dashColor,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(15.dp.toPx()),
                                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                                )
                            }
                            .clickable(onClick = onAddClick),
                        contentAlignment = Alignment.Center
                    ) { Text("+ Añadir ${meal.label.lowercase()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.textSecondary) }
                    if (onScanClick != null) { Spacer(Modifier.width(8.dp)); MealIconButton(Icons.Default.QrCodeScanner, "Escanear", p.track, p.textPrimary, onScanClick, size = 44) }
                }
            }
        }
    }
}

@Composable
private fun MealIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, description: String,
    bg: androidx.compose.ui.graphics.Color, tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit, size: Int = 34
) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(11.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(17.dp)) }
}

/** "Viernes, 27 de noviembre", para la cabecera. */
private fun nutritionLongDate(d: LocalDate): String {
    val dias = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    return "${dias[d.dayOfWeek.ordinal]}, ${d.day} de ${meses[d.month.ordinal]}"
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
                .background(Palette.current.surfaceSunken)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (selectedTab == index) Palette.current.surface else androidx.compose.ui.graphics.Color.Transparent)
                        .border(if (selectedTab == index) 1.dp else 0.dp, if (selectedTab == index) Palette.current.accentText else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.extraSmall)
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == index) Palette.current.accentText else Palette.current.textSecondary
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
                        placeholder = { Text("ej: arroz, pollo...", color = Palette.current.textSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Palette.current.textSecondary) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = ""; results = emptyList() }) {
                                Icon(Icons.Default.Close, null, tint = Palette.current.textSecondary)
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
                            containerColor = Palette.current.hero,
                            contentColor = Palette.current.onHero,
                            disabledContainerColor = Palette.current.border
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Text(if (isLoading) "..." else "Buscar", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                errorMsg?.let { Text(it, color = Palette.current.danger, style = MaterialTheme.typography.bodySmall) }
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
        color = Palette.current.surfaceSunken
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
                    color = Palette.current.textPrimary
                )
                val totalKcal = collection.items.sumOf { it.kcal }
                val count = collection.items.size
                Text(
                    "$count ${if (count == 1) "alimento" else "alimentos"} · ${totalKcal.toInt()} kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.current.textSecondary
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar colección", tint = Palette.current.textSecondary)
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar colección", tint = Palette.current.danger)
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
                    Text("Eliminar", color = Palette.current.danger)
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
                        color = Palette.current.surfaceSunken
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(foodIconFor(item.name), fontSize = 18.sp, modifier = Modifier.width(26.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Palette.current.textPrimary
                                )
                                Text(
                                    "${item.grams.toInt()} g${basisSuffix(item)} · ${item.kcal.toInt()} kcal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.current.textSecondary
                                )
                            }
                            IconButton(onClick = { onRemoveItem(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Quitar", tint = Palette.current.danger)
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
                containerColor = Palette.current.hero,
                disabledContainerColor = Palette.current.border
            )
        ) {
            Text("Guardar colección", color = Palette.current.onHero, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptySheetState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.current.textSecondary, textAlign = TextAlign.Center)
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
                color = Palette.current.surfaceSunken
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ancho fijo para que los nombres queden alineados aunque el emoji mida distinto.
                    Text(
                        foodIconFor(product.product_name),
                        fontSize = 22.sp,
                        modifier = Modifier.width(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            product.product_name ?: "Sin nombre",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.current.textPrimary
                        )
                        if (brand != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                brand,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (brand == GENERIC_BRAND) Palette.current.accentText
                                        else Palette.current.textSecondary
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$DEFAULT_GRAMS g",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.current.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${kcal.toInt()} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.current.textSecondary
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
            Text(foodIconFor(product.product_name), fontSize = 26.sp, modifier = Modifier.padding(end = 10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.product_name ?: "Sin nombre", style = MaterialTheme.typography.titleLarge, color = Palette.current.textPrimary)
                Text(
                    if (cookingYield == null) "Datos por ${grams.toInt()} g"
                    else "Datos por ${grams.toInt()} g - peso ${basisLabel.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.current.textSecondary
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
                        .background(if (isFavorite) Palette.current.danger.copy(alpha = 0.12f) else Palette.current.surfaceSunken)
                ) { Text(if (isFavorite) "❤️" else "🤍", fontSize = 18.sp) }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.extraSmall).background(Palette.current.surfaceSunken)
                ) { Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Palette.current.textPrimary, modifier = Modifier.size(18.dp)) }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Cantidad",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.current.textSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { if (it.length <= 5) gramsText = it.filter { c -> c.isDigit() } },
                    placeholder = { Text("Cantidad", color = Palette.current.textSecondary) },
                    suffix = { Text("g", color = Palette.current.textSecondary, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = sheetFieldColors(),
                    textStyle = LocalTextStyle.current.copy(color = Palette.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                )
            }

            // Mismo aspecto que el campo de al lado, pero es un boton: se pulsa y alterna la base.
            // Solo aparece cuando el alimento tiene factor; si no, la cantidad ocupa todo el ancho.
            if (cookingYield != null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Tipo de peso",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.current.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(MaterialTheme.shapes.small)
                            .border(1.dp, Palette.current.border, MaterialTheme.shapes.small)
                            .clickable {
                                basis = if (basis == WeighBasis.RAW) WeighBasis.COOKED else WeighBasis.RAW
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            basisLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Palette.current.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(shape = MaterialTheme.shapes.medium, color = Palette.current.surfaceSunken, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (cookingYield == null) "Valores nutricionales por ${grams.toInt()} g"
                    else "Valores nutricionales por ${grams.toInt()} g (${basisLabel.lowercase()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.current.textSecondary
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    NutrientPreview("Calorías",  "${kcal.toInt()}",     "kcal", Palette.current.accentText)
                    NutrientPreview("Proteínas", "${proteins.toInt()}", "g",    Palette.current.macroProtein)
                    NutrientPreview("Carbs",     "${carbs.toInt()}",    "g",    Palette.current.macroCarbs)
                    NutrientPreview("Grasas",    "${fat.toInt()}",      "g",    Palette.current.macroFat)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, Palette.current.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.current.textSecondary)
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
                colors = ButtonDefaults.buttonColors(containerColor = Palette.current.hero, contentColor = Palette.current.onHero, disabledContainerColor = Palette.current.border)
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
                Text(entry.name, style = MaterialTheme.typography.titleLarge, color = Palette.current.textPrimary)
                Text("Editar porción en $mealLabel", style = MaterialTheme.typography.bodySmall, color = Palette.current.textSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.extraSmall).background(Palette.current.danger.copy(alpha = 0.12f))) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Palette.current.danger, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.extraSmall).background(Palette.current.surfaceSunken)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Palette.current.textPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = gramsText,
            onValueChange = { if (it.length <= 5) gramsText = it.filter { c -> c.isDigit() } },
            label = { Text("Cantidad (g)") },
            suffix = { Text("g", color = Palette.current.textSecondary, fontWeight = FontWeight.Bold) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
            colors = sheetFieldColors(),
            textStyle = LocalTextStyle.current.copy(color = Palette.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        )

        Spacer(Modifier.height(16.dp))

        Surface(shape = MaterialTheme.shapes.medium, color = Palette.current.surfaceSunken, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Valores para ${grams.toInt()}g${basisSuffix(entry)}", style = MaterialTheme.typography.bodySmall, color = Palette.current.textSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    NutrientPreview("Calorías",  "${kcal.toInt()}",     "kcal", Palette.current.accentText)
                    NutrientPreview("Proteínas", "${proteins.toInt()}", "g",    Palette.current.macroProtein)
                    NutrientPreview("Carbs",     "${carbs.toInt()}",    "g",    Palette.current.macroCarbs)
                    NutrientPreview("Grasas",    "${fat.toInt()}",      "g",    Palette.current.macroFat)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { if (grams > 0) onConfirm(grams) }, enabled = grams > 0,
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.current.hero, contentColor = Palette.current.onHero, disabledContainerColor = Palette.current.border)
        ) { Text("Guardar cambios", fontWeight = FontWeight.Bold) }
    }
}

// ── Componentes reutilizables ─────────────────────────────────────────────────

@Composable
private fun SheetHandle() {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Palette.current.border))
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String, onClose: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.current.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.current.textSecondary)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.extraSmall).background(Palette.current.surfaceSunken)) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Palette.current.textPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NutrientPreview(label: String, value: String, unit: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = Palette.current.textSecondary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.current.textSecondary)
    }
}

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = Palette.current.accentText,
    unfocusedBorderColor    = Palette.current.border,
    focusedLabelColor       = Palette.current.accentText,
    unfocusedLabelColor     = Palette.current.textSecondary,
    cursorColor             = Palette.current.accentText,
    focusedTextColor        = Palette.current.textPrimary,
    unfocusedTextColor      = Palette.current.textPrimary,
    focusedContainerColor   = Palette.current.surfaceSunken,
    unfocusedContainerColor = Palette.current.surfaceSunken,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekDayPicker(today: LocalDate, selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val dayLabels = listOf("L", "M", "X", "J", "V", "S", "D")
    val weekCount = 5
    val initialPage = 2
    val pagerState = rememberPagerState(initialPage = initialPage) { weekCount }
    val currentMonday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { pageIndex ->
        val weekOffset = pageIndex - initialPage
        val monday = currentMonday.plus(DatePeriod(days = weekOffset * 7))
        val weekDays = (0..6).map { monday.plus(DatePeriod(days = it)) }

        // Baldosas de la maqueta: el día que se mira en chocolate, hoy con borde melocotón si no es
        // el que se mira, los futuros y los que están fuera del rango consultable, apagados.
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekDays.forEachIndexed { index, date ->
                val isSelected = date == selectedDate
                val isToday    = date == today
                val diffDays   = (date.toEpochDays() - today.toEpochDays()).toInt()
                val inRange    = diffDays in -14..14
                val dim        = diffDays > 0 || !inRange
                val p = Palette.current
                val shape = RoundedCornerShape(15.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(shape)
                        .background(
                            when {
                                isSelected -> p.hero
                                dim -> androidx.compose.ui.graphics.Color.Transparent
                                else -> p.surface
                            }
                        )
                        .then(
                            when {
                                isSelected -> Modifier
                                isToday -> Modifier.border(1.5.dp, p.accent, shape)
                                dim -> Modifier
                                else -> Modifier.border(1.dp, p.border, shape)
                            }
                        )
                        .clickable(enabled = inRange) { onDateSelected(date) }
                ) {
                    Text(
                        dayLabels[index], fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = when {
                            isSelected -> p.onHeroSecondary
                            dim -> p.chevron
                            else -> p.textSecondary
                        }
                    )
                    Text(
                        date.day.toString(), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                        color = when {
                            isSelected -> p.onHero
                            dim -> p.chevron
                            else -> p.textPrimary
                        }
                    )
                }
            }
        }
    }
}

fun Double.round1(): String = (kotlin.math.round(this * 10) / 10.0).toString()