package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.WeightEntry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class WeightService(private val client: HttpClient) {

    suspend fun getWeights(): List<WeightEntry> {
        return client.get("${AppConfig.BASE_URL}/weight").body()
    }

    suspend fun addWeight(day: Int, weight: Float): WeightEntry {
        val payload = WeightEntry(day, weight)

        return client.post("${AppConfig.BASE_URL}/weight") {
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(payload)
        }.body()
    }
}
