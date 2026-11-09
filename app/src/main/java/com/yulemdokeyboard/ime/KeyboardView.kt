package com.yulemdokeyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.yulemdokeyboard.model.KeyData
import com.yulemdokeyboard.model.KeyboardLayout
import com.yulemdokeyboard.model.SpecialCodes

/**
 * Grid asosida klaviatura tugmalarini chizadigan va bosishlarni aniqlaydigan View.
 * Sayt bilan bir xil "glass" (shisha) uslubida: yarim shaffof fon, nozik chegara,
 * yumshoq soya va olovrang urg'u. Tugmalar ixcham (mayda) qilib chizilgan.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var layoutModel: KeyboardLayout? = null
        set(value) {
            field = value
            invalidate()
        }

    var onKeyPressed: ((KeyData) -> Unit)? = null
    var shiftOn = false

    // ===== Glass uslubidagi ranglar =====
    private val fillNormal = Color.parseColor("#26FFFFFF")      // yarim shaffof oq
    private val fillSpecial = Color.parseColor("#1AFFFFFF")     // maxsus tugmalar birozroq xira
    private val fillPressed = Color.parseColor("#45FFFFFF")     // bosilganda yorqinroq
    private val borderColor = Color.parseColor("#33FFFFFF")
    private val fireStart = Color.parseColor("#FFFF3D00")
    private val fireEnd = Color.parseColor("#FFFFC107")

    private val keyPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = borderColor
    }
    private val textPaint = Paint().apply {
        color = Color.parseColor("#F1F1F1")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 30f // ixcham (kichikroq) harflar
    }
    private val shiftActivePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    private val keyRects = mutableMapOf<KeyData, RectF>()
    private var pressedKey: KeyData? = null

    // Tugmalar orasidagi bo'shliq kattaroq -> tugmalar o'zi mayda va ixcham ko'rinadi
    private val rowGap = 14f
    private val colGap = 10f
    private val padding = 10f
    private val cornerRadius = 16f

    init {
        try {
            background = androidx.core.content.ContextCompat.getDrawable(context, com.yulemdokeyboard.R.drawable.bg_app_gradient)
        } catch (e: Exception) {
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeRects()
    }

    private fun computeRects() {
        keyRects.clear()
        val model = layoutModel ?: return
        if (width == 0 || height == 0) return

        val rows = model.rows
        // Qatorlar balandligi: umumiy balandlikning taxminan 82% i ishlatiladi,
        // qolgani tepa-pastdagi "havo" (nafas oladigan bo'shliq) uchun — bu tugmalarni
        // vizual jihatdan kichikroq va ozoda ko'rsatadi.
        val usableHeight = height * 0.86f
        val topOffset = (height - usableHeight) / 2f
        val rowHeight = (usableHeight - padding * 2 - rowGap * (rows - 1)) / rows

        val byRow = model.keys.groupBy { it.row }
        for (r in 0 until rows) {
            val rowKeys = byRow[r]?.sortedBy { it.col } ?: continue
            val totalWidthUnits = rowKeys.sumOf { it.width.toDouble() }.toFloat()
            val availableWidth = width - padding * 2 - colGap * (rowKeys.size - 1).coerceAtLeast(0)
            val unitWidth = if (totalWidthUnits > 0) availableWidth / totalWidthUnits else 0f

            var x = padding
            val y = topOffset + padding + r * (rowHeight + rowGap)
            for (k in rowKeys) {
                val w = unitWidth * k.width
                // Har bir tugma atrofida kichik ichki bo'shliq qoldirib, yanada "mayda" ko'rinish beramiz
                val inset = 3f
                keyRects[k] = RectF(x + inset, y + inset, x + w - inset, y + rowHeight - inset)
                x += w + colGap
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val model = layoutModel ?: return
        if (keyRects.isEmpty()) computeRects()

        for ((key, rect) in keyRects) {
            drawGlassKey(canvas, key, rect)
        }
    }

    private fun drawGlassKey(canvas: Canvas, key: KeyData, rect: RectF) {
        val isShiftActive = key.code == SpecialCodes.SHIFT && shiftOn
        val isPressed = key == pressedKey

        when {
            isShiftActive -> {
                // Shift yoqilganda olovrang gradient bilan ajralib turadi
                shiftActivePaint.shader = LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    fireStart, fireEnd, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, shiftActivePaint)
            }
            else -> {
                keyPaint.color = when {
                    isPressed -> fillPressed
                    key.isSpecial -> fillSpecial
                    else -> fillNormal
                }
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, keyPaint)
            }
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        val label = displayLabel(key)
        if (label.isNotEmpty()) {
            val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(label, rect.centerX(), textY, textPaint)
        }
    }

    private fun displayLabel(key: KeyData): String {
        if (key.code == SpecialCodes.SPACE) return "" // space tugmasida hech qanday yozuv ko'rsatilmaydi
        if (key.isSpecial) return key.label
        return if (shiftOn) key.label.uppercase() else key.label
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val key = findKeyAt(event.x, event.y)
                pressedKey = key
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val key = findKeyAt(event.x, event.y)
                pressedKey = null
                invalidate()
                if (key != null) {
                    onKeyPressed?.invoke(key)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKeyAt(x: Float, y: Float): KeyData? {
        for ((key, rect) in keyRects) {
            if (rect.contains(x, y)) return key
        }
        return null
    }
}
