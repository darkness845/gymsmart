package com.gymsmart.gymsmart.services

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId

class HealthConnectProvider(
    private val context: Context
) : HealthDataProvider {

    private val client: HealthConnectClient? = try {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else null
    } catch (e: Exception) {
        null
    }

    private fun startOfToday(): Instant =
        Instant.now()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()

    // Antes se sumaban los registros crudos de readRecords(), lo que podía contar de más (varias
    // apps/fuentes escribiendo los mismos pasos, sin deduplicar) y de menos a la vez (readRecords
    // pagina y se ignoraba el pageToken, truncando en la primera página). aggregate() resuelve
    // ambos: deduplica solapes entre fuentes y devuelve un único total ya calculado por Health
    // Connect, sin paginación que gestionar (ver WEAR-1/WEAR-2 en doc/08-backlog-mejoras.md).
    override suspend fun getTodaySteps(): Long {
        val client = client ?: return 0L
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfToday(), Instant.now())
            )
        )
        return response[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    override suspend fun getTodayActiveCalories(): Double {
        val client = client ?: return 0.0
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfToday(), Instant.now())
            )
        )
        return response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
    }
}
