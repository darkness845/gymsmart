package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.SubscriptionStatus
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class PayCardRequest(
    val cardNumber: String,
    val expMonth: String,
    val expYear: String,
    val cvc: String,
    val cardName: String
)

class SubscriptionService(private val client: HttpClient) {

    suspend fun getStatus(): Result<SubscriptionStatus> = runCatching {
        client.get("${AppConfig.BASE_URL}/subscription/status").body()
    }

    suspend fun pay(
        cardNumber: String, expMonth: String, expYear: String,
        cvc: String, cardName: String
    ): Result<Unit> = runCatching {
        val response = client.post("${AppConfig.BASE_URL}/subscription/pay") {
            contentType(ContentType.Application.Json)
            setBody(PayCardRequest(cardNumber, expMonth, expYear, cvc, cardName))
        }
        if (response.status != HttpStatusCode.OK) {
            error("Error del servidor: ${response.status} — ${response.bodyAsText()}")
        }
    }
}