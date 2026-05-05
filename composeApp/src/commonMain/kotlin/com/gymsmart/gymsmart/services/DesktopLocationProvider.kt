package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.RoutePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DesktopLocationProvider : LocationProvider {

    override fun getLocationUpdates(intervalMs: Long): Flow<RoutePoint> = flow {
        // en desktop no hay GPS real → no emitimos nada o simulación mínima
        while (true) {
            kotlinx.coroutines.delay(intervalMs)
        }
    }
}