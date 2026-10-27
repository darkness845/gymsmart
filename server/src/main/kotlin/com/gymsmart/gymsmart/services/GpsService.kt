package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.GpsRouteResponse
import com.gymsmart.gymsmart.model.RoutePointDto
import com.gymsmart.gymsmart.model.SaveRouteRequest
import java.util.UUID

class GpsService(private val turso: TursoService) {

    companion object {
        private const val MAX_STATEMENTS_PER_BATCH = 200
    }

    suspend fun initTables() {
        turso.execute("""
            CREATE TABLE IF NOT EXISTS gps_routes (
                id               TEXT PRIMARY KEY,
                user_id          TEXT NOT NULL,
                name             TEXT NOT NULL,
                distance_meters  REAL NOT NULL,
                duration_seconds INTEGER NOT NULL,
                started_at       INTEGER NOT NULL,
                created_at       INTEGER NOT NULL,
                is_public        INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """)
        turso.execute("""
            CREATE TABLE IF NOT EXISTS gps_points (
                id         TEXT PRIMARY KEY,
                route_id   TEXT NOT NULL,
                lat        REAL NOT NULL,
                lon        REAL NOT NULL,
                speed_ms   REAL NOT NULL,
                altitude_m REAL NOT NULL,
                accuracy_m REAL NOT NULL,
                timestamp  INTEGER NOT NULL,
                FOREIGN KEY(route_id) REFERENCES gps_routes(id) ON DELETE CASCADE
            )
        """)
    }

    // Antes esto hacía una petición HTTP por punto (N+1): una ruta de 900 puntos tardaba entre 45 s y
    // varios minutos, con riesgo alto de timeout. Ahora se manda todo en lotes de como mucho
    // MAX_STATEMENTS_PER_BATCH statements por llamada a /v2/pipeline, que acepta varios statements de
    // una vez (ver GPS-4 en doc/08-backlog-mejoras.md). Se trocea (no se manda todo en una sola
    // llamada) para no arriesgar los límites de tamaño de request de Turso en rutas muy largas.
    suspend fun saveRoute(userId: String, req: SaveRouteRequest): String {
        val routeId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val routeInsert = """INSERT INTO gps_routes
               (id, user_id, name, distance_meters, duration_seconds, started_at, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""" to listOf(
            routeId, userId, req.name, req.distanceMeters.toString(),
            req.durationSeconds.toString(), req.startedAt.toString(), now.toString()
        )

        val pointInserts = req.points.map { point ->
            """INSERT INTO gps_points
               (id, route_id, lat, lon, speed_ms, altitude_m, accuracy_m, timestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""" to listOf(
                UUID.randomUUID().toString(), routeId, point.lat.toString(), point.lon.toString(),
                point.speedMs.toString(), point.altitudeM.toString(), point.accuracyM.toString(),
                point.timestamp.toString()
            )
        }

        // El INSERT de gps_routes va siempre primero (gps_points tiene FOREIGN KEY sobre él).
        (listOf(routeInsert) + pointInserts)
            .chunked(MAX_STATEMENTS_PER_BATCH)
            .forEach { chunk -> turso.executeBatch(chunk) }

        return routeId
    }

    private suspend fun queryRoutes(whereClause: String, params: List<String>): List<GpsRouteResponse> {
        val result = turso.execute(
            """SELECT r.id, r.user_id, r.name, r.distance_meters, r.duration_seconds,
                      r.started_at, r.created_at, r.is_public,
                      COUNT(p.id) as point_count
               FROM gps_routes r
               LEFT JOIN gps_points p ON p.route_id = r.id
               WHERE $whereClause
               GROUP BY r.id
               ORDER BY r.created_at DESC""",
            params
        )
        return turso.extractRows(result).map { row ->
            GpsRouteResponse(
                id              = row[0] ?: "",
                userId          = row[1] ?: "",
                name            = row[2] ?: "",
                distanceMeters  = row[3]?.toDoubleOrNull() ?: 0.0,
                durationSeconds = row[4]?.toLongOrNull() ?: 0L,
                startedAt       = row[5]?.toLongOrNull() ?: 0L,
                createdAt       = row[6]?.toLongOrNull() ?: 0L,
                isPublic        = row[7]?.toIntOrNull() == 1,
                pointCount      = row[8]?.toIntOrNull() ?: 0
            )
        }
    }

    suspend fun getRoutesByUser(userId: String): List<GpsRouteResponse> =
        queryRoutes("r.user_id = ?", listOf(userId))

    suspend fun getRoutePoints(routeId: String, userId: String): List<RoutePointDto>? {
        // Si la ruta es pública cualquiera puede verla, si es privada solo el dueño
        val check = turso.execute(
            "SELECT id FROM gps_routes WHERE id = ? AND (user_id = ? OR is_public = 1)",
            listOf(routeId, userId)
        )
        if (turso.extractRows(check).isEmpty()) return null

        val result = turso.execute(
            """SELECT lat, lon, timestamp, speed_ms, altitude_m, accuracy_m
           FROM gps_points WHERE route_id = ? ORDER BY timestamp ASC""",
            listOf(routeId)
        )
        return turso.extractRows(result).map { row ->
            RoutePointDto(
                lat       = row[0]?.toDoubleOrNull() ?: 0.0,
                lon       = row[1]?.toDoubleOrNull() ?: 0.0,
                timestamp = row[2]?.toLongOrNull() ?: 0L,
                speedMs   = row[3]?.toFloatOrNull() ?: 0f,
                altitudeM = row[4]?.toDoubleOrNull() ?: 0.0,
                accuracyM = row[5]?.toFloatOrNull() ?: 0f
            )
        }
    }

    suspend fun deleteRoute(routeId: String, userId: String): Boolean {
        val check = turso.execute(
            "SELECT id FROM gps_routes WHERE id = ? AND user_id = ?",
            listOf(routeId, userId)
        )
        if (turso.extractRows(check).isEmpty()) return false
        turso.execute("DELETE FROM gps_routes WHERE id = ?", listOf(routeId))
        return true
    }

    suspend fun publishRoute(routeId: String, userId: String): Boolean {
        val check = turso.execute(
            "SELECT id, distance_meters FROM gps_routes WHERE id = ? AND user_id = ?",
            listOf(routeId, userId)
        )
        val rows = turso.extractRows(check)
        if (rows.isEmpty()) return false
        val distancia = rows[0][1]?.toDoubleOrNull() ?: 0.0
        if (distancia < 100.0) return false  // mínimo 0.1km
        turso.execute(
            "UPDATE gps_routes SET is_public = 1 WHERE id = ?",
            listOf(routeId)
        )
        return true
    }

    suspend fun unpublishRoute(routeId: String, userId: String): Boolean {
        val check = turso.execute(
            "SELECT id FROM gps_routes WHERE id = ? AND user_id = ?",
            listOf(routeId, userId)
        )
        if (turso.extractRows(check).isEmpty()) return false
        turso.execute(
            "UPDATE gps_routes SET is_public = 0 WHERE id = ?",
            listOf(routeId)
        )
        return true
    }

    suspend fun getCommunityRoutes(): List<GpsRouteResponse> =
        queryRoutes("r.is_public = 1", emptyList())
}