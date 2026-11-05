package com.pathfinder.diagnostics

/** Amostra de bateria. */
data class BatterySample(val time: Long, val percent: Int, val charging: Boolean)

/** Cálculos do diagnóstico de campo (funções puras). */
object DiagnosticsMath {
    private const val MIN_SPAN_MS = 15 * 60_000L

    /**
     * Consumo em %/hora no último trecho sem carregar (pelo menos 15 min de
     * amostras). null se ainda não há dados suficientes.
     */
    fun drainPerHour(samples: List<BatterySample>): Double? {
        val run = samples.sortedBy { it.time }.takeLastWhile { !it.charging }
        if (run.size < 2) return null
        val span = run.last().time - run.first().time
        if (span < MIN_SPAN_MS) return null
        val used = run.first().percent - run.last().percent
        return used * 3_600_000.0 / span
    }

    /** Horas restantes no ritmo atual. */
    fun hoursLeft(percent: Int?, drainPerHour: Double?): Double? =
        if (percent == null || drainPerHour == null || drainPerHour <= 0.1) null else percent / drainPerHour

    /** Percentil (0–100) de uma lista, ou null se vazia. */
    fun percentile(values: List<Float>, p: Int): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = ((p / 100.0) * (sorted.size - 1)).let { kotlin.math.round(it).toInt() }
        return sorted[index.coerceIn(0, sorted.lastIndex)]
    }
}
