package com.pathfinder.ui.map

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pathfinder.data.db.TrailPointEntity
import com.pathfinder.data.settings.CoordinateFormat
import com.pathfinder.geo.Formats
import java.io.File

/**
 * Formulário de ponto (nome, observações, foto). A foto original só é apagada
 * pelo repositório ao salvar; fotos novas descartadas são apagadas aqui.
 */
@Composable
private fun PointFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialNotes: String,
    initialPhoto: String?,
    onConfirm: (name: String, notes: String, photoPath: String?) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initialName) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var photoPath by rememberSaveable { mutableStateOf(initialPhoto) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    /** Apaga a foto atual se ela foi tirada neste diálogo (não é a original). */
    fun discardNewPhoto() {
        photoPath?.takeIf { it != initialPhoto }?.let { File(it).delete() }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPhoto?.let(::File)
        if (ok && file != null && file.length() > 0) {
            discardNewPhoto()
            photoPath = file.absolutePath
        } else {
            file?.delete()
        }
        pendingPhoto = null
    }

    fun launchCamera() {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "ponto_${System.currentTimeMillis()}.jpg")
        pendingPhoto = file.absolutePath
        takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::launchCamera) {
                        Icon(Icons.Filled.PhotoCamera, null)
                        Text(if (photoPath == null) " Foto" else " Trocar")
                    }
                    photoPath?.let {
                        PhotoThumbnail(it)
                        TextButton(onClick = { discardNewPhoto(); photoPath = null }) { Text("Remover") }
                    }
                }
                if (onDelete != null) {
                    var confirmDelete by remember { mutableStateOf(false) }
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Text(" Apagar ponto", color = MaterialTheme.colorScheme.error)
                    }
                    if (confirmDelete) {
                        AlertDialog(
                            onDismissRequest = { confirmDelete = false },
                            title = { Text("Apagar \"$initialName\"?") },
                            text = { Text("Os azimutes e distâncias dos pontos seguintes serão recalculados.") },
                            confirmButton = {
                                TextButton(onClick = { discardNewPhoto(); confirmDelete = false; onDelete() }) {
                                    Text("Apagar")
                                }
                            },
                            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, notes, photoPath) }, enabled = name.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = { discardNewPhoto(); onDismiss() }) { Text("Cancelar") }
        },
    )
}

/** Diálogo para criar um ponto, escolhendo onde ele entra na trilha. */
@Composable
fun NewPointDialog(
    draft: PointDraft,
    points: List<TrailPointEntity>,
    suggestedIndex: Int,
    previewAt: (index: Int) -> PointPreview?,
    format: CoordinateFormat,
    defaultName: String,
    onConfirm: (name: String, notes: String, photoPath: String?, index: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(suggestedIndex.coerceIn(0, points.size)) }
    PointFormDialog(
        title = "Novo ponto",
        confirmLabel = "Salvar ponto",
        initialName = defaultName,
        initialNotes = "",
        initialPhoto = null,
        onConfirm = { name, notes, photo -> onConfirm(name, notes, photo, index) },
        onDismiss = onDismiss,
    ) {
        PointInfo(draft.latitude, draft.longitude, draft.origin, remember(index) { previewAt(index) }, format)
        if (points.isNotEmpty()) InsertPositionSelector(points, index) { index = it }
    }
}

/** Diálogo para editar ou apagar um ponto existente. */
@Composable
fun EditPointDialog(
    point: TrailPointEntity,
    format: CoordinateFormat,
    declinationDegrees: Double?,
    onSave: (name: String, notes: String, photoPath: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    PointFormDialog(
        title = "${point.sequence + 1}. ${point.name}",
        confirmLabel = "Salvar",
        initialName = point.name,
        initialNotes = point.notes,
        initialPhoto = point.photoPath,
        onConfirm = onSave,
        onDismiss = onDismiss,
        onDelete = onDelete,
    ) {
        val utm = Formats.utm(point.utmZone, point.utmBand, point.utmEasting, point.utmNorthing)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Formats.coordinate(format, point.latitude, point.longitude, utm), style = MaterialTheme.typography.bodyLarge)
            if (point.sequence == 0) {
                Text("Ponto de partida", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "Azimute verdadeiro ${Formats.azimuth(point.trueAzimuthDegrees)} · " +
                        "magnético ${Formats.azimuth(point.magneticAzimuthDegrees)} · " +
                        Formats.distance(point.distanceFromPreviousMeters),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text("Declinação: ${Formats.declination(declinationDegrees)}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Para mover, ative a edição no mapa e arraste o marcador.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsertPositionSelector(points: List<TrailPointEntity>, index: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = (0..points.size).map { it to insertLabel(points, it) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = insertLabel(points, index),
            onValueChange = {},
            readOnly = true,
            label = { Text("Posição na trilha") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (i, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(i); expanded = false })
            }
        }
    }
}

private fun insertLabel(points: List<TrailPointEntity>, index: Int): String {
    fun label(i: Int) = "${i + 1}. ${points[i].name}"
    return when (index) {
        0 -> "Antes de ${label(0)} (nova partida)"
        points.size -> "No final, depois de ${label(points.size - 1)}"
        else -> "Entre ${label(index - 1)} e ${label(index)}"
    }
}

@Composable
private fun PointInfo(latitude: Double, longitude: Double, origin: String, preview: PointPreview?, format: CoordinateFormat) {
    val utm = preview?.utm?.let(Formats::utm)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(Formats.coordinate(format, latitude, longitude, utm), style = MaterialTheme.typography.bodyLarge)
        Text(origin, style = MaterialTheme.typography.bodySmall)
        val leg = preview?.leg
        if (leg != null) {
            Text(
                "Desde \"${preview.previousName}\": ${Formats.distance(leg.distanceMeters)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Azimute verdadeiro ${Formats.azimuth(leg.trueAzimuthDegrees)} · " +
                    "magnético ${Formats.azimuth(leg.magneticAzimuthDegrees)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text("Ponto de partida", style = MaterialTheme.typography.bodyMedium)
        }
        if (preview != null) {
            Text("Declinação: ${Formats.declination(preview.declinationDegrees)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PhotoThumbnail(path: String, sizeDp: Int = 56) {
    val bitmap = remember(path) {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 })
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Foto do ponto",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp),
        )
    }
}
