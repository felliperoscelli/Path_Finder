package com.pathfinder.ui.results

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pathfinder.data.NavigationRepository
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.geo.Formats
import com.pathfinder.pdf.PdfActions
import com.pathfinder.pdf.PdfState
import com.pathfinder.pdf.TrailPdfRenderer
import com.pathfinder.results.GroupResult
import com.pathfinder.results.Results
import com.pathfinder.results.ResultsSheetBuilder
import com.pathfinder.transfer.FileActions
import com.pathfinder.ui.appContainer
import com.pathfinder.ui.components.PdfResultDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class ResultsViewModel(
    private val app: Application,
    trailId: Long,
    trails: TrailRepository,
    navigation: NavigationRepository,
) : ViewModel() {
    val trail: StateFlow<TrailWithPoints?> = trails.observeTrail(trailId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Percursos encerrados desta trilha: feitos aqui e resumos recebidos pela rede. */
    val results: StateFlow<List<GroupResult>?> = combine(trail, navigation.observeSessions(trailId)) { t, sessions ->
        t?.let { Results.compute(sessions, it.points.size) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pdf = MutableStateFlow<PdfState?>(null)
    val pdf: StateFlow<PdfState?> = _pdf.asStateFlow()

    fun generatePdf() {
        val t = trail.value ?: return
        val r = results.value ?: return
        _pdf.value = PdfState.Generating
        viewModelScope.launch {
            _pdf.value = try {
                val file = withContext(Dispatchers.IO) {
                    val sheet = ResultsSheetBuilder.build(t.trail, t.orderedPoints.map { it.name }, r)
                    File(PdfActions.outputDir(app), "resultado_${FileActions.safeName(t.trail.name)}.pdf").also {
                        TrailPdfRenderer.render(sheet, it)
                    }
                }
                PdfState.Ready(file, "Resultado – ${t.trail.name}")
            } catch (e: Exception) {
                PdfState.Failed(e.message ?: "Erro ao gerar o PDF")
            }
        }
    }

    fun dismissPdf() {
        _pdf.value = null
    }

    companion object {
        fun factory(trailId: Long) = viewModelFactory {
            initializer {
                val c = appContainer()
                ResultsViewModel(this[APPLICATION_KEY]!!, trailId, c.trailRepository, c.navigationRepository)
            }
        }
    }
}

/** Classificação dos grupos na trilha, com tempos por trecho e relatório em PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    trailId: Long,
    onBack: () -> Unit,
    viewModel: ResultsViewModel = viewModel(key = "results-$trailId", factory = ResultsViewModel.factory(trailId)),
) {
    val trail by viewModel.trail.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val pdf by viewModel.pdf.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Resultado")
                        Text(trail?.trail?.name.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    IconButton(onClick = viewModel::generatePdf, enabled = !results.isNullOrEmpty()) {
                        Icon(Icons.Filled.PictureAsPdf, "Relatório em PDF")
                    }
                },
            )
        },
    ) { padding ->
        val list = results
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text(
                    "Nenhum percurso encerrado nesta trilha ainda. Os resultados aparecem quando os monitores " +
                        "encerram a navegação (os de outros aparelhos chegam pela rede).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.sessionId }) { ResultCard(it, trail?.orderedPoints?.map { p -> p.name }.orEmpty()) }
            }
        }
    }
    pdf?.let { PdfResultDialog(it, viewModel::dismissPdf) }
}

@Composable
private fun ResultCard(r: GroupResult, pointNames: List<String>) {
    Card(
        colors = if (r.rank == 1) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                r.rank?.let { "${it}º" } ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(48.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${r.groupName} · ${r.monitorName}", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (r.completed) "Tempo total ${Formats.duration(r.totalMillis)}" else "Não completou: ${r.reachedCount}/${r.totalPoints} pontos",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Início ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.startedAt))} · " +
                        "${r.deviationAlerts} alerta(s) de desvio" +
                        (if (r.offRouteMillis > 0) " · ${Formats.duration(r.offRouteMillis)} fora da rota" else "") +
                        (if (r.remote) " · recebido pela rede" else ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                val legs = r.legMillis.mapIndexedNotNull { i, ms ->
                    ms?.let { "${i + 1}→${i + 2} ${Formats.duration(it)}" }
                }
                if (legs.isNotEmpty()) Text(legs.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
