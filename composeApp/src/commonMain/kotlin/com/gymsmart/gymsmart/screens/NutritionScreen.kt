package com.gymsmart.gymsmart.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.services.NutritionService
import com.gymsmart.gymsmart.model.MealEntry
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── Design Tokens ─────────────────────────────────────────────────────────────

private val BgColor       = Color(0xFFF5F3EF)
private val CardColor     = Color(0xFFFFFFFF)
private val AccentYellow  = Color(0xFFFFB800)
private val AccentOrange  = Color(0xFFFF8C00)
private val TextPrimary   = Color(0xFF1A1A1A)
private val TextSecondary = Color(0xFF888888)
private val DividerColor  = Color(0xFFEEEEEE)
private val ColorProtein  = Color(0xFF4CAF50)
private val ColorCarbs    = Color(0xFF2196F3)
private val ColorFat      = Color(0xFFFF5722)

// ── Modelos ───────────────────────────────────────────────────────────────────

@Serializable
data class Nutriments(
    @SerialName("energy-kcal_100g") val energy_kcal_100g: Double? = null,
    @SerialName("energy-kcal")      val energy_kcal: Double? = null,
    val proteins_100g: Double? = null,
    val carbohydrates_100g: Double? = null,
    val fat_100g: Double? = null
)

@Serializable
data class Product(
    val product_name: String? = null,
    val brands: String? = null,
    val nutriments: Nutriments? = null
)

@Serializable
data class FoodSearchResponse(
    val products: List<Product> = emptyList()
)

enum class MealType(val label: String, val emoji: String) {
    DESAYUNO("Desayuno", "🌅"),
    ALMUERZO("Almuerzo", "🥗"),
    COMIDA  ("Comida",   "🍽️"),
    CENA    ("Cena",     "🌙")
}

// Sheet que se abre: búsqueda o edición de un entry existente
private sealed class SheetMode {
    data class Search(val meal: MealType) : SheetMode()
    data class PortionPicker(
        val meal: MealType,
        val product: Product,           // producto recién buscado
        val existingEntry: MealEntry? = null  // si editamos uno ya añadido
    ) : SheetMode()
    data class EditEntry(
        val meal: MealType,
        val entry: MealEntry
    ) : SheetMode()
}

// ── HTTP Client ───────────────────────────────────────────────────────────────

