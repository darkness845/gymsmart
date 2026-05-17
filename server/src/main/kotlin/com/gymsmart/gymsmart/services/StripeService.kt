package com.gymsmart.gymsmart.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class StripeService {
    private val secretKey = System.getenv("STRIPE_SECRET_KEY")
        ?: throw IllegalStateException("STRIPE_SECRET_KEY no definida")

    private val client = HttpClient(CIO)

    suspend fun payWithCard(
        amountCents: Int,
        currency: String,
        email: String,
        cardNumber: String,
        expMonth: String,
        expYear: String,
        cvc: String,
        cardName: String
    ): Boolean {
        // En test mode usamos el token de Stripe directamente
        // tok_visa = 4242424242424242
        // tok_chargeDeclined = tarjeta rechazada
        val testToken = when (cardNumber.replace(" ", "")) {
            "4242424242424242" -> "tok_visa"
            "4000000000000002" -> "tok_chargeDeclined"
            "4000000000009995" -> "tok_chargeDeclinedInsufficientFunds"
            else -> "tok_visa" // fallback
        }

        val piRawText = client.post("https://api.stripe.com/v1/charges") {
            basicAuth(secretKey, "")
            setBody(FormDataContent(Parameters.build {
                append("amount", amountCents.toString())
                append("currency", currency)
                append("source", testToken)
                append("receipt_email", email)
                append("description", "GymSmart Premium")
            }))
        }.bodyAsText()

        println(">>> [Stripe] Charge response: $piRawText")

        val piBody = Json.parseToJsonElement(piRawText).jsonObject
        val status = piBody["status"]?.jsonPrimitive?.content
        println(">>> [Stripe] Charge status: $status")

        return status == "succeeded"
    }
}