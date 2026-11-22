package com.gymsmart.gymsmart.services

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.Period
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

    /**
     * Media de días completos + ayer, con `aggregateGroupByPeriod`, que devuelve un total por día.
     *
     * Medido en dispositivo el 2026-11-01: la pulsera marcaba 56 kcal, Mi Fitness 54 y Health Connect
     * 37. El desfase va **siempre por debajo** porque Mi Fitness sincroniza por lotes, así que el
     * presupuesto del día no puede apoyarse en la lectura en curso. Los días ya cerrados no tienen ese
     * problema: a esa altura la sincronización ya terminó.
     */
    override suspend fun getEnergySummary(days: Int): WearableEnergy {
        val client = client ?: return WearableEnergy.EMPTY

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val firstDay = today.minusDays(days.toLong())

        val buckets = runCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        firstDay.atStartOfDay(),
                        today.atStartOfDay()   // excluye hoy: solo días completos
                    ),
                    timeRangeSlicer = Period.ofDays(1)
                )
            )
        }.getOrElse { return WearableEnergy.EMPTY }

        val perDay = buckets.mapNotNull { bucket ->
            bucket.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                ?.inKilocalories
                ?.let { bucket.startTime.toLocalDate() to it }
        }

        if (perDay.isEmpty()) return WearableEnergy.EMPTY

        val yesterday = today.minusDays(1)
        val average = perDay.map { it.second }.average()

        return WearableEnergy(
            averageActiveKcal   = average,
            // Si ayer no hay dato (pulsera sin sincronizar, día sin llevarla), se usa la media: así el
            // arrastre sale 0 en vez de restar un día entero de actividad que sí existió.
            yesterdayActiveKcal = perDay.firstOrNull { it.first == yesterday }?.second ?: average,
            todayActiveKcal     = getTodayActiveCalories(),
            hasData             = true
        )
    }
}
