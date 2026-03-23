package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.WeightEntry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class WeightService(private val client: HttpClient) {

    suspend fun getWeights(): List<WeightEntry> {
        return client.get("http://localhost:8080/weight").body()
    }

    suspend fun addWeight(weight: Float, date: String): WeightEntry {
        val payload = WeightEntry(date, weight)
        return client.post("http://localhost:8080/weight") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()
    }
}