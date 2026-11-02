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

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f }
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

    fun render(sheet: TrailSheet, out: File) {
        val widths = columnWidths(sheet)
        val headerRow = sheet.columns.mapIndexed { i, c -> layout(c.title, columnTitlePaint, widths[i], c.align) }
        val headerRowHeight = rowHeight(headerRow, 0f)
        val rows = sheet.rows.map { row ->
            row.mapIndexed { i, text -> layout(text, cellPaint, widths[i], sheet.columns[i].align) }
        }
        val minRow = if (sheet.writableRows) WRITABLE_MIN_ROW else 0f
        val heights = rows.map { rowHeight(it, minRow) }

        val firstPageTop = MARGIN + titleBlockHeight(sheet)
        val otherPagesTop = MARGIN + continuationHeight()
        val pages = paginate(heights, firstPageTop + headerRowHeight, otherPagesTop + headerRowHeight)

        val doc = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, rowIndices ->
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = doc.startPage(info)
                val canvas = page.canvas
                var y = if (pageIndex == 0) drawTitleBlock(canvas, sheet) else drawContinuation(canvas, sheet)
                y = drawRow(canvas, headerRow, widths, y, headerRowHeight, headerFill)
                for (r in rowIndices) y = drawRow(canvas, rows[r], widths, y, heights[r], null)
                drawFooter(canvas, "${sheet.footer} · Página ${pageIndex + 1} de ${pages.size}")
                doc.finishPage(page)
            }
            out.parentFile?.mkdirs()
            out.outputStream().use(doc::writeTo)
        } finally {
            doc.close()
        }
    }

    /** Divide as linhas em páginas. Uma página vazia é gerada se não houver pontos. */
    private fun paginate(heights: List<Float>, firstTop: Float, otherTop: Float): List<List<Int>> {
        val bottom = PAGE_HEIGHT - MARGIN - FOOTER_SPACE
        val pages = mutableListOf(mutableListOf<Int>())
        var y = firstTop
        heights.forEachIndexed { i, h ->
            if (y + h > bottom && pages.last().isNotEmpty()) {
                pages += mutableListOf<Int>()
                y = otherTop
            }
            pages.last() += i
            y += h
        }
        return pages
    }

    private fun columnWidths(sheet: TrailSheet): List<Float> {
        val total = sheet.columns.sumOf { it.weight.toDouble() }.toFloat()
        return sheet.columns.map { CONTENT_WIDTH * it.weight / total }
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
