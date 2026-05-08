package com.gymsmart.gymsmart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.gymsmart.gymsmart.navigation.NavGraph
import com.gymsmart.gymsmart.services.HealthDataProvider
import com.gymsmart.gymsmart.services.LocationProvider

@Composable
fun App(
    locationProvider: LocationProvider,
    healthDataProvider: HealthDataProvider,
    onRequestLocationPermission: (onResult: (Boolean) -> Unit) -> Unit
) {
    MaterialTheme {
        NavGraph(
            locationProvider = locationProvider,
            healthDataProvider = healthDataProvider,
            onRequestLocationPermission = onRequestLocationPermission
        )
    }
}