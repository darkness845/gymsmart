package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

@Serializable
data class WeightEntry(
    val day: String,
    val weightKg: Float
)