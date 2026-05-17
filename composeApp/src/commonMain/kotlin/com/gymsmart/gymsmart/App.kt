package com.gymsmart.gymsmart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.gymsmart.gymsmart.navigation.NavGraph
import com.gymsmart.gymsmart.services.HealthDataProvider
import com.gymsmart.gymsmart.services.LocationProvider
import com.gymsmart.gymsmart.ui.theme.GymSmartTheme

@Composable
fun App(
    locationProvider: LocationProvider,
    healthDataProvider: HealthDataProvider,
    onRequestLocationPermission: (callback: (Boolean) -> Unit) -> Unit,
    onRequestCameraPermission: (callback: (Boolean) -> Unit) -> Unit = {}
) {
    GymSmartTheme {
        NavGraph(
            locationProvider = locationProvider,
            healthDataProvider = healthDataProvider,
            onRequestLocationPermission = onRequestLocationPermission,
            onRequestCameraPermission = onRequestCameraPermission
        )
    }
}