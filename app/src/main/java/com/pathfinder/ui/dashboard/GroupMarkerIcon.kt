package com.pathfinder.ui.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable

/**
 * Ícone de grupo para o mapa: etiqueta colorida com o nome e uma ponta
 * apontando para a posição (âncora no centro de baixo).
 */
object GroupMarkerIcon {
    fun create(context: Context, label: String, color: Int): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 13f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val padH = 8f * density
        val padV = 5f * density
        val tip = 8f * density
        val textWidth = text.measureText(label)
        val boxH = text.fontSpacing + 2 * padV
        val width = (textWidth + 2 * padH).toInt().coerceAtLeast((2 * tip).toInt())
        val height = (boxH + tip).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        val box = RectF(0f, 0f, width.toFloat(), boxH)
        canvas.drawRoundRect(box, 6 * density, 6 * density, fill)
        canvas.drawRoundRect(box, 6 * density, 6 * density, border)
        val pointer = Path().apply {
            moveTo(width / 2f - tip, boxH - 1)
            lineTo(width / 2f, height.toFloat())
            lineTo(width / 2f + tip, boxH - 1)
            close()
        }
        canvas.drawPath(pointer, fill)
        canvas.drawText(label, padH, padV - text.ascent(), text)
        return BitmapDrawable(context.resources, bitmap)
    }
}
