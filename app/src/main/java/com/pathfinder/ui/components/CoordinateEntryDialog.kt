package com.pathfinder.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pathfinder.geo.CoordinateParser
import com.pathfinder.geo.Formats
import com.pathfinder.geo.LatLon
import com.pathfinder.geo.Utm

/**
 * Digitar uma coordenada (ex.: tirada de uma carta topográfica), em
 * latitude/longitude ou UTM. Mostra a conversão antes de confirmar.
 */
@Composable
fun CoordinateEntryDialog(
    title: String,
    initial: LatLon?,
    preferUtm: Boolean,
    onConfirm: (LatLon) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialUtm = initial?.let { Utm.fromLatLon(it.latitude, it.longitude) }
    var utmMode by rememberSaveable { mutableStateOf(preferUtm) }
    var latLonText by rememberSaveable { mutableStateOf(initial?.let { Formats.latLon(it.latitude, it.longitude) }.orEmpty()) }
    var zone by rememberSaveable { mutableStateOf(initialUtm?.zone?.toString() ?: "22") }
    var south by rememberSaveable { mutableStateOf(initialUtm?.isSouthern ?: true) }
    var easting by rememberSaveable { mutableStateOf(initialUtm?.easting?.let { "%.0f".format(it) }.orEmpty()) }
    var northing by rememberSaveable { mutableStateOf(initialUtm?.northing?.let { "%.0f".format(it) }.orEmpty()) }

    val result: LatLon? = if (utmMode) {
        val z = zone.toIntOrNull()
        val e = easting.replace(',', '.').toDoubleOrNull()
        val n = northing.replace(',', '.').toDoubleOrNull()
        if (z != null && e != null && n != null) Utm.toLatLon(z, south, e, n) else null
    } else {
        CoordinateParser.parseLatLon(latLonText)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !utmMode, onClick = { utmMode = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text("Lat/long")
                    }
                    SegmentedButton(selected = utmMode, onClick = { utmMode = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text("UTM")
                    }
                }
                if (utmMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            zone, { zone = it.filter(Char::isDigit).take(2) },
                            label = { Text("Zona") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.weight(1.4f)) {
                            SegmentedButton(selected = south, onClick = { south = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Sul") }
                            SegmentedButton(selected = !south, onClick = { south = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Norte") }
                        }
                    }
                    OutlinedTextField(
                        easting, { easting = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(10) },
                        label = { Text("E (leste, m)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        northing, { northing = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(11) },
                        label = { Text("N (norte, m)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        latLonText, { latLonText = it.take(80) },
                        label = { Text("Latitude, longitude") },
                        supportingText = { Text("Ex.: -17.7441, -48.6250 ou 17°44'38\"S 48°37'30\"O") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    when {
                        result == null -> "Coordenada incompleta ou inválida."
                        utmMode -> "= ${Formats.latLon(result.latitude, result.longitude)}"
                        else -> Utm.fromLatLon(result.latitude, result.longitude)?.let { "= ${Formats.utm(it)}" } ?: ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (result == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { result?.let(onConfirm) }, enabled = result != null) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
