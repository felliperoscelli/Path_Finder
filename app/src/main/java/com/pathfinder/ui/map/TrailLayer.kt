package com.pathfinder.ui.map

import android.graphics.Color
import com.pathfinder.data.db.TrackPointEntity
import com.pathfinder.data.db.TrailPointEntity
import com.pathfinder.ui.components.MapIcons
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Desenha sobre o mapa o percurso gravado pelo GPS (azul) e a trilha
 * (linha vermelha entre os pontos + marcadores numerados). No modo de
 * edição os marcadores podem ser arrastados.
 */
class TrailLayer(
    private val mapView: MapView,
    private val onPointClick: (TrailPointEntity) -> Unit,
    private val onPointMoved: (TrailPointEntity, GeoPoint) -> Unit,
) {
    private val trailOverlays = mutableListOf<Overlay>()
    private val markers = mutableListOf<Marker>()
    private var trailLine: Polyline? = null
    private val trackLine = TrackLine(mapView, Color.rgb(30, 136, 229))
    private var progress: Pair<Int, Set<Int>>? = null
    /** Modo desafio: esconde a linha da trilha e os pontos ainda não alcançados. */
    private var hideUnreached = false
    private var shownPoints: List<TrailPointEntity>? = null
    private var shownTrackSize = -1

    var editing: Boolean = false
        set(value) {
            field = value
            markers.forEach { it.isDraggable = value }
        }

    /** Percurso gravado na criação da trilha. */
    fun showTrack(track: List<TrackPointEntity>) {
        if (track.size == shownTrackSize) return
        shownTrackSize = track.size
        trackLine.show(track.map { GeoPoint(it.latitude, it.longitude) })
    }

    /** Navegação: destaca o próximo ponto (verde) e esmaece os já alcançados. */
    fun showProgress(nextIndex: Int, reached: Set<Int>, hideUnreached: Boolean = false) {
        progress = nextIndex to reached
        this.hideUnreached = hideUnreached
        applyProgress()
    }

    private fun applyProgress() {
        val (next, reached) = progress ?: return
        trailLine?.isEnabled = !hideUnreached
        markers.forEachIndexed { i, marker ->
            marker.isEnabled = !hideUnreached || i in reached
            marker.alpha = if (i in reached && i != next) 0.4f else 1f
            marker.icon = pinIcon(i, if (i == next) MapIcons.PIN_GREEN else MapIcons.PIN_RED)
        }
        mapView.invalidate()
    }

    fun showPoints(points: List<TrailPointEntity>) {
        if (points == shownPoints) return
        mapView.overlays.removeAll(trailOverlays.toSet())
        trailOverlays.clear()
        markers.clear()
        trailLine = null
        shownPoints = points
        if (points.isNotEmpty()) {
            val geo = points.map { GeoPoint(it.latitude, it.longitude) }
            trailLine = Polyline(mapView).apply {
                setPoints(geo)
                outlinePaint.color = Color.rgb(198, 40, 40)
                outlinePaint.strokeWidth = 8f
            }.also { trailOverlays += it }
            points.forEachIndexed { index, p ->
                markers += Marker(mapView).apply {
                    position = geo[index]
                    title = "${p.sequence + 1}. ${p.name}"
                    icon = pinIcon(index, MapIcons.PIN_RED)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    isDraggable = editing
                    setOnMarkerClickListener { _, _ -> onPointClick(p); true }
                    setOnMarkerDragListener(DragListener(index, p))
                }
            }
            trailOverlays += markers
            insertBelowMyLocation(trailOverlays)
            applyProgress()
        }
        mapView.invalidate()
    }

    /** Atualiza a linha durante o arraste e salva a posição ao soltar. */
    private inner class DragListener(private val index: Int, private val point: TrailPointEntity) :
        Marker.OnMarkerDragListener {
        override fun onMarkerDragStart(marker: Marker) = Unit

        override fun onMarkerDrag(marker: Marker) {
            val line = trailLine ?: return
            val pts = line.actualPoints.toMutableList()
            if (index in pts.indices) {
                pts[index] = marker.position
                line.setPoints(pts)
            }
        }

        override fun onMarkerDragEnd(marker: Marker) = onPointMoved(point, marker.position)
    }

    /** Ajusta o enquadramento para mostrar a trilha e o percurso inteiros. */
    fun zoomToTrail() {
        val geo = shownPoints.orEmpty().map { GeoPoint(it.latitude, it.longitude) } +
            trackLine.points
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

    /** Pino com o número do ponto na trilha (1, 2, 3…). */
    private fun pinIcon(index: Int, color: Int) = MapIcons.pin(mapView.context, "${index + 1}", color)

    private fun insertBelowMyLocation(items: List<Overlay>) {
        val index = mapView.overlays.indexOfFirst { it is MyLocationNewOverlay }
        if (index >= 0) mapView.overlays.addAll(index, items) else mapView.overlays.addAll(items)
    }
}
