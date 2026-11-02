package com.pathfinder.ui.map

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.geo.Formats
import com.pathfinder.recording.RecordingSession
import com.pathfinder.recording.RecordingState
import com.pathfinder.ui.components.TopoMap
import com.pathfinder.ui.components.centerOnMe
import com.pathfinder.ui.components.rememberLocationPermission
import com.pathfinder.ui.components.rememberMyLocationOverlay
import com.pathfinder.ui.components.rememberTopoMapView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import kotlin.math.roundToInt

/** Uma leitura de GPS com mais de 30 s não serve para marcar ponto. */
private const val MAX_FIX_AGE_MS = 30_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    trailId: Long?,
    onBack: () -> Unit,
    onOpenPoints: (Long) -> Unit,
    viewModel: MapViewModel = viewModel(key = "map-$trailId", factory = MapViewModel.factory(trailId)),
) {
    val context = LocalContext.current
    val trail by viewModel.trail.collectAsStateWithLifecycle()
    val track by viewModel.track.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val format by viewModel.coordinateFormat.collectAsStateWithLifecycle()
    val mapView = rememberTopoMapView()
    val permission = rememberLocationPermission()
    val myLocation = rememberMyLocationOverlay(mapView, permission.granted)
    var editing by rememberSaveable { mutableStateOf(false) }
    var selectedPointId by rememberSaveable { mutableStateOf<Long?>(null) }
    val trailLayer = remember(mapView) {
        TrailLayer(
            mapView,
            onPointClick = { selectedPointId = it.id },
            onPointMoved = { p, g -> viewModel.movePoint(p.id, g.latitude, g.longitude) },
        )
    }
    LaunchedEffect(editing) { trailLayer.editing = editing }
    var framedTrail by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(stateSaver = PointDraftSaver) { mutableStateOf<PointDraft?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val recordingThis = trailId != null && recording.trailId == trailId
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // A gravação funciona mesmo sem notificações; elas só avisam com a tela desligada.
        if (trailId != null) RecordingSession.start(context, trailId)
    }

    fun startRecording() {
        val id = trailId ?: return
        when {
            !permission.granted -> permission.request()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> RecordingSession.start(context, id)
        }
    }

    fun markPointHere() {
        val now = System.currentTimeMillis()
        val fix = recording.lastFix?.takeIf { recordingThis && now - it.time < MAX_FIX_AGE_MS }
            ?: myLocation?.lastFix?.takeIf { now - it.time < MAX_FIX_AGE_MS }
        if (fix == null) {
            if (!permission.granted) permission.request()
            scope.launch {
                snackbar.showSnackbar("Sem sinal de GPS. Toque e segure no mapa para escolher a posição.")
            }
            return
        }
        val accuracy = if (fix.hasAccuracy()) "GPS ±${fix.accuracy.roundToInt()} m" else "GPS"
        draft = PointDraft(fix.latitude, fix.longitude, if (fix.hasAltitude()) fix.altitude else null, accuracy)
    }

    LaunchedEffect(Unit) {
        if (!permission.granted) permission.request()
    }

    if (trailId != null) {
        MapLongPress(mapView) { p ->
            draft = PointDraft(p.latitude, p.longitude, null, "Posição escolhida no mapa")
        }
    }

    LaunchedEffect(trail) {
        val points = trail?.orderedPoints ?: return@LaunchedEffect
        trailLayer.showPoints(points)
        if (!framedTrail && points.isNotEmpty()) {
            trailLayer.zoomToTrail()
            framedTrail = true
        }
    }
    LaunchedEffect(track) { trailLayer.showTrack(track) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trail?.trail?.name ?: "Mapa") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    if (trailId != null) {
                        IconButton(onClick = { editing = !editing }) {
                            if (editing) Icon(Icons.Filled.Check, "Concluir edição")
                            else Icon(Icons.Filled.Edit, "Editar pontos")
                        }
                        IconButton(onClick = { trailLayer.zoomToTrail() }) {
                            Icon(Icons.Filled.CenterFocusStrong, "Ver trilha inteira")
                        }
                        IconButton(onClick = { onOpenPoints(trailId) }) {
                            Icon(Icons.AutoMirrored.Filled.List, "Lista de pontos")
                        }
                    }
                },
            )
        },
        bottomBar = {
            val t = trail
            if (t != null) {
                RecordingPanel(
                    trail = t,
                    recording = recording,
                    recordingThis = recordingThis,
                    onStart = ::startRecording,
                    onStop = { RecordingSession.stop(context) },
                    onMarkPoint = ::markPointHere,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when {
                    !permission.granted -> permission.request()
                    myLocation?.centerOnMe(mapView) != true -> scope.launch {
                        snackbar.showSnackbar("Aguardando sinal do GPS…")
                    }
                }
            }) { Icon(Icons.Filled.MyLocation, "Minha localização") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            TopoMap(mapView, Modifier.fillMaxSize())
            if (editing) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        "Modo edição: toque e segure um ponto e arraste para movê-lo. " +
                            "Toque num ponto para renomear ou apagar.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    // "Marcar ponto aqui?" quando o usuário fica parado durante a gravação.
    val stop = recording.stopSuggestion
    if (recordingThis && stop != null && draft == null && selectedPointId == null) {
        val seconds = ((System.currentTimeMillis() - stop.sinceMillis) / 1000).coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = RecordingSession::dismissStopSuggestion,
            title = { Text("Marcar ponto aqui?") },
            text = { Text("Você está parado há $seconds s.") },
            confirmButton = {
                TextButton(onClick = {
                    draft = PointDraft(stop.latitude, stop.longitude, stop.altitude, "Média das leituras enquanto parado")
                    RecordingSession.dismissStopSuggestion()
                }) { Text("Marcar ponto") }
            },
            dismissButton = {
                TextButton(onClick = RecordingSession::dismissStopSuggestion) { Text("Agora não") }
            },
        )
    }

    draft?.let { d ->
        val points = trail?.orderedPoints.orEmpty()
        NewPointDialog(
            draft = d,
            points = points,
            suggestedIndex = remember(d) { viewModel.suggestedIndex(d) },
            previewAt = { index -> viewModel.preview(d, index) },
            format = format,
            defaultName = remember(d) { viewModel.nextPointName() },
            onConfirm = { name, notes, photo, index ->
                viewModel.addPoint(d, name, notes, photo, index)
                draft = null
            },
            onDismiss = { draft = null },
        )
    }

    val selected = selectedPointId?.let { id -> trail?.points?.firstOrNull { it.id == id } }
    if (selected != null) {
        EditPointDialog(
            point = selected,
            format = format,
            declinationDegrees = trail?.trail?.declinationDegrees,
            onSave = { name, notes, photo ->
                viewModel.updatePoint(selected.id, name, notes, photo)
                selectedPointId = null
            },
            onDelete = {
                viewModel.deletePoint(selected.id)
                selectedPointId = null
            },
            onDismiss = { selectedPointId = null },
        )
    }
}

