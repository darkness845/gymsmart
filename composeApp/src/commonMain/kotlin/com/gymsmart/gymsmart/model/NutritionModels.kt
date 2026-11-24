package com.gymsmart.gymsmart.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

/**
 * kcal por 100g a usar para esta entrada. `energy-kcal_100g` es el campo estándar de Open Food Facts
 * para esto; `energy-kcal` (sin sufijo) NO está garantizado a ser por 100g en los datos de OFF (puede
 * ser por ración), así que no se usa como si lo fuera. Si falta `energy-kcal_100g`, se estima a partir
 * de los macros por 100g (regla 4/4/9), más fiable que asumir que un campo ambiguo es por 100g. Solo
 * como último recurso, si tampoco hay macros, se usa `energy-kcal` (mejor una cifra aproximada que
 * ninguna).
 */
fun Nutriments?.estimatedKcalPer100(): Double {
    this ?: return 0.0
    energy_kcal_100g?.let { return it }
    val hasMacros = proteins_100g != null || carbohydrates_100g != null || fat_100g != null
    if (hasMacros) {
        return (proteins_100g ?: 0.0) * 4.0 + (carbohydrates_100g ?: 0.0) * 4.0 + (fat_100g ?: 0.0) * 9.0
    }
    return energy_kcal ?: 0.0
}

@Serializable
data class FoodSearchResponse(val products: List<Product> = emptyList())

/**
 * Colección de alimentos con su cantidad ya fijada, para añadirlos todos de una vez a una comida en
 * vez de buscarlos uno por uno. Reutiliza `MealEntry` para cada ítem: ya tiene exactamente los
 * campos que hacen falta (nombre, gramos, kcal y macros por 100g), sin un segundo modelo paralelo.
 */
@Serializable
data class FoodCollection(
    val id: String,
    val name: String,
    val items: List<MealEntry> = emptyList()
)

/** Documento único por usuario, espejo exacto del que guarda y devuelve el servidor. */
@Serializable
data class FoodCollections(
    val collections: List<FoodCollection> = emptyList()
)
