package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

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

@Serializable
data class UserProfile(
    val userId:        String,
    val weightKg:      Double,
    val heightCm:      Double,
    val age:           Int,
    val sex:           String,
    val activityLevel: String,
    val goal:          String,
    val goalRate:      Double
)

@Serializable
data class NutritionTargets(
    val tdee:        Int,
    val targetKcal:  Int,
    val proteinG:    Int,
    val carbsG:      Int,
    val fatG:        Int,
    val bmi:         Double,
    val bmiCategory: String,
    val bmr:            Int     = 0,
    /** El suelo de seguridad recortó el ritmo pedido: hay que avisar, no callarlo. */
    val rateLimited:    Boolean = false,
    /** Ritmo pedido y ritmo realmente alcanzable, en % de peso corporal por semana. */
    val requestedRate:  Double  = 0.0,
    val effectiveRate:  Double  = 0.0,
    /** El gasto viene de medición real (autoajuste) y no de la fórmula. */
    val tdeeIsMeasured: Boolean = false,
    /** El suelo por disponibilidad energética mordió: el objetivo **es** ese suelo. */
    val energyFloorHit: Boolean = false,
    /** El suelo calculado para este cuerpo, en kcal, para poder nombrarlo en el aviso. */
    val energyFloor:    Int     = 0,
    /** Arrastre del día realmente aplicado, ya acotado y filtrado por modo pulsera. */
    val appliedCarryOver: Int   = 0,
    /**
     * No hay déficit seguro posible: el suelo alcanza el mantenimiento y el objetivo se queda ahí.
     * Es la diferencia entre "voy despacio" y "no voy", y hay que decírselo al usuario.
     */
    val noSafeDeficit:  Boolean = false,
    /** Ya está en bajo peso y pide más déficit: el objetivo se cae a mantenimiento (AUD-8). */
    val lowBmiGuard:    Boolean = false,
    /** El reparto de macros no cupo en el objetivo y tuvo que recortar grasa/proteína (AUD-17). */
    val macrosDegraded: Boolean = false
)

/** Respuesta de `POST /profile/adaptive` (autoajuste). */
@Serializable
data class AdaptiveResponse(
    val adjusted:     Boolean,
    val reason:       String? = null,
    val newTdee:      Int?    = null,
    val measuredTdee: Int?    = null,
    /**
     * Objetivo calórico resultante del ajuste.
     *
     * `deltaKcal` es ahora el cambio real **del objetivo**, no la diferencia entre gastos mezclados:
     * esa podía tener el signo opuesto a lo que el usuario iba a comer (AUD-4).
     */
    val newTargetKcal: Int?   = null,
    val previousTdee: Int?    = null,
    val deltaKcal:    Int?    = null
)

@Serializable
data class ProfileResponse(
    val profile: UserProfile,
    val targets: NutritionTargets
)