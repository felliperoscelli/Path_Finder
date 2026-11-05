package com.pathfinder.ui.map

import android.graphics.DashPathEffect
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** Uma linha no mapa (percurso, ligação até o próximo ponto…), criada sob demanda. */
class TrackLine(
    private val mapView: MapView,
    private val color: Int,
    private val width: Float = 6f,
    private val dashed: Boolean = false,
) {
    private var line: Polyline? = null

    fun show(points: List<GeoPoint>) {
        val l = line ?: Polyline(mapView).apply {
            outlinePaint.color = color
            outlinePaint.strokeWidth = width
            if (dashed) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
            line = this
            val index = mapView.overlays.indexOfFirst { it is MyLocationNewOverlay }
            if (index >= 0) mapView.overlays.add(index, this) else mapView.overlays.add(this)
        }
        l.setPoints(points)
        mapView.invalidate()
    }

    val points: List<GeoPoint> get() = line?.actualPoints ?: emptyList()

    fun clear() = show(emptyList())
}
