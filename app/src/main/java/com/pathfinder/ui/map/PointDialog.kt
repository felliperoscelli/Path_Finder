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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.pathfinder.data.settings.CoordinateFormat
import com.pathfinder.geo.Formats
import java.io.File

/** Diálogo para nomear e salvar um novo ponto, mostrando os valores calculados. */
@Composable
fun PointDialog(
    draft: PointDraft,
    preview: PointPreview?,
    format: CoordinateFormat,
    defaultName: String,
    onConfirm: (name: String, notes: String, photoPath: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var notes by rememberSaveable { mutableStateOf("") }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPhoto?.let(::File)
        if (ok && file != null && file.length() > 0) {
            photoPath?.let { File(it).delete() }
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
        title = { Text("Novo ponto") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PointInfo(draft, preview, format)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = ::launchCamera) {
                        Icon(Icons.Filled.PhotoCamera, null)
                        Text(if (photoPath == null) " Foto" else " Trocar foto")
                    }
                    photoPath?.let { PhotoThumbnail(it) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, notes, photoPath) }, enabled = name.isNotBlank()) {
                Text("Salvar ponto")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                photoPath?.let { File(it).delete() }
                onDismiss()
            }) { Text("Cancelar") }
        },
    )
}

@Composable
private fun PointInfo(draft: PointDraft, preview: PointPreview?, format: CoordinateFormat) {
    val utm = preview?.utm?.let(Formats::utm)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(Formats.coordinate(format, draft.latitude, draft.longitude, utm), style = MaterialTheme.typography.bodyLarge)
        Text(draft.origin, style = MaterialTheme.typography.bodySmall)
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
