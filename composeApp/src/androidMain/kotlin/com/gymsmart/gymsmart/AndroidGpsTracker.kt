package com.gymsmart.gymsmart

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.gymsmart.gymsmart.model.GpsData
import com.google.android.gms.location.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*

class AndroidGpsTracker(private val context: Context) {

    private val client = HttpClient()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun start() {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000
        ).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return

                sendToServer(loc.latitude, loc.longitude)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )
    }

    private fun sendToServer(lat: Double, lon: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.post("http://192.168.1.145:8080/gps") {
                    contentType(ContentType.Application.Json)
                    setBody(GpsData(lat, lon, System.currentTimeMillis()))
                }
            } catch (_: Exception) {
            }
        }
    }
}