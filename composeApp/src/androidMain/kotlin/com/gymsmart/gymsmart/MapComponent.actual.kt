package com.gymsmart.gymsmart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.gymsmart.gymsmart.model.RoutePoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
actual fun MapComponent(
    points: List<RoutePoint>,
    modifier: Modifier
) {
    val context = LocalContext.current
    val ultimoPunto = points.lastOrNull()

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(17.5)
                Configuration.getInstance().userAgentValue = context.packageName
            }
        },
        update = { mapView ->
            ultimoPunto?.let {
                val currentPos = GeoPoint(it.lat, it.lon)
                mapView.controller.animateTo(currentPos)

                mapView.overlays.clear()

                // Dibujar línea
                val line = Polyline(mapView)
                line.setPoints(points.map { p -> GeoPoint(p.lat, p.lon) })
                line.outlinePaint.color = android.graphics.Color.parseColor("#FFC107")
                line.outlinePaint.strokeWidth = 10f
                mapView.overlays.add(line)

                // Dibujar marcador
                val marker = Marker(mapView)
                marker.position = currentPos
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                mapView.overlays.add(marker)
            }
            mapView.invalidate()
        },
        modifier = modifier
    )
}