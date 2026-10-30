package com.pathfinder.ui.points

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.TrailPointEntity
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.data.settings.CoordinateFormat
import com.pathfinder.data.settings.SettingsRepository
import com.pathfinder.geo.Formats
import com.pathfinder.ui.appContainer
import com.pathfinder.ui.map.PhotoThumbnail
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PointsViewModel(trailId: Long, trails: TrailRepository, settings: SettingsRepository) : ViewModel() {
    val trail: StateFlow<TrailWithPoints?> = trails.observeTrail(trailId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val format: StateFlow<CoordinateFormat> = settings.settings.map { it.coordinateFormat }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoordinateFormat.BOTH)

    companion object {
        fun factory(trailId: Long) = viewModelFactory {
            initializer {
                val c = appContainer()
                PointsViewModel(trailId, c.trailRepository, c.settingsRepository)
            }
        }
    }
}

/** Lista dos pontos da trilha com coordenadas, azimutes e distâncias. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsScreen(
    trailId: Long,
    onBack: () -> Unit,
    viewModel: PointsViewModel = viewModel(key = "points-$trailId", factory = PointsViewModel.factory(trailId)),
) {
    val trail by viewModel.trail.collectAsStateWithLifecycle()
    val format by viewModel.format.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trail?.trail?.name ?: "Pontos") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
            )
        },
    ) { padding ->
        val t = trail
        if (t == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val points = t.orderedPoints
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val total = points.sumOf { it.distanceFromPreviousMeters ?: 0.0 }
                        Text("${points.size} pontos · ${Formats.distance(total)}", style = MaterialTheme.typography.titleMedium)
                        Text("Declinação usada: ${Formats.declination(t.trail.declinationDegrees)}")
                        Text(
                            "Alerta de desvio: ${t.trail.deviationDistanceMeters} m por ${t.trail.deviationTimeSeconds / 60} min",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (points.isEmpty()) {
                item { Text("Nenhum ponto marcado ainda.", Modifier.padding(8.dp)) }
            }
            items(points, key = { it.id }) { PointCard(it, format) }
        }
    }
}

@Composable
private fun PointCard(p: TrailPointEntity, format: CoordinateFormat) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${p.sequence + 1}. ${p.name}", style = MaterialTheme.typography.titleSmall)
                val utm = Formats.utm(p.utmZone, p.utmBand, p.utmEasting, p.utmNorthing)
                Text(Formats.coordinate(format, p.latitude, p.longitude, utm), style = MaterialTheme.typography.bodyMedium)
                if (p.sequence == 0) {
                    Text("Partida", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        "Az. verdadeiro ${Formats.azimuth(p.trueAzimuthDegrees)} · " +
                            "magnético ${Formats.azimuth(p.magneticAzimuthDegrees)} · " +
                            Formats.distance(p.distanceFromPreviousMeters),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (p.notes.isNotBlank()) Text(p.notes, style = MaterialTheme.typography.bodySmall)
            }
            p.photoPath?.let { PhotoThumbnail(it, sizeDp = 64) }
        }
    }
}
