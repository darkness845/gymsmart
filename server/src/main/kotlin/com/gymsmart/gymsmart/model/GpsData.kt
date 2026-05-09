package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

@Serializable
data class GpsData(
    val lat: Double,
    val lon: Double,
    val timestamp: Long
)

@Serializable
data class RoutePointDto(
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val speedMs: Float = 0f,
    val altitudeM: Double = 0.0,
    val accuracyM: Float = 0f
)

@Serializable
data class SaveRouteRequest(
    val name: String,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val startedAt: Long,
    val points: List<RoutePointDto>
)

@Serializable
data class GpsRouteResponse(
    val id: String,
    val userId: String,
    val name: String,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val startedAt: Long,
    val createdAt: Long,
    val pointCount: Int = 0,
    val isPublic: Boolean = false
)

@Serializable
data class SaveRouteResponse(val id: String)