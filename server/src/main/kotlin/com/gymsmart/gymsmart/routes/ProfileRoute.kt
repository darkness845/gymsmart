package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.UserSession
import com.gymsmart.gymsmart.services.ProfileService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ProfileRequest(
    val weightKg:      Double,
    val heightCm:      Double,
    val age:           Int,
    val sex:           String,
    val activityLevel: String,
    val goal:          String,
    val goalRate:      Double
)

fun Route.profileRoutes(profileService: ProfileService) {

    route("/profile") {

        // GET /profile/has  — ¿el usuario ya completó su perfil?
        get("/has") {
            val session = call.sessions.get<UserSession>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Sin sesión")

            call.respond(HttpStatusCode.OK, buildJsonObject {
                put("hasProfile", profileService.hasProfile(session.userId))
            })
        }

        // GET /profile/me  — devuelve perfil + targets calculados
        get("/me") {
            val session = call.sessions.get<UserSession>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Sin sesión")

            val profile = profileService.getProfile(session.userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Sin perfil")

            val targets = profileService.computeTargets(profile)
            call.respond(HttpStatusCode.OK, buildJsonObject {
                putJsonObject("profile") {
                    put("userId",        profile.userId)
                    put("weightKg",      profile.weightKg)
                    put("heightCm",      profile.heightCm)
                    put("age",           profile.age)
                    put("sex",           profile.sex)
                    put("activityLevel", profile.activityLevel)
                    put("goal",          profile.goal)
                    put("goalRate",      profile.goalRate)
                }
                putJsonObject("targets") {
                    put("tdee",        targets.tdee)
                    put("targetKcal",  targets.targetKcal)
                    put("proteinG",    targets.proteinG)
                    put("carbsG",      targets.carbsG)
                    put("fatG",        targets.fatG)
                    put("bmi",         targets.bmi)
                    put("bmiCategory", targets.bmiCategory)
                }
            })
        }

        // POST /profile/save  — crea o actualiza el perfil
        post("/save") {
            val session = call.sessions.get<UserSession>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Sin sesión")

            val req = try {
                call.receive<ProfileRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, "Cuerpo inválido")
            }

            // Validaciones básicas
            if (req.weightKg !in 30.0..300.0)
                return@post call.respond(HttpStatusCode.BadRequest, "Peso fuera de rango (30–300 kg)")
            if (req.heightCm !in 100.0..250.0)
                return@post call.respond(HttpStatusCode.BadRequest, "Altura fuera de rango (100–250 cm)")
            if (req.age !in 14..100)
                return@post call.respond(HttpStatusCode.BadRequest, "Edad fuera de rango (14–100)")
            if (req.sex !in listOf("male", "female"))
                return@post call.respond(HttpStatusCode.BadRequest, "Sexo inválido")
            if (req.activityLevel !in listOf("sedentary", "light", "moderate", "active", "very_active"))
                return@post call.respond(HttpStatusCode.BadRequest, "Nivel de actividad inválido")
            if (req.goal !in listOf("lose_fat", "maintain", "gain_muscle"))
                return@post call.respond(HttpStatusCode.BadRequest, "Objetivo inválido")

            val profile = profileService.upsertProfile(
                userId        = session.userId,
                weightKg      = req.weightKg,
                heightCm      = req.heightCm,
                age           = req.age,
                sex           = req.sex,
                activityLevel = req.activityLevel,
                goal          = req.goal,
                goalRate      = req.goalRate
            )
            val targets = profileService.computeTargets(profile)

            call.respond(HttpStatusCode.OK, buildJsonObject {
                putJsonObject("profile") {
                    put("userId",        profile.userId)
                    put("weightKg",      profile.weightKg)
                    put("heightCm",      profile.heightCm)
                    put("age",           profile.age)
                    put("sex",           profile.sex)
                    put("activityLevel", profile.activityLevel)
                    put("goal",          profile.goal)
                    put("goalRate",      profile.goalRate)
                }
                putJsonObject("targets") {
                    put("tdee",        targets.tdee)
                    put("targetKcal",  targets.targetKcal)
                    put("proteinG",    targets.proteinG)
                    put("carbsG",      targets.carbsG)
                    put("fatG",        targets.fatG)
                    put("bmi",         targets.bmi)
                    put("bmiCategory", targets.bmiCategory)
                }
            })
        }
    }
}