package com.gymsmart.gymsmart.services

class DesktopHealthDataProvider : HealthDataProvider {
    override suspend fun getTodaySteps(): Long = 0L
}