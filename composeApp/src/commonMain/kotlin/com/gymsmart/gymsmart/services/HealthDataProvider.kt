package com.gymsmart.gymsmart.services

interface HealthDataProvider {
    suspend fun getTodaySteps(): Long
    suspend fun getTodayActiveCalories(): Double
}