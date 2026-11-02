package com.pathfinder.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pathfinder.PathFinderApp
import com.pathfinder.data.db.TrailSummary
import com.pathfinder.net.MeshService
import com.pathfinder.ui.components.rememberNearbyPermissions
import kotlinx.coroutines.launch
import com.pathfinder.data.settings.UserRole
import com.pathfinder.navigation.NavigationRuntime
import com.pathfinder.recording.RecordingSession
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTrail: (Long) -> Unit,
    onNavigateTrail: (Long) -> Unit,
    onOpenMap: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenDashboard: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val trails by viewModel.trailList.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val recording by RecordingSession.state.collectAsStateWithLifecycle()
    val navigation by NavigationRuntime.state.collectAsStateWithLifecycle()
    var showNewTrail by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val network by remember { (context.applicationContext as PathFinderApp).container.nearby.state }
        .collectAsStateWithLifecycle()
    val unread by remember { (context.applicationContext as PathFinderApp).container.chatRepository.observeUnread() }
        .collectAsStateWithLifecycle(initialValue = 0)
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importFile(uri) { result ->
                result.onSuccess(onOpenTrail).onFailure {
                    scope.launch { snackbar.showSnackbar("Não foi possível importar: ${it.message}") }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Path Finder")
                        val who = settings.userName.ifBlank { "Sem nome" }
                        Text(
                            "$who · ${settings.role.label}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = {
                    if (settings.role == UserRole.COORDINATOR) {
                        IconButton(onClick = onOpenDashboard) { Icon(Icons.Filled.Dashboard, "Painel") }
                    }
                    if (settings.role != UserRole.REPEATER) {
                        IconButton(onClick = onOpenMessages) {
                            BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                                Icon(Icons.AutoMirrored.Filled.Message, "Mensagens")
                            }
                        }
                    }
                    IconButton(onClick = onOpenNetwork) {
                        BadgedBox(badge = {
                            if (network.running) Badge { Text("${network.connected.size}") }
                        }) { Icon(Icons.Filled.Hub, "Rede local") }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais opções") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Importar trilha (.json/.gpx)") },
                                leadingIcon = { Icon(Icons.Filled.FileOpen, null) },
                                onClick = { menu = false; importer.launch(arrayOf("*/*")) },
                            )
                            DropdownMenuItem(
                                text = { Text("Mapa") },
                                leadingIcon = { Icon(Icons.Filled.Map, null) },
                                onClick = { menu = false; onOpenMap() },
                            )
                            DropdownMenuItem(
                                text = { Text("Mapas offline") },
                                leadingIcon = { Icon(Icons.Filled.Download, null) },
                                onClick = { menu = false; onOpenOfflineMaps() },
                            )
                            DropdownMenuItem(
                                text = { Text("Configurações") },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = { menu = false; onOpenSettings() },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewTrail = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nova trilha") },
            )
        },
    ) { padding ->
        val list = trails
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() && settings.role != UserRole.REPEATER -> EmptyState(Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                if (settings.role == UserRole.REPEATER) {
                    item { RepeaterPanel(onOpenNetwork) }
                }
                items(list, key = { it.trail.id }) { summary ->
                    TrailRow(
                        summary = summary,
                        recording = recording.trailId == summary.trail.id,
                        navigating = navigation.trailId == summary.trail.id,
                        // Monitor vai direto para a navegação; coordenador, para o mapa de edição.
                        onClick = {
                            if (settings.role == UserRole.MONITOR) onNavigateTrail(summary.trail.id)
                            else onOpenTrail(summary.trail.id)
                        },
                        onNavigate = { onNavigateTrail(summary.trail.id) },
                        onDelete = { pendingDelete = summary.trail.id },
                    )
                }
            }
        }
    }

    if (showNewTrail) {
        NewTrailDialog(
            onDismiss = { showNewTrail = false },
            onConfirm = { name ->
                showNewTrail = false
                viewModel.createTrail(name, onOpenTrail)
            },
        )
    }

    pendingDelete?.let { id ->
        val name = trails?.firstOrNull { it.trail.id == id }?.trail?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Apagar trilha?") },
            text = { Text("\"$name\" e todos os seus pontos serão apagados.") },
            confirmButton = {
                TextButton(onClick = {
                    if (recording.trailId == id) RecordingSession.stop(context)
                    if (navigation.trailId == id) NavigationRuntime.stop(context)
                    viewModel.deleteTrail(id)
                    pendingDelete = null
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TrailRow(
    summary: TrailSummary,
    recording: Boolean,
    navigating: Boolean,
    onClick: () -> Unit,
    onNavigate: () -> Unit,
    onDelete: () -> Unit,
) {
    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(summary.trail.updatedAt))
    val points = when (summary.pointCount) {
        0 -> "sem pontos"
        1 -> "1 ponto"
        else -> "${summary.pointCount} pontos"
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.Filled.Route, null) },
        headlineContent = { Text(summary.trail.name) },
        supportingContent = {
            Text(
                when {
                    recording -> "● Gravando · $points"
                    navigating -> "● Navegando · $points"
                    else -> "$points · $date"
                }
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onNavigate, enabled = summary.pointCount > 0) {
                    Icon(Icons.Filled.Explore, "Navegar")
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Apagar") }
            }
        },
    )
}

/** Repetidor: celular fixo num ponto intermediário, ligado a um power bank. */
@Composable
private fun RepeaterPanel(onOpenNetwork: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PathFinderApp).container }
    val network by container.nearby.state.collectAsStateWithLifecycle()
    val stats by container.mesh.stats.collectAsStateWithLifecycle()
    val permissions = rememberNearbyPermissions(onGranted = { MeshService.start(context) })
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Modo repetidor", style = MaterialTheme.typography.titleLarge)
            Text(
                "Deixe este celular num ponto intermediário da pista, ligado a um power bank. " +
                    "Ele não usa o GPS: só repassa as mensagens e posições entre os outros aparelhos.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (network.running) "Rede ligada · ${network.connected.size} aparelho(s) conectado(s)" else "Rede desligada",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Guardadas: ${stats.stored} · repassadas: ${stats.relayed} · enviadas: ${stats.sent}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    when {
                        network.running -> MeshService.stop(context)
                        permissions.granted -> MeshService.start(context)
                        else -> permissions.request()
                    }
                }) { Text(if (network.running) "Desligar rede" else "Ligar rede") }
                OutlinedButton(onClick = onOpenNetwork) { Text("Detalhes") }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Route, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Nenhuma trilha ainda", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Toque em \"Nova trilha\" para criar a primeira pista. " +
                "Antes do evento, baixe a área do mapa em \"Mapas offline\".",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun NewTrailDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova trilha") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome da pista") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
