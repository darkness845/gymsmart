package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

/**
 * [basis] es la base sobre la que se pesó el alimento ("raw", "cooked" o null) y [yieldId] el alimento
 * del catálogo de factores que se usó para convertir (Tarea G).
 *
 * Los valores por 100 g **ya están convertidos** a esa base, así que el cálculo del total no cambia:
 * los gramos de [grams] son los que el usuario pesó de verdad, en la base que eligió. Los dos campos
 * sirven para poder decírselo ("150 g en cocido"), para reabrir la edición donde la dejó y para
 * **deshacer** la conversión si el alimento se reutiliza desde el historial. [yieldId] es necesario
 * además de [basis] porque el usuario puede haber elegido el alimento de referencia a mano, y entonces
 * su nombre no basta para volver a encontrarlo en el catálogo.
 *
 * Los dos son null en las comidas registradas antes de esta función y en los alimentos sin conversión.
 */
@Serializable
data class MealEntry(
    val id: String = "",
    val name: String,
    val grams: Double,
    val kcalPer100: Double,
    val proteinsPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val basis: String? = null,
    val yieldId: String? = null
) {
    val kcal:     Double get() = kcalPer100     / 100.0 * grams
    val proteins: Double get() = proteinsPer100 / 100.0 * grams
    val carbs:    Double get() = carbsPer100    / 100.0 * grams
    val fat:      Double get() = fatPer100      / 100.0 * grams
}

@Serializable
data class MealEntryWithTarget(
    val entry: MealEntry,
    val mealType: String
)

@Serializable
data class MealEntryRequest(
    val name: String,
    val grams: Double,
    val kcalPer100: Double,
    val proteinsPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val mealType: String,
    val date: String,
    val basis: String? = null,
    val yieldId: String? = null
)

@Serializable
data class FavoriteBody(
    val name: String,
    val kcalPer100: Double,
    val proteinsPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double
)
