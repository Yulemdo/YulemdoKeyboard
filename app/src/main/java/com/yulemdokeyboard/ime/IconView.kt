package com.yulemdokeyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Emoji o'rniga jiddiy, bir rangli (oq yoki qora) chiziqli ikonkalar.
 * Hammasi 24x24 koordinata setkasida chiziladi va o'lchamga qarab masshtablanadi.
 */
class IconView(
    context: Context,
    var type: Type,
    var color: Int,
    var filled: Boolean = false
) : View(context) {

    enum class Type { SMILE, KEYBOARD, CLIPBOARD, GEAR, PIN, CLOSE, TRASH, CHECK }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.9f
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        val side = min(width, height).toFloat()
        if (side <= 0f) return
        val s = side * 0.72f
        canvas.save()
        canvas.translate((width - s) / 2f, (height - s) / 2f)
        canvas.scale(s / 24f, s / 24f)
        stroke.color = color
        fill.color = color
        when (type) {
            Type.SMILE -> {
                canvas.drawCircle(12f, 12f, 9f, stroke)
                canvas.drawCircle(8.8f, 10f, 1.1f, fill)
                canvas.drawCircle(15.2f, 10f, 1.1f, fill)
                canvas.drawArc(RectF(7f, 7.5f, 17f, 17f), 25f, 130f, false, stroke)
            }
            Type.KEYBOARD -> {
                canvas.drawRoundRect(RectF(2.2f, 6f, 21.8f, 18f), 2.2f, 2.2f, stroke)
                for (i in 0 until 5) canvas.drawCircle(6f + i * 3f, 9.6f, 0.9f, fill)
                for (i in 0 until 4) canvas.drawCircle(7.5f + i * 3f, 12.6f, 0.9f, fill)
                canvas.drawLine(8f, 15.4f, 16f, 15.4f, stroke)
            }
            Type.CLIPBOARD -> {
                canvas.drawRoundRect(RectF(5f, 4.5f, 19f, 21f), 2.2f, 2.2f, stroke)
                canvas.drawRoundRect(RectF(9f, 2.5f, 15f, 6.5f), 1.2f, 1.2f, stroke)
                canvas.drawLine(8.5f, 11f, 15.5f, 11f, stroke)
                canvas.drawLine(8.5f, 14.5f, 15.5f, 14.5f, stroke)
                canvas.drawLine(8.5f, 18f, 12.5f, 18f, stroke)
            }
            Type.GEAR -> {
                val p = Path()
                for (i in 0 until 8) {
                    val base = i * 45.0
                    val pts = listOf(base - 14 to 7.0, base - 8 to 9.8, base + 8 to 9.8, base + 14 to 7.0)
                    pts.forEachIndexed { j, (deg, r) ->
                        val rad = Math.toRadians(deg)
                        val x = (12 + r * cos(rad)).toFloat()
                        val y = (12 + r * sin(rad)).toFloat()
                        if (i == 0 && j == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                }
                p.close()
                canvas.drawPath(p, stroke)
                canvas.drawCircle(12f, 12f, 3f, stroke)
            }
            Type.PIN -> {
                val p = Path().apply {
                    moveTo(9f, 3.5f); lineTo(15f, 3.5f)
                    moveTo(10f, 3.5f); lineTo(10f, 10.5f); lineTo(7f, 14f); lineTo(17f, 14f)
                    lineTo(14f, 10.5f); lineTo(14f, 3.5f)
                }
                if (filled) {
                    canvas.drawPath(p, fill)
                }
                canvas.drawPath(p, stroke)
                canvas.drawLine(12f, 14f, 12f, 21f, stroke)
            }
            Type.CHECK -> {
                canvas.drawCircle(12f, 12f, 10f, fill)
                val c = Paint(stroke).apply { color = if (color == android.graphics.Color.WHITE || android.graphics.Color.red(color) > 128) android.graphics.Color.BLACK else android.graphics.Color.WHITE; strokeWidth = 2.4f }
                canvas.drawLine(7f, 12.5f, 10.7f, 16f, c)
                canvas.drawLine(10.7f, 16f, 17f, 8.5f, c)
            }
            Type.CLOSE -> {
                canvas.drawLine(6f, 6f, 18f, 18f, stroke)
                canvas.drawLine(18f, 6f, 6f, 18f, stroke)
            }
            Type.TRASH -> {
                canvas.drawLine(4.5f, 7f, 19.5f, 7f, stroke)
                val h = Path().apply { moveTo(9.5f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 7f) }
                canvas.drawPath(h, stroke)
                val b = Path().apply { moveTo(6.5f, 7f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 7f) }
                canvas.drawPath(b, stroke)
                canvas.drawLine(10.5f, 10.5f, 10.5f, 17f, stroke)
                canvas.drawLine(13.5f, 10.5f, 13.5f, 17f, stroke)
            }
        }
        canvas.restore()
    }
}
