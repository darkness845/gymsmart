package com.gymsmart.gymsmart

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import com.gymsmart.gymsmart.services.LocationService
import com.gymsmart.gymsmart.services.appContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appContext = applicationContext

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
                onRequestLocationPermission = { callback ->
                    pendingPermissionCallback = callback
                    permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            )
        }
    }
}