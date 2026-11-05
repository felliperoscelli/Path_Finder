package com.pathfinder.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathfinder.PathFinderApp
import com.pathfinder.R
import com.pathfinder.data.settings.UserRole
import com.pathfinder.navigation.NavigationRuntime
import com.pathfinder.plans.Plans
import com.pathfinder.recording.RecordingSession

/**
 * Página inicial: cabeçalho com a logo e o usuário, cartão "Em andamento" e os quatro botões
 * (Mapa, Grupo, Configurações e Mensagens).
 */
@Composable
fun HomeScreen(
    onOpenTrails: () -> Unit,
    onOpenGroup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenNavigation: (trailId: Long) -> Unit,
    onOpenRecording: (trailId: Long) -> Unit,
    groupModeUnlocked: Boolean = Plans.groupModeUnlocked,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PathFinderApp).container }
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val unread by remember { container.chatRepository.observeUnread() }.collectAsStateWithLifecycle(initialValue = 0)
    val recording by RecordingSession.state.collectAsStateWithLifecycle()
    val navigation by NavigationRuntime.state.collectAsStateWithLifecycle()
    var groupModeNotice by rememberSaveable { mutableStateOf(false) }
    val repeater = settings?.role == UserRole.REPEATER

    /** Recursos do Modo grupo: abrem direto se liberado; senão, mostram o aviso. */
    fun groupFeature(open: () -> Unit): () -> Unit = { if (groupModeUnlocked) open() else groupModeNotice = true }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(
                name = settings?.userName.orEmpty().ifBlank { "Sem nome" },
                role = settings?.role?.label.orEmpty(),
            )

            val navTrail = navigation.trailId
            val recTrail = recording.trailId
            if (navigation.isActive && navTrail != null) {
                InProgressCard(
                    icon = Icons.Filled.Explore,
                    title = "Navegando",
                    detail = navigation.groupName.ifBlank { "Toque para voltar à navegação" },
                    onClick = { onOpenNavigation(navTrail) },
                )
            }
            if (recTrail != null) {
                InProgressCard(
                    icon = Icons.Filled.FiberManualRecord,
                    title = "Gravando trilha",
                    detail = if (recording.trackPoints == 1) "1 posição do GPS gravada" else "${recording.trackPoints} posições do GPS gravadas",
                    onClick = { onOpenRecording(recTrail) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeButton(Icons.Filled.Map, "Mapa", Modifier.weight(1f), onClick = onOpenTrails)
                HomeButton(Icons.Filled.Groups, "Grupo", Modifier.weight(1f), onClick = groupFeature(onOpenGroup))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeButton(Icons.Filled.Settings, "Configurações", Modifier.weight(1f), onClick = onOpenSettings)
                HomeButton(
                    Icons.AutoMirrored.Filled.Message,
                    "Mensagens",
                    Modifier.weight(1f),
                    badge = if (repeater) 0 else unread,
                    enabled = !repeater,
                    note = if (repeater) "Indisponível no repetidor" else null,
                    onClick = groupFeature(onOpenMessages),
                )
            }
        }
    }

    if (groupModeNotice) {
        AlertDialog(
            onDismissRequest = { groupModeNotice = false },
            title = { Text("Recurso do Modo grupo") },
            text = {
                Text(
                    "Rede entre celulares, painel do coordenador, mensagens, alertas e emergência fazem " +
                        "parte do Modo grupo. A navegação simples continua grátis.",
                )
            },
            confirmButton = { TextButton(onClick = { groupModeNotice = false }) { Text("Entendi") } },
        )
    }
}

@Composable
private fun Header(name: String, role: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = colorResource(R.color.ic_launcher_background), modifier = Modifier.size(56.dp)) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null)
        }
        Column(Modifier.weight(1f)) {
            Text("Path Finder", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                if (role.isBlank()) name else "$name · $role",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InProgressCard(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null)
            Column(Modifier.weight(1f)) {
                Text("Em andamento", style = MaterialTheme.typography.labelMedium)
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Abrir")
        }
    }
}

@Composable
private fun HomeButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    enabled: Boolean = true,
    note: String? = null,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 132.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 132.dp).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            }
            FitText(label, MaterialTheme.typography.titleMedium)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Uma linha que diminui a letra até caber: em tela estreita ou com letra grande (alto contraste,
 * fonte do sistema) "Configurações" não pode ser partida no meio da palavra.
 */
@Composable
private fun FitText(text: String, style: TextStyle) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    var ready by remember(text, style) { mutableStateOf(false) }
    Text(
        text,
        style = style.copy(fontSize = style.fontSize * scale),
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        onTextLayout = { layout ->
            if (layout.didOverflowWidth && scale > 0.5f) scale *= 0.9f else ready = true
        },
        modifier = Modifier.drawWithContent { if (ready) drawContent() },
    )
}
