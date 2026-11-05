package com.pathfinder.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File

/** Desenha uma [TrailSheet] em PDF A4 (retrato), com quebra de página e cabeçalho repetido. */
object TrailPdfRenderer {
    private const val PAGE_WIDTH = 595 // A4 em pontos (1/72")
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val CELL_PAD = 4f
    private const val FOOTER_SPACE = 24f
    private const val WRITABLE_MIN_ROW = 30f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    private const val TABLE_GAP = 14f

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f }
    private val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f }
    private val columnTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 7.5f
        color = Color.DKGRAY
    }
    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = Color.GRAY
    }
    private val headerFill = Paint().apply { color = Color.rgb(225, 236, 228) }

    /** Tabela já com o texto medido. */
    private class LaidOutTable(
        val title: StaticLayout?,
        val widths: List<Float>,
        val header: List<StaticLayout>,
        val headerHeight: Float,
        val rows: List<List<StaticLayout>>,
        val heights: List<Float>,
    )

    fun render(sheet: TrailSheet, out: File) {
        val tables = sheet.tables.map { t ->
            val widths = columnWidths(t.columns)
            val header = t.columns.mapIndexed { i, c -> layout(c.title, columnTitlePaint, widths[i], c.align) }
            val rows = t.rows.map { row -> row.mapIndexed { i, text -> layout(text, cellPaint, widths[i], t.columns[i].align) } }
            val minRow = if (t.writableRows) WRITABLE_MIN_ROW else 0f
            LaidOutTable(
                title = t.title?.let { textBlock(it, sectionPaint) },
                widths = widths,
                header = header,
                headerHeight = rowHeight(header, 0f),
                rows = rows,
                heights = rows.map { rowHeight(it, minRow) },
            )
        }
        // 1ª passada só conta as páginas (para o "Página x de y"); a 2ª desenha.
        val pageCount = draw(sheet, tables, doc = null, totalPages = 0)
        val doc = PdfDocument()
        try {
            draw(sheet, tables, doc, pageCount)
            out.parentFile?.mkdirs()
            out.outputStream().use(doc::writeTo)
        } finally {
            doc.close()
        }
    }

    /** Desenha (ou só mede, se [doc] for null) e retorna o número de páginas. */
    private fun draw(sheet: TrailSheet, tables: List<LaidOutTable>, doc: PdfDocument?, totalPages: Int): Int {
        val bottom = PAGE_HEIGHT - MARGIN - FOOTER_SPACE
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun finishPage() {
            val p = page ?: return
            drawFooter(p.canvas, "${sheet.footer} · Página $pageNumber de $totalPages")
            doc?.finishPage(p)
            page = null
        }

        fun newPage() {
            finishPage()
            pageNumber++
            page = doc?.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            val canvas = page?.canvas
            y = if (pageNumber == 1) {
                if (canvas != null) drawTitleBlock(canvas, sheet) else MARGIN + titleBlockHeight(sheet)
            } else {
                if (canvas != null) drawContinuation(canvas, sheet) else MARGIN + continuationHeight()
            }
        }

        newPage()
        tables.forEach { t ->
            val titleHeight = t.title?.let { it.height + 6f } ?: 0f
            // Título + cabeçalho + 1ª linha precisam caber juntos.
            val firstRow = t.heights.firstOrNull() ?: 0f
            if (y + titleHeight + t.headerHeight + firstRow > bottom) newPage()
            t.title?.let { title ->
                page?.canvas?.let { drawText(it, title, MARGIN, y + 4f) }
                y += titleHeight
            }
            y = drawRowOrSkip(page?.canvas, t.header, t.widths, y, t.headerHeight, headerFill)
            t.rows.forEachIndexed { i, row ->
                if (y + t.heights[i] > bottom) {
                    newPage()
                    y = drawRowOrSkip(page?.canvas, t.header, t.widths, y, t.headerHeight, headerFill)
                }
                y = drawRowOrSkip(page?.canvas, row, t.widths, y, t.heights[i], null)
            }
            y += TABLE_GAP
        }
        finishPage()
        return pageNumber
    }

    private fun drawRowOrSkip(canvas: Canvas?, cells: List<StaticLayout>, widths: List<Float>, top: Float, height: Float, fill: Paint?) =
        if (canvas != null) drawRow(canvas, cells, widths, top, height, fill) else top + height

    private fun columnWidths(columns: List<SheetColumn>): List<Float> {
        val total = columns.sumOf { it.weight.toDouble() }.toFloat()
        return columns.map { CONTENT_WIDTH * it.weight / total }
    }

    private fun layout(text: String, paint: TextPaint, width: Float, align: Align): StaticLayout {
        val alignment = when (align) {
            Align.START -> Layout.Alignment.ALIGN_NORMAL
            Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Align.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val w = (width - 2 * CELL_PAD).toInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, w).setAlignment(alignment).build()
    }

    private fun rowHeight(cells: List<StaticLayout>, min: Float) =
        maxOf(min, (cells.maxOfOrNull { it.height } ?: 0) + 2 * CELL_PAD)

    private fun textBlock(text: String, paint: TextPaint) = layout(text, paint, CONTENT_WIDTH + 2 * CELL_PAD, Align.START)

    private fun titleBlockHeight(sheet: TrailSheet): Float {
        var h = textBlock(sheet.title, titlePaint).height + 6f
        sheet.headerLines.forEach { h += textBlock(it, headerPaint).height + 2f }
        return h + 10f
    }

    private fun drawTitleBlock(canvas: Canvas, sheet: TrailSheet): Float {
        var y = MARGIN
        y = drawText(canvas, textBlock(sheet.title, titlePaint), MARGIN, y) + 6f
        sheet.headerLines.forEach { y = drawText(canvas, textBlock(it, headerPaint), MARGIN, y) + 2f }
        return y + 10f
    }

    private fun continuationHeight() = textBlock("X", headerPaint).height + 8f

    private fun drawContinuation(canvas: Canvas, sheet: TrailSheet): Float =
        drawText(canvas, textBlock("${sheet.title} (continuação)", headerPaint), MARGIN, MARGIN) + 8f

    private fun drawRow(canvas: Canvas, cells: List<StaticLayout>, widths: List<Float>, top: Float, height: Float, fill: Paint?): Float {
        var x = MARGIN
        cells.forEachIndexed { i, cell ->
            if (fill != null) canvas.drawRect(x, top, x + widths[i], top + height, fill)
            canvas.drawRect(x, top, x + widths[i], top + height, gridPaint)
            drawText(canvas, cell, x + CELL_PAD, top + CELL_PAD)
            x += widths[i]
        }
        return top + height
    }

    private fun drawFooter(canvas: Canvas, text: String) {
        drawText(canvas, textBlock(text, footerPaint), MARGIN, PAGE_HEIGHT - MARGIN - 10f)
    }

    private fun drawText(canvas: Canvas, layout: StaticLayout, x: Float, y: Float): Float {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }
}
