package com.pathfinder.ui.diagnostics

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathfinder.PathFinderApp
import com.pathfinder.diagnostics.DiagnosticsMath
import com.pathfinder.navigation.NavigationRuntime
import com.pathfinder.transfer.FileActions
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Diagnóstico de campo: bateria, GPS, rede e alertas, com exportação do registro. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PathFinderApp).container }
    val diagnostics = container.diagnostics
    val summary by diagnostics.summary.collectAsStateWithLifecycle()
    val network by container.nearby.state.collectAsStateWithLifecycle()
    val mesh by container.mesh.stats.collectAsStateWithLifecycle()
    val navigation by NavigationRuntime.state.collectAsStateWithLifecycle()

    fun export() {
        val file = diagnostics.file
        if (!file.exists()) {
            Toast.makeText(context, "Ainda não há registro.", Toast.LENGTH_SHORT).show()
            return
        }
        // O FileProvider só expõe a pasta de exportação: copia para lá.
        val copy = java.io.File(FileActions.exportDir(context), "diagnostico_campo.csv").apply {
            parentFile?.mkdirs()
            file.copyTo(this, overwrite = true)
        }
        FileActions.share(context, copy, "text/csv", "Diagnóstico de campo – Path Finder")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico de campo") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = { IconButton(onClick = ::export) { Icon(Icons.Filled.Share, "Exportar") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "O app registra a bateria, o GPS, a rede e os alertas enquanto estiver aberto. " +
                    "Depois do teste de campo, exporte o arquivo e envie para análise.",
                style = MaterialTheme.typography.bodySmall,
            )
            val battery = summary.battery.lastOrNull()
            val drain = DiagnosticsMath.drainPerHour(summary.battery)
            val left = DiagnosticsMath.hoursLeft(battery?.percent, drain)
            Section(
                "Bateria",
                listOfNotNull(
                    battery?.let { "Agora: ${it.percent}%" + if (it.charging) " (carregando)" else "" } ?: "Sem leitura ainda (a cada 5 min)",
                    drain?.let { "Consumo: %.1f%% por hora".format(Locale("pt", "BR"), it) } ?: "Consumo: precisa de 15 min sem carregar",
                    left?.let { "Dura mais cerca de %.1f h nesse ritmo".format(Locale("pt", "BR"), it) },
                    navigation.powerReason?.let { "Navegação: ${if (navigation.powerSaving) "economia ativa" else "modo normal"} ($it)" },
                ),
            )
            val acc = summary.recentAccuracy
            Section(
                "GPS (últimos 10 min)",
                if (acc.isEmpty()) {
                    listOf("Sem leituras (o GPS só é usado ao gravar ou navegar).")
                } else {
                    listOf(
                        "${acc.size} leituras · ${summary.fixesTotal} desde o início",
                        "Precisão mediana ${DiagnosticsMath.percentile(acc, 50)?.toInt()} m · " +
                            "90% abaixo de ${DiagnosticsMath.percentile(acc, 90)?.toInt()} m",
                    )
                },
            )
            Section(
                "Rede",
                listOf(
                    if (network.running) "Ligada · ${network.connected.size} conectado(s)" else "Desligada",
                    "Conexões: ${summary.connections} · quedas: ${summary.disconnections}",
                    "Mensagens: criadas ${mesh.originated} · recebidas ${mesh.received} · repassadas ${mesh.relayed} · " +
                        "enviadas ${mesh.sent} · guardadas ${mesh.stored}",
                ),
            )
            Section(
                "Registro",
                listOf(
                    "Desde ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(summary.since))}",
                    "Alertas registrados: ${summary.alarms}",
                    "Arquivo: ${summary.logBytes / 1024} kB",
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::export) { Text("Exportar (.csv)") }
                OutlinedButton(onClick = diagnostics::clear) { Text("Limpar") }
            }
        }
    }
}

@Composable
private fun Section(title: String, lines: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
