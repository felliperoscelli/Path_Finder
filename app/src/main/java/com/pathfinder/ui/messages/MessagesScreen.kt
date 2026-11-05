package com.pathfinder.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathfinder.PathFinderApp
import com.pathfinder.data.db.ChatMessageEntity
import com.pathfinder.data.settings.UserRole
import com.pathfinder.geo.Formats
import com.pathfinder.location.CurrentLocation
import com.pathfinder.net.Contact
import com.pathfinder.net.QuickMessage
import com.pathfinder.net.QuickMessages
import com.pathfinder.ui.components.EmergencyButton
import com.pathfinder.ui.components.EmergencyRed
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Mensagens de texto (geral e individual), alertas rápidos e emergência. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(initialTo: String?, onBack: () -> Unit, onOpenNetwork: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PathFinderApp).container }
    val hub = container.networkHub
    val messages by container.chatRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val contacts by hub.contacts.collectAsStateWithLifecycle()
    val network by container.nearby.state.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val myDevice = container.nearby.deviceId
    val scope = rememberCoroutineScope()
    var to by rememberSaveable { mutableStateOf(initialTo) }
    var text by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()

    // Abrir a tela marca tudo como lido (inclusive o que chegar com ela aberta).
    LaunchedEffect(messages.size) {
        container.chatRepository.markAllRead()
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex)
    }

    fun send(body: String, urgent: Boolean, withLocation: Boolean) {
        scope.launch {
            val location = if (withLocation) CurrentLocation.get(context) else null
            hub.sendText(body, to, urgent, location)
        }
    }

    val role = settings?.role ?: UserRole.COORDINATOR
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mensagens") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RecipientSelector(contacts, to) { to = it }
                    QuickRow(QuickMessages.forRole(role)) { q ->
                        // Alertas do monitor vão com a posição.
                        send(q.text, q.urgent, withLocation = role != UserRole.COORDINATOR)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.take(500) },
                            placeholder = { Text("Escreva uma mensagem") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                        )
                        IconButton(
                            onClick = { send(text, urgent = false, withLocation = false); text = "" },
                            enabled = text.isNotBlank(),
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar") }
                    }
                    if (role != UserRole.COORDINATOR) EmergencyButton(Modifier.fillMaxWidth())
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!network.running) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Rede desligada: as mensagens ficam guardadas e saem quando ela for ligada.",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onOpenNetwork) { Text("Ligar") }
                    }
                }
            }
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text("Nenhuma mensagem ainda.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(messages, key = { it.id }) { m ->
                        Bubble(m, contacts, myDevice, onReply = { to = m.fromDevice })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipientSelector(contacts: List<Contact>, to: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = to?.let { id -> contacts.firstOrNull { it.deviceId == id }?.label ?: "Aparelho $id" } ?: "Todos"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Para") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todos") }, onClick = { onSelect(null); expanded = false })
            contacts.forEach { c ->
                DropdownMenuItem(text = { Text(c.label) }, onClick = { onSelect(c.deviceId); expanded = false })
            }
        }
    }
}

@Composable
private fun QuickRow(options: List<QuickMessage>, onPick: (QuickMessage) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { q ->
            AssistChip(
                onClick = { onPick(q) },
                label = { Text(q.text) },
                colors = if (q.urgent) {
                    AssistChipDefaults.assistChipColors(containerColor = EmergencyRed, labelColor = Color.White)
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}

@Composable
private fun Bubble(m: ChatMessageEntity, contacts: List<Contact>, myDevice: String, onReply: () -> Unit) {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.createdAt))
    val alarming = m.urgent || m.emergency
    val bg = when {
        alarming -> EmergencyRed
        m.outgoing -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (alarming) Color.White else MaterialTheme.colorScheme.onSurface
    val sender = if (m.outgoing) "Você" else (m.fromGroup?.let { "$it · ${m.fromName}" } ?: m.fromName)
    val recipient = when (m.toDevice) {
        null -> "para todos"
        myDevice -> "para você"
        else -> "para ${contacts.firstOrNull { it.deviceId == m.toDevice }?.label ?: "um aparelho"}"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.outgoing) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp)
                .background(bg, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("$sender · $recipient · $time", style = MaterialTheme.typography.labelSmall, color = fg)
            Text(
                m.text,
                style = if (m.emergency) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                fontWeight = if (alarming) FontWeight.Bold else FontWeight.Normal,
                color = fg,
            )
            if (m.latitude != null && m.longitude != null) {
                Text("Posição: ${Formats.latLon(m.latitude, m.longitude)}", style = MaterialTheme.typography.bodySmall, color = fg)
            }
            if (!m.outgoing) {
                TextButton(onClick = onReply, contentPadding = PaddingValues(0.dp)) {
                    Text("Responder só para ${m.fromName}", color = fg, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
