package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.UserSession
import com.gymsmart.gymsmart.services.EmailService
import com.gymsmart.gymsmart.services.ProfileService
import com.gymsmart.gymsmart.services.StripeService
import com.gymsmart.gymsmart.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class PayCardRequest(
    val cardNumber: String,
    val expMonth: String,
    val expYear: String,
    val cvc: String,
    val cardName: String
)

fun Route.subscriptionRoutes(
    profileService: ProfileService,
    stripeService: StripeService,
    userService: UserService,
    emailService: EmailService
) {
    route("/subscription") {

        get("/status") {
            val session = call.sessions.get<UserSession>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val (plan, expiresAt) = profileService.getSubscription(session.userId)
            val active = plan == "premium" && expiresAt > System.currentTimeMillis()
            call.respond(HttpStatusCode.OK, buildJsonObject {
                put("plan", plan)
                put("active", active)
                put("expiresAt", expiresAt)
            })
        }

        // Nuevo endpoint único: crea PI + PaymentMethod + confirma todo en backend
        post("/pay") {
            val session = call.sessions.get<UserSession>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val req = try {
                call.receive<PayCardRequest>()
            } catch (e: Exception) {
                println(">>> ERROR deserializando PayCardRequest: ${e.message}")
                return@post call.respond(HttpStatusCode.BadRequest, "Cuerpo inválido: ${e.message}")
            }

            println(">>> Pay request recibido: card=${req.cardNumber.take(4)}****")

            val user = userService.findById(session.userId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Usuario no encontrado")

            val result = stripeService.payWithCard(
                amountCents = 499,
                currency    = "eur",
                email       = user.email,
                cardNumber  = req.cardNumber,
                expMonth    = req.expMonth,
                expYear     = req.expYear,
                cvc         = req.cvc,
                cardName    = req.cardName
            )

            println(">>> Stripe result: $result")

            if (!result) return@post call.respond(HttpStatusCode.PaymentRequired, "Pago rechazado")

            val duration = 60L * 60 * 1000
            profileService.activatePremium(session.userId, duration)
            println(">>> Premium activado para ${session.userId}")

            emailService.sendPurchaseConfirmation(user.email, user.name)
            println(">>> Email enviado a ${user.email}")

            call.respond(HttpStatusCode.OK, buildJsonObject { put("ok", true) })
        }
    }
}