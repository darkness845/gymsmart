package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

@Serializable
data class GpsData(
    val lat: Double,
    val lon: Double,
    val timestamp: Long
)