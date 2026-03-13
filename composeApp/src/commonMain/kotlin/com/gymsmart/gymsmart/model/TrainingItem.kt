package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

@Serializable
data class TrainingItem(
    val date: String,
    val duration: Int,
    val status: String
)