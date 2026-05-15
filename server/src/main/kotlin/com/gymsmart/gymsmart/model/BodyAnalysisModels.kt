package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

@Serializable
data class BodyAnalysisRequest(
    val imageBase64: String
)

@Serializable
data class BodyAnalysisResponse(
    val bodyFat: String,
    val estimatedWeight: String,
    val muscleMass: String,
    val physiqueType: String,
    val summary: String
)