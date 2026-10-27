package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.RoutePoint
import kotlinx.serialization.Serializable

@Serializable
data class RecoverableRoute(
    val points: List<RoutePoint>,
    val distanceMeters: Double,
    val startedAt: Long
)

/**
 * Guarda una copia de la ruta en curso en almacenamiento local, para poder recuperarla si Android
 * mata el proceso a mitad de la grabación (ruta larga, pantalla apagada). Sin esto, la ruta entera
 * se perdía porque `activeRoute` solo vivía en memoria (ver GPS-3 en doc/08-backlog-mejoras.md).
 * Solo tiene implementación real en Android, la única plataforma donde el GPS funciona hoy.
 */
expect class RouteRecoveryStore() {
    fun save(route: RecoverableRoute)
    fun load(): RecoverableRoute?
    fun clear()
}
