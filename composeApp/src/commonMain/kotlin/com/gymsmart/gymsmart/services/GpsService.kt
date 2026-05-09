package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.GpsRouteResponse
import com.gymsmart.gymsmart.model.RoutePointDto
import com.gymsmart.gymsmart.model.SaveRouteRequest
import com.gymsmart.gymsmart.model.SaveRouteResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class GpsService(private val client: HttpClient) {

    suspend fun saveRoute(req: SaveRouteRequest): Result<String> {
        return try {
            val response = client.post("${AppConfig.BASE_URL}/gps/routes/save") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            if (response.status.isSuccess()) {
                val body = response.body<SaveRouteResponse>()
                Result.success(body.id)
            } else {
                Result.failure(Exception("Error ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyRoutes(): Result<List<GpsRouteResponse>> {
        return try {
            val routes = client.get("${AppConfig.BASE_URL}/gps/routes/mine").body<List<GpsRouteResponse>>()
            Result.success(routes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRoutePoints(routeId: String): Result<List<RoutePointDto>> {
        return try {
            val points = client.get("${AppConfig.BASE_URL}/gps/routes/$routeId/points")
                .body<List<RoutePointDto>>()
            Result.success(points)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRoute(routeId: String): Result<Unit> {
        return try {
            client.delete("${AppConfig.BASE_URL}/gps/routes/$routeId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun publishRoute(routeId: String): Result<Unit> {
        return try {
            val response = client.put("${AppConfig.BASE_URL}/gps/routes/$routeId/publish")
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(Exception("Error ${response.status.value}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unpublishRoute(routeId: String): Result<Unit> {
        return try {
            val response = client.put("${AppConfig.BASE_URL}/gps/routes/$routeId/unpublish")
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(Exception("Error ${response.status.value}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCommunityRoutes(): Result<List<GpsRouteResponse>> {
        return try {
            val routes = client.get("${AppConfig.BASE_URL}/gps/routes/community")
                .body<List<GpsRouteResponse>>()
            Result.success(routes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}