package com.gymsmart.gymsmart.services

class DesktopHealthDataProvider : HealthDataProvider {
    override suspend fun getTodaySteps(): Long = 0L
    override suspend fun getTodayActiveCalories(): Double = 0.0

    // Desktop no tiene pulsera. EMPTY lleva hasData = false, así que el objetivo no se calcula con
    // un cero que en realidad significa "aquí no se puede saber".
    override suspend fun getEnergySummary(days: Int): WearableEnergy = WearableEnergy.EMPTY
}