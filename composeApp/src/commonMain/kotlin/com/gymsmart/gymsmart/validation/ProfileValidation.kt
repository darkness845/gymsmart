package com.gymsmart.gymsmart.validation

import com.gymsmart.gymsmart.nutrition.NutritionMath

/**
 * Validación de los campos físicos del perfil (peso/altura/edad/ritmo de objetivo), extraída de
 * `OnboardingScreen` para que `ProfileScreen` y `WeightScreen` la reutilicen (VAL-1 en
 * doc/08-backlog-mejoras.md). Antes esas dos pantallas convertían con `toDoubleOrNull() ?: 0.0` y
 * mandaban el cero al servidor en silencio si el campo estaba vacío o mal escrito — la validación
 * correcta ya existía solo en el Onboarding.
 */

/** Acepta coma o punto como separador decimal (en teclado español lo normal es la coma). */
fun parseDecimal(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()

fun validateWeightKg(raw: String): String? {
    val value = parseDecimal(raw)
    return if (value == null || value !in 30.0..300.0) "Introduce un peso válido (30–300 kg)" else null
}

fun validateHeightCm(raw: String): String? {
    val value = parseDecimal(raw)
    return if (value == null || value !in 100.0..250.0) "Introduce una altura válida (100–250 cm)" else null
}

/**
 * La cintura es opcional: en blanco es válido. Solo se valida el rango si el usuario escribió algo,
 * para no bloquear el guardado de peso a quien no quiera medirse.
 */
fun validateWaistCm(raw: String): String? {
    if (raw.isBlank()) return null
    val value = parseDecimal(raw)
    return if (value == null || value !in 40.0..200.0) "Introduce una cintura válida (40–200 cm)" else null
}

fun validateAge(raw: String): String? {
    val value = raw.toIntOrNull()
    return if (value == null || value !in 14..100) "Introduce una edad válida (14–100 años)" else null
}

/**
 * El ritmo va en **% de peso corporal por semana**, con signo. Antes esto solo comprobaba que el campo
 * no estuviera vacío; ahora también valida que caiga en el rango seguro del objetivo elegido
 * (doc/10-fundamento-cientifico.md §3.2), que es lo que impide ofrecer ritmos que no conservan masa
 * muscular.
 */
fun validateGoalRate(goal: String, raw: String): String? {
    if (goal == NutritionMath.GOAL_MAINTAIN) return null
    if (raw.isBlank()) return "Selecciona un ritmo semanal"
    val value = parseDecimal(raw) ?: return "Selecciona un ritmo semanal"
    return if (!NutritionMath.isRateValid(goal, value)) "Ese ritmo no es seguro para tu objetivo" else null
}
