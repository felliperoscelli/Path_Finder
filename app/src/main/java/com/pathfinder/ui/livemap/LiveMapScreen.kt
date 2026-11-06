package com.pathfinder.ui.livemap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pathfinder.data.db.TrailSummary
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.data.settings.UserRole
import com.pathfinder.ui.components.MapControls
import com.pathfinder.ui.components.TopoMap
import com.pathfinder.ui.components.centerOnMe
import com.pathfinder.ui.components.rememberLocationPermission
import com.pathfinder.ui.components.rememberMyLocationOverlay
import com.pathfinder.ui.components.rememberTopoMapView
import com.pathfinder.ui.map.TrailLayer
import kotlinx.coroutines.launch

/** Ponto tocado no mapa: de qual trilha e qual ponto. */
private data class Picked(val trailId: Long, val pointId: Long)

/**
 * Mapa em tempo real: a posição do usuário (seguida ao abrir) e as trilhas que ele escolher
 * mostrar, várias ao mesmo tempo, cada uma com uma cor. O botão "Trilhas" escolhe quais aparecem
 * e leva à lista completa (criar, importar, apagar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    onBack: () -> Unit,
    onOpenTrail: (Long) -> Unit,
    onNavigateTrail: (Long) -> Unit,
    onManageTrails: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    viewModel: LiveMapViewModel = viewModel(factory = LiveMapViewModel.Factory),
) {
    val trails by viewModel.trailList.collectAsStateWithLifecycle()
    val visibleIds by viewModel.visibleIds.collectAsStateWithLifecycle()
    val visibleTrails by viewModel.visibleTrails.collectAsStateWithLifecycle()
    val colors by viewModel.colors.collectAsStateWithLifecycle()
    val role by viewModel.role.collectAsStateWithLifecycle()
    val mapView = rememberTopoMapView()
    val permission = rememberLocationPermission()
    val myLocation = rememberMyLocationOverlay(mapView, permission.granted)
    val layers = remember(mapView) { mutableMapOf<Long, TrailLayer>() }
    var picked by remember { mutableStateOf<Picked?>(null) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    /** Trilha recém-marcada na lista: enquadrar quando ela aparecer no mapa. */
    var frameTrail by remember { mutableStateOf<Long?>(null) }
    /** Sem localização, o mapa abre mostrando as trilhas escolhidas (uma vez). */
    var framedAll by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { if (!permission.granted) permission.request() }

    // Ao abrir, o mapa vai até a pessoa e passa a acompanhá-la (até ela arrastar o mapa).
    LaunchedEffect(myLocation) {
        val overlay = myLocation ?: return@LaunchedEffect
        overlay.enableFollowLocation()
        overlay.runOnFirstFix {
            mapView.post { if (mapView.zoomLevelDouble < FOLLOW_ZOOM) mapView.controller.setZoom(FOLLOW_ZOOM) }
        }
    }

    LaunchedEffect(visibleTrails, colors, role) {
        val shown = visibleTrails.associateBy { it.trail.id }
        (layers.keys - shown.keys).forEach { id -> layers.remove(id)?.remove() }
        shown.forEach { (id, trail) ->
            val color = colors[id]
            val layer = layers[id]?.takeIf { it.color == color } ?: run {
                layers.remove(id)?.remove()
                TrailLayer(
                    mapView,
                    onPointClick = { p -> picked = Picked(id, p.id) },
                    onPointMoved = { _, _ -> },
                    color = color,
                ).also { layers[id] = it }
            }
            layer.showPoints(trail.orderedPoints)
            // Modo desafio: o monitor não vê os pontos da pista, nem aqui.
            if (hiddenFor(role, trail)) layer.showProgress(-1, emptySet(), hideUnreached = true)
        }
        val target = frameTrail
        if (target != null && target in shown && !hiddenFor(role, shown.getValue(target))) {
            myLocation?.disableFollowLocation()
            layers[target]?.zoomToTrail()
            frameTrail = null
        }
        if (!framedAll && !permission.granted && shown.isNotEmpty()) {
            framedAll = true
            val open = visibleTrails.filterNot { hiddenFor(role, it) }.mapNotNull { layers[it.trail.id] }
            TrailLayer.zoomTo(mapView, open.flatMap { it.geoPoints })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    IconButton(onClick = onOpenOfflineMaps) { Icon(Icons.Filled.Download, "Mapas offline") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            TopoMap(mapView, Modifier.fillMaxSize())
            if (visibleTrails.isNotEmpty()) {
                Legend(
                    trails = visibleTrails,
                    colors = colors,
                    onTrailClick = { id ->
                        if (visibleTrails.none { it.trail.id == id && hiddenFor(role, it) }) {
                            myLocation?.disableFollowLocation()
                            layers[id]?.zoomToTrail()
                        }
                    },
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                )
            }
            MapControls(mapView, Modifier.align(Alignment.CenterEnd).padding(12.dp)) {
                when {
                    !permission.granted -> permission.request()
                    myLocation?.centerOnMe(mapView) != true -> scope.launch { snackbar.showSnackbar("Aguardando sinal do GPS…") }
                }
            }
            val selected = picked?.let { p ->
                visibleTrails.firstOrNull { it.trail.id == p.trailId }?.let { t ->
                    t.orderedPoints.withIndex().firstOrNull { it.value.id == p.pointId }?.let { Triple(t, it.index, it.value) }
                }
            }
            if (selected != null) {
                val (trail, index, point) = selected
                PointCard(
                    trailName = trail.trail.name,
                    pointLabel = "Ponto ${index + 1}: ${point.name}" + when (index) {
                        0 -> " · início"
                        trail.points.lastIndex -> " · fim"
                        else -> ""
                    },
                    color = colors[trail.trail.id],
                    onOpen = { picked = null; openByRole(role, trail.trail.id, onOpenTrail, onNavigateTrail) },
                    onNavigate = { picked = null; onNavigateTrail(trail.trail.id) },
                    onClose = { picked = null },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { choosing = true },
                    icon = { Icon(Icons.Filled.Route, null) },
                    text = { Text(if (visibleIds.isEmpty()) "Trilhas" else "Trilhas (${visibleTrails.size})") },
                    // No canto direito: o esquerdo é da atribuição do mapa e da escala.
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }

    if (choosing) {
        TrailPicker(
            trails = trails.orEmpty(),
            visibleIds = visibleIds,
            colors = colors,
            onToggle = { id, visible ->
                if (visible) frameTrail = id
                viewModel.setVisible(id, visible)
            },
            onShowAll = { viewModel.setAllVisible(trails.orEmpty().map { it.trail.id }) },
            onHideAll = { viewModel.setAllVisible(emptySet()) },
            onOpen = { id -> choosing = false; openByRole(role, id, onOpenTrail, onNavigateTrail) },
            onManage = { choosing = false; onManageTrails() },
            onDismiss = { choosing = false },
        )
    }
}

private const val FOLLOW_ZOOM = 16.0

private fun hiddenFor(role: UserRole?, trail: TrailWithPoints) = role == UserRole.MONITOR && trail.trail.challengeMode

/** Como na lista de trilhas: o monitor vai direto para a navegação; os outros, para o mapa da trilha. */
private fun openByRole(role: UserRole?, trailId: Long, onOpenTrail: (Long) -> Unit, onNavigateTrail: (Long) -> Unit) {
    if (role == UserRole.MONITOR) onNavigateTrail(trailId) else onOpenTrail(trailId)
}

/** Legenda das trilhas mostradas; tocar enquadra a trilha. */
@Composable
private fun Legend(
    trails: List<TrailWithPoints>,
    colors: Map<Long, Int>,
    onTrailClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.widthIn(max = 220.dp), shape = MaterialTheme.shapes.medium, shadowElevation = 4.dp) {
        Column(Modifier.padding(vertical = 4.dp)) {
            trails.take(MAX_LEGEND).forEach { t ->
                Row(
                    Modifier.clickable { onTrailClick(t.trail.id) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ColorDot(colors[t.trail.id])
                    Text(t.trail.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trails.size > MAX_LEGEND) {
                Text(
                    "+ ${trails.size - MAX_LEGEND} trilha(s)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private const val MAX_LEGEND = 4

@Composable
private fun ColorDot(color: Int?) {
    Box(Modifier.size(12.dp).background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline, CircleShape))
}

@Composable
private fun PointCard(
    trailName: String,
    pointLabel: String,
    color: Int?,
    onOpen: () -> Unit,
    onNavigate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().navigationBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorDot(color)
                Text(trailName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Fechar") }
            }
            Text(pointLabel, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 8.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen) { Icon(Icons.Filled.Map, null); Text(" Abrir trilha") }
                Button(onClick = onNavigate) { Icon(Icons.Filled.Explore, null); Text(" Navegar") }
            }
        }
    }
}

/** Escolha das trilhas mostradas no mapa (várias ao mesmo tempo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrailPicker(
    trails: List<TrailSummary>,
    visibleIds: Set<Long>,
    colors: Map<Long, Int>,
    onToggle: (Long, Boolean) -> Unit,
    onShowAll: () -> Unit,
    onHideAll: () -> Unit,
    onOpen: (Long) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Trilhas no mapa", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onManage) { Text("Gerenciar") }
            }
            if (trails.isEmpty()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Nenhuma trilha ainda.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onManage) { Text("Criar ou importar trilha") }
                }
            } else {
                Text(
                    "Marque as trilhas que devem aparecer. Dá para mostrar várias ao mesmo tempo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = onShowAll) { Text("Mostrar todas") }
                    TextButton(onClick = onHideAll, enabled = visibleIds.isNotEmpty()) { Text("Esconder todas") }
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(trails, key = { it.trail.id }) { s ->
                        val id = s.trail.id
                        val checked = id in visibleIds
                        val tint = colors[id]?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                        ListItem(
                            modifier = Modifier.clickable { onToggle(id, !checked) },
                            leadingContent = {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onToggle(id, it) },
                                    colors = CheckboxDefaults.colors(checkedColor = tint, uncheckedColor = tint),
                                )
                            },
                            headlineContent = { Text(s.trail.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    when (s.pointCount) {
                                        0 -> "sem pontos"
                                        1 -> "1 ponto"
                                        else -> "${s.pointCount} pontos"
                                    },
                                )
                            },
                            trailingContent = { TextButton(onClick = { onOpen(id) }) { Text("Abrir") } },
                        )
                    }
                }
            }
        }
    }
}
