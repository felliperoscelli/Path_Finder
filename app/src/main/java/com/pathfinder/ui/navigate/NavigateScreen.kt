package com.pathfinder.ui.navigate

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pathfinder.data.db.NavigationSessionEntity
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.geo.Formats
import com.pathfinder.geo.TimeOfDay
import com.pathfinder.navigation.NavigationRuntime
import com.pathfinder.navigation.NavigationState
import com.pathfinder.navigation.NavigationStatus
import com.pathfinder.PathFinderApp
import com.pathfinder.recording.RecordingSession
import com.pathfinder.ui.components.EmergencyButton
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.pathfinder.ui.components.TopoMap
import com.pathfinder.ui.components.centerOnMe
import com.pathfinder.ui.components.rememberLocationPermission
import com.pathfinder.ui.components.rememberMagneticHeading
import com.pathfinder.ui.components.rememberMyLocationOverlay
import com.pathfinder.ui.components.rememberTopoMapView
import com.pathfinder.ui.map.TrackLine
import com.pathfinder.ui.map.TrailLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.DateFormat
import java.util.Date
import android.graphics.Color as AndroidColor

private val OnRoute = Color(0xFF2E7D32)
private val Warning = Color(0xFFEF6C00)
private val Danger = Color(0xFFC62828)

