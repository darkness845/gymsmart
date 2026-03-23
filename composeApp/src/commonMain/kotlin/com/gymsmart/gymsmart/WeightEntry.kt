package com.gymsmart.gymsmart

import kotlinx.serialization.Serializable

@Serializable
data class WeightEntry(
    val date: String, // formato ISO: "2026-03-23"
    val weight: Float
)