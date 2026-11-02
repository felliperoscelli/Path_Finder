package com.pathfinder.ui.points

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pathfinder.data.db.TrailEntity
import com.pathfinder.data.db.TrailPointEntity
import com.pathfinder.data.settings.CoordinateFormat
import com.pathfinder.geo.Formats
import com.pathfinder.pdf.PdfActions
import com.pathfinder.transfer.FileActions
import com.pathfinder.pdf.SheetMode
import com.pathfinder.pdf.SheetOptions
import com.pathfinder.ui.map.EditPointDialog
import com.pathfinder.ui.map.PhotoThumbnail

/** Lista dos pontos: reordenar, editar, apagar e gerar a ficha em PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsScreen(
    trailId: Long,
    onBack: () -> Unit,
    viewModel: PointsViewModel = viewModel(key = "points-$trailId", factory = PointsViewModel.factory(trailId)),
) {
    val trail by viewModel.trail.collectAsStateWithLifecycle()
    val format by viewModel.format.collectAsStateWithLifecycle()
    val pdf by viewModel.pdf.collectAsStateWithLifecycle()
    var editingPointId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingTrail by rememberSaveable { mutableStateOf(false) }
    var choosingPdf by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    val connected by viewModel.connectedDevices.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trail?.trail?.name ?: "Pontos") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    IconButton(onClick = { sharing = true }, enabled = !trail?.points.isNullOrEmpty()) {
                        Icon(Icons.Filled.Share, "Enviar trilha")
                    }
                    IconButton(onClick = { choosingPdf = true }, enabled = !trail?.points.isNullOrEmpty()) {
                        Icon(Icons.Filled.PictureAsPdf, "Ficha em PDF")
                    }
                },
            )
        },
    ) { padding ->
        val t = trail
        if (t == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val points = t.orderedPoints
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { TrailHeader(t.trail, points, onEdit = { editingTrail = true }) }
            if (points.isEmpty()) {
                item { Text("Nenhum ponto marcado ainda.", Modifier.padding(8.dp)) }
            }
            items(points, key = { it.id }) { p ->
                PointCard(
                    p = p,
                    format = format,
                    isFirst = p.sequence == 0,
                    isLast = p.sequence == points.lastIndex,
                    onUp = { viewModel.reorder(p.id, p.sequence - 1) },
                    onDown = { viewModel.reorder(p.id, p.sequence + 1) },
                    onEdit = { editingPointId = p.id },
                )
            }
        }

        val editing = editingPointId?.let { id -> points.firstOrNull { it.id == id } }
        if (editing != null) {
            EditPointDialog(
                point = editing,
                format = format,
                declinationDegrees = t.trail.declinationDegrees,
                onSave = { name, notes, photo ->
                    viewModel.updatePoint(editing.id, name, notes, photo)
                    editingPointId = null
                },
                onDelete = {
                    viewModel.deletePoint(editing.id)
                    editingPointId = null
                },
                onDismiss = { editingPointId = null },
            )
        }
        if (editingTrail) {
            EditTrailDialog(
                trail = t.trail,
                onSave = { name, distance, minutes ->
                    viewModel.updateTrail(name, distance, minutes * 60)
                    editingTrail = false
                },
                onDismiss = { editingTrail = false },
            )
        }
        if (choosingPdf) {
            PdfOptionsDialog(
                defaultFormat = format,
                onGenerate = { options ->
                    choosingPdf = false
                    viewModel.generatePdf(options)
                },
                onDismiss = { choosingPdf = false },
            )
        }
        pdf?.let { PdfResultDialog(it, viewModel::dismissPdf) }
        if (sharing) {
            ShareTrailDialog(
                connected = connected,
                trailName = t.trail.name,
                onNetwork = viewModel::shareOverNetwork,
                onExport = viewModel::exportFile,
                onDismiss = { sharing = false },
            )
        }
    }
}

@Composable
private fun TrailHeader(trail: TrailEntity, points: List<TrailPointEntity>, onEdit: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val total = points.sumOf { it.distanceFromPreviousMeters ?: 0.0 }
                Text("${points.size} pontos · ${Formats.distance(total)}", style = MaterialTheme.typography.titleMedium)
                Text("Declinação usada: ${Formats.declination(trail.declinationDegrees)}")
                Text(
                    "Alerta de desvio: ${trail.deviationDistanceMeters} m por ${trail.deviationTimeSeconds / 60} min",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar trilha") }
        }
    }
}

@Composable
private fun PointCard(
    p: TrailPointEntity,
    format: CoordinateFormat,
    isFirst: Boolean,
    isLast: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${p.sequence + 1}. ${p.name}", style = MaterialTheme.typography.titleSmall)
                val utm = Formats.utm(p.utmZone, p.utmBand, p.utmEasting, p.utmNorthing)
                Text(Formats.coordinate(format, p.latitude, p.longitude, utm), style = MaterialTheme.typography.bodyMedium)
                if (isFirst) {
                    Text("Partida", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        "Az. verdadeiro ${Formats.azimuth(p.trueAzimuthDegrees)} · " +
                            "magnético ${Formats.azimuth(p.magneticAzimuthDegrees)} · " +
                            Formats.distance(p.distanceFromPreviousMeters),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (p.notes.isNotBlank()) Text(p.notes, style = MaterialTheme.typography.bodySmall)
            }
            p.photoPath?.let { PhotoThumbnail(it, sizeDp = 56) }
            Column {
                IconButton(onClick = onUp, enabled = !isFirst) { Icon(Icons.Filled.ArrowUpward, "Subir") }
                IconButton(onClick = onDown, enabled = !isLast) { Icon(Icons.Filled.ArrowDownward, "Descer") }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar ponto") }
        }
    }
}

@Composable
private fun EditTrailDialog(trail: TrailEntity, onSave: (String, Int, Int) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(trail.name) }
    var distance by rememberSaveable { mutableStateOf(trail.deviationDistanceMeters.toString()) }
    var minutes by rememberSaveable { mutableStateOf((trail.deviationTimeSeconds / 60).toString()) }
    val d = distance.toIntOrNull()?.takeIf { it in 5..500 }
    val m = minutes.toIntOrNull()?.takeIf { it in 1..60 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar trilha") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome da pista") }, singleLine = true)
                OutlinedTextField(
                    distance, { distance = it.filter(Char::isDigit).take(3) },
                    label = { Text("Desvio máximo (m, 5–500)") },
                    isError = d == null, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    minutes, { minutes = it.filter(Char::isDigit).take(2) },
                    label = { Text("Tempo fora da rota (min, 1–60)") },
                    isError = m == null, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, d!!, m!!) },
                enabled = name.isNotBlank() && d != null && m != null,
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PdfOptionsDialog(
    defaultFormat: CoordinateFormat,
    onGenerate: (SheetOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(SheetMode.FULL) }
    var format by rememberSaveable { mutableStateOf(defaultFormat) }
    var notes by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ficha em PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Conteúdo", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SheetMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(i, SheetMode.entries.size),
                        ) { Text(m.label, maxLines = 1) }
                    }
                }
                Text(
                    if (mode == SheetMode.FULL) {
                        "Coordenadas, azimute verdadeiro e magnético, distância e observações."
                    } else {
                        "Só as coordenadas, com colunas em branco para o grupo calcular azimute e distância."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Coordenadas", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CoordinateFormat.entries.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = format == f,
                            onClick = { format = f },
                            shape = SegmentedButtonDefaults.itemShape(i, CoordinateFormat.entries.size),
                        ) { Text(if (f == CoordinateFormat.LAT_LON) "Lat/long" else f.label, maxLines = 1) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = notes, onCheckedChange = { notes = it })
                    Text("Incluir observações")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onGenerate(SheetOptions(mode, format, notes)) }) { Text("Gerar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PdfResultDialog(state: PdfState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val ready = state as? PdfState.Ready
        if (uri != null && ready != null) {
            val ok = runCatching { PdfActions.saveTo(context, ready.file, uri) }.isSuccess
            Toast.makeText(context, if (ok) "Ficha salva." else "Não foi possível salvar.", Toast.LENGTH_SHORT).show()
        }
    }
    when (state) {
        PdfState.Generating -> AlertDialog(
            onDismissRequest = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("Gerando a ficha…")
                }
            },
            confirmButton = {},
        )
        is PdfState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Erro") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is PdfState.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ficha pronta") },
            text = { Text("O que deseja fazer com \"${state.file.name}\"?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { PdfActions.print(context, state.file, state.title) }) { Text("Imprimir") }
                    TextButton(onClick = {
                        if (!PdfActions.share(context, state.file, state.title)) {
                            Toast.makeText(context, "Nenhum app para compartilhar.", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Compartilhar") }
                    TextButton(onClick = { save.launch(state.file.name) }) { Text("Salvar em…") }
                    TextButton(onClick = onDismiss) { Text("Fechar") }
                }
            },
        )
    }
}

/** Enviar a trilha pela rede local ou exportar como arquivo (.json do Path Finder ou .gpx). */
@Composable
private fun ShareTrailDialog(
    connected: Int,
    trailName: String,
    onNetwork: (onResult: (Int) -> Unit) -> Unit,
    onExport: (gpx: Boolean, onReady: (java.io.File?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var pendingSave by remember { mutableStateOf<java.io.File?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = pendingSave
        if (uri != null && file != null) {
            val ok = runCatching { FileActions.saveTo(context, file, uri) }.isSuccess
            Toast.makeText(context, if (ok) "Arquivo salvo." else "Não foi possível salvar.", Toast.LENGTH_SHORT).show()
        }
        pendingSave = null
    }
    fun export(gpx: Boolean, save: Boolean) = onExport(gpx) { file ->
        when {
            file == null -> Toast.makeText(context, "Não foi possível gerar o arquivo.", Toast.LENGTH_SHORT).show()
            save -> { pendingSave = file; saver.launch(file.name) }
            else -> FileActions.share(context, file, if (gpx) "application/gpx+xml" else "application/json", trailName)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enviar trilha") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pela rede local (sem internet)", style = MaterialTheme.typography.labelLarge)
                TextButton(
                    onClick = {
                        onNetwork { n ->
                            Toast.makeText(context, "Trilha enviada para $n aparelho(s).", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    },
                    enabled = connected > 0,
                ) { Text(if (connected > 0) "Enviar para $connected aparelho(s) conectado(s)" else "Nenhum aparelho conectado") }
                Text(
                    "Ligue a rede na tela \"Rede local\" nos dois aparelhos.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Por arquivo (WhatsApp, e-mail…)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row {
                    TextButton(onClick = { export(gpx = false, save = false) }) { Text("Compartilhar .json") }
                    TextButton(onClick = { export(gpx = false, save = true) }) { Text("Salvar") }
                }
                Row {
                    TextButton(onClick = { export(gpx = true, save = false) }) { Text("Compartilhar .gpx") }
                    TextButton(onClick = { export(gpx = true, save = true) }) { Text("Salvar") }
                }
                Text(
                    ".json: formato completo do Path Finder. .gpx: abre em outros apps de mapa.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
