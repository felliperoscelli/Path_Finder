package com.pathfinder.ui.navigate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pathfinder.data.NavigationRepository
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.DeviationEventEntity
import com.pathfinder.data.db.NavigationSessionEntity
import com.pathfinder.data.db.TrackPointEntity
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.geo.Formats
import com.pathfinder.geo.Geodesy
import com.pathfinder.geo.LatLon
import com.pathfinder.ui.appContainer
import com.pathfinder.ui.components.TopoMap
import com.pathfinder.ui.components.rememberTopoMapView
import com.pathfinder.ui.map.TrackLine
import com.pathfinder.ui.map.TrailLayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.DateFormat
import java.util.Date
import android.graphics.Color as AndroidColor

@OptIn(ExperimentalCoroutinesApi::class)
class SessionReviewViewModel(
    private val sessionId: Long,
    private val navigation: NavigationRepository,
    trails: TrailRepository,
) : ViewModel() {
    val session: StateFlow<NavigationSessionEntity?> = navigation.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val trail: StateFlow<TrailWithPoints?> = navigation.observeSession(sessionId).filterNotNull()
        .map { it.trailId }.distinctUntilChanged()
        .flatMapLatest { trails.observeTrail(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val track: StateFlow<List<TrackPointEntity>> = navigation.observeTrack(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val deviations: StateFlow<List<DeviationEventEntity>> = navigation.observeDeviations(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            navigation.deleteSession(sessionId)
            onDone()
        }
    }

    companion object {
        fun factory(sessionId: Long) = viewModelFactory {
            initializer {
                val c = appContainer()
                SessionReviewViewModel(sessionId, c.navigationRepository, c.trailRepository)
            }
        }
    }
}

/** Revisão de um percurso: trajeto real do grupo, pontos alcançados e desvios. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionReviewScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: SessionReviewViewModel = viewModel(key = "session-$sessionId", factory = SessionReviewViewModel.factory(sessionId)),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val trail by viewModel.trail.collectAsStateWithLifecycle()
    val track by viewModel.track.collectAsStateWithLifecycle()
    val deviations by viewModel.deviations.collectAsStateWithLifecycle()
    val mapView = rememberTopoMapView()
    val trailLayer = remember(mapView) { TrailLayer(mapView, onPointClick = {}, onPointMoved = { _, _ -> }) }
    val walkedLine = remember(mapView) { TrackLine(mapView, AndroidColor.rgb(239, 108, 0), width = 7f) }
    val deviationMarkers = remember(mapView) { mutableListOf<Marker>() }
    var framed by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(trail, session) {
        val points = trail?.orderedPoints ?: return@LaunchedEffect
        trailLayer.showPoints(points)
        session?.let { trailLayer.showProgress(-1, it.reachedSet) }
    }
    LaunchedEffect(track) {
        walkedLine.show(track.map { GeoPoint(it.latitude, it.longitude) })
        if (!framed && track.size > 1) {
            mapView.post { mapView.zoomToBoundingBox(org.osmdroid.util.BoundingBox.fromGeoPointsSafe(walkedLine.points), false, 80) }
            framed = true
        }
    }
    LaunchedEffect(deviations) { showDeviations(mapView, deviationMarkers, deviations) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session?.groupName ?: "Percurso")
                        Text(trail?.trail?.name.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    IconButton(onClick = { trailLayer.zoomToTrail() }) { Icon(Icons.Filled.CenterFocusStrong, "Ver trilha") }
                    if (session?.endedAt != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Apagar percurso") }
                    }
                },
            )
        },
        bottomBar = {
            val s = session
            val t = trail
            if (s != null && t != null) Summary(s, t, track, deviations)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { TopoMap(mapView, Modifier.fillMaxSize()) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar este percurso?") },
            text = { Text("O trajeto e os alertas registrados serão apagados.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Summary(
    s: NavigationSessionEntity,
    t: TrailWithPoints,
    track: List<TrackPointEntity>,
    deviations: List<DeviationEventEntity>,
) {
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    val walked = track.zipWithNext { a, b ->
        Geodesy.distance(LatLon(a.latitude, a.longitude), LatLon(b.latitude, b.longitude))
    }.sum()
    val end = s.endedAt
    val duration = ((end ?: System.currentTimeMillis()) - s.startedAt) / 1000
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Monitor: ${s.monitorName}", style = MaterialTheme.typography.titleSmall)
            Text(
                "Início ${fmt.format(Date(s.startedAt))}" +
                    (if (end != null) " · duração %d:%02d".format(duration / 3600, (duration / 60) % 60) else " · em andamento"),
            )
            Text("${s.reachedSet.size} de ${t.points.size} pontos alcançados · ${Formats.distance(walked)} percorridos")
            Text(
                if (deviations.isEmpty()) "Nenhum alerta de desvio"
                else "${deviations.size} alerta(s) de desvio; maior afastamento ${Formats.distance(deviations.maxOf { it.maxDistanceMeters })}",
                color = if (deviations.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            Text("Laranja: trajeto do grupo · vermelho: trilha", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun showDeviations(mapView: MapView, markers: MutableList<Marker>, events: List<DeviationEventEntity>) {
    mapView.overlays.removeAll(markers.toSet())
    markers.clear()
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    events.forEach { e ->
        markers += Marker(mapView).apply {
            position = GeoPoint(e.latitude, e.longitude)
            title = "Fora da rota às ${fmt.format(Date(e.alarmAt))}"
            snippet = buildString {
                append("Saiu às ${fmt.format(Date(e.startedAt))}")
                e.endedAt?.let { append(", voltou às ${fmt.format(Date(it))}") }
                append(" · até ${Formats.distance(e.maxDistanceMeters)} da rota")
            }
            icon = icon?.constantState?.newDrawable()?.mutate()?.apply { setTint(AndroidColor.rgb(198, 40, 40)) }
        }
    }
    mapView.overlays.addAll(markers)
    mapView.invalidate()
}
