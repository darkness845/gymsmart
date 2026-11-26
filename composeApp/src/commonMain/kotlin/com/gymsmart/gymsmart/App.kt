package com.gymsmart.gymsmart

import androidx.compose.runtime.Composable
import com.gymsmart.gymsmart.navigation.NavGraph
import com.gymsmart.gymsmart.services.HealthDataProvider
import com.gymsmart.gymsmart.services.LocationProvider
import com.gymsmart.gymsmart.ui.theme.MelocotonTheme

@Composable
fun App(
    locationProvider: LocationProvider,
    healthDataProvider: HealthDataProvider,
    onRequestLocationPermission: (callback: (Boolean) -> Unit) -> Unit,
    onRequestCameraPermission: (callback: (Boolean) -> Unit) -> Unit = {}
) {
    // Todas las pantallas en uso van en Melocotón: la raíz también, para que entre pantalla y pantalla
    // no asome el fondo oscuro del tema antiguo.
    MelocotonTheme {
        NavGraph(
            locationProvider = locationProvider,
            healthDataProvider = healthDataProvider,
            onRequestLocationPermission = onRequestLocationPermission,
            onRequestCameraPermission = onRequestCameraPermission
        )
    }
}