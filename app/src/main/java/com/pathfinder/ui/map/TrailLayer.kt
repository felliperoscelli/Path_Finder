package com.pathfinder.ui.map

import android.graphics.Color
import com.pathfinder.data.db.TrackPointEntity
import com.pathfinder.data.db.TrailPointEntity
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Desenha sobre o mapa o percurso gravado pelo GPS (azul) e a trilha
 * (linha vermelha entre os pontos + marcadores numerados).
 */
class TrailLayer(private val mapView: MapView) {
    private val trailOverlays = mutableListOf<Overlay>()
    private var trackLine: Polyline? = null
    private var shownPoints: List<TrailPointEntity>? = null
    private var shownTrackSize = -1

    fun showTrack(track: List<TrackPointEntity>) {
        if (track.size == shownTrackSize) return
        shownTrackSize = track.size
        val line = trackLine ?: Polyline(mapView).apply {
            outlinePaint.color = Color.rgb(30, 136, 229)
            outlinePaint.strokeWidth = 6f
            isGeodesic = false
            trackLine = this
            insertBelowMyLocation(listOf(this))
        }
        line.setPoints(track.map { GeoPoint(it.latitude, it.longitude) })
        mapView.invalidate()
    }

    fun showPoints(points: List<TrailPointEntity>) {
        if (points == shownPoints) return
        mapView.overlays.removeAll(trailOverlays.toSet())
        trailOverlays.clear()
        shownPoints = points
        if (points.isNotEmpty()) {
            val geo = points.map { GeoPoint(it.latitude, it.longitude) }
            if (geo.size > 1) {
                trailOverlays += Polyline(mapView).apply {
                    setPoints(geo)
                    outlinePaint.color = Color.rgb(198, 40, 40)
                    outlinePaint.strokeWidth = 8f
                }
            }
            points.forEachIndexed { index, p ->
                trailOverlays += Marker(mapView).apply {
                    position = geo[index]
                    title = "${p.sequence + 1}. ${p.name}"
                    snippet = p.notes.takeIf { it.isNotBlank() }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
            }
            insertBelowMyLocation(trailOverlays)
        }
        mapView.invalidate()
    }

    /** Ajusta o enquadramento para mostrar a trilha e o percurso inteiros. */
    fun zoomToTrail() {
        val geo = shownPoints.orEmpty().map { GeoPoint(it.latitude, it.longitude) } +
            (trackLine?.actualPoints ?: emptyList())
        if (geo.isEmpty()) return
        mapView.post {
            if (geo.size == 1) {
                mapView.controller.setZoom(16.0)
                mapView.controller.setCenter(geo.first())
            } else {
                mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(geo), false, 80)
            }
        }
    }

    private fun insertBelowMyLocation(items: List<Overlay>) {
        val index = mapView.overlays.indexOfFirst { it is MyLocationNewOverlay }
        if (index >= 0) mapView.overlays.addAll(index, items) else mapView.overlays.addAll(items)
    }
}
