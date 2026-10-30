package com.pathfinder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.TrailSummary
import com.pathfinder.data.settings.AppSettings
import com.pathfinder.data.settings.SettingsRepository
import com.pathfinder.ui.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val trails: TrailRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val trailList: StateFlow<List<TrailSummary>?> = trails.observeTrails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Cria uma trilha com os limites de desvio padrão das configurações. */
    fun createTrail(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val s = settings.current()
            val id = trails.createTrail(name, s.deviationDistanceMeters, s.deviationTimeSeconds)
            onCreated(id)
        }
    }

    fun deleteTrail(id: Long) {
        viewModelScope.launch { trails.deleteTrail(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                HomeViewModel(c.trailRepository, c.settingsRepository)
            }
        }
    }
}
