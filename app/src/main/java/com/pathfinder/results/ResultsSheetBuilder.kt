package com.pathfinder.results

import com.pathfinder.data.db.TrailEntity
import com.pathfinder.geo.Formats
import com.pathfinder.pdf.Align
import com.pathfinder.pdf.SheetColumn
import com.pathfinder.pdf.SheetTable
import com.pathfinder.pdf.TrailSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Relatório de resultados em PDF: classificação e tempos por trecho. */
object ResultsSheetBuilder {
    private const val LEGS_PER_TABLE = 8
    private val dayTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun build(
        trail: TrailEntity,
        pointNames: List<String>,
        results: List<GroupResult>,
        generatedAt: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): TrailSheet {
        val completed = results.count { it.completed }
        val header = listOf(
            "Resultado da pista · ${pointNames.size} pontos",
            "${results.size} grupo(s), $completed completaram",
            "Tempo total: da chegada ao ponto 1 até a chegada ao último ponto.",
        )
        val columns = listOf(
            SheetColumn("Pos.", 0.6f, Align.CENTER),
            SheetColumn("Grupo", 2f),
            SheetColumn("Monitor", 1.8f),
            SheetColumn("Pontos", 0.9f, Align.CENTER),
            SheetColumn("Tempo total", 1.3f, Align.END),
            SheetColumn("Alertas de desvio", 1.1f, Align.CENTER),
            SheetColumn("Fora da rota", 1.2f, Align.END),
        )
        val rows = results.map { r ->
            listOf(
                r.rank?.toString() ?: "—",
                r.groupName,
                r.monitorName,
                "${r.reachedCount}/${r.totalPoints}",
                Formats.duration(r.totalMillis),
                r.deviationAlerts.toString(),
                Formats.duration(r.offRouteMillis.takeIf { it > 0 }),
            )
        }
        val legCount = (pointNames.size - 1).coerceAtLeast(0)
        val legTables = (0 until legCount).chunked(LEGS_PER_TABLE).mapIndexed { chunk, legs ->
            SheetTable(
                title = if (chunk == 0) "Tempos por trecho" else null,
                columns = listOf(SheetColumn("Grupo", 2f)) + legs.map { SheetColumn("${it + 1}→${it + 2}", 1f, Align.END) },
                rows = results.map { r -> listOf(r.groupName) + legs.map { Formats.duration(r.legMillis.getOrNull(it)) } },
            )
        }
        val pointsTable = SheetTable(
            title = "Pontos",
            columns = listOf(SheetColumn("Nº", 0.5f, Align.CENTER), SheetColumn("Nome", 4f)),
            rows = pointNames.mapIndexed { i, n -> listOf("${i + 1}", n) },
        )
        return TrailSheet(
            title = "Resultado – ${trail.name}",
            headerLines = header,
            columns = columns,
            rows = rows,
            writableRows = false,
            footer = "Gerado pelo Path Finder em ${dayTime.format(Instant.ofEpochMilli(generatedAt).atZone(zone))}",
            extraTables = legTables + pointsTable,
        )
    }
}
