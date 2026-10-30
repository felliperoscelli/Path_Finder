package com.pathfinder.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.ui.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class MapViewModel(trailId: Long?, trails: TrailRepository) : ViewModel() {
    val trail: StateFlow<TrailWithPoints?> =
        (if (trailId != null) trails.observeTrail(trailId) else flowOf(null))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(trailId: Long?) = viewModelFactory {
            initializer { MapViewModel(trailId, appContainer().trailRepository) }
        }
    }
}
