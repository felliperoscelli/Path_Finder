package com.pathfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            UserRole.entries.forEachIndexed { i, role ->
                SegmentedButton(
                    selected = s.role == role,
                    onClick = { update { it.copy(role = role) } },
                    shape = SegmentedButtonDefaults.itemShape(i, UserRole.entries.size),
                ) { Text(role.label) }
            }
        }

        HorizontalDivider()
        Section("Alerta de desvio (padrão para novas trilhas)")
        NumberField("Distância máxima da rota (m)", s.deviationDistanceMeters, 5..500) { v ->
            update { it.copy(deviationDistanceMeters = v) }
        }
        NumberField("Tempo fora da rota (min)", s.deviationTimeSeconds / 60, 1..60) { v ->
            update { it.copy(deviationTimeSeconds = v * 60) }
        }

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
