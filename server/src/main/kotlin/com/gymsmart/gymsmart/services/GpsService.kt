package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.GpsRouteResponse
import com.gymsmart.gymsmart.model.RoutePointDto
import com.gymsmart.gymsmart.model.SaveRouteRequest
import java.util.UUID

class GpsService(private val turso: TursoService) {

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

    suspend fun saveRoute(userId: String, req: SaveRouteRequest): String {
        val routeId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        turso.execute(
            """INSERT INTO gps_routes
               (id, user_id, name, distance_meters, duration_seconds, started_at, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            listOf(
                routeId,
                userId,
                req.name,
                req.distanceMeters.toString(),
                req.durationSeconds.toString(),
                req.startedAt.toString(),
                now.toString()
            )
        )

        req.points.forEach { point ->
            turso.execute(
                """INSERT INTO gps_points
                   (id, route_id, lat, lon, speed_ms, altitude_m, accuracy_m, timestamp)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                listOf(
                    UUID.randomUUID().toString(),
                    routeId,
                    point.lat.toString(),
                    point.lon.toString(),
                    point.speedMs.toString(),
                    point.altitudeM.toString(),
                    point.accuracyM.toString(),
                    point.timestamp.toString()
                )
            )
        }

        return routeId
    }

    suspend fun getRoutesByUser(userId: String): List<GpsRouteResponse> {
        val result = turso.execute(
            """SELECT r.id, r.user_id, r.name, r.distance_meters, r.duration_seconds,
                          r.started_at, r.created_at, r.is_public,
                          COUNT(p.id) as point_count
                    FROM gps_routes r
                    LEFT JOIN gps_points p ON p.route_id = r.id
                    WHERE r.user_id = ?
                    GROUP BY r.id
                    ORDER BY r.created_at DESC""",
                    listOf(userId)
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

    suspend fun getCommunityRoutes(): List<GpsRouteResponse> {
        val result = turso.execute(
            """SELECT r.id, r.user_id, r.name, r.distance_meters, r.duration_seconds,
                  r.started_at, r.created_at, r.is_public,
                  COUNT(p.id) as point_count
           FROM gps_routes r
           LEFT JOIN gps_points p ON p.route_id = r.id
           WHERE r.is_public = 1
           GROUP BY r.id
           ORDER BY r.created_at DESC"""
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
}