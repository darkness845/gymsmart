package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName
import io.ktor.client.statement.*

// Modelos de respuesta
@Serializable
data class Nutriments(
    @SerialName("energy-kcal_100g") val energy_kcal_100g: Double? = null,
    @SerialName("energy-kcal") val energy_kcal: Double? = null,
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

// Cliente HTTP
private val client = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

@Composable
fun NutritionScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Product>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Cabecera
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { navController.popBackStack() }) {
                Text("← Volver")
            }
            Spacer(Modifier.width(16.dp))
            Text("Nutrición 🥗", style = MaterialTheme.typography.headlineLarge)
        }

        Spacer(Modifier.height(24.dp))

        // Buscador
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar alimento...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMsg = null
                        results = emptyList()
                        try {
                            val rawResponse = client
                                .get("http://localhost:8080/food/search") {
                                    parameter("q", query)
                                }.bodyAsText()
                            println("RESPUESTA RAW: $rawResponse")
                            val response = Json { ignoreUnknownKeys = true }.decodeFromString<FoodSearchResponse>(rawResponse)
                            results = response.products
                        } catch (e: Exception) {
                            errorMsg = "Error: ${e.message}"
                        }
                        isLoading = false
                    }
                },
                enabled = query.isNotBlank() && !isLoading
            ) {
                Text(if (isLoading) "Buscando..." else "Buscar")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Error
        errorMsg?.let {
            Text(it, color = Color.Red, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }

        // Resultados
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { product ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            product.product_name ?: "Sin nombre",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        product.brands?.let {
                            Text(it, fontSize = 12.sp, color = Color(0xFFAAAAAA))
                        }
                        Spacer(Modifier.height(8.dp))
                        product.nutriments?.let { n ->
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                val kcal = n.energy_kcal_100g ?: n.energy_kcal
                                NutrientChip("⚡ ${kcal?.round1() ?: "--"} kcal")
                                NutrientChip("🥩 ${(n.proteins_100g ?: 0.0).round1()}g proteína")
                                NutrientChip("🍞 ${(n.carbohydrates_100g ?: 0.0).round1()}g carbs")
                                NutrientChip("🧈 ${(n.fat_100g ?: 0.0).round1()}g grasa")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NutrientChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color(0xFF2A2A2A)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = Color(0xFFCCCCCC)
        )
    }
}

fun Double.round1(): String = (kotlin.math.round(this * 10) / 10.0).toString()