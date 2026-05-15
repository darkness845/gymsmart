package com.gymsmart.gymsmart.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*

class EmailService {
    private val apiKey = System.getenv("BREVO_API_KEY")
        ?: throw IllegalStateException("BREVO_API_KEY no definida")
    private val fromEmail = System.getenv("BREVO_USER")
        ?: throw IllegalStateException("BREVO_USER no definida")

    private val client = HttpClient(CIO)

    private suspend fun sendEmail(to: String, subject: String, html: String): Boolean {
        return try {
            val response = client.post("https://api.brevo.com/v3/smtp/email") {
                header("api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                      "sender": {"name": "GymSmart", "email": "$fromEmail"},
                      "to": [{"email": "$to"}],
                      "subject": "$subject",
                      "htmlContent": ${kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(html))}
                    }
                """.trimIndent())
            }
            println(">>> Brevo response: ${response.status}")
            response.status.isSuccess()
        } catch (e: Exception) {
            println(">>> Error enviando email: ${e.message}")
            false
        }
    }

    suspend fun sendEmailVerification(toEmail: String, toName: String, token: String): Boolean {
        val html = """
            <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 32px; background: #f5f3ef; border-radius: 16px;">
                <h2 style="color: #1a1a1a;">¡Bienvenido a GymSmart, $toName! 💪</h2>
                <p style="color: #444;">Para activar tu cuenta introduce este código en la app:</p>
                <div style="background: #1a1a1a; border-radius: 8px; padding: 14px; text-align: center; margin: 24px 0; letter-spacing: 2px;">
                    <code style="font-size: 13px; font-weight: bold; color: #FFB800; word-break: break-all;">$token</code>
                </div>
                <p style="color: #888; font-size: 12px;">Mantén pulsado el código para copiarlo. Caduca en 24 horas.</p>
                <p style="color: #888; font-size: 12px;">Si no creaste esta cuenta, ignora este correo.</p>
            </div>
        """.trimIndent()
        return sendEmail(toEmail, "Verifica tu cuenta de GymSmart", html)
    }

    suspend fun sendPasswordReset(toEmail: String, toName: String, token: String): Boolean {
        val html = """
            <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 32px; background: #f5f3ef; border-radius: 16px;">
                <h2 style="color: #1a1a1a;">Hola, $toName 👋</h2>
                <p style="color: #444;">Recibimos una solicitud para restablecer tu contraseña de GymSmart.</p>
                <p style="color: #444;">Introduce este código en la app:</p>
                <div style="background: #1a1a1a; border-radius: 8px; padding: 14px; text-align: center; margin: 24px 0; letter-spacing: 2px;">
                    <code style="font-size: 13px; font-weight: bold; color: #FFB800; word-break: break-all;">$token</code>
                </div>
                <p style="color: #888; font-size: 12px;">Mantén pulsado el código para copiarlo. Caduca en 1 hora.</p>
                <p style="color: #888; font-size: 12px;">Si no solicitaste este cambio, ignora este correo.</p>
            </div>
        """.trimIndent()
        return sendEmail(toEmail, "Restablecer contraseña de GymSmart", html)
    }

    suspend fun sendPurchaseConfirmation(toEmail: String, toName: String): Boolean {
        val html = """
        <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 32px; background: #f5f3ef; border-radius: 16px;">
            <h2 style="color: #1a1a1a;">¡Gracias por tu compra, $toName! 🎉</h2>
            <p style="color: #444;">Tu suscripción <strong>GymSmart Premium</strong> ya está activa.</p>
            <div style="background: #1a1a1a; border-radius: 8px; padding: 14px; text-align: center; margin: 24px 0;">
                <span style="font-size: 32px;">💪</span>
                <p style="color: #FFB800; font-weight: bold; margin: 8px 0;">Plan Premium activado</p>
                <p style="color: #aaa; font-size: 12px;">Análisis de físico con IA desbloqueado</p>
            </div>
            <p style="color: #888; font-size: 12px;">Si tienes cualquier problema escríbenos a soporte.</p>
        </div>
    """.trimIndent()
        return sendEmail(toEmail, "¡Bienvenido a GymSmart Premium! 🏆", html)
    }
}