package com.gymsmart.gymsmart.services

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId

class HealthConnectProvider(
    private val context: Context
) : HealthDataProvider {

    private val client = HealthConnectClient.getOrCreate(context)

    override suspend fun getTodaySteps(): Long {

        val startOfDay = Instant.now()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()

        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    startOfDay,
                    Instant.now()
                )
            )
        )

        return response.records.sumOf { it.count }
    }
}