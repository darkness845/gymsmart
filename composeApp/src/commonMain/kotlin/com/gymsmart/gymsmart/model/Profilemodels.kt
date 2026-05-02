package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

@Serializable
data class ProfileRequest(
    val weightKg:      Double,
    val heightCm:      Double,
    val age:           Int,
    val sex:           String,
    val activityLevel: String,
    val goal:          String,
    val goalRate:      Double
)

@Serializable
data class UserProfile(
    val userId:        String,
    val weightKg:      Double,
    val heightCm:      Double,
    val age:           Int,
    val sex:           String,
    val activityLevel: String,
    val goal:          String,
    val goalRate:      Double
)

@Serializable
data class NutritionTargets(
    val tdee:        Int,
    val targetKcal:  Int,
    val proteinG:    Int,
    val carbsG:      Int,
    val fatG:        Int,
    val bmi:         Double,
    val bmiCategory: String
)

@Serializable
data class ProfileResponse(
    val profile: UserProfile,
    val targets: NutritionTargets
)