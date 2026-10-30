package com.pathfinder.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pathfinder.data.db.TrailSummary
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTrail: (Long) -> Unit,
    onOpenMap: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val trails by viewModel.trailList.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    var showNewTrail by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

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
                    IconButton(onClick = onOpenMap) { Icon(Icons.Filled.Map, "Mapa") }
                    IconButton(onClick = onOpenOfflineMaps) { Icon(Icons.Filled.Download, "Mapas offline") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Configurações") }
                },
            )
        },
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
            list.isEmpty() -> EmptyState(Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                items(list, key = { it.trail.id }) { summary ->
                    TrailRow(
                        summary = summary,
                        onClick = { onOpenTrail(summary.trail.id) },
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
                TextButton(onClick = { viewModel.deleteTrail(id); pendingDelete = null }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TrailRow(summary: TrailSummary, onClick: () -> Unit, onDelete: () -> Unit) {
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
        supportingContent = { Text("$points · $date") },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Apagar") }
        },
    )
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
