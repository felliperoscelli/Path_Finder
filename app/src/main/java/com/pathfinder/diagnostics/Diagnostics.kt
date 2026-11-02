package com.pathfinder.diagnostics

import android.content.Context
import android.os.BatteryManager
import com.pathfinder.net.MeshCore
import com.pathfinder.net.MeshStats
import com.pathfinder.net.NearbyManager
import com.pathfinder.net.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Resumo para a tela de diagnóstico. */
data class DiagnosticsSummary(
    val since: Long = System.currentTimeMillis(),
    val battery: List<BatterySample> = emptyList(),
    /** Precisão (m) das leituras de GPS dos últimos 10 min. */
    val recentAccuracy: List<Float> = emptyList(),
    val fixesTotal: Int = 0,
    val connections: Int = 0,
    val disconnections: Int = 0,
    val alarms: Int = 0,
    val powerReason: String? = null,
    val logBytes: Long = 0,
)

/**
 * Diagnóstico de campo: registra num arquivo CSV (abre em planilha) a
 * bateria, a qualidade do GPS, as conexões da rede, as mensagens e os
 * alertas, para avaliar o teste de campo e ajustar o consumo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Diagnostics(
    private val context: Context,
    private val nearby: NearbyManager,
    private val mesh: MeshCore,
    private val scope: CoroutineScope,
) {
    val file: File = File(File(context.filesDir, "diagnostics"), "diagnostico_campo.csv")
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val _summary = MutableStateFlow(DiagnosticsSummary())
    val summary: StateFlow<DiagnosticsSummary> = _summary.asStateFlow()

    // Leituras de GPS agregadas por minuto (uma linha por minuto no arquivo).
    private var minuteStart = 0L
    private val minuteAccuracy = mutableListOf<Float>()
    private val recent = ArrayDeque<Pair<Long, Float>>()
    private var lastStats = MeshStats()

    init {
        scope.launch {
            log("INICIO", "app aberto")
            while (true) {
                sampleBattery()
                logMesh()
                delay(SAMPLE_INTERVAL_MS)
            }
        }
        scope.launch {
            nearby.events.collect { e ->
                when (e) {
                    is TransportEvent.Connected -> {
                        _summary.update { it.copy(connections = it.connections + 1) }
                        log("REDE", "conectou ${e.deviceId} (${nearby.state.value.connected.size} conectados)")
                    }
                    is TransportEvent.Disconnected -> {
                        _summary.update { it.copy(disconnections = it.disconnections + 1) }
                        log("REDE", "desconectou ${e.endpointId}")
                    }
                    is TransportEvent.Bytes -> Unit
                }
            }
        }
    }

    /** Uma leitura de GPS (precisão em metros). */
    fun onFix(accuracyMeters: Float?, time: Long = System.currentTimeMillis(), simulated: Boolean = false) {
        val acc = accuracyMeters ?: return
        synchronized(this) {
            if (minuteStart == 0L) minuteStart = time
            minuteAccuracy += acc
            recent.addLast(time to acc)
            while (recent.isNotEmpty() && time - recent.first().first > RECENT_MS) recent.removeFirst()
            if (time - minuteStart >= 60_000) {
                val values = minuteAccuracy.toList()
                minuteAccuracy.clear()
                minuteStart = time
                log(
                    if (simulated) "GPS_SIMULADO" else "GPS",
                    "${values.size} leituras; precisão mediana ${DiagnosticsMath.percentile(values, 50)?.toInt()} m; " +
                        "pior ${values.max().toInt()} m",
                )
            }
            val recentValues = recent.map { it.second }
            _summary.update { it.copy(recentAccuracy = recentValues, fixesTotal = it.fixesTotal + 1) }
        }
    }

    fun onPowerPlan(reason: String, gpsIntervalMs: Long, publishIntervalMs: Long) {
        if (_summary.value.powerReason == reason) return
        _summary.update { it.copy(powerReason = reason) }
        log("ENERGIA", "$reason · GPS a cada ${gpsIntervalMs / 1000} s · posição a cada ${publishIntervalMs / 1000} s")
    }

    fun onAlarm(kind: String) {
        _summary.update { it.copy(alarms = it.alarms + 1) }
        log("ALERTA", kind)
    }

    fun log(type: String, detail: String) {
        val now = System.currentTimeMillis()
        val line = "${TIME.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))};$type;${detail.replace(';', ',').replace('\n', ' ')}\n"
        scope.launch(io) {
            file.parentFile?.mkdirs()
            if (!file.exists()) file.writeText("data_hora;tipo;detalhe\n")
            file.appendText(line)
            if (file.length() > MAX_BYTES) trim()
            _summary.update { it.copy(logBytes = file.length()) }
        }
    }

    fun clear() {
        scope.launch(io) {
            file.delete()
            _summary.value = DiagnosticsSummary()
            log("INICIO", "diagnóstico limpo")
        }
    }

    private fun sampleBattery() {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return
        val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: return
        val sample = BatterySample(System.currentTimeMillis(), percent, bm.isCharging)
        _summary.update { s -> s.copy(battery = (s.battery + sample).takeLast(MAX_BATTERY_SAMPLES)) }
        val drain = DiagnosticsMath.drainPerHour(_summary.value.battery)
        log("BATERIA", "$percent%" + (if (sample.charging) " carregando" else "") + (drain?.let { " · %.1f%%/h".format(it) } ?: ""))
    }

    private fun logMesh() {
        val s = mesh.stats.value
        val d = MeshStats(
            stored = s.stored,
            originated = s.originated - lastStats.originated,
            received = s.received - lastStats.received,
            relayed = s.relayed - lastStats.relayed,
            sent = s.sent - lastStats.sent,
        )
        lastStats = s
        if (nearby.state.value.running || d.received + d.sent > 0) {
            log(
                "MALHA",
                "${nearby.state.value.connected.size} conectados · últimos 5 min: criadas ${d.originated}, recebidas ${d.received}, " +
                    "repassadas ${d.relayed}, enviadas ${d.sent} · guardadas ${d.stored}",
            )
        }
    }

    /** Mantém só a metade mais recente quando o arquivo passa do limite. */
    private fun trim() {
        val lines = file.readLines()
        file.writeText((listOf(lines.first()) + lines.drop(1).takeLast(lines.size / 2)).joinToString("\n", postfix = "\n"))
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 5 * 60_000L
        const val RECENT_MS = 10 * 60_000L
        const val MAX_BYTES = 2L * 1024 * 1024
        const val MAX_BATTERY_SAMPLES = 300
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
