package com.pathfinder.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pathfinder.pdf.PdfActions
import com.pathfinder.pdf.PdfState

/** PDF pronto: imprimir, compartilhar ou salvar. */
@Composable
fun PdfResultDialog(state: PdfState, onDismiss: () -> Unit) {
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
