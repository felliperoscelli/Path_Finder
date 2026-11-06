package com.pathfinder.ui.livemap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.TrailSummary
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.data.settings.SettingsRepository
import com.pathfinder.data.settings.UserRole
import com.pathfinder.map.TrailColors
import com.pathfinder.ui.appContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LiveMapViewModel(
    trails: TrailRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val trailList: StateFlow<List<TrailSummary>?> = trails.observeTrails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Cor de cada trilha (a mesma no mapa e na lista). */
    val colors: StateFlow<Map<Long, Int>> = trails.observeTrails()
        .map { list -> TrailColors.assign(list.map { it.trail.id }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val visibleIds: StateFlow<Set<Long>> = settings.settings.map { it.visibleTrailIds }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Trilhas marcadas para aparecer no mapa (trilhas apagadas somem sozinhas). */
    val visibleTrails: StateFlow<List<TrailWithPoints>> = visibleIds
        .flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList())
            else combine(ids.sorted().map { trails.observeTrail(it) }) { all -> all.filterNotNull() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val role: StateFlow<UserRole?> = settings.settings.map { it.role }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setVisible(trailId: Long, visible: Boolean) {
        viewModelScope.launch {
            settings.update { s -> s.copy(visibleTrailIds = if (visible) s.visibleTrailIds + trailId else s.visibleTrailIds - trailId) }
        }
    }

    fun setAllVisible(trailIds: Collection<Long>) {
        viewModelScope.launch { settings.update { it.copy(visibleTrailIds = trailIds.toSet()) } }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                LiveMapViewModel(c.trailRepository, c.settingsRepository)
            }
        }
    }
}
