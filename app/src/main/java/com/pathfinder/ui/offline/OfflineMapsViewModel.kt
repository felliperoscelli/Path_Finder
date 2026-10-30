package com.pathfinder.ui.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pathfinder.data.OfflineAreaRepository
import com.pathfinder.data.db.OfflineAreaEntity
import com.pathfinder.map.DownloadState
import com.pathfinder.map.GeoBounds
import com.pathfinder.map.OfflineMapManager
import com.pathfinder.map.TileMath
import com.pathfinder.map.TopoMap
import com.pathfinder.ui.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OfflineUiState(
    val bounds: GeoBounds? = null,
    val minZoom: Int = 10,
    val maxZoom: Int = TopoMap.MAX_ZOOM,
    val download: DownloadState? = null,
    val message: String? = null,
) {
    val tileCount: Long = bounds?.let { TileMath.tileCount(it, minZoom, maxZoom) } ?: 0
    val estimatedMegabytes: Double = tileCount * OfflineMapManager.AVERAGE_TILE_BYTES / 1_000_000.0
    val tooLarge: Boolean = tileCount > OfflineMapManager.MAX_TILES_PER_AREA
    val downloading: Boolean = download is DownloadState.Progress
}

class OfflineMapsViewModel(
    private val areas: OfflineAreaRepository,
    private val manager: OfflineMapManager,
) : ViewModel() {

    private val _state = MutableStateFlow(OfflineUiState())
    val state: StateFlow<OfflineUiState> = _state.asStateFlow()

    val savedAreas: StateFlow<List<OfflineAreaEntity>> = areas.observeAreas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var downloadJob: Job? = null

    fun onVisibleBoundsChanged(bounds: GeoBounds) {
        if (!_state.value.downloading) _state.update { it.copy(bounds = bounds) }
    }

    fun setZoomRange(min: Int, max: Int) {
        _state.update { it.copy(minZoom = min, maxZoom = max) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null, download = null) }

    fun startDownload(name: String) {
        val s = _state.value
        val bounds = s.bounds ?: return
        if (s.downloading || s.tooLarge) return
        downloadJob = viewModelScope.launch {
            manager.download(bounds, s.minZoom, s.maxZoom).collect { d ->
                _state.update { it.copy(download = d) }
                when (d) {
                    is DownloadState.Finished -> {
                        areas.save(
                            OfflineAreaEntity(
                                name = name.ifBlank { "Área sem nome" },
                                north = bounds.north,
                                south = bounds.south,
                                east = bounds.east,
                                west = bounds.west,
                                minZoom = s.minZoom,
                                maxZoom = s.maxZoom,
                                tileCount = d.total,
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                        _state.update { it.copy(message = "Área baixada: ${d.total} tiles.") }
                    }
                    is DownloadState.Failed -> _state.update {
                        it.copy(message = "Download concluído com ${d.errors} falhas. Verifique a internet e tente de novo.")
                    }
                    is DownloadState.Progress -> Unit
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update { it.copy(download = null, message = "Download cancelado.") }
    }

    /**
     * Apaga a área. Os tiles só são removidos do cache se nenhuma outra área
     * salva se sobrepuser a ela, para não estragar áreas vizinhas.
     */
    fun deleteArea(area: OfflineAreaEntity) {
        viewModelScope.launch {
            areas.delete(area)
            val bounds = area.bounds()
            val overlaps = savedAreas.value.any { it.id != area.id && it.bounds().intersects(bounds) }
            if (!overlaps) manager.deleteTiles(bounds, area.minZoom, area.maxZoom)
        }
    }
}

fun OfflineAreaEntity.bounds() = GeoBounds(north, south, east, west)

val OfflineMapsViewModelFactory = viewModelFactory {
    initializer {
        val c = appContainer()
        OfflineMapsViewModel(c.offlineAreaRepository, c.offlineMapManager)
    }
}
