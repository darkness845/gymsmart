package com.gymsmart.gymsmart.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

class EmailService {
    private val apiKey = System.getenv("RESEND_API_KEY")
        ?: throw IllegalStateException("RESEND_API_KEY no definida")

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    suspend fun sendPasswordReset(toEmail: String, toName: String, token: String): Boolean {

        val response = client.post("https://api.resend.com/emails") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("from", "GymSmart <onboarding@resend.dev>")
                put("to", JsonArray(listOf(JsonPrimitive(toEmail))))
                put("subject", "Restablecer contraseña de GymSmart")
                put("html", """
                    <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 32px; background: #f5f3ef; border-radius: 16px;">
                        <h2 style="color: #1a1a1a;">Hola, $toName 👋</h2>
                        <p style="color: #444;">Recibimos una solicitud para restablecer tu contraseña de GymSmart.</p>
                        <p style="color: #444;">Introduce este código en la app para continuar:</p>
                        <div style="background: #FFB800; border-radius: 12px; padding: 16px; text-align: center; margin: 24px 0;">
                            <code style="font-size: 14px; font-weight: bold; color: #1a1a1a; word-break: break-all;">$token</code>
                        </div>
                        <p style="color: #888; font-size: 12px;">Este código caduca en 1 hora. Si no solicitaste este cambio, ignora este correo.</p>
                    </div>
                """.trimIndent())
            })
        }

        return response.status == HttpStatusCode.OK || response.status.value == 200
    }
}