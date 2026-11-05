package com.pathfinder.ui.offline

import android.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pathfinder.data.db.OfflineAreaEntity
import com.pathfinder.map.DownloadState
import com.pathfinder.map.MapLayer
import com.pathfinder.map.OfflineMapManager
import com.pathfinder.map.TopoMap
import com.pathfinder.ui.components.TopoMap
import com.pathfinder.ui.components.VisibleBoundsListener
import com.pathfinder.ui.components.rememberTopoMapView
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private const val MIN_SELECTABLE_ZOOM = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(
    onBack: () -> Unit,
    viewModel: OfflineMapsViewModel = viewModel(factory = OfflineMapsViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val areas by viewModel.savedAreas.collectAsStateWithLifecycle()
    // O download é só de curvas de nível; o mapa mostra a mesma camada que será baixada.
    val mapView = rememberTopoMapView(fixedLayer = MapLayer.TOPO)
    val areaLayer = remember(mapView) { AreaLayer(mapView) }
    var areaName by rememberSaveable { mutableStateOf("") }

    VisibleBoundsListener(mapView) { bounds, _ -> viewModel.onVisibleBoundsChanged(bounds) }
    LaunchedEffect(areas) { areaLayer.show(areas) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapas offline") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                TopoMap(mapView, Modifier.fillMaxSize())
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                item {
                    DownloadPanel(
                        state = state,
                        areaName = areaName,
                        onAreaNameChange = { areaName = it },
                        onZoomRange = viewModel::setZoomRange,
                        onDownload = { viewModel.startDownload(areaName) },
                        onCancel = viewModel::cancelDownload,
                    )
                    HorizontalDivider()
                    Text(
                        "Áreas baixadas",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    )
                    if (areas.isEmpty()) {
                        Text(
                            "Nenhuma área baixada ainda.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(areas, key = { it.id }) { area ->
                    AreaRow(
                        area = area,
                        onClick = { areaLayer.zoomTo(area) },
                        onDelete = { viewModel.deleteArea(area) },
                    )
                }
            }
        }
    }

    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
        )
    }
}

@Composable
private fun DownloadPanel(
    state: OfflineUiState,
    areaName: String,
    onAreaNameChange: (String) -> Unit,
    onZoomRange: (Int, Int) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Posicione o mapa sobre a área do evento e baixe enquanto houver internet.",
            style = MaterialTheme.typography.bodyMedium,
        )
        val progress = state.download as? DownloadState.Progress
        if (progress != null) {
            val fraction = if (progress.total > 0) progress.done.toFloat() / progress.total else 0f
            Text("Baixando… ${progress.done} de ${progress.total} tiles (zoom ${progress.zoom})")
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            return@Column
        }

        OutlinedTextField(
            value = areaName,
            onValueChange = onAreaNameChange,
            label = { Text("Nome da área") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Níveis de zoom: ${state.minZoom} a ${state.maxZoom}")
        RangeSlider(
            value = state.minZoom.toFloat()..state.maxZoom.toFloat(),
            onValueChange = { r -> onZoomRange(r.start.roundToInt(), r.endInclusive.roundToInt()) },
            valueRange = MIN_SELECTABLE_ZOOM.toFloat()..TopoMap.MAX_ZOOM.toFloat(),
            steps = TopoMap.MAX_ZOOM - MIN_SELECTABLE_ZOOM - 1,
        )
        Text(
            "${state.tileCount} tiles · cerca de ${"%.0f".format(state.estimatedMegabytes)} MB",
            color = if (state.tooLarge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (state.tooLarge) {
            Text(
                "Área grande demais (máximo ${OfflineMapManager.MAX_TILES_PER_AREA} tiles). " +
                    "Aproxime o mapa ou reduza o zoom máximo.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = onDownload,
            enabled = state.bounds != null && !state.tooLarge,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Download, null)
            Text("Baixar área visível", Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun AreaRow(area: OfflineAreaEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val date = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(area.createdAt))
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.Filled.Layers, null) },
        headlineContent = { Text(area.name) },
        supportingContent = { Text("Zoom ${area.minZoom}–${area.maxZoom} · ${area.tileCount} tiles · $date") },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Apagar") }
            }
        },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Apagar área?") },
            text = { Text("O mapa de \"${area.name}\" deixará de funcionar sem internet.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

/** Mostra no mapa o contorno das áreas já baixadas. */
private class AreaLayer(private val mapView: MapView) {
    private val polygons = mutableListOf<Polygon>()

    fun show(areas: List<OfflineAreaEntity>) {
        mapView.overlays.removeAll(polygons.toSet())
        polygons.clear()
        areas.forEach { area ->
            polygons += Polygon(mapView).apply {
                points = listOf(
                    GeoPoint(area.north, area.west),
                    GeoPoint(area.north, area.east),
                    GeoPoint(area.south, area.east),
                    GeoPoint(area.south, area.west),
                )
                fillPaint.color = Color.argb(30, 46, 94, 62)
                outlinePaint.color = Color.rgb(46, 94, 62)
                outlinePaint.strokeWidth = 4f
                title = area.name
            }
        }
        mapView.overlays.addAll(polygons)
        mapView.invalidate()
    }

    fun zoomTo(area: OfflineAreaEntity) {
        mapView.zoomToBoundingBox(area.boundingBox(), true, 32)
    }

    private fun OfflineAreaEntity.boundingBox() = BoundingBox(north, east, south, west)
}
