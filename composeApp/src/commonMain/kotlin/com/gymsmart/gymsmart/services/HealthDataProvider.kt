package com.gymsmart.gymsmart.services

interface HealthDataProvider {
    suspend fun getTodaySteps(): Long
    suspend fun getTodayActiveCalories(): Double
}

/**
 * Cada cuánto deben refrescar las pantallas los datos de la pulsera mientras están visibles (WEAR-3 en
 * doc/08-backlog-mejoras.md). Antes se leía una sola vez al entrar en la pantalla: si el usuario la
 * abría a primera hora, el presupuesto de kcal se quedaba congelado en un valor cercano a 0 el resto del
 * día. Leer Health Connect es una consulta local al dispositivo (no hay coste de red ni de cuota que
 * vigilar, a diferencia de Turso/OpenAI/Open Food Facts), así que un intervalo corto es seguro.
 */
const val WEARABLE_POLL_INTERVAL_MS = 5 * 60 * 1000L