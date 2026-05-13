package com.gymsmart.gymsmart.services

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties

class EmailService {
    private val gmailUser = System.getenv("GMAIL_USER")
        ?: throw IllegalStateException("GMAIL_USER no definida")
    private val gmailPass = System.getenv("GMAIL_PASS")
        ?: throw IllegalStateException("GMAIL_PASS no definida")

    private val session: Session by lazy {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.ssl.enable", "true")
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "465")
            put("mail.smtp.ssl.trust", "smtp.gmail.com")
        }
        Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(gmailUser, gmailPass)
        })
    }

    private fun sendEmail(to: String, subject: String, html: String): Boolean {
        return try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(gmailUser, "GymSmart"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject, "UTF-8")
                setContent(html, "text/html; charset=UTF-8")
            }
            Transport.send(message)
            println(">>> Email enviado OK a $to")
            true
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
}