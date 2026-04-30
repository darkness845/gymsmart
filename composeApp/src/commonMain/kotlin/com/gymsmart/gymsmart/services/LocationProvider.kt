package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.RoutePoint
import kotlinx.coroutines.flow.Flow

interface LocationProvider {
    // Bajamos a 1000ms (1 segundo) para tener una ruta fluida
    fun getLocationUpdates(intervalMs: Long = 1000L): Flow<RoutePoint>
}