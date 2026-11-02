package com.pathfinder.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pathfinder.data.NewPoint
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.TrackPointEntity
import com.pathfinder.data.db.TrackSource
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.data.settings.CoordinateFormat
import com.pathfinder.data.settings.SettingsRepository
import com.pathfinder.geo.LatLon
import com.pathfinder.geo.LegMetrics
import com.pathfinder.geo.TrailCalculator
import com.pathfinder.geo.TrailEditing
import com.pathfinder.geo.Utm
import com.pathfinder.geo.UtmCoordinate
import com.pathfinder.recording.RecordingSession
import com.pathfinder.recording.RecordingState
import com.pathfinder.ui.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Posição candidata a novo ponto e de onde ela veio. */
data class PointDraft(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    /** Ex.: "GPS ±4 m", "Média enquanto parado", "Escolhido no mapa". */
    val origin: String,
)

/** Valores calculados para mostrar antes de salvar o ponto. */
data class PointPreview(
    val utm: UtmCoordinate?,
    val previousName: String?,
    val leg: LegMetrics?,
    val declinationDegrees: Double,
)

class MapViewModel(
    private val trailId: Long?,
    private val trails: TrailRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val trail: StateFlow<TrailWithPoints?> =
        (if (trailId != null) trails.observeTrail(trailId) else flowOf(null))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val track: StateFlow<List<TrackPointEntity>> =
        (if (trailId != null) trails.observeTrack(trailId, TrackSource.CREATION) else flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recording: StateFlow<RecordingState> = RecordingSession.state

    val coordinateFormat: StateFlow<CoordinateFormat> = settings.settings.map { it.coordinateFormat }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoordinateFormat.BOTH)

    /** Valores do ponto se ele for inserido na posição [index]. */
    fun preview(draft: PointDraft, index: Int): PointPreview? {
        val t = trail.value ?: return null
        val decl = trails.declinationFor(t.trail, draft.latitude, draft.longitude, draft.altitude)
        val previous = t.orderedPoints.getOrNull(index - 1)
        val here = LatLon(draft.latitude, draft.longitude)
        return PointPreview(
            utm = Utm.fromLatLon(draft.latitude, draft.longitude),
            previousName = previous?.name,
            leg = previous?.let { TrailCalculator.leg(LatLon(it.latitude, it.longitude), here, decl) },
            declinationDegrees = decl,
        )
    }

    /** Posição sugerida: no trecho mais próximo, ou no final. */
    fun suggestedIndex(draft: PointDraft): Int {
        val points = trail.value?.orderedPoints.orEmpty().map { LatLon(it.latitude, it.longitude) }
        return TrailEditing.suggestInsertIndex(points, LatLon(draft.latitude, draft.longitude))
    }

    fun nextPointName(): String = "Ponto ${(trail.value?.points?.size ?: 0) + 1}"

    fun addPoint(draft: PointDraft, name: String, notes: String, photoPath: String?, index: Int) {
        val id = trailId ?: return
        viewModelScope.launch {
            trails.insertPoint(id, index, NewPoint(draft.latitude, draft.longitude, draft.altitude, name, notes, photoPath))
        }
    }

    fun updatePoint(pointId: Long, name: String, notes: String, photoPath: String?) {
        viewModelScope.launch { trails.updatePointDetails(pointId, name, notes, photoPath) }
    }

    fun movePoint(pointId: Long, latitude: Double, longitude: Double) {
        viewModelScope.launch { trails.movePoint(pointId, latitude, longitude) }
    }

    fun deletePoint(pointId: Long) {
        viewModelScope.launch { trails.deletePoint(pointId) }
    }

    companion object {
        fun factory(trailId: Long?) = viewModelFactory {
            initializer {
                val c = appContainer()
                MapViewModel(trailId, c.trailRepository, c.settingsRepository)
            }
        }
    }
}
