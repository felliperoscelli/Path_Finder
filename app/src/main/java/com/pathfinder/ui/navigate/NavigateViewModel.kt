package com.pathfinder.ui.navigate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pathfinder.data.NavigationRepository
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.NavigationSessionEntity
import com.pathfinder.data.db.TrackPointEntity
import com.pathfinder.data.db.TrackSource
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.data.settings.AppSettings
import com.pathfinder.data.settings.SettingsRepository
import com.pathfinder.navigation.NavigationRuntime
import com.pathfinder.navigation.NavigationState
import com.pathfinder.ui.appContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class NavigateViewModel(
    private val trailId: Long,
    trails: TrailRepository,
    private val navigation: NavigationRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val trail: StateFlow<TrailWithPoints?> = trails.observeTrail(trailId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val creationTrack: StateFlow<List<TrackPointEntity>> = trails.observeTrack(trailId, TrackSource.CREATION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val runtime: StateFlow<NavigationState> = NavigationRuntime.state

    /** Trajeto da navegação ativa desta trilha. */
    val walkedTrack: StateFlow<List<TrackPointEntity>> = NavigationRuntime.state
        .map { s -> s.sessionId?.takeIf { s.trailId == trailId } }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else navigation.observeTrack(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessions: StateFlow<List<NavigationSessionEntity>> = navigation.observeSessions(trailId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appSettings: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Cria a sessão e devolve o id para a tela iniciar o serviço. */
    fun createSession(groupName: String, monitorName: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            settings.update { it.copy(groupName = groupName.trim(), userName = monitorName.trim()) }
            onCreated(navigation.startSession(trailId, groupName, monitorName))
        }
    }

    companion object {
        fun factory(trailId: Long) = viewModelFactory {
            initializer {
                val c = appContainer()
                NavigateViewModel(trailId, c.trailRepository, c.navigationRepository, c.settingsRepository)
            }
        }
    }
}
