package com.pathfinder.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** Ícones do mapa no estilo dos apps de mapa comuns: pinos numerados e o ponto azul do GPS. */
object MapIcons {
    val PIN_RED = Color.rgb(219, 68, 55)
    val PIN_GREEN = Color.rgb(46, 125, 50)
    private val MY_LOCATION_BLUE = Color.rgb(26, 115, 232)
    private val SHADOW = Color.argb(90, 0, 0, 0)

    private val pins = HashMap<Pair<String, Int>, Bitmap>()

    /** Pino em forma de gota com [label] (número do ponto) em branco. Ponta embaixo, no centro. */
    fun pin(context: Context, label: String, color: Int): Drawable {
        val bitmap = pins.getOrPut(label to color) { drawPin(context.resources.displayMetrics.density, label, color) }
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun drawPin(density: Float, label: String, color: Int): Bitmap {
        val r = 14f * density
        val margin = 3f * density
        val width = (2 * (r + margin)).toInt()
        val height = (2.75f * r + 2 * margin).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = width / 2f
        val cy = margin + r
        val shape = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            val tip = Path().apply {
                moveTo(cx - r * 0.78f, cy + r * 0.62f)
                lineTo(cx + r * 0.78f, cy + r * 0.62f)
                lineTo(cx, height - margin)
                close()
            }
            op(tip, Path.Op.UNION)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            setShadowLayer(margin * 0.8f, 0f, margin * 0.4f, SHADOW)
        }
        canvas.drawPath(shape, fill)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            this.color = darker(color)
        }
        canvas.drawPath(shape, border)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = if (label.length > 2) r * 0.85f else r * 1.05f
        }
        canvas.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2, text)
        return bitmap
    }

    /** Ponto azul parado (sem direção conhecida). */
    fun myLocation(context: Context): Bitmap = drawMyLocation(context.resources.displayMetrics.density, withArrow = false)

    /** Ponto azul com seta: o osmdroid gira a figura conforme o rumo do GPS (seta para cima = norte). */
    fun myLocationDirection(context: Context): Bitmap = drawMyLocation(context.resources.displayMetrics.density, withArrow = true)

    private fun drawMyLocation(density: Float, withArrow: Boolean): Bitmap {
        val size = (44 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = size / 2f
        val dot = 8f * density
        val ring = 3f * density
        if (withArrow) {
            val arrow = Path().apply {
                moveTo(c, 1.5f * density)
                lineTo(c + dot * 0.95f, c - dot * 0.7f)
                lineTo(c - dot * 0.95f, c - dot * 0.7f)
                close()
            }
            canvas.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MY_LOCATION_BLUE })
        }
        canvas.drawCircle(c, c, dot + ring, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(ring, 0f, ring * 0.3f, SHADOW)
        })
        canvas.drawCircle(c, c, dot, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MY_LOCATION_BLUE })
        return bitmap
    }

    private fun darker(color: Int): Int =
        Color.rgb((Color.red(color) * 0.7f).toInt(), (Color.green(color) * 0.7f).toInt(), (Color.blue(color) * 0.7f).toInt())
}
