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
            println("🔑 Permisos concedidos: $granted")
            if (!granted.containsAll(healthPermissions)) {
                println("⚠️ Pidiendo permisos Health Connect...")
                requestHealthPermissions.launch(healthPermissions)
            } else {
                println("✅ Permisos ya concedidos")
            }
        }

        setContent {
            val locationService = remember { LocationService(this) }
            var pendingPermissionCallback: ((Boolean) -> Unit)? = null

            val permLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                pendingPermissionCallback?.invoke(granted)
                pendingPermissionCallback = null
            }

            App(
                locationProvider = locationService,
                healthDataProvider = healthProvider,
                onRequestLocationPermission = { callback ->
                    pendingPermissionCallback = callback
                    permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            )
        }
    }
}