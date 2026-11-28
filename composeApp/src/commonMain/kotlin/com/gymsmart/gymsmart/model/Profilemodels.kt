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
    val goalRate:      Double,
    /**
     * Si este guardado debe registrar [weightKg] como el pesaje de hoy (C-6).
     *
     * `true` por defecto, que es lo que corresponde a la mayoría de los caminos: el onboarding, el
     * guardado de datos físicos y el alta de peso son todos acciones en las que el usuario está
     * confirmando su peso, aunque no lo haya cambiado — y registrar los días sin cambio es deliberado
     * (AUD-12): quitarlos de la muestra inflaba la pendiente del autoajuste.
     *
     * Se manda `false` desde el reguardado que dispara **editar los datos personales**: ahí el peso que
     * viaja es el que estaba cargado en el formulario, puede tener días, y el usuario no ha tocado nada
     * relacionado con su peso. Grabarlo como el pesaje de hoy mete un valor arrastrado (LOCF) en la
     * muestra: aplana la tendencia y además cuenta para el mínimo de 8 pesajes del autoajuste sin
     * aportar ninguna medida nueva.
     */
    val recordWeighIn: Boolean = true
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
    /** Un suelo de seguridad mordió: el objetivo **es** ese suelo. */
    val energyFloorHit: Boolean = false,
    /** El suelo que gobernó el objetivo, en kcal, para nombrarlo en el aviso. Nunca lo supera. */
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
    val macrosDegraded: Boolean = false,
    /**
     * El objetivo de pérdida queda por debajo del umbral clínico de su sexo (1200♀/1500♂): se emite
     * igual, pero se avisa (C-2). No implica que esté mal, sino que cubrir los micronutrientes con esa
     * cantidad de comida es difícil.
     */
    val belowSoftFloor: Boolean = false,
    /** El umbral con el que se comparó [belowSoftFloor], en kcal, para nombrarlo en el aviso. */
    val softFloorKcal:  Int     = 0
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
    val deltaKcal:    Int?    = null,
    /**
     * La medición está pausada **a propósito** (periodo de gracia tras cambiar de objetivo, C-5), no por
     * falta de registros del usuario. Cambia el consejo que se le da: durante la gracia, "registra tus
     * comidas y pésate" no serviría de nada porque la pausa seguiría igual.
     */
    val recalculating: Boolean = false
)

@Serializable
data class ProfileResponse(
    val profile: UserProfile,
    val targets: NutritionTargets
)