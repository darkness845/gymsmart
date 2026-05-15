package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.BodyAnalysisRequest
import com.gymsmart.gymsmart.model.BodyAnalysisResponse
import com.gymsmart.gymsmart.model.WeightEntry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class WeightService(private val client: HttpClient) {

    suspend fun getHistory(): List<WeightEntry> =
        client.get("${AppConfig.BASE_URL}/weight/history").body()

    suspend fun addWeight(weightKg: Float) {
        client.post("${AppConfig.BASE_URL}/profile/save") {
        }
    }

    suspend fun analyzeBody(
        imageBase64: String
    ): BodyAnalysisResponse {

        return client.post("${AppConfig.BASE_URL}/ai/body-analysis") {

            contentType(ContentType.Application.Json)

            setBody(
                BodyAnalysisRequest(
                    imageBase64 = imageBase64
                )
            )
        }.body()
    }
}