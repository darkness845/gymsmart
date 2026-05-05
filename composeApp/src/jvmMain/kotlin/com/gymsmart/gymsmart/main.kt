package com.gymsmart.gymsmart

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.gymsmart.gymsmart.services.DesktopLocationProvider

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "gymsmart",
    ) {
        App(
            locationProvider = DesktopLocationProvider(),
            onRequestLocationPermission = { it(true) }
        )
    }
}