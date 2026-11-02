package com.pathfinder.ui.dashboard

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pathfinder.data.ChatRepository
import com.pathfinder.data.TrailRepository
import com.pathfinder.data.db.TrackPointEntity
import com.pathfinder.data.db.TrackSource
import com.pathfinder.data.db.TrailSummary
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.geo.Formats
import com.pathfinder.net.BoardState
import com.pathfinder.net.MonitorInfo
import com.pathfinder.net.NetworkHub
import com.pathfinder.net.NetworkState
import com.pathfinder.net.QuickMessages
import com.pathfinder.ui.appContainer
import com.pathfinder.ui.components.EmergencyRed
import com.pathfinder.ui.components.TopoMap
import com.pathfinder.ui.components.rememberTopoMapView
import com.pathfinder.ui.map.TrailLayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val OnRoute = Color(0xFF2E7D32)
private val Warning = Color(0xFFEF6C00)
private val Stale = Color(0xFF757575)

/** Sem notícias há mais que isso, o grupo aparece em cinza. */
private const val STALE_MS = 3 * 60_000L

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    trails: TrailRepository,
    chat: ChatRepository,
    val hub: NetworkHub,
) : ViewModel() {
    val board: StateFlow<BoardState> = hub.board
    val network: StateFlow<NetworkState> = hub.nearby.state
    val unread: StateFlow<Int> = chat.observeUnread().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val trailList: StateFlow<List<TrailSummary>> = trails.observeTrails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Trilha escolhida pelo coordenador (null = automática). */
    private val chosen = MutableStateFlow<Long?>(null)

    /** A escolhida ou, se não houver, a que os grupos estão navegando (ou a mais recente). */
    val selectedTrailId: StateFlow<Long?> = combine(chosen, trailList, hub.board) { c, list, b ->
        c ?: b.monitors.values.firstNotNullOfOrNull { m -> list.firstOrNull { it.trail.uuid == m.trailUuid }?.trail?.id }
            ?: list.firstOrNull()?.trail?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trail: StateFlow<TrailWithPoints?> = selectedTrailId
        .flatMapLatest { id -> if (id == null) flowOf(null) else trails.observeTrail(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val track: StateFlow<List<TrackPointEntity>> = selectedTrailId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else trails.observeTrack(id, TrackSource.CREATION) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun chooseTrail(id: Long) {
        chosen.value = id
    }

    fun reply(deviceId: String, text: String) {
        viewModelScope.launch { hub.sendText(text, to = deviceId) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                DashboardViewModel(c.trailRepository, c.chatRepository, c.networkHub)
            }
        }
    }
}

/** Alguém a quem responder a partir de um alerta. */
private data class ReplyTarget(val deviceId: String, val title: String)

/** Painel do coordenador: mapa com os grupos, alertas no topo e lista de grupos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    onOpenMessages: (String?) -> Unit,
    onOpenNetwork: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val board by viewModel.board.collectAsStateWithLifecycle()
    val network by viewModel.network.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    val trails by viewModel.trailList.collectAsStateWithLifecycle()
    val trail by viewModel.trail.collectAsStateWithLifecycle()
    val track by viewModel.track.collectAsStateWithLifecycle()
    val mapView = rememberTopoMapView()
    val trailLayer = remember(mapView) { TrailLayer(mapView, onPointClick = {}, onPointMoved = { _, _ -> }) }
    var framedTrailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var replyTo by remember { mutableStateOf<ReplyTarget?>(null) }
    var trailMenu by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); now = System.currentTimeMillis() } }

    LaunchedEffect(trail) {
        val t = trail ?: return@LaunchedEffect
        trailLayer.showPoints(t.orderedPoints)
        if (framedTrailId != t.trail.id) {
            trailLayer.zoomToTrail()
            framedTrailId = t.trail.id
        }
    }
    LaunchedEffect(track) { trailLayer.showTrack(track) }
    GroupMarkers(mapView, board, now)

    fun center(lat: Double?, lon: Double?) {
        if (lat == null || lon == null) return
        mapView.controller.animateTo(GeoPoint(lat, lon), maxOf(mapView.zoomLevelDouble, 16.0), null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Painel") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    // Indicador do modo de rede e de aparelhos conectados.
                    AssistChip(
                        onClick = onOpenNetwork,
                        label = { Text(if (network.running) "Malha · ${network.connected.size}" else "Rede desligada") },
                        leadingIcon = { Icon(Icons.Filled.Hub, null, Modifier.size(18.dp)) },
                    )
                    Box {
                        IconButton(onClick = { trailMenu = true }) { Icon(Icons.Filled.Route, "Escolher trilha") }
                        DropdownMenu(expanded = trailMenu, onDismissRequest = { trailMenu = false }) {
                            trails.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.trail.name) },
                                    onClick = { viewModel.chooseTrail(s.trail.id); trailMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { onOpenMessages(null) }) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                            Icon(Icons.AutoMirrored.Filled.Message, "Mensagens")
                        }
                    }
                },
            )
        },
        bottomBar = {
            GroupList(board, now, onSelect = { center(it.latitude, it.longitude) })
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            TopoMap(mapView, Modifier.fillMaxSize())
            IconButton(
                onClick = { trailLayer.zoomToTrail() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) { Icon(Icons.Filled.CenterFocusStrong, "Ver trilha inteira") }
            AlertsArea(
                board = board,
                now = now,
                trailName = trail?.trail?.name,
                onCenter = ::center,
                onReply = { replyTo = it },
                onAcknowledge = viewModel.hub::acknowledgeEmergency,
                onSilence = viewModel.hub::silenceAlerts,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    replyTo?.let { target ->
        QuickReplyDialog(
            target = target,
            onSend = { text -> viewModel.reply(target.deviceId, text); replyTo = null },
            onOpenChat = { replyTo = null; onOpenMessages(target.deviceId) },
            onDismiss = { replyTo = null },
        )
    }
}

/** Alertas no topo: emergências e grupos fora da rota, com resposta rápida. */
@Composable
private fun AlertsArea(
    board: BoardState,
    now: Long,
    trailName: String?,
    onCenter: (Double?, Double?) -> Unit,
    onReply: (ReplyTarget) -> Unit,
    onAcknowledge: (String) -> Unit,
    onSilence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emergencies = board.openEmergencies
    val deviations = board.activeAlerts
    if (emergencies.isEmpty() && deviations.isEmpty()) return
    Column(
        modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        emergencies.forEach { e ->
            val who = e.groupName?.let { "$it · ${e.name}" } ?: e.name
            AlertCard(
                title = "EMERGÊNCIA – $who",
                details = listOfNotNull(
                    "Há ${elapsed(now - e.time)}",
                    if (e.latitude != null && e.longitude != null) "posição ${Formats.latLon(e.latitude, e.longitude)}" else "sem posição",
                    e.trailName,
                ).joinToString(" · "),
                color = EmergencyRed,
                onCenter = { onCenter(e.latitude, e.longitude) },
                onReply = { onReply(ReplyTarget(e.deviceId, "Responder a $who")) },
                extra = { TextButton(onClick = { onAcknowledge(e.messageId) }) { Text("Ciente", color = Color.White) } },
            )
        }
        deviations.forEach { a ->
            val who = "${a.groupName} · ${a.monitorName}"
            AlertCard(
                title = "Fora da rota – $who",
                details = "Há ${elapsed(now - a.since)} fora da rota · ${Formats.distance(a.distanceMeters)} · " +
                    "última posição ${Formats.latLon(a.latitude, a.longitude)}" +
                    (if (a.trailName != trailName) " · ${a.trailName}" else ""),
                color = Warning,
                onCenter = { onCenter(a.latitude, a.longitude) },
                onReply = { onReply(ReplyTarget(a.deviceId, "Responder a $who")) },
                extra = { TextButton(onClick = onSilence) { Text("Silenciar", color = Color.White) } },
            )
        }
    }
}

@Composable
private fun AlertCard(
    title: String,
    details: String,
    color: Color,
    onCenter: () -> Unit,
    onReply: () -> Unit,
    extra: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = color, contentColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(details, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = onCenter) { Text("Ver no mapa", color = Color.White) }
                TextButton(onClick = onReply) { Text("Responder", color = Color.White) }
                extra()
            }
        }
    }
}

