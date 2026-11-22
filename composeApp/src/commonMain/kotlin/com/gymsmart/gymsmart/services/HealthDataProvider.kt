package com.gymsmart.gymsmart.services

/**
 * Energía de la pulsera, con la separación que hace falta para calcular bien el objetivo diario
 * (doc/10-fundamento-cientifico.md §1.3).
 *
 * @param averageActiveKcal media de los últimos días **completos**. Es la base del presupuesto: la
 *   lectura del día en curso va sistemáticamente por debajo porque la app de la pulsera sincroniza
 *   por lotes, y comer contra ella produce déficit encubierto.
 * @param yesterdayActiveKcal actividad real de ayer, para arrastrar al día de hoy lo que ayer se
 *   desvió de la media. Así un día atípico no se pierde sin tener que perseguir un número que
 *   todavía se está moviendo.
 * @param todayActiveKcal lectura del día en curso. **Solo informativa**: no debe alimentar el
 *   presupuesto.
 * @param hasData false si Health Connect no devolvió ningún dato (sin permiso, sin app que escriba,
 *   o sin sincronizar todavía). Distinguirlo de "0 kcal" evita presentar un objetivo basado en un
 *   cero que en realidad significa "no lo sé".
 */
data class WearableEnergy(
    val averageActiveKcal:   Double,
    val yesterdayActiveKcal: Double,
    val todayActiveKcal:     Double,
    val hasData:             Boolean
) {
    /** Diferencia entre lo que ayer se gastó de verdad y lo que se había previsto. */
    val carryOverKcal: Double get() = if (hasData) yesterdayActiveKcal - averageActiveKcal else 0.0

    companion object {
        val EMPTY = WearableEnergy(0.0, 0.0, 0.0, hasData = false)
    }
}

interface HealthDataProvider {
    suspend fun getTodaySteps(): Long
    suspend fun getTodayActiveCalories(): Double

    /**
     * Resumen energético de los últimos [days] días completos más hoy.
     *
     * Health Connect guarda histórico y sabe agregarlo por día, así que esto no requiere almacenar
     * nada por nuestra parte: la limitación anterior ("solo sabemos leer hoy") era de esta interfaz,
     * no de la plataforma.
     */
    suspend fun getEnergySummary(days: Int = 14): WearableEnergy
}

/**
 * Cada cuánto deben refrescar las pantallas los datos de la pulsera mientras están visibles (WEAR-3 en
 * doc/08-backlog-mejoras.md). Antes se leía una sola vez al entrar en la pantalla: si el usuario la
 * abría a primera hora, el presupuesto de kcal se quedaba congelado en un valor cercano a 0 el resto del
 * día. Leer Health Connect es una consulta local al dispositivo (no hay coste de red ni de cuota que
 * vigilar, a diferencia de Turso/OpenAI/Open Food Facts), así que un intervalo corto es seguro.
 */
const val WEARABLE_POLL_INTERVAL_MS = 5 * 60 * 1000L
