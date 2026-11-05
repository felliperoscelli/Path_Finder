package com.pathfinder.geo

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Hora do dia em minutos desde 0h (ex.: 17:00 = 1020). */
object TimeOfDay {
    /** Aceita "17:00", "17h", "17h30", "1730" e "9:05". */
    fun parse(text: String): Int? {
        val t = text.trim().lowercase().replace('h', ':').trimEnd(':')
        val (h, m) = when {
            ':' in t -> t.split(':').let { it[0] to it.getOrElse(1) { "0" }.ifEmpty { "0" } }
            t.length in 3..4 -> t.dropLast(2) to t.takeLast(2)
            t.length in 1..2 -> t to "0"
            else -> return null
        }
        val hour = h.toIntOrNull() ?: return null
        val minute = m.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun format(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    /**
     * Primeiro instante na hora [minutes] a partir de [start]: no mesmo dia,
     * ou no dia seguinte se a hora já tiver passado (pista que vira a noite).
     */
    fun nextOccurrence(start: Long, minutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val startTime = Instant.ofEpochMilli(start).atZone(zone)
        var at = startTime.with(LocalTime.of(minutes / 60, minutes % 60))
        if (!at.isAfter(startTime)) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }
}