@Composable
private fun QuickReplyDialog(target: ReplyTarget, onSend: (String) -> Unit, onOpenChat: () -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickMessages.coordinator.forEach { q ->
                    OutlinedButton(onClick = { onSend(q.text) }, modifier = Modifier.fillMaxWidth()) { Text(q.text) }
                }
                OutlinedTextField(custom, { custom = it.take(500) }, label = { Text("Outra mensagem") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSend(custom) }, enabled = custom.isNotBlank()) { Text("Enviar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenChat) { Text("Conversa") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun GroupList(board: BoardState, now: Long, onSelect: (MonitorInfo) -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            if (board.monitors.isEmpty()) {
                Text(
                    "Nenhum grupo enviou posição ainda. Os grupos aparecem aqui quando os monitores navegarem com a rede ligada.",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            board.monitors.values.sortedBy { it.groupName }.forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(m) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(statusColor(m, now)))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("${m.groupName} · ${m.monitorName}", fontWeight = FontWeight.SemiBold)
                        val progress = if (m.finished) "concluiu" else "${m.nextIndex}/${m.totalPoints} pontos"
                        Text(
                            "Último: ${m.lastReachedName ?: "—"} · $progress · há ${elapsed(now - m.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        when {
                            m.alarm -> "FORA"
                            m.offRouteSince != null -> "Afastado"
                            now - m.updatedAt > STALE_MS -> "Sem sinal"
                            else -> "Na rota"
                        },
                        color = statusColor(m, now),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** Ícone de cada grupo no mapa, com nome e cor do estado. */
@Composable
private fun GroupMarkers(mapView: MapView, board: BoardState, now: Long) {
    val context = LocalContext.current
    val markers = remember(mapView) { mutableMapOf<String, Marker>() }
    LaunchedEffect(board, now) {
        val ids = board.monitors.keys
        markers.keys.filter { it !in ids }.forEach { mapView.overlays.remove(markers.remove(it)) }
        board.monitors.values.forEach { m ->
            val marker = markers.getOrPut(m.deviceId) {
                Marker(mapView).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    mapView.overlays.add(this)
                }
            }
            marker.position = GeoPoint(m.latitude, m.longitude)
            marker.icon = GroupMarkerIcon.create(context, m.groupName, statusColor(m, now).toArgb())
            marker.title = "${m.groupName} · ${m.monitorName}"
            marker.snippet = "Último ponto: ${m.lastReachedName ?: "—"} · há ${elapsed(now - m.updatedAt)}" +
                (m.distanceToRouteMeters?.let { " · ${Formats.distance(it)} da rota" } ?: "")
        }
        mapView.invalidate()
    }
    DisposableEffect(mapView) {
        onDispose { markers.values.forEach { mapView.overlays.remove(it) } }
    }
}

private fun statusColor(m: MonitorInfo, now: Long) = when {
    m.alarm -> EmergencyRed
    m.offRouteSince != null -> Warning
    now - m.updatedAt > STALE_MS -> Stale
    else -> OnRoute
}

private fun elapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "$s s"
        s < 3600 -> "${s / 60} min"
        else -> "%dh%02d".format(s / 3600, (s / 60) % 60)
    }
}
