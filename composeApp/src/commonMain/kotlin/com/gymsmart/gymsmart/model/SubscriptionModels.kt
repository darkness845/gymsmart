package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionStatus(
    val plan: String,
    val active: Boolean,
    val expiresAt: Long
)

@Serializable
data class ClientSecretResponse(
    val clientSecret: String
)

@Serializable
data class ConfirmPaymentRequest(
    val paymentIntentId: String
)