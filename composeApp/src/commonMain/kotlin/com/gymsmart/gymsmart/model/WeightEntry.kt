package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

@Serializable
data class WeightEntry(
    val day: String,
    val weightKg: Float,
    /** El servidor omite la clave si ese día no se registró cintura. */
    val waistCm: Float? = null
)