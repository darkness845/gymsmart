package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

@Serializable
data class MealEntry(
    val id: String = "",
    val name: String,
    val grams: Double,
    val kcalPer100: Double,
    val proteinsPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double
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
    val name: String, val grams: Double, val kcalPer100: Double,
    val proteinsPer100: Double, val carbsPer100: Double,
    val fatPer100: Double, val mealType: String
)
