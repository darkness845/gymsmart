package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.WorkoutPlan
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Los días de entrenamiento y la sesión en curso del usuario.
 *
 * Se guarda el plan entero de una vez (un documento por usuario) en lugar de tabla por nivel:
 * es una estructura anidada (días → ejercicios → series) que siempre se lee y se escribe completa,
 * y de uso personal. Si algún día hace falta consultar por ejercicio o sacar estadísticas en el
 * servidor, habrá que normalizarlo en tablas — está anotado en `doc/05-deuda-tecnica.md`.
 */
class WorkoutService(private val client: HttpClient) {

    suspend fun getPlan(): Result<WorkoutPlan> = try {
        val response = client.get("${AppConfig.BASE_URL}/workouts")
        if (response.status.isSuccess()) Result.success(response.body<WorkoutPlan>())
        else Result.failure(Exception("Error ${response.status.value}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun savePlan(plan: WorkoutPlan): Result<Unit> = try {
        val response = client.put("${AppConfig.BASE_URL}/workouts") {
            contentType(ContentType.Application.Json)
            setBody(plan)
        }
        if (response.status.isSuccess()) Result.success(Unit)
        else Result.failure(Exception("Error ${response.status.value}"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