@Composable
private fun RecordingPanel(
    trail: TrailWithPoints,
    recording: RecordingState,
    recordingThis: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMarkPoint: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val points = trail.points
            val total = points.sumOf { it.distanceFromPreviousMeters ?: 0.0 }
            if (recordingThis) {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) { delay(1_000); now = System.currentTimeMillis() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FiberManualRecord, null, tint = Color(0xFFD32F2F))
                    Text(
                        " Gravando · ${elapsed(now - recording.startedAt)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                val fix = recording.lastFix
                Text(
                    (if (fix != null && fix.hasAccuracy()) "GPS ±${fix.accuracy.roundToInt()} m" else "Aguardando GPS…") +
                        " · ${recording.trackPoints} amostras · ${points.size} pontos · ${Formats.distance(total)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    if (recording.isRecording) "Outra trilha está sendo gravada" else "${points.size} pontos · ${Formats.distance(total)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Toque e segure no mapa para marcar um ponto em qualquer lugar.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recordingThis) {
                    FilledTonalButton(onClick = onStop) {
                        Icon(Icons.Filled.Stop, null)
                        Text(" Parar")
                    }
                } else {
                    FilledTonalButton(onClick = onStart, enabled = !recording.isRecording) {
                        Icon(Icons.Filled.FiberManualRecord, null)
                        Text(" Gravar")
                    }
                }
                Button(onClick = onMarkPoint, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.AddLocationAlt, null)
                    Text(" Marcar ponto")
                }
            }
        }
    }
}

private fun elapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
}

/** Mantém o ponto em edição se o Android recriar a tela (ex.: ao abrir a câmera). */
private val PointDraftSaver = listSaver<PointDraft?, Any?>(
    save = { d -> if (d == null) emptyList() else listOf(d.latitude, d.longitude, d.altitude, d.origin) },
    restore = { l ->
        if (l.isEmpty()) null else PointDraft(l[0] as Double, l[1] as Double, l[2] as Double?, l[3] as String)
    },
)

/** Toque longo no mapa: escolhe uma posição manualmente. */
@Composable
private fun MapLongPress(mapView: MapView, onLongPress: (GeoPoint) -> Unit) {
    val current by rememberUpdatedState(onLongPress)
    DisposableEffect(mapView) {
        val overlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?) = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let(current)
                return p != null
            }
        })
        mapView.overlays.add(0, overlay)
        onDispose { mapView.overlays.remove(overlay) }
    }
}
