package com.gymsmart.gymsmart.routes

import com.gymsmart.gymsmart.model.GpsData
import com.gymsmart.gymsmart.services.GpsService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.gpsRoute() {

    route("/gps") {

        post {
            val data = call.receive<GpsData>()
            GpsService.lastLocation = data
            call.respond(HttpStatusCode.OK)
        }

        get {
            val location = GpsService.lastLocation
            if (location != null) {
                call.respond(location)
            } else {
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}