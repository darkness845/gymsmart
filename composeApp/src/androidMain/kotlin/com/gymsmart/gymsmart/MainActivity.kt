package com.gymsmart.gymsmart

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.health.connect.client.HealthConnectClient
import com.gymsmart.gymsmart.services.HealthConnectProvider
import com.gymsmart.gymsmart.services.LocationService
import com.gymsmart.gymsmart.services.appContext
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord

class MainActivity : ComponentActivity() {

    private val healthPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    )

    private val requestHealthPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        println("🔑 Resultado permisos Health Connect: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appContext = applicationContext

        val healthProvider = HealthConnectProvider(this)

        lifecycleScope.launch {
            val client = HealthConnectClient.getOrCreate(this@MainActivity)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(healthPermissions)) {
                requestHealthPermissions.launch(healthPermissions)
            }
        }

        setContent {
            val locationService = remember { LocationService(this) }
            var pendingLocationCallback: ((Boolean) -> Unit)? = null
            var pendingCameraCallback: ((Boolean) -> Unit)? = null  // ← NUEVO

            val locationPermLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                pendingLocationCallback?.invoke(granted)
                pendingLocationCallback = null
            }

            // ← NUEVO: launcher para cámara
            val cameraPermLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                pendingCameraCallback?.invoke(granted)
                pendingCameraCallback = null
            }

            App(
                locationProvider = locationService,
                healthDataProvider = healthProvider,
                onRequestLocationPermission = { callback ->
                    pendingLocationCallback = callback
                    locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                onRequestCameraPermission = { callback ->   // ← NUEVO
                    pendingCameraCallback = callback
                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        }
    }
}