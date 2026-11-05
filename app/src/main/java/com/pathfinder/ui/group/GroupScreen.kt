package com.pathfinder.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathfinder.PathFinderApp
import com.pathfinder.data.settings.UserRole
import com.pathfinder.net.MeshService
import com.pathfinder.ui.components.rememberNearbyPermissions

/** Parte de grupo do app: rede local, painel do coordenador e navegação em grupo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    onBack: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenTrails: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PathFinderApp).container }
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val role = settings?.role

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grupo") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (role == UserRole.REPEATER) RepeaterPanel(onOpenNetwork) else NetworkCard()
            }
            if (role == UserRole.COORDINATOR) {
                item {
                    GroupAction(Icons.Filled.Dashboard, "Painel do coordenador", "Posição e situação de cada grupo", onOpenDashboard)
                }
            }
            item { GroupAction(Icons.Filled.Hub, "Rede local", "Aparelhos por perto, alcance e repasses", onOpenNetwork) }
            item { GroupAction(Icons.Filled.Explore, "Navegar em grupo", "Escolha a trilha na lista", onOpenTrails) }
        }
    }
}

/** Estado da rede local, com o botão de ligar e desligar. */
@Composable
private fun NetworkCard() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PathFinderApp).container }
    val network by container.nearby.state.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val permissions = rememberNearbyPermissions(onGranted = { MeshService.start(context) })
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rede local", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (network.running) "Ligada · ${network.connected.size} aparelho(s) conectado(s)" else "Desligada",
                        style = MaterialTheme.typography.bodyMedium,
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
            settings?.let { s ->
                Text(
                    if (s.eventCode.isBlank()) "Sem código de evento: rede aberta (defina em Configurações)."
                    else "Código do evento: ${s.eventCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.eventCode.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            network.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun GroupAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { Icon(icon, null) },
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        )
    }
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
        modifier = Modifier.fillMaxWidth(),
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
