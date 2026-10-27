package com.gymsmart.gymsmart.validation

/**
 * Validación de los campos físicos del perfil (peso/altura/edad/ritmo de objetivo), extraída de
 * `OnboardingScreen` para que `ProfileScreen` y `WeightScreen` la reutilicen (VAL-1 en
 * doc/08-backlog-mejoras.md). Antes esas dos pantallas convertían con `toDoubleOrNull() ?: 0.0` y
 * mandaban el cero al servidor en silencio si el campo estaba vacío o mal escrito (p. ej. con coma en
 * vez de punto decimal) — la validación correcta ya existía solo en el Onboarding.
 */

fun validateWeightKg(raw: String): String? {
    val value = raw.toDoubleOrNull()
    return if (value == null || value !in 30.0..300.0) "Introduce un peso válido (30–300 kg)" else null
}

fun validateHeightCm(raw: String): String? {
    val value = raw.toDoubleOrNull()
    return if (value == null || value !in 100.0..250.0) "Introduce una altura válida (100–250 cm)" else null
}

fun validateAge(raw: String): String? {
    val value = raw.toIntOrNull()
    return if (value == null || value !in 14..100) "Introduce una edad válida (14–100 años)" else null
}

fun validateGoalRate(goal: String, raw: String): String? =
    if (goal != "maintain" && raw.isBlank()) "Selecciona un ritmo semanal" else null
