package com.pathfinder.pdf

import com.pathfinder.data.db.TrailEntity
import com.pathfinder.data.db.TrailPointEntity
import com.pathfinder.data.settings.CoordinateFormat
import com.pathfinder.geo.Formats
import com.pathfinder.geo.TimeOfDay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SheetMode(val label: String) {
    /** Coordenadas, azimutes, distâncias e observações. */
    FULL("Completa"),

    /** Só coordenadas, com colunas em branco para o grupo calcular azimute e distância. */
    COORDINATES_ONLY("Só coordenadas"),
}

data class SheetOptions(
    val mode: SheetMode = SheetMode.FULL,
    val format: CoordinateFormat = CoordinateFormat.BOTH,
    val includeNotes: Boolean = true,
)

enum class Align { START, CENTER, END }

data class SheetColumn(val title: String, val weight: Float, val align: Align = Align.START)

/** Uma tabela do documento (com título opcional acima dela). */
data class SheetTable(
    val title: String?,
    val columns: List<SheetColumn>,
    val rows: List<List<String>>,
    /** Linhas com espaço para escrever à mão. */
    val writableRows: Boolean = false,
)

/** Conteúdo da ficha, independente de como ela é desenhada. */
data class TrailSheet(
    val title: String,
    val headerLines: List<String>,
    val columns: List<SheetColumn>,
    val rows: List<List<String>>,
    /** Linhas com espaço para escrever à mão (modo só coordenadas). */
    val writableRows: Boolean,
    val footer: String,
    /** Tabelas adicionais depois da principal (ex.: tempos por trecho). */
    val extraTables: List<SheetTable> = emptyList(),
) {
    val tables: List<SheetTable> get() = listOf(SheetTable(null, columns, rows, writableRows)) + extraTables
}

object TrailSheetBuilder {
    private val day = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val dayTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun build(
        trail: TrailEntity,
        points: List<TrailPointEntity>,
        options: SheetOptions,
        generatedAt: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): TrailSheet {
        val ordered = points.sortedBy { it.sequence }
        val full = options.mode == SheetMode.FULL
        val total = ordered.sumOf { it.distanceFromPreviousMeters ?: 0.0 }

        val header = buildList {
            add("Data: ${day.format(Instant.ofEpochMilli(trail.createdAt).atZone(zone))}")
            add(
                "Declinação magnética usada: ${Formats.declination(trail.declinationDegrees)}" +
                    "  (azimute magnético = verdadeiro − declinação)"
            )
            add("Coordenadas: ${formatDescription(options.format)}")
            add(if (full) "${ordered.size} pontos · distância total ${Formats.distance(total)}" else "${ordered.size} pontos")
            trail.deadlineMinutes?.let { add("Hora limite: ${TimeOfDay.format(it)}") }
            if (trail.notes.isNotBlank()) add(trail.notes)
        }

        val coordWeight = if (options.format == CoordinateFormat.BOTH) 3.2f else 2.8f
        val columns = buildList {
            add(SheetColumn("Nº", 0.5f, Align.CENTER))
            add(SheetColumn("Nome", 2f))
            add(SheetColumn("Coordenadas", coordWeight))
            add(SheetColumn("Az. verdadeiro", 1.1f, Align.CENTER))
            add(SheetColumn("Az. magnético", 1.1f, Align.CENTER))
            add(SheetColumn("Distância", 1.1f, Align.END))
            if (options.includeNotes) add(SheetColumn("Observações", 2.8f))
        }

        val rows = ordered.mapIndexed { i, p ->
            val utm = Formats.utm(p.utmZone, p.utmBand, p.utmEasting, p.utmNorthing)
            buildList {
                add("${i + 1}")
                add(p.name)
                add(Formats.coordinate(options.format, p.latitude, p.longitude, utm))
                when {
                    !full -> repeat(3) { add("") }
                    i == 0 -> { add("Partida"); add(""); add("") }
                    else -> {
                        add(Formats.azimuth(p.trueAzimuthDegrees))
                        add(Formats.azimuth(p.magneticAzimuthDegrees))
                        add(Formats.distance(p.distanceFromPreviousMeters))
                    }
                }
                if (options.includeNotes) add(p.notes)
            }
        }

        return TrailSheet(
            title = trail.name,
            headerLines = header,
            columns = columns,
            rows = rows,
            writableRows = !full,
            footer = "Gerado pelo Path Finder em ${dayTime.format(Instant.ofEpochMilli(generatedAt).atZone(zone))}",
        )
    }

    private fun formatDescription(format: CoordinateFormat) = when (format) {
        CoordinateFormat.UTM -> "UTM, datum WGS84 (zona e banda, E, N em metros)"
        CoordinateFormat.LAT_LON -> "latitude, longitude em graus decimais (WGS84)"
        CoordinateFormat.BOTH -> "UTM e latitude/longitude em graus decimais (WGS84)"
    }
}
