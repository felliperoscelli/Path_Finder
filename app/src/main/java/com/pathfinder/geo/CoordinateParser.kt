package com.pathfinder.geo

import kotlin.math.abs

/**
 * Lê coordenadas digitadas em latitude/longitude, nos formatos comuns:
 * "-17.7441, -48.6250", "-17,7441 -48,6250" (vírgula decimal),
 * "17°44'38.8\"S 48°37'30\"W", "S 17 44 38.8 O 48 37 30", "17°44.647' S 48°37.5' W".
 * Hemisférios: N/S e E/W ou L/O (leste/oeste).
 */
object CoordinateParser {

    private sealed interface Token
    private data class Num(val value: Double, val negative: Boolean, val hasFraction: Boolean) : Token
    private data class Hemi(val c: Char) : Token

    private class Component {
        var leading: Char? = null
        var trailing: Char? = null
        val numbers = mutableListOf<Num>()
        val closed get() = trailing != null || numbers.lastOrNull()?.hasFraction == true || numbers.size == 3
    }

    fun parseLatLon(text: String): LatLon? {
        val tokens = tokenize(text) ?: return null
        val comps = mutableListOf<Component>()
        for (t in tokens) {
            when (t) {
                is Num -> {
                    val c = comps.lastOrNull()
                    if (c == null || c.closed || (t.negative && c.numbers.isNotEmpty())) {
                        comps += Component().also { it.numbers += t }
                    } else {
                        c.numbers += t
                    }
                }
                is Hemi -> {
                    val c = comps.lastOrNull()
                    if (c != null && c.numbers.isNotEmpty() && c.trailing == null && c.leading == null) {
                        c.trailing = t.c
                    } else {
                        comps += Component().also { it.leading = t.c }
                    }
                }
            }
        }
        if (comps.size != 2 || comps.any { it.numbers.isEmpty() }) return null
        val values = comps.map { toDegrees(it) ?: return null }
        val axes = comps.map { axisOf(it) }
        // Se a primeira veio marcada como longitude (E/W/L/O), troca a ordem.
        val (lat, lon) = if (axes[0] == Axis.LON || axes[1] == Axis.LAT) values[1] to values[0] else values[0] to values[1]
        if (abs(lat) > 90 || abs(lon) > 180) return null
        return LatLon(lat, lon)
    }

    private enum class Axis { LAT, LON, UNKNOWN }

    private fun axisOf(c: Component): Axis = when (c.leading ?: c.trailing) {
        'N', 'S' -> Axis.LAT
        'E', 'W', 'L', 'O' -> Axis.LON
        else -> Axis.UNKNOWN
    }

    private fun toDegrees(c: Component): Double? {
        val (d, m, s) = Triple(c.numbers[0], c.numbers.getOrNull(1), c.numbers.getOrNull(2))
        if (m != null && (m.negative || m.value >= 60)) return null
        if (s != null && (s.negative || s.value >= 60)) return null
        val magnitude = abs(d.value) + (m?.value ?: 0.0) / 60 + (s?.value ?: 0.0) / 3600
        val negative = d.negative || (c.leading ?: c.trailing) in setOf('S', 'W', 'O')
        return if (negative) -magnitude else magnitude
    }

    /** Números (com sinal e decimal "." ou ",") e letras de hemisfério; o resto separa. */
    private fun tokenize(text: String): List<Token>? {
        val s = text.uppercase().replace('º', '°').replace('−', '-')
        val out = mutableListOf<Token>()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch.isDigit() || (ch == '-' && i + 1 < s.length && s[i + 1].isDigit()) -> {
                    val start = i
                    if (ch == '-') i++
                    while (i < s.length && s[i].isDigit()) i++
                    var fraction = false
                    // "." decimal; "," só é decimal se vier entre dígitos (sem espaço).
                    if (i + 1 < s.length && (s[i] == '.' || s[i] == ',') && s[i + 1].isDigit() &&
                        !(s[i] == ',' && looksLikeSeparatorComma(s, start, i))
                    ) {
                        fraction = true
                        i++
                        while (i < s.length && s[i].isDigit()) i++
                    }
                    val raw = s.substring(start, i).replace(',', '.')
                    out += Num(raw.toDoubleOrNull() ?: return null, raw.startsWith("-"), fraction)
                }
                ch in "NSEWLO" && (i + 1 >= s.length || !s[i + 1].isLetter()) && (i == 0 || !s[i - 1].isLetter()) -> {
                    out += Hemi(ch)
                    i++
                }
                ch.isLetter() -> return null
                else -> i++ // espaço, vírgula, ponto e vírgula, °, ', "
            }
        }
        return out
    }

    /** "-17.5,-48.2": a vírgula separa porque o número já tem ponto decimal. */
    private fun looksLikeSeparatorComma(s: String, start: Int, comma: Int) = s.substring(start, comma).contains('.')
}
