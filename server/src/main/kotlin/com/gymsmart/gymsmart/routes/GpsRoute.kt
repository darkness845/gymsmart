package com.gymsmart.gymsmart.routes

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.sessions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.gymsmart.gymsmart.model.SaveRouteRequest
import com.gymsmart.gymsmart.model.SaveRouteResponse
import com.gymsmart.gymsmart.model.UserSession
import com.gymsmart.gymsmart.services.GpsService
import io.ktor.server.sessions.*

@Serializable
data class GpsLocation(
    val lat: Double,
    val lon: Double,
    val city: String = "",
    val country: String = "",
    val source: String = "ip-geolocation"
)

@Serializable
data class IpApiResponse(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val city: String = "",
    val country: String = "",
    val status: String = ""
)

private val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

// Cache para no spamear la API externa
private var cachedLocation: GpsLocation? = null
private var lastFetchTime: Long = 0
private val CACHE_TTL_MS = 30_000L // 30 segundos

fun Route.gpsRoutes(gpsService: GpsService) {
    route("/gps") {

        // Guardar una ruta completa con todos sus puntos
        post("/routes/save") {
            val session = call.sessions.get<UserSession>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "No autenticado")

            val req = runCatching { call.receive<SaveRouteRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, "Cuerpo inválido: ${it.message}")
            }

            try {
                val routeId = gpsService.saveRoute(session.userId, req)
                call.respond(HttpStatusCode.Created, SaveRouteResponse(routeId))
            } catch (e: Exception) {
                println("❌ Error guardando ruta: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }

        // Listar rutas del usuario autenticado
        get("/routes/mine") {
            val session = call.sessions.get<UserSession>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "No autenticado")

            val routes = gpsService.getRoutesByUser(session.userId)
            call.respond(HttpStatusCode.OK, routes)
        }

        // Obtener puntos de una ruta concreta
        get("/routes/{id}/points") {
            val session = call.sessions.get<UserSession>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "No autenticado")

            val routeId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "ID requerido")

            val points = gpsService.getRoutePoints(routeId, session.userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Ruta no encontrada")

            call.respond(HttpStatusCode.OK, points)
        }

        // Eliminar una ruta
        delete("/routes/{id}") {
            val session = call.sessions.get<UserSession>()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, "No autenticado")

            val routeId = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "ID requerido")

            val deleted = gpsService.deleteRoute(routeId, session.userId)
            if (deleted) call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            else call.respond(HttpStatusCode.NotFound, "Ruta no encontrada")
        }

        put("/routes/{id}/publish") {
            val session = call.sessions.get<UserSession>()
                ?: return@put call.respond(HttpStatusCode.Unauthorized)
            val routeId = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest)
            val ok = gpsService.publishRoute(routeId, session.userId)
            if (ok) call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            else call.respond(HttpStatusCode.BadRequest, "Ruta no encontrada o distancia insuficiente (mínimo 0.1km)")
        }

        put("/routes/{id}/unpublish") {
            val session = call.sessions.get<UserSession>()
                ?: return@put call.respond(HttpStatusCode.Unauthorized)
            val routeId = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest)
            val ok = gpsService.unpublishRoute(routeId, session.userId)
            if (ok) call.respond(HttpStatusCode.OK, mapOf("ok" to true))
            else call.respond(HttpStatusCode.NotFound, "Ruta no encontrada")
        }

        get("/routes/community") {
            call.sessions.get<UserSession>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val routes = gpsService.getCommunityRoutes()
            call.respond(HttpStatusCode.OK, routes)
        }
    }
}

suspend fun getLocation(): GpsLocation {
    val now = System.currentTimeMillis()
    if (cachedLocation != null && (now - lastFetchTime) < CACHE_TTL_MS) {
        return cachedLocation!!
    }

    return try {
        val response: IpApiResponse = httpClient.get("http://ip-api.com/json/") {
            parameter("fields", "status,country,city,lat,lon")
        }.body()

        if (response.status == "success") {
            val location = GpsLocation(
                lat = response.lat,
                lon = response.lon,
                city = response.city,
                country = response.country
            )
            cachedLocation = location
            lastFetchTime = now
            location
        } else {
            // Fallback a Madrid si falla
            GpsLocation(lat = 40.4168, lon = -3.7038, city = "Madrid (fallback)", country = "ES")
        }
    } catch (e: Exception) {
        println("⚠️  Error obteniendo GPS: ${e.message}")
        GpsLocation(lat = 40.4168, lon = -3.7038, city = "Madrid (fallback)", country = "ES")
    }
}