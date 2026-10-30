package com.pathfinder.ui.map

import android.graphics.Color
import com.pathfinder.data.db.TrailPointEntity
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

/** Desenha a trilha (linha + marcadores numerados) sobre o mapa. */
class TrailLayer(private val mapView: MapView) {
    private val overlays = mutableListOf<Overlay>()
    private var shownPoints: List<TrailPointEntity>? = null

    fun show(points: List<TrailPointEntity>) {
        if (points == shownPoints) return
        clear()
        shownPoints = points
        if (points.isEmpty()) return

        val geo = points.map { GeoPoint(it.latitude, it.longitude) }
        if (geo.size > 1) {
            overlays += Polyline(mapView).apply {
                setPoints(geo)
                outlinePaint.color = Color.rgb(198, 40, 40)
                outlinePaint.strokeWidth = 8f
            }
        }
        points.forEachIndexed { index, p ->
            overlays += Marker(mapView).apply {
                position = geo[index]
                title = "${p.sequence + 1}. ${p.name}"
                snippet = p.notes.takeIf { it.isNotBlank() }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
        }
        // Linha antes dos marcadores; tudo abaixo da camada "minha localização".
        mapView.overlays.addAll(1.coerceAtMost(mapView.overlays.size), overlays)
        mapView.invalidate()
    }

    /** Ajusta o enquadramento para mostrar a trilha inteira. */
    fun zoomToTrail() {
        val points = shownPoints ?: return
        if (points.isEmpty()) return
        val geo = points.map { GeoPoint(it.latitude, it.longitude) }
        mapView.post {
            if (geo.size == 1) {
                mapView.controller.setZoom(16.0)
                mapView.controller.setCenter(geo.first())
            } else {
                mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(geo), false, 80)
            }
        }
    }

    fun clear() {
        mapView.overlays.removeAll(overlays.toSet())
        overlays.clear()
        shownPoints = null
        mapView.invalidate()
    }
}
