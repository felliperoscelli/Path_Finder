package com.pathfinder.ui.trails

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.pathfinder.transfer.FileActions
import com.pathfinder.transfer.Gpx
import com.pathfinder.transfer.TrailJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

class TrailsViewModel(
    private val app: Application,
    private val trails: TrailRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /**
     * Importa um arquivo .json (Path Finder) ou .gpx. Retorna o id da trilha,
     * ou uma mensagem de erro.
     */
    fun importFile(uri: Uri, onResult: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val (bytes, name) = withContext(Dispatchers.IO) {
                    val data = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Não foi possível ler o arquivo")
                    require(data.size <= MAX_IMPORT_BYTES) { "Arquivo grande demais" }
                    data to (FileActions.displayName(app, uri) ?: "Trilha importada")
                }
                val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                val pkg = if (text.startsWith("{")) {
                    TrailJson.decode(text)
                } else {
                    val s = settings.current()
                    Gpx.read(bytes, name, s.deviationDistanceMeters, s.deviationTimeSeconds)
                }
                trails.importPackage(pkg).trailId
            }
            onResult(result)
        }
    }

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
        private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024

        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                TrailsViewModel(this[APPLICATION_KEY]!!, c.trailRepository, c.settingsRepository)
            }
        }
    }
}
