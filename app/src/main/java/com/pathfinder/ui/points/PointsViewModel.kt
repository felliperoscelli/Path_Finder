package com.pathfinder.ui.points

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.data.settings.CoordinateFormat
import com.pathfinder.data.settings.SettingsRepository
import com.pathfinder.net.NetworkHub
import com.pathfinder.pdf.PdfActions
import com.pathfinder.transfer.FileActions
import com.pathfinder.transfer.Gpx
import com.pathfinder.transfer.TrailJson
import com.pathfinder.pdf.SheetOptions
import com.pathfinder.pdf.TrailPdfRenderer
import com.pathfinder.pdf.TrailSheetBuilder
import com.pathfinder.ui.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface PdfState {
    data object Generating : PdfState
    data class Ready(val file: File, val title: String) : PdfState
    data class Failed(val message: String) : PdfState
}

class PointsViewModel(
    private val app: Application,
    private val trailId: Long,
    private val trails: TrailRepository,
    settings: SettingsRepository,
    private val hub: NetworkHub,
) : ViewModel() {
    val connectedDevices: StateFlow<Int> = hub.nearby.state.map { it.connected.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val trail: StateFlow<TrailWithPoints?> = trails.observeTrail(trailId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val format: StateFlow<CoordinateFormat> = settings.settings.map { it.coordinateFormat }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoordinateFormat.BOTH)

    private val _pdf = MutableStateFlow<PdfState?>(null)
    val pdf: StateFlow<PdfState?> = _pdf.asStateFlow()

    fun reorder(pointId: Long, newIndex: Int) {
        viewModelScope.launch { trails.reorderPoint(pointId, newIndex) }
    }

    fun updatePoint(pointId: Long, name: String, notes: String, photoPath: String?) {
        viewModelScope.launch { trails.updatePointDetails(pointId, name, notes, photoPath) }
    }

    fun deletePoint(pointId: Long) {
        viewModelScope.launch { trails.deletePoint(pointId) }
    }

    fun updateTrail(name: String, deviationDistanceMeters: Int, deviationTimeSeconds: Int) {
        viewModelScope.launch { trails.updateTrail(trailId, name, deviationDistanceMeters, deviationTimeSeconds) }
    }

    fun generatePdf(options: SheetOptions) {
        val t = trail.value ?: return
        _pdf.value = PdfState.Generating
        viewModelScope.launch {
            _pdf.value = try {
                val file = withContext(Dispatchers.IO) {
                    val sheet = TrailSheetBuilder.build(t.trail, t.points, options)
                    File(PdfActions.outputDir(app), PdfActions.fileName(t.trail.name)).also {
                        TrailPdfRenderer.render(sheet, it)
                    }
                }
                PdfState.Ready(file, "Ficha – ${t.trail.name}")
            } catch (e: Exception) {
                PdfState.Failed(e.message ?: "Erro ao gerar o PDF")
            }
        }
    }

    /** Envia a trilha aos aparelhos conectados; retorna quantos receberam. */
    fun shareOverNetwork(onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(hub.shareTrail(trailId)) }
    }

    /** Gera o arquivo .json ou .gpx da trilha para compartilhar ou salvar. */
    fun exportFile(gpx: Boolean, onReady: (File?) -> Unit) {
        viewModelScope.launch {
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    val pkg = trails.exportPackage(trailId) ?: error("Trilha não encontrada")
                    val ext = if (gpx) "gpx" else "json"
                    File(FileActions.exportDir(app), "${FileActions.safeName(pkg.name)}.$ext").apply {
                        parentFile?.mkdirs()
                        writeText(if (gpx) Gpx.write(pkg) else TrailJson.encode(pkg))
                    }
                }
            }.getOrNull()
            onReady(file)
        }
    }

    fun dismissPdf() {
        _pdf.value = null
    }

    companion object {
        fun factory(trailId: Long) = viewModelFactory {
            initializer {
                val c = appContainer()
                PointsViewModel(this[APPLICATION_KEY]!!, trailId, c.trailRepository, c.settingsRepository, c.networkHub)
            }
        }
    }
}