/** Navegação do monitor: próximo ponto, direção, distância e alerta de desvio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigateScreen(
    trailId: Long,
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenResults: (Long) -> Unit,
    viewModel: NavigateViewModel = viewModel(key = "nav-$trailId", factory = NavigateViewModel.factory(trailId)),
) {
    val context = LocalContext.current
    val trail by viewModel.trail.collectAsStateWithLifecycle()
    val creationTrack by viewModel.creationTrack.collectAsStateWithLifecycle()
    val walked by viewModel.walkedTrack.collectAsStateWithLifecycle()
    val runtime by viewModel.runtime.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val recording by RecordingSession.state.collectAsStateWithLifecycle()
    val chat = remember { (context.applicationContext as PathFinderApp).container.chatRepository }
    val unread by chat.observeUnread().collectAsStateWithLifecycle(initialValue = 0)
    val latestUnread by chat.observeLatestUnread().collectAsStateWithLifecycle(initialValue = null)

    val mapView = rememberTopoMapView()
    val permission = rememberLocationPermission()
    val myLocation = rememberMyLocationOverlay(mapView, permission.granted)
    val trailLayer = remember(mapView) { TrailLayer(mapView, onPointClick = {}, onPointMoved = { _, _ -> }) }
    val walkedLine = remember(mapView) { TrackLine(mapView, AndroidColor.rgb(239, 108, 0), width = 7f) }
    val pointer = remember(mapView) { TrackLine(mapView, AndroidColor.rgb(46, 125, 50), width = 5f, dashed = true) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var framed by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }

    val activeHere = runtime.isActive && runtime.trailId == trailId
    val status = runtime.status.takeIf { activeHere }
    val challenge = trail?.trail?.challengeMode == true
    var confirmReturn by remember { mutableStateOf(false) }

    // Tela acesa durante a navegação (configurável).
    val view = LocalView.current
    DisposableEffect(activeHere, settings?.keepScreenOn) {
        view.keepScreenOn = activeHere && settings?.keepScreenOn != false
        onDispose { view.keepScreenOn = false }
    }

    var pendingSession by remember { mutableStateOf<Long?>(null) }
    // Ensaio: velocidade (0 = GPS real) e desvio forçado.
    var simSpeed by remember { mutableIntStateOf(0) }
    var simDeviation by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingSession?.let { NavigationRuntime.start(context, it, simSpeed, simDeviation) }
        pendingSession = null
    }
    fun launch(sessionId: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingSession = sessionId
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NavigationRuntime.start(context, sessionId, simSpeed, simDeviation)
        }
    }

    LaunchedEffect(Unit) { if (!permission.granted) permission.request() }
    LaunchedEffect(trail) {
        val points = trail?.orderedPoints ?: return@LaunchedEffect
        trailLayer.showPoints(points)
        if (!framed && points.isNotEmpty()) {
            trailLayer.zoomToTrail()
            framed = true
        }
    }
    LaunchedEffect(creationTrack, challenge) { trailLayer.showTrack(if (challenge) emptyList() else creationTrack) }
    LaunchedEffect(walked) { walkedLine.show(walked.map { GeoPoint(it.latitude, it.longitude) }) }
    LaunchedEffect(status?.nextIndex, status?.reached, challenge) {
        if (status != null) {
            trailLayer.showProgress(status.nextIndex, status.reached, hideUnreached = challenge)
        } else {
            trailLayer.showProgress(-1, emptySet(), hideUnreached = challenge)
        }
    }
    LaunchedEffect(runtime.lastFix, status?.nextIndex, status?.returningToBase) {
        val fix = runtime.lastFix
        val returning = status?.returningToBase == true
        val target = when {
            status == null -> null
            returning -> trail?.trail?.let { t ->
                if (t.baseLatitude != null && t.baseLongitude != null) GeoPoint(t.baseLatitude, t.baseLongitude)
                else trail?.orderedPoints?.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
            }
            challenge -> null // modo desafio: sem linha até o ponto
            else -> trail?.orderedPoints?.getOrNull(status.nextIndex)?.let { GeoPoint(it.latitude, it.longitude) }
        }
        if (activeHere && fix != null && target != null) {
            pointer.show(listOf(GeoPoint(fix.latitude, fix.longitude), target))
        } else {
            pointer.clear()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(trail?.trail?.name ?: "Navegação")
                        if (activeHere) Text("Grupo ${runtime.groupName}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    IconButton(onClick = onOpenMessages) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                            Icon(Icons.AutoMirrored.Filled.Message, "Mensagens")
                        }
                    }
                    IconButton(onClick = { trailLayer.zoomToTrail() }) {
                        Icon(Icons.Filled.CenterFocusStrong, "Ver trilha inteira")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when {
                    !permission.granted -> permission.request()
                    myLocation?.centerOnMe(mapView) != true -> scope.launch { snackbar.showSnackbar("Aguardando sinal do GPS…") }
                }
            }) { Icon(Icons.Filled.MyLocation, "Minha localização") }
        },
        bottomBar = {
            val t = trail
            when {
                t == null -> Unit
                activeHere && status != null -> NavigationControls(
                    status = status,
                    onBack = { NavigationRuntime.back(context) },
                    onSkip = { NavigationRuntime.skip(context) },
                    onReturn = { confirmReturn = true },
                    onResume = { NavigationRuntime.resumeTrail(context) },
                    onFinish = { confirmFinish = true },
                )
                else -> StartPanel(
                    trail = t,
                    defaultGroup = settings?.groupName.orEmpty(),
                    defaultMonitor = settings?.userName.orEmpty(),
                    blockedReason = when {
                        t.points.isEmpty() -> "A trilha não tem pontos."
                        recording.isRecording -> "Pare a gravação de trilha antes de navegar."
                        runtime.isActive -> "Já existe uma navegação em andamento em outra trilha."
                        !permission.granted -> "Permita o acesso à localização para navegar."
                        else -> null
                    },
                    sessions = sessions,
                    onStart = { group, monitor ->
                        simSpeed = 0
                        viewModel.createSession(group, monitor, ::launch)
                    },
                    allowSimulation = settings?.allowSimulation == true,
                    onSimulate = { group, monitor, speed, deviation ->
                        simSpeed = speed
                        simDeviation = deviation
                        viewModel.createSession("[SIM] $group", monitor, ::launch)
                    },
                    onOpenSession = onOpenSession,
                    onOpenResults = { onOpenResults(trailId) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            TopoMap(mapView, Modifier.fillMaxSize())
            if (status != null && trail != null) {
                Column(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (runtime.simulated) Banner(Color(0xFF6A1B9A), "ENSAIO: posição simulada, não é o GPS real.")
                    NextPointCard(trail!!, status, runtime, challenge)
                    if (!status.returningToBase) RouteStatusBanner(status, onSilence = { NavigationRuntime.silence(context) })
                    DeadlineBanner(runtime, status, onReturn = { confirmReturn = true })
                    runtime.batteryPercent?.takeIf { it <= LOW_BATTERY }?.let { b ->
                        Banner(Warning, "Bateria baixa ($b%): diminua o brilho da tela ou ligue o power bank.")
                    }
                    latestUnread?.let { m ->
                        // Última mensagem não lida (ex.: resposta do coordenador).
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            shadowElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenMessages),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(m.fromName, style = MaterialTheme.typography.labelMedium)
                                Text(m.text, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (activeHere) {
                EmergencyButton(Modifier.align(Alignment.BottomStart).padding(12.dp), compact = true)
            }
        }
    }

    if (confirmReturn) {
        AlertDialog(
            onDismissRequest = { confirmReturn = false },
            title = { Text("Voltar à base?") },
            text = { Text("O app passa a guiar até a base, e o alerta de desvio fica pausado. Dá para retomar a trilha depois.") },
            confirmButton = {
                TextButton(onClick = { confirmReturn = false; NavigationRuntime.returnToBase(context) }) { Text("Voltar à base") }
            },
            dismissButton = { TextButton(onClick = { confirmReturn = false }) { Text("Cancelar") } },
        )
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Encerrar navegação?") },
            text = { Text("O trajeto percorrido fica salvo para revisão.") },
            confirmButton = {
                TextButton(onClick = { confirmFinish = false; NavigationRuntime.stop(context) }) { Text("Encerrar") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Continuar") } },
        )
    }
}

@Composable
private fun NextPointCard(trail: TrailWithPoints, status: NavigationStatus, runtime: NavigationState, challenge: Boolean) {
    val points = trail.orderedPoints
    val heading by rememberMagneticHeading()
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp, shadowElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (status.returningToBase) {
                    Text(
                        if (status.arrivedAtBase) "Na base" else "Voltando à base",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (status.arrivedAtBase) OnRoute else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(status.distanceToNextMeters?.let(Formats::distance) ?: "Aguardando GPS…", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    status.magneticBearingToNext?.let {
                        Text(
                            "Azimute magnético ${Formats.azimuth(it)} · verdadeiro ${Formats.azimuth(status.trueBearingToNext)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    GpsAndBattery(runtime)
                    return@Column
                }
                if (status.finished) {
                    Text("Pista concluída!", style = MaterialTheme.typography.titleLarge, color = OnRoute)
                    Text("${status.reached.size} de ${points.size} pontos alcançados")
                    return@Column
                }
                val next = points.getOrNull(status.nextIndex)
                Text(
                    "Próximo: ${status.nextIndex + 1}. ${next?.name.orEmpty()}  (${status.nextIndex + 1} de ${points.size})",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (challenge) {
                    Text("Modo desafio", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Use a bússola e a ficha. O app avisa ao chegar no ponto e se o grupo sair da rota.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    GpsAndBattery(runtime)
                    return@Column
                }
                Text(
                    status.distanceToNextMeters?.let(Formats::distance) ?: "Aguardando GPS…",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (status.magneticBearingToNext != null) {
                    Text(
                        "Azimute magnético ${Formats.azimuth(status.magneticBearingToNext)} · " +
                            "verdadeiro ${Formats.azimuth(status.trueBearingToNext)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                GpsAndBattery(runtime)
            }
            // Seta apontando para o próximo ponto, relativa ao topo do celular.
            val bearing = status.magneticBearingToNext
            val h = heading
            val showArrow = status.returningToBase || (!status.finished && !challenge)
            if (showArrow && bearing != null && h != null) {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = "Direção do próximo ponto",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp).rotate((bearing - h).toFloat()),
                )
            }
        }
    }
}

@Composable
private fun RouteStatusBanner(status: NavigationStatus, onSilence: () -> Unit) {
    if (status.finished) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1_000); now = System.currentTimeMillis() }
    }
    val since = status.offRouteSince
    val distance = status.distanceToRouteMeters?.let(Formats::distance)
    val (color, text) = when {
        !status.monitoring -> MaterialTheme.colorScheme.secondary to
            "Vá até o ponto de partida. O alerta de desvio começa ao chegar nele."
        status.alarmActive -> Danger to "FORA DA ROTA há ${elapsed(now - since!!)} · $distance da rota"
        since != null -> Warning to "Afastado da rota há ${elapsed(now - since)} · $distance da rota"
        else -> OnRoute to "Na rota" + (distance?.let { " · $it da linha" } ?: "")
    }
    Surface(color = color, contentColor = Color.White, shape = MaterialTheme.shapes.medium, shadowElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), fontWeight = if (status.alarmActive) FontWeight.Bold else FontWeight.Normal)
            if (status.alarmActive && !status.silenced) {
                FilledTonalButton(onClick = onSilence) {
                    Icon(Icons.Filled.NotificationsOff, null)
                    Text(" Silenciar")
                }
            }
        }
    }
}

@Composable
private fun NavigationControls(
    status: NavigationStatus,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onReturn: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status.returningToBase) {
                OutlinedButton(onClick = onResume) {
                    Icon(Icons.Filled.Route, null)
                    Text(" Retomar trilha")
                }
            } else {
                OutlinedIconButton(onClick = onBack, enabled = status.nextIndex > 0) {
                    Icon(Icons.Filled.SkipPrevious, "Ponto anterior")
                }
                OutlinedIconButton(onClick = onSkip, enabled = !status.finished) {
                    Icon(Icons.Filled.SkipNext, "Pular ponto")
                }
                OutlinedButton(onClick = onReturn) {
                    Icon(Icons.Filled.Home, null)
                    Text(" Base")
                }
            }
            Button(
                onClick = onFinish,
                modifier = Modifier.weight(1f),
                colors = if (status.finished || status.arrivedAtBase) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = Danger),
            ) {
                Icon(Icons.Filled.Flag, null)
                Text(" Encerrar")
            }
        }
    }
}

@Composable
private fun GpsAndBattery(runtime: NavigationState) {
    val parts = listOfNotNull(
        runtime.lastFix?.takeIf { it.hasAccuracy() }?.let { "GPS ±${it.accuracy.toInt()} m" },
        runtime.batteryPercent?.let { "bateria $it%" },
        "economia de bateria".takeIf { runtime.powerSaving },
    )
    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Banner(color: Color, text: String, action: (@Composable () -> Unit)? = null) {
    Surface(color = color, contentColor = Color.White, shape = MaterialTheme.shapes.medium, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, Modifier.weight(1f))
            action?.invoke()
        }
    }
}

/** Hora limite: informa, avisa 30 min antes e, na hora, sugere voltar à base. */
@Composable
private fun DeadlineBanner(runtime: NavigationState, status: NavigationStatus, onReturn: () -> Unit) {
    val deadline = runtime.deadlineAt ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(deadline))
    val left = (deadline - now) / 60_000
    when {
        now >= deadline && !status.returningToBase -> Banner(Danger, "Hora limite ($time)! Voltem à base.") {
            FilledTonalButton(onClick = onReturn) { Text("Voltar") }
        }
        now >= deadline -> Unit
        left <= 30 -> Banner(Warning, "Faltam $left min para a hora limite ($time).")
        else -> Text(
            "Hora limite: $time",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun StartPanel(
    trail: TrailWithPoints,
    defaultGroup: String,
    defaultMonitor: String,
    blockedReason: String?,
    sessions: List<NavigationSessionEntity>,
    onStart: (group: String, monitor: String) -> Unit,
    allowSimulation: Boolean,
    onSimulate: (group: String, monitor: String, speed: Int, deviation: Boolean) -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenResults: () -> Unit,
) {
    var group by rememberSaveable(defaultGroup) { mutableStateOf(defaultGroup) }
    var monitor by rememberSaveable(defaultMonitor) { mutableStateOf(defaultMonitor) }
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Iniciar navegação", style = MaterialTheme.typography.titleMedium)
            Text(
                "${trail.points.size} pontos · alerta se ficar a mais de ${trail.trail.deviationDistanceMeters} m da rota " +
                    "por ${trail.trail.deviationTimeSeconds / 60} min",
                style = MaterialTheme.typography.bodySmall,
            )
            val extras = listOfNotNull(
                trail.trail.deadlineMinutes?.let { "Hora limite ${TimeOfDay.format(it)}" },
                "Modo desafio: sem distância nem direção dos pontos".takeIf { trail.trail.challengeMode },
            )
            if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(group, { group = it }, label = { Text("Grupo") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(monitor, { monitor = it }, label = { Text("Monitor") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            if (blockedReason != null) {
                Text(blockedReason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { onStart(group, monitor) },
                enabled = blockedReason == null && group.isNotBlank() && monitor.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Navigation, null)
                Text(" Começar")
            }
            if (allowSimulation) {
                var speed by remember { mutableIntStateOf(5) }
                var deviation by remember { mutableStateOf(true) }
                HorizontalDivider()
                Text("Ensaio (caminhada simulada)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Percorre a trilha sem sair do lugar, para testar alarmes, rede e painel. O grupo aparece como [SIM].",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 5, 20).forEach { v ->
                        FilterChip(selected = speed == v, onClick = { speed = v }, label = { Text("${v}x") })
                    }
                    FilterChip(selected = deviation, onClick = { deviation = !deviation }, label = { Text("Forçar desvio") })
                }
                OutlinedButton(
                    onClick = { onSimulate(group, monitor, speed, deviation) },
                    enabled = blockedReason == null && group.isNotBlank() && monitor.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ensaiar") }
            }
            if (sessions.isNotEmpty()) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Percursos anteriores", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenResults) { Text("Resultado") }
                }
                sessions.forEach { s ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenSession(s.id) },
                        headlineContent = { Text("${s.groupName} · ${s.monitorName}") },
                        supportingContent = {
                            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.startedAt))
                            val state = if (s.endedAt == null) "em andamento" else "${s.reachedSet.size}/${trail.points.size} pontos"
                            Text("$date · $state · ${s.deviationAlerts} alertas")
                        },
                    )
                }
            }
        }
    }
}

private const val LOW_BATTERY = 20

private fun elapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
