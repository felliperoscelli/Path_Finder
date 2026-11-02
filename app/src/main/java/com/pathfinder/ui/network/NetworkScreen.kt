package com.pathfinder.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathfinder.PathFinderApp
import com.pathfinder.data.settings.UserRole
import com.pathfinder.geo.Formats
import com.pathfinder.net.MeshService
import com.pathfinder.net.MeshStats
import com.pathfinder.net.MonitorInfo
import com.pathfinder.net.Peer
import com.pathfinder.net.PeerStatus
import com.pathfinder.ui.components.rememberNearbyPermissions
import kotlinx.coroutines.delay

/** Rede local: liga/desliga, aparelhos conectados e situação dos monitores. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PathFinderApp).container }
    val network by container.nearby.state.collectAsStateWithLifecycle()
    val board by container.networkHub.board.collectAsStateWithLifecycle()
    val mesh by container.mesh.stats.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val permissions = rememberNearbyPermissions(onGranted = { MeshService.start(context) })
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rede local") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Rede sem internet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Bluetooth e Wi-Fi Direct, alcance de 100 a 200 m em campo aberto.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                checked = network.running,
                                onCheckedChange = { on ->
                                    when {
                                        !on -> MeshService.stop(context)
                                        permissions.granted -> MeshService.start(context)
                                        else -> permissions.request()
                                    }
                                },
                            )
                        }
                        val s = settings
                        if (s != null) {
                            Text(
                                "Você aparece como: ${s.userName.ifBlank { "Sem nome" }} (${s.role.label})",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (s.userName.isBlank()) {
                                Text(
                                    "Defina seu nome em Configurações para os outros aparelhos reconhecerem você.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        network.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (board.activeAlerts.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFC62828), contentColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            board.activeAlerts.forEach { a ->
                                Text("${a.groupName} fora da rota", fontWeight = FontWeight.Bold)
                                Text(
                                    "${a.monitorName} · há ${minutes(now - a.since)} · ${Formats.distance(a.distanceMeters)} da rota · " +
                                        Formats.latLon(a.latitude, a.longitude),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Button(
                                onClick = { container.networkHub.silenceAlerts() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFC62828)),
                            ) {
                                Icon(Icons.Filled.NotificationsOff, null)
                                Text(" Silenciar alarme")
                            }
                        }
                    }
                }
            }

            item { MeshStatsCard(mesh) }

            if (network.running) {
                item { Section("Aparelhos (${network.connected.size} conectados)") }
                if (network.peers.isEmpty()) {
                    item { Text("Procurando aparelhos com o Path Finder e a rede ligada…", style = MaterialTheme.typography.bodyMedium) }
                }
                items(network.peers.values.sortedBy { it.name }, key = { it.endpointId }) { PeerRow(it) }
            }

            if (board.monitors.isNotEmpty() || settings?.role == UserRole.COORDINATOR) {
                item { Section("Grupos em navegação") }
                if (board.monitors.isEmpty()) {
                    item {
                        Text(
                            "As posições dos monitores aparecem aqui quando eles estiverem navegando com a rede ligada.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(board.monitors.values.sortedBy { it.groupName }, key = { it.deviceId }) { MonitorRow(it, now) }
            }
        }
    }
}

/** Números da malha: o que está guardado para entregar e o que foi repassado. */
@Composable
fun MeshStatsCard(stats: MeshStats) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Rede em malha", style = MaterialTheme.typography.titleSmall)
            Text(
                "Cada aparelho repassa as mensagens dos outros. O que não puder ser entregue fica " +
                    "guardado e sai quando outro aparelho se conectar. Alertas passam na frente.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Guardadas: ${stats.stored} · criadas aqui: ${stats.originated} · recebidas: ${stats.received} · " +
                    "repassadas: ${stats.relayed} · enviadas: ${stats.sent}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PeerRow(peer: Peer) {
    val role = UserRole.entries.firstOrNull { it.name == peer.role }?.label ?: peer.role
    ListItem(
        leadingContent = { Icon(Icons.Filled.PhoneAndroid, null) },
        headlineContent = { Text(peer.name) },
        supportingContent = { Text(role) },
        trailingContent = {
            Text(
                when (peer.status) {
                    PeerStatus.CONNECTED -> "Conectado"
                    PeerStatus.CONNECTING -> "Conectando…"
                    PeerStatus.FOUND -> "Encontrado"
                },
                color = if (peer.status == PeerStatus.CONNECTED) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun MonitorRow(m: MonitorInfo, now: Long) {
    val color = when {
        m.alarm -> Color(0xFFC62828)
        m.offRouteSince != null -> Color(0xFFEF6C00)
        else -> Color(0xFF2E7D32)
    }
    ListItem(
        leadingContent = { Icon(Icons.Filled.Groups, null, tint = color) },
        headlineContent = { Text("${m.groupName} · ${m.monitorName}") },
        supportingContent = {
            Column {
                val progress = if (m.finished) "pista concluída" else "próximo ${m.nextIndex + 1} de ${m.totalPoints}"
                Text("${m.trailName} · $progress" + (m.lastReachedName?.let { " · último: $it" } ?: ""))
                Text(
                    "Atualizado há ${minutes(now - m.updatedAt)}" +
                        (m.distanceToRouteMeters?.let { " · ${Formats.distance(it)} da rota" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        trailingContent = {
            Text(
                when {
                    m.alarm -> "FORA DA ROTA"
                    m.offRouteSince != null -> "Afastado"
                    else -> "Na rota"
                },
                color = color,
                fontWeight = FontWeight.Bold,
            )
        },
    )
}

private fun minutes(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s < 60) "$s s" else "${s / 60} min"
}