private val httpClient = HttpClient {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

// ── Pantalla principal ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(navController: NavController,
                    nutritionService: NutritionService
) {

    val meals = remember {
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

    fun closeSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { sheetMode = null }
    }

    val totalKcal     by remember { derivedStateOf { meals.values.flatten().sumOf { it.kcal } } }
    val totalProteins by remember { derivedStateOf { meals.values.flatten().sumOf { it.proteins } } }
    val totalCarbs    by remember { derivedStateOf { meals.values.flatten().sumOf { it.carbs } } }
    val totalFat      by remember { derivedStateOf { meals.values.flatten().sumOf { it.fat } } }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        val remoteEntries = nutritionService.getUserMeals()
        remoteEntries?.forEach { entryWithTarget ->
            val mealType = MealType.entries.find { it.name == entryWithTarget.mealType }
            if (mealType != null) {
                meals[mealType]?.add(entryWithTarget.entry)
            }
        }
    }
    // ── Bottom Sheet ──────────────────────────────────────────────────────────
    if (sheetMode != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetMode = null },
            sheetState = sheetState,
            containerColor = BgColor,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            when (val mode = sheetMode!!) {

                is SheetMode.Search -> FoodSearchSheet(
                    mealLabel = mode.meal.label,
                    onProductSelected = { product ->
                        sheetMode = SheetMode.PortionPicker(mode.meal, product)
                    },
                    onClose = ::closeSheet
                )

                is SheetMode.PortionPicker -> PortionPickerSheet(
                    mealLabel = mode.meal.label,
                    product = mode.product,
                    initialGrams = mode.existingEntry?.grams ?: 100.0,
                    onConfirm = { grams ->
                        val list = meals[mode.meal] ?: return@PortionPickerSheet
                        val n = mode.product.nutriments

                        // 1. Creamos el objeto con los datos
                        val entry = MealEntry(
                            id = mode.existingEntry?.id ?: "",
                            name = mode.product.product_name ?: "Sin nombre",
                            grams = grams,
                            kcalPer100 = n?.energy_kcal_100g ?: n?.energy_kcal ?: 0.0,
                            proteinsPer100 = n?.proteins_100g ?: 0.0,
                            carbsPer100 = n?.carbohydrates_100g ?: 0.0,
                            fatPer100 = n?.fat_100g ?: 0.0
                        )

                        // 2. LANZAMOS EL GUARDADO A TURSO
                        scope.launch {
                            // mode.meal.name será "DESAYUNO", "ALMUERZO", etc.
                            val success = nutritionService.saveMealRemote(entry, mode.meal.name)

                            if (success) {
                                // Solo si el servidor responde OK, lo añadimos a la lista visual
                                if (mode.existingEntry != null) {
                                    val idx = list.indexOfFirst { it.id == mode.existingEntry.id }
                                    if (idx >= 0) list[idx] = entry
                                } else {
                                    list.add(entry)
                                }
                                closeSheet()
                            } else {
                                snackbarHostState.showSnackbar(
                                    message = "Error: No se pudo guardar en la nube. Revisa tu conexión.",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onBack = { sheetMode = SheetMode.Search(mode.meal) },
                    onClose = ::closeSheet
                )

                is SheetMode.EditEntry -> EditEntrySheet(
                    mealLabel = mode.meal.label,
                    entry     = mode.entry,
                    onConfirm = { newGrams ->
                        scope.launch {
                            val updatedEntry = mode.entry.copy(grams = newGrams)
                            val success = nutritionService.updateMealRemote(updatedEntry, mode.meal.name)
                            if (success) {
                                val list = meals[mode.meal] ?: return@launch
                                val idx = list.indexOfFirst { it.id == mode.entry.id }
                                if (idx >= 0) list[idx] = updatedEntry
                                closeSheet()
                            } else {
                                snackbarHostState.showSnackbar(
                                    message = "Error al actualizar. Revisa tu conexión.",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onDelete = {
                        scope.launch {
                            val success = nutritionService.deleteMealRemote(mode.entry.id)
                            if (success) {
                                meals[mode.meal]?.removeAll { it.id == mode.entry.id }
                                closeSheet()
                            } else {
                                snackbarHostState.showSnackbar(
                                    message = "Error al eliminar. Revisa tu conexión.",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    onClose = ::closeSheet
                )
            }
        }
    }

    // ── Contenido ─────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardColor)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BgColor)
                        ) {
                            Text("←", fontSize = 18.sp, color = TextPrimary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Nutrición", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPrimary)
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                CalorieCard(
                    totalKcal = totalKcal, kcalGoal = 2000.0,
                    proteins = totalProteins, proteinGoal = 122.0,
                    carbs = totalCarbs, carbGoal = 167.0,
                    fat = totalFat, fatGoal = 55.0
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            items(MealType.entries) { meal ->
                MealSection(
                    meal    = meal,
                    entries = meals[meal] ?: emptyList(),
                    onAddClick  = { sheetMode = SheetMode.Search(meal) },
                    onEntryClick = { entry -> sheetMode = SheetMode.EditEntry(meal, entry) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
}

// ── Rueda de calorías ─────────────────────────────────────────────────────────

@Composable
private fun CalorieCard(
    totalKcal: Double, kcalGoal: Double,
    proteins: Double, proteinGoal: Double,
    carbs: Double, carbGoal: Double,
    fat: Double, fatGoal: Double
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(CardColor)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(contentAlignment = Alignment.Center) {
                val progress = (totalKcal / kcalGoal).coerceIn(0.0, 1.0).toFloat()
                Canvas(modifier = Modifier.size(130.dp)) {
                    val stroke = 16.dp.toPx()
                    val inset  = stroke / 2
                    val arc    = Size(size.width - stroke, size.height - stroke)
                    drawArc(color = Color(0xFFF0EDE8), startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, topLeft = Offset(inset, inset),
                        size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(color = AccentYellow, startAngle = -90f,
                        sweepAngle = 360f * progress, useCenter = false,
                        topLeft = Offset(inset, inset), size = arc,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${totalKcal.toInt()}", color = TextPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("/ ${kcalGoal.toInt()} kcal", color = TextSecondary, fontSize = 10.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroRow("Proteínas", proteins, proteinGoal, ColorProtein)
                MacroRow("Carbs",     carbs,    carbGoal,    ColorCarbs)
                MacroRow("Grasas",    fat,      fatGoal,     ColorFat)
            }
        }
    }
}

@Composable
private fun MacroRow(label: String, value: Double, goal: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(65.dp))
        Text("${value.toInt()} / ${goal.toInt()}g", color = TextPrimary,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Sección de comida ─────────────────────────────────────────────────────────

@Composable
private fun MealSection(
    meal: MealType,
    entries: List<MealEntry>,
    onAddClick: () -> Unit,
    onEntryClick: (MealEntry) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
            .padding(16.dp)
            .animateContentSize()
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(meal.emoji, fontSize = 18.sp)
                    Text(meal.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entries.isNotEmpty()) {
                        Text("${entries.sumOf { it.kcal }.toInt()} kcal",
                            color = AccentOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                            .background(AccentYellow),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onAddClick, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Añadir",
                                tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            entries.forEach { entry ->
                Divider(color = DividerColor, modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEntryClick(entry) }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name, color = TextPrimary, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium)
                        Text(
                            "${entry.grams.toInt()}g · P: ${entry.proteins.toInt()}g · C: ${entry.carbs.toInt()}g · G: ${entry.fat.toInt()}g",
                            color = TextSecondary, fontSize = 11.sp
                        )
                    }
                    Text("${entry.kcal.toInt()} kcal", color = TextSecondary,
                        fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ── Sheet: Búsqueda ───────────────────────────────────────────────────────────

@Composable
private fun FoodSearchSheet(
    mealLabel: String,
    onProductSelected: (Product) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query     by remember { mutableStateOf("") }
    var results   by remember { mutableStateOf<List<Product>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp).padding(bottom = 32.dp)
    ) {
        SheetHandle()
        SheetHeader(title = "Añadir a $mealLabel", subtitle = "Busca el alimento", onClose = onClose)
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("ej: chicken, rice...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; results = emptyList() }) {
                            Icon(Icons.Default.Close, null, tint = TextSecondary)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = searchFieldColors()
            )
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true; errorMsg = null; results = emptyList()
                        try {
                            val raw = httpClient.get("${AppConfig.BASE_URL}/food/search") {
                                parameter("q", query)
                            }.bodyAsText()
                            results = Json { ignoreUnknownKeys = true }
                                .decodeFromString<FoodSearchResponse>(raw).products
                        } catch (e: Exception) { errorMsg = "Error: ${e.message}" }
                        isLoading = false
                    }
                },
                enabled = query.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentYellow),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(if (isLoading) "..." else "Buscar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))
        errorMsg?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { product ->
                val kcal = product.nutriments?.energy_kcal_100g ?: product.nutriments?.energy_kcal ?: 0.0
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardColor)
                        .clickable { onProductSelected(product) }
                        .padding(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.product_name ?: "Sin nombre",
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MacroChip("⚡ ${kcal.toInt()} kcal/100g", AccentYellow)
                                MacroChip("🥩 ${(product.nutriments?.proteins_100g ?: 0.0).toInt()}g", ColorProtein)
                                MacroChip("🍞 ${(product.nutriments?.carbohydrates_100g ?: 0.0).toInt()}g", ColorCarbs)  // ← añade
                                MacroChip("🧈 ${(product.nutriments?.fat_100g ?: 0.0).toInt()}g", ColorFat)              // ← añade
                            }
                        }
                        Icon(Icons.Default.Add, contentDescription = null,
                            tint = AccentYellow, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

// ── Sheet: Selector de porción ────────────────────────────────────────────────

@Composable
private fun PortionPickerSheet(
    mealLabel: String,
    product: Product,
    initialGrams: Double,
    onConfirm: (Double) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    var gramsText by remember { mutableStateOf(initialGrams.toInt().toString()) }
    val grams = gramsText.toDoubleOrNull() ?: 0.0
    val n = product.nutriments
    val kcalPer100     = n?.energy_kcal_100g ?: n?.energy_kcal ?: 0.0
    val proteinsPer100 = n?.proteins_100g ?: 0.0
    val carbsPer100    = n?.carbohydrates_100g ?: 0.0
    val fatPer100      = n?.fat_100g ?: 0.0

    val kcal     = kcalPer100     / 100.0 * grams
    val proteins = proteinsPer100 / 100.0 * grams
    val carbs    = carbsPer100    / 100.0 * grams
    val fat      = fatPer100      / 100.0 * grams

    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp).padding(bottom = 32.dp)
    ) {
        SheetHandle()
        SheetHeader(
            title = product.product_name ?: "Sin nombre",
            subtitle = "Ajusta la cantidad",
            onClose = onClose
        )
        Spacer(Modifier.height(20.dp))

        // Input de gramos
        OutlinedTextField(
            value = gramsText,
            onValueChange = { if (it.length <= 5) gramsText = it.filter { c -> c.isDigit() } },
            label = { Text("Cantidad (g)", color = TextSecondary) },
            suffix = { Text("g", color = TextSecondary, fontWeight = FontWeight.Bold) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = searchFieldColors(),
            textStyle = LocalTextStyle.current.copy(
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        )

        Spacer(Modifier.height(20.dp))

        // Preview de macros en tiempo real
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardColor)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Valores nutricionales para ${grams.toInt()}g",
                    fontSize = 13.sp, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround) {
                    NutrientPreview("Calorías", "${kcal.toInt()}", "kcal", AccentYellow)
                    NutrientPreview("Proteínas", "${proteins.toInt()}", "g", ColorProtein)
                    NutrientPreview("Carbs", "${carbs.toInt()}", "g", ColorCarbs)
                    NutrientPreview("Grasas", "${fat.toInt()}", "g", ColorFat)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Accesos rápidos de gramos
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 100, 150, 200).forEach { preset ->
                OutlinedButton(
                    onClick = { gramsText = preset.toString() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = if (gramsText == preset.toString()) AccentYellow else TextSecondary,
                        containerColor = if (gramsText == preset.toString()) AccentYellow.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (gramsText == preset.toString()) AccentYellow else Color(0xFFDDDDDD)
                        )
                    )
                ) {
                    Text("${preset}g", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(contentColor = TextSecondary)
            ) {
                Text("← Volver")
            }
            Button(
                onClick = { if (grams > 0) onConfirm(grams) },
                enabled = grams > 0,
                modifier = Modifier.weight(2f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentYellow)
            ) {
                Text("Añadir a $mealLabel", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Sheet: Editar entrada existente ──────────────────────────────────────────

@Composable
private fun EditEntrySheet(
    mealLabel: String,
    entry: MealEntry,
    onConfirm: (Double) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    var gramsText by remember { mutableStateOf(entry.grams.toInt().toString()) }
    val grams = gramsText.toDoubleOrNull() ?: 0.0

    val kcal     = entry.kcalPer100     / 100.0 * grams
    val proteins = entry.proteinsPer100 / 100.0 * grams
    val carbs    = entry.carbsPer100    / 100.0 * grams
    val fat      = entry.fatPer100      / 100.0 * grams

    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp).padding(bottom = 32.dp)
    ) {
        SheetHandle()

        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(entry.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                Text("Editar porción en $mealLabel", fontSize = 13.sp, color = TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFEEEE))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                        tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEEEEEE))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar",
                        tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = gramsText,
            onValueChange = { if (it.length <= 5) gramsText = it.filter { c -> c.isDigit() } },
            label = { Text("Cantidad (g)", color = TextSecondary) },
            suffix = { Text("g", color = TextSecondary, fontWeight = FontWeight.Bold) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = searchFieldColors(),
            textStyle = LocalTextStyle.current.copy(
                color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp
            )
        )

        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(CardColor).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Valores para ${grams.toInt()}g", fontSize = 13.sp, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround) {
                    NutrientPreview("Calorías", "${kcal.toInt()}", "kcal", AccentYellow)
                    NutrientPreview("Proteínas", "${proteins.toInt()}", "g", ColorProtein)
                    NutrientPreview("Carbs", "${carbs.toInt()}", "g", ColorCarbs)
                    NutrientPreview("Grasas", "${fat.toInt()}", "g", ColorFat)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 100, 150, 200).forEach { preset ->
                OutlinedButton(
                    onClick = { gramsText = preset.toString() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = if (gramsText == preset.toString()) AccentYellow else TextSecondary,
                        containerColor = if (gramsText == preset.toString()) AccentYellow.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (gramsText == preset.toString()) AccentYellow else Color(0xFFDDDDDD)
                        )
                    )
                ) {
                    Text("${preset}g", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { if (grams > 0) onConfirm(grams) },
            enabled = grams > 0,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentYellow)
        ) {
            Text("Guardar cambios", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Componentes reutilizables ─────────────────────────────────────────────────

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(width = 40.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp)).background(Color(0xFFDDDDDD)))
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String, onClose: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
            Text(subtitle, fontSize = 13.sp, color = TextSecondary)
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEEEEEE))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar",
                tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NutrientPreview(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        Text(unit, fontSize = 11.sp, color = TextSecondary)
        Text(label, fontSize = 10.sp, color = TextSecondary)
    }
}

@Composable
private fun MacroChip(text: String, color: Color) {
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
        .background(color.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(text, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun searchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = AccentYellow,
    unfocusedBorderColor = Color(0xFFDDDDDD),
    cursorColor = AccentYellow,
    focusedContainerColor = CardColor,
    unfocusedContainerColor = CardColor
)

fun Double.round1(): String = (kotlin.math.round(this * 10) / 10.0).toString()