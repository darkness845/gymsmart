package com.gymsmart.gymsmart

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.gymsmart.gymsmart.model.RoutePoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

@Composable
actual fun MapComponent(
    points: List<RoutePoint>,
    completedPoints: List<RoutePoint>,
    userLocation: RoutePoint?,
    modifier: Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(17.5)
                Configuration.getInstance().userAgentValue = context.packageName
                controller.setCenter(GeoPoint(40.4168, -3.7038))
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            if (points.size >= 2) {
                val lineaPendiente = Polyline(mapView)
                lineaPendiente.setPoints(points.map { GeoPoint(it.lat, it.lon) })
                lineaPendiente.outlinePaint.color = android.graphics.Color.parseColor("#1E88E5")
                lineaPendiente.outlinePaint.strokeWidth = 10f
                mapView.overlays.add(lineaPendiente)
            }

            if (completedPoints.size >= 2) {
                val lineaCompletada = Polyline(mapView)
                lineaCompletada.setPoints(completedPoints.map { GeoPoint(it.lat, it.lon) })
                lineaCompletada.outlinePaint.color = android.graphics.Color.parseColor("#FF9800")
                lineaCompletada.outlinePaint.strokeWidth = 10f
                mapView.overlays.add(lineaCompletada)
            }

            userLocation?.let { punto ->
                val halo = Polygon(mapView)
                halo.points = Polygon.pointsAsCircle(GeoPoint(punto.lat, punto.lon), 12.0)
                halo.fillPaint.color = android.graphics.Color.argb(50, 25, 118, 210)
                halo.outlinePaint.color = android.graphics.Color.argb(80, 25, 118, 210)
                halo.outlinePaint.strokeWidth = 1f
                mapView.overlays.add(halo)

                val circle = Polygon(mapView)
                circle.points = Polygon.pointsAsCircle(GeoPoint(punto.lat, punto.lon), 4.0)
                circle.fillPaint.color = android.graphics.Color.parseColor("#1976D2")
                circle.outlinePaint.color = android.graphics.Color.WHITE
                circle.outlinePaint.strokeWidth = 2f
                mapView.overlays.add(circle)

                mapView.controller.animateTo(GeoPoint(punto.lat, punto.lon))
            } ?: points.firstOrNull()?.let {
                mapView.controller.animateTo(GeoPoint(it.lat, it.lon))
            }

            mapView.invalidate()
        },
        modifier = modifier
    )
}