package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

@Serializable
data class WeightEntry(
    val day: Int,
    val weight: Float
)