package com.pathfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.pathfinder.PathFinderApp
import com.pathfinder.data.settings.Pin
import com.pathfinder.net.EventCode
import com.pathfinder.net.MeshService
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pathfinder.data.settings.AppSettings
import com.pathfinder.data.settings.CoordinateFormat
import com.pathfinder.data.settings.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
            )
        },
    ) { padding ->
        val s = settings
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
        } else {
            SettingsContent(s, viewModel::update, Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    s: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Usuário")
        var name by remember { mutableStateOf(s.userName) }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; update { cur -> cur.copy(userName = it) } },
            label = { Text("Seu nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Perfil")
        // Virar coordenador exige o PIN, se houver um definido.
        var askPinFor by remember { mutableStateOf<UserRole?>(null) }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            UserRole.entries.forEachIndexed { i, role ->
                SegmentedButton(
                    selected = s.role == role,
                    onClick = {
                        if (role == UserRole.COORDINATOR && s.role != role && s.coordinatorPinHash.isNotEmpty()) {
                            askPinFor = role
                        } else {
                            update { it.copy(role = role) }
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, UserRole.entries.size),
                ) { Text(role.label) }
            }
        }
        askPinFor?.let { role ->
            PinDialog(
                title = "PIN do coordenador",
                onPin = { pin ->
                    val ok = Pin.matches(pin, s.coordinatorPinHash)
                    if (ok) {
                        update { it.copy(role = role) }
                        askPinFor = null
                    }
                    ok
                },
                onDismiss = { askPinFor = null },
            )
        }
        PinSettings(s, update)

        HorizontalDivider()
        Section("Evento")
        EventCodeField(s, update)

        HorizontalDivider()
        Section("Alerta de desvio (padrão para novas trilhas)")
        NumberField("Distância máxima da rota (m)", s.deviationDistanceMeters, 5..500) { v ->
            update { it.copy(deviationDistanceMeters = v) }
        }
        NumberField("Tempo fora da rota (min)", s.deviationTimeSeconds / 60, 1..60) { v ->
            update { it.copy(deviationTimeSeconds = v * 60) }
        }

        HorizontalDivider()
        Section("Navegação do monitor")
        NumberField("Raio de chegada ao ponto (m)", s.arrivalRadiusMeters, 5..100) { v ->
            update { it.copy(arrivalRadiusMeters = v) }
        }
        SwitchRow("Manter a tela acesa durante a navegação", s.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }

        HorizontalDivider()
        Section("Painel do coordenador")
        NumberField("Alarme se um grupo ficar sem notícias por (min)", s.noSignalMinutes, 2..120) { v ->
            update { it.copy(noSignalMinutes = v) }
        }

        HorizontalDivider()
        Section("Tela")
        SwitchRow("Alto contraste (para ler no sol)", s.highContrast) { v -> update { it.copy(highContrast = v) } }

        HorizontalDivider()
        Section("Criação de trilha")
        NumberField("Tempo parado para sugerir ponto (s)", s.stopDetectionSeconds, 5..300) { v ->
            update { it.copy(stopDetectionSeconds = v) }
        }

        HorizontalDivider()
        Section("Rede")
        NumberField("Intervalo de envio de posição (s)", s.positionIntervalSeconds, 5..600) { v ->
            update { it.copy(positionIntervalSeconds = v) }
        }

        HorizontalDivider()
        Section("Formato de coordenadas padrão")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CoordinateFormat.entries.forEachIndexed { i, format ->
                SegmentedButton(
                    selected = s.coordinateFormat == format,
                    onClick = { update { it.copy(coordinateFormat = format) } },
                    shape = SegmentedButtonDefaults.itemShape(i, CoordinateFormat.entries.size),
                ) { Text(format.label, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** Campo numérico que só salva valores dentro de [range]. */
@Composable
private fun NumberField(label: String, value: Int, range: IntRange, onValid: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in range
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new.filter(Char::isDigit).take(4)
            text.toIntOrNull()?.takeIf { it in range }?.let(onValid)
        },
        label = { Text(label) },
        isError = !valid,
        supportingText = if (!valid) {
            { Text("Entre ${range.first} e ${range.last}") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Código do evento: só aparelhos com o mesmo código se enxergam na rede. */
@Composable
private fun EventCodeField(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PathFinderApp).container }
    var code by remember(s.eventCode) { mutableStateOf(s.eventCode) }
    val normalized = EventCode.normalize(code)
    Text(
        "Use o mesmo código em todos os celulares do evento. Assim grupos de outros eventos por perto " +
            "não entram na sua rede. Sem código, a rede é aberta a qualquer aparelho com o app.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.take(EventCode.MAX_LENGTH + 5) },
            label = { Text("Código do evento") },
            supportingText = { if (normalized != code.trim() && normalized.isNotEmpty()) Text("Será usado: $normalized") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                update { it.copy(eventCode = normalized) }
                code = normalized
                MeshService.restartIfRunning(context, container.nearby.state.value.running)
            },
            enabled = normalized != s.eventCode,
        ) { Text("Aplicar") }
    }
}

/** Definir, trocar ou remover o PIN do coordenador (pede o PIN atual). */
@Composable
private fun PinSettings(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    var step by remember { mutableIntStateOf(0) } // 0 nada · 1 confirmar atual · 2 novo PIN
    var removing by remember { mutableStateOf(false) }
    val hasPin = s.coordinatorPinHash.isNotEmpty()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (hasPin) "PIN do coordenador definido" else "Sem PIN: qualquer um pode virar coordenador",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { removing = false; step = if (hasPin) 1 else 2 }) { Text(if (hasPin) "Trocar PIN" else "Definir PIN") }
        if (hasPin) TextButton(onClick = { removing = true; step = 1 }) { Text("Remover") }
    }
    when (step) {
        1 -> PinDialog(
            title = "PIN atual",
            onPin = { pin ->
                val ok = Pin.matches(pin, s.coordinatorPinHash)
                if (ok) {
                    if (removing) {
                        update { it.copy(coordinatorPinHash = "") }
                        step = 0
                    } else {
                        step = 2
                    }
                }
                ok
            },
            onDismiss = { step = 0 },
        )
        2 -> PinDialog(
            title = "Novo PIN (4 a 8 números)",
            onPin = { pin ->
                val ok = Pin.isValid(pin)
                if (ok) {
                    update { it.copy(coordinatorPinHash = Pin.hash(pin)) }
                    step = 0
                }
                ok
            },
            onDismiss = { step = 0 },
        )
    }
}

/** Pede um PIN; [onPin] retorna false se estiver errado. */
@Composable
private fun PinDialog(title: String, onPin: (String) -> Boolean, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(8); wrong = false },
                isError = wrong,
                supportingText = { if (wrong) Text("PIN incorreto ou inválido") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = { TextButton(onClick = { wrong = !onPin(pin) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
