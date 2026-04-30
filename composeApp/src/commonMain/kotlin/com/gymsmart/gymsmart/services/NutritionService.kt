package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class NutritionService(private val httpClient: HttpClient) {

    suspend fun saveMealRemote(entry: MealEntry, mealType: String): Boolean {
        return try {
            val response = httpClient.post("${AppConfig.BASE_URL}/nutrition/add") {
                contentType(ContentType.Application.Json)
                setBody(
                    MealEntryRequest(
                        entry.name, entry.grams, entry.kcalPer100,
                        entry.proteinsPer100, entry.carbsPer100, entry.fatPer100, mealType
                    )
                )
            }
            response.status == HttpStatusCode.Created
        } catch (e: Exception) { false }
    }

    suspend fun getUserMeals(): List<MealEntryWithTarget>? {
        return try {
            val response = httpClient.get("${AppConfig.BASE_URL}/nutrition/mine")
            response.body<List<MealEntryWithTarget>>()
        } catch (e: Exception) { null }
    }

    suspend fun deleteMealRemote(id: String): Boolean {
        return try {
            val response = httpClient.delete("${AppConfig.BASE_URL}/nutrition/delete/$id")
            response.status == HttpStatusCode.OK
        } catch (e: Exception) { false }
    }

    suspend fun updateMealRemote(entry: MealEntry, mealType: String): Boolean {
        return try {
            val response = httpClient.put("${AppConfig.BASE_URL}/nutrition/update/${entry.id}") {
                contentType(ContentType.Application.Json)
                setBody(MealEntryRequest(
                    entry.name, entry.grams, entry.kcalPer100,
                    entry.proteinsPer100, entry.carbsPer100, entry.fatPer100, mealType
                ))
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) { false }
    }
}