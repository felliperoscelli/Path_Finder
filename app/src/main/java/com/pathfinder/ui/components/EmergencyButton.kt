package com.pathfinder.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathfinder.PathFinderApp
import com.pathfinder.location.CurrentLocation
import kotlinx.coroutines.launch

val EmergencyRed = Color(0xFFB71C1C)

/**
 * Botão de emergência bem visível. Pede confirmação (evita toque acidental)
 * e envia um alerta prioritário com a posição atual para todos.
 */
@Composable
fun EmergencyButton(modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val hub = remember { (context.applicationContext as PathFinderApp).container.networkHub }
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    Button(
        onClick = { confirm = true },
        enabled = !sending,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed, contentColor = Color.White),
    ) {
        Icon(Icons.Filled.Sos, null, modifier = Modifier.size(if (compact) 20.dp else 28.dp))
        if (!compact) Text(" EMERGÊNCIA", fontWeight = FontWeight.Bold)
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Enviar emergência?") },
            text = { Text("Todos os aparelhos da rede recebem um alerta prioritário com a sua posição.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    sending = true
                    scope.launch {
                        val location = CurrentLocation.get(context)
                        hub.sendEmergency(location)
                        sending = false
                        val connected = hub.nearby.state.value.connected.size
                        Toast.makeText(
                            context,
                            if (connected > 0) "Emergência enviada." else "Emergência guardada: será enviada assim que houver conexão.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }) { Text("ENVIAR", color = EmergencyRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
