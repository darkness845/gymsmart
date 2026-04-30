package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

@Serializable
data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val speedMs: Float = 0f,
    val altitudeM: Double = 0.0,
    val accuracyM: Float
)

data class ActiveRoute(
    val points: List<RoutePoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val durationSeconds: Long = 0,
    val isRecording: Boolean = false
)