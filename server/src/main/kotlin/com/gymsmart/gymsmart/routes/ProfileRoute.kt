package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.UserSession
import com.gymsmart.gymsmart.nutrition.NutritionMath
import com.gymsmart.gymsmart.services.NutritionService
import com.gymsmart.gymsmart.services.ProfileService
import com.gymsmart.gymsmart.services.UserProfile
import com.gymsmart.gymsmart.services.UserService
import io.ktor.http.*
import java.time.LocalDate
import java.time.Period
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
    val goalRate:      Double,
    // Ya no se usa (AUD-16): nada leía la columna has_wearable. Se sigue aceptando porque el JSON del
    // servidor es estricto y las apps ya instaladas lo mandan; quitarlo les daría un 400 al guardar.
    @Suppress("unused") val hasWearable: Boolean = false
)

/** Respuesta común de perfil + objetivos, para no repetir el mapeo en cada endpoint. */
/**
 * Tope de cordura para las calorías activas que aporta el cliente, en kcal/día.
 *
 * Un día de ultrafondo ronda las 6.000 kcal activas, así que 8.000 deja margen de sobra para cualquier
 * caso real y sigue atrapando la basura (un bug de agregación, una app que escriba totales en el
 * registro de activas, o un parámetro manipulado).
 */
private const val MAX_ACTIVE_KCAL = 8_000.0

/**
 * Convierte un parámetro de query en un `Double` válido dentro de un rango.
 *
 * - Ausente → 0.0, que es el comportamiento que ya había y significa "no tengo ese dato".
 * - No numérico, `NaN`, `Infinity` o fuera de rango → `null`, para que la ruta responda 400.
 *
 * `String.toDoubleOrNull()` acepta `"NaN"` e `"Infinity"`, de ahí la comprobación explícita de finitud:
 * sin ella, `NaN` llegaba al cálculo y `roundToInt()` lanzaba, convirtiendo un parámetro mal formado en
 * un error 500 (AUD-15).
 */
private fun String?.asFiniteInRange(min: Double, max: Double): Double? {
    if (this == null) return 0.0
    val value = toDoubleOrNull() ?: return null
    if (!value.isFinite()) return null
    // Fuera de rango se **recorta**, no se rechaza: estos parámetros son telemetría de la pulsera, no
    // una intención del usuario. Si Health Connect devuelve una burrada, degradar el objetivo es mucho
    // mejor que dejar la pantalla de nutrición sin cargar. Lo no numérico sí se rechaza, porque eso es
    // un cliente roto, no un dato raro.
    return value.coerceIn(min, max)
}

private fun profilePayload(profile: UserProfile, targets: NutritionMath.TargetResult) =
    buildJsonObject {
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
            put("bmr",            targets.bmr)
            put("tdee",           targets.tdee)
            put("targetKcal",     targets.targetKcal)
            put("proteinG",       targets.proteinG)
            put("carbsG",         targets.carbsG)
            put("fatG",           targets.fatG)
            put("bmi",            targets.bmi)
            put("bmiCategory",    targets.bmiCategory)
            put("rateLimited",    targets.rateLimited)
            put("requestedRate",  targets.requestedRate)
            put("effectiveRate",  targets.effectiveRate)
            put("tdeeIsMeasured", targets.tdeeIsMeasured)
            put("energyFloorHit", targets.energyFloorHit)
            put("energyFloor",    targets.energyFloor)
            put("appliedCarryOver", targets.appliedCarryOver)
            put("noSafeDeficit",  targets.noSafeDeficit)
            put("lowBmiGuard",    targets.lowBmiGuard)
            put("macrosDegraded", targets.macrosDegraded)
        }
    }

fun Route.profileRoutes(
    profileService:   ProfileService,
    userService:      UserService,
    nutritionService: NutritionService
) {

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

            // En modo pulsera el cliente aporta la media de días completos y el arrastre de ayer: es
            // quien tiene acceso a Health Connect. Si no los manda, el objetivo cae al BMR, que es
            // conservador pero no inventa actividad que no se ha podido medir.
            // Se validan igual que el resto del perfil. Antes entraban con `toDoubleOrNull() ?: 0.0`,
            // sin rango ni comprobación de finitud (AUD-15), y `String.toDoubleOrNull()` **acepta** las
            // cadenas "NaN" e "Infinity": `NaN` acababa en `roundToInt()`, que lanza por contrato, y
            // devolvía un 500; un arrastre de −3000 producía un objetivo negativo con macros negativas.
            val avgActive = call.request.queryParameters["avgActiveKcal"].asFiniteInRange(0.0, MAX_ACTIVE_KCAL)
                ?: return@get call.respond(HttpStatusCode.BadRequest, "avgActiveKcal fuera de rango")
            val carryOver = call.request.queryParameters["carryOverKcal"].asFiniteInRange(-MAX_ACTIVE_KCAL, MAX_ACTIVE_KCAL)
                ?: return@get call.respond(HttpStatusCode.BadRequest, "carryOverKcal fuera de rango")

            val targets = profileService.computeTargets(profile, avgActive, carryOver)
            call.respond(HttpStatusCode.OK, profilePayload(profile, targets))
        }

        // POST /profile/adaptive — mide el gasto real y ajusta el objetivo si hay datos suficientes.
        // Era GET (AUD-24): un endpoint con efecto de escritura es cacheable y reintentable por
        // proxies/infraestructura sin que nada lo impida a nivel HTTP, y semánticamente un GET no
        // debería mutar nada. El `Mutex` por usuario de `runAdaptiveAdjustment` ya cubre la
        // concurrencia; esto cubre la semántica.
        post("/adaptive") {
            val session = call.sessions.get<UserSession>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Sin sesión")

            val since = java.time.LocalDate.now().minusDays(28).toString()
            val intake = nutritionService.getDailyIntake(session.userId, since)

            // Sin la media de activas, el ancla del ajuste en modo pulsera caía al respaldo `BMR × 1,2`
            // y el ajuste salía en dirección contraria a la medición (AUD-4).
            val avgActive = call.request.queryParameters["avgActiveKcal"].asFiniteInRange(0.0, MAX_ACTIVE_KCAL)
                ?: 0.0

            // El objetivo de ANTES del ajuste, para poder informar del cambio real que ve el usuario.
            val before = profileService.getProfile(session.userId)
                ?.let { profileService.computeTargets(it, avgActive) }

            when (val result = profileService.runAdaptiveAdjustment(session.userId, intake, avgActiveKcal = avgActive)) {
                is NutritionMath.AdaptiveResult.NotEnoughData ->
                    call.respond(HttpStatusCode.OK, buildJsonObject {
                        put("adjusted", false)
                        put("reason", result.reason)
                    })

                is NutritionMath.AdaptiveResult.Adjusted -> {
                    // `deltaKcal` del `reconcile` es la diferencia entre gastos mezclados, que **no** es
                    // lo que cambia el objetivo del usuario: con el ancla mal puesta llegaba a tener el
                    // signo opuesto al cambio real ("+100 kcal/día" mientras el objetivo bajaba 139).
                    // Ahora se informa del cambio medido sobre el objetivo, que es lo que él va a comer.
                    val after = profileService.getProfile(session.userId)
                        ?.let { profileService.computeTargets(it, avgActive) }
                    val deltaTarget = if (before != null && after != null) {
                        after.targetKcal - before.targetKcal
                    } else null

                    call.respond(HttpStatusCode.OK, buildJsonObject {
                        put("adjusted", true)
                        put("newTdee", (after?.tdee ?: result.newTdee.toInt()))
                        put("measuredTdee", result.rawMeasured.toInt())
                        put("previousTdee", (before?.tdee ?: result.previousTdee.toInt()))
                        put("deltaKcal", deltaTarget ?: result.deltaKcal)
                        put("newTargetKcal", after?.targetKcal)
                    })
                }
            }
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
            if (req.activityLevel !in NutritionMath.ALL_ACTIVITY_LEVELS)
                return@post call.respond(HttpStatusCode.BadRequest, "Nivel de actividad inválido")
            if (req.goal !in NutritionMath.ALL_GOALS)
                return@post call.respond(HttpStatusCode.BadRequest, "Objetivo inválido")
            // goalRate es ahora % de peso corporal por semana, y cada objetivo tiene su rango seguro
            // (doc/10-fundamento-cientifico.md §3.2). Antes no se validaba en el servidor en absoluto.
            if (!NutritionMath.isRateValid(req.goal, req.goalRate))
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Ritmo fuera del rango seguro para ese objetivo"
                )
            // AUD-19: el cliente guarda la fecha de nacimiento como 8 dígitos sin separadores
            // ("01012000", ver ProfileScreen.kt), pero esto parseaba con el patrón "dd/MM/yyyy" — nunca
            // coincidía, siempre lanzaba, y caía siempre a `req.age` sin que nadie lo notara. El
            // mecanismo que debía mantener la edad autoritativa en el servidor nunca se ejecutaba.
            //
            // Al arreglar el formato se reactiva un riesgo que estaba dormido por el mismo motivo
            // (una fecha futura o absurda escrita a mano producía una edad fuera de rango): por eso la
            // edad resuelta se valida contra el mismo rango 14..100 que ya se exige a `req.age`, y si
            // no cuadra se descarta en vez de persistir una edad negativa o de 126 años.
            val resolvedAge: Int = run {
                val user = userService.findById(session.userId)
                val bd = user?.birthDate?.takeIf { it.isNotBlank() }
                if (bd != null) {
                    try {
                        val formatter = java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy")
                        val years = Period.between(LocalDate.parse(bd, formatter), LocalDate.now()).years
                        years.takeIf { it in 14..100 } ?: req.age
                    } catch (_: Exception) { req.age }
                } else {
                    req.age
                }
            }

            val profile = profileService.upsertProfile(
                userId        = session.userId,
                weightKg      = req.weightKg,
                heightCm      = req.heightCm,
                age           = resolvedAge,
                sex           = req.sex,
                activityLevel = req.activityLevel,
                goal          = req.goal,
                goalRate      = req.goalRate
            )
            // Igual que en /profile/me: sin esto, el resumen que ve el usuario justo después de
            // guardar se calculaba siempre con 0 de actividad, así que a los usuarios de pulsera les
            // salía un objetivo que no era el que luego veían en Nutrición.
            val avgActive = call.request.queryParameters["avgActiveKcal"].asFiniteInRange(0.0, MAX_ACTIVE_KCAL)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "avgActiveKcal fuera de rango")
            val targets = profileService.computeTargets(profile, avgActive)

            call.respond(HttpStatusCode.OK, profilePayload(profile, targets))
        }
    }

    route("/weight") {
        get("/history") {
            val session = call.sessions.get<UserSession>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Sin sesión")

            val result = profileService.getWeightHistory(session.userId)
            call.respond(HttpStatusCode.OK, result)
        }
    }
}