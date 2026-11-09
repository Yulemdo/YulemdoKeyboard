package com.yulemdokeyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.yulemdokeyboard.model.KeyData
import com.yulemdokeyboard.model.KeyboardLayout
import com.yulemdokeyboard.model.SpecialCodes

/**
 * Ixcham, gradientli "glass" klaviatura.
 * - Balandligi qat'iy (dp) -> tugmalar ekranni to'ldirib yubormaydi.
 * - Maxsus tugmalar (shift, backspace, enter, globus, emoji) emoji emas, Canvas bilan chizilgan ikonkalar.
 * - Birinchi qatorda raqam ishoralari (1..0); tugmani uzoq bosilsa raqam yoziladi.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var layoutModel: KeyboardLayout? = null
        set(value) {
            field = value
            requestLayout()
            computeRects()
            invalidate()
        }

    var onKeyPressed: ((KeyData) -> Unit)? = null
    var shiftOn = false

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    // ===== O'lchamlar (dp) =====
    private val rowHeightPx = 50f * dp
    private val rowGapPx = 7f * dp
    private val colGapPx = 5f * dp
    private val sidePad = 5f * dp
    private val vertPad = 8f * dp
    private val corner = 11f * dp

    // ===== Ranglar =====
    private val fireStart = Color.parseColor("#FFFF3D00")
    private val fireEnd = Color.parseColor("#FFFFC107")
    private val iconColor = Color.parseColor("#E6FFFFFF")

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
        color = Color.parseColor("#40FFFFFF")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5F5")
        textAlign = Paint.Align.CENTER
        textSize = 21f * sp
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        textAlign = Paint.Align.RIGHT
        textSize = 9f * sp
    }
    private val iconStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * dp
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = iconColor
    }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = iconColor
    }

    // 0..1 oraliqdagi vertikal gradientlar (har tugma uchun matrix bilan kattalashtiriladi)
    private val normalShader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(Color.parseColor("#3DFFFFFF"), Color.parseColor("#14FFFFFF")),
        null, Shader.TileMode.CLAMP
    )
    private val specialShader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(Color.parseColor("#26FFFFFF"), Color.parseColor("#0AFFFFFF")),
        null, Shader.TileMode.CLAMP
    )
    private val pressedShader = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(Color.parseColor("#66FFFFFF"), Color.parseColor("#33FFFFFF")),
        null, Shader.TileMode.CLAMP
    )
    private val shaderMatrix = Matrix()

    private val keyRects = LinkedHashMap<KeyData, RectF>()
    private val hints = HashMap<KeyData, String>()
    private var pressedKey: KeyData? = null
    private var downTime = 0L

    init {
        try {
            background = androidx.core.content.ContextCompat.getDrawable(
                context, com.yulemdokeyboard.R.drawable.bg_app_gradient
            )
        } catch (e: Exception) {
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
    }

    // ===== Qat'iy balandlik =====
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rows = layoutModel?.rows ?: 4
        val desired = (vertPad * 2 + rowHeightPx * rows + rowGapPx * (rows - 1)).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), desired)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeRects()
    }

    private fun computeRects() {
        keyRects.clear()
        hints.clear()
        val model = layoutModel ?: return
        if (width == 0) return

        val byRow = model.keys.groupBy { it.row }
        val availableFull = { n: Int -> width - sidePad * 2 - colGapPx * (n - 1).coerceAtLeast(0) }

        // Faqat oddiy harflardan iborat qatorlar uchun yagona (eng tor) tugma kengligi -> qator markazlanadi
        var letterUnit = Float.MAX_VALUE
        for ((_, ks) in byRow) {
            if (ks.none { it.isSpecial }) {
                val units = ks.sumOf { it.width.toDouble() }.toFloat()
                letterUnit = minOf(letterUnit, availableFull(ks.size) / units)
            }
        }

        for (r in 0 until model.rows) {
            val rowKeys = byRow[r]?.sortedBy { it.col } ?: continue
            val units = rowKeys.sumOf { it.width.toDouble() }.toFloat()
            if (units <= 0f) continue
            val isLetterRow = rowKeys.none { it.isSpecial }
            val unit = if (isLetterRow && letterUnit != Float.MAX_VALUE) letterUnit
            else availableFull(rowKeys.size) / units

            val rowW = unit * units + colGapPx * (rowKeys.size - 1)
            var x = (width - rowW) / 2f
            val y = vertPad + r * (rowHeightPx + rowGapPx)

            rowKeys.forEachIndexed { i, k ->
                val w = unit * k.width
                keyRects[k] = RectF(x, y, x + w, y + rowHeightPx)
                x += w + colGapPx
                // 1-qatordagi birinchi 10 ta harfga raqam ishorasi
                if (r == 0 && i < 10 && !k.isSpecial && k.label.firstOrNull()?.isLetter() == true) hints[k] = ((i + 1) % 10).toString()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (keyRects.isEmpty()) computeRects()
        for ((key, rect) in keyRects) drawGlassKey(canvas, key, rect)
    }

    private fun drawGlassKey(canvas: Canvas, key: KeyData, rect: RectF) {
        val isShiftActive = key.code == SpecialCodes.SHIFT && shiftOn
        val isPressed = key == pressedKey

        if (isShiftActive) {
            fillPaint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                fireStart, fireEnd, Shader.TileMode.CLAMP
            )
        } else {
            val base = when {
                isPressed -> pressedShader
                key.isSpecial -> specialShader
                else -> normalShader
            }
            shaderMatrix.reset()
            shaderMatrix.setScale(1f, rect.height())
            shaderMatrix.postTranslate(0f, rect.top)
            base.setLocalMatrix(shaderMatrix)
            fillPaint.shader = base
        }
        canvas.drawRoundRect(rect, corner, corner, fillPaint)

        // Yuqori yorug'lik chizig'i (glass effekti) va umumiy chegara
        borderPaint.color = Color.parseColor("#2EFFFFFF")
        canvas.drawRoundRect(rect, corner, corner, borderPaint)
        canvas.save()
        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.35f)
        canvas.drawRoundRect(
            RectF(rect.left + 0.5f * dp, rect.top + 0.5f * dp, rect.right - 0.5f * dp, rect.bottom),
            corner, corner, highlightPaint
        )
        canvas.restore()

        val cx = rect.centerX()
        val cy = rect.centerY()
        when (iconType(key)) {
            Icon.SHIFT -> drawShift(canvas, cx, cy, isShiftActive)
            Icon.BACKSPACE -> drawBackspace(canvas, cx, cy)
            Icon.ENTER -> drawEnter(canvas, cx, cy)
            Icon.GLOBE -> drawGlobe(canvas, cx, cy)
            Icon.EMOJI -> drawSmile(canvas, cx, cy)
            Icon.NONE -> {
                val label = displayLabel(key)
                if (label.isNotEmpty()) {
                    val ty = cy - (textPaint.descent() + textPaint.ascent()) / 2
                    textPaint.textSize = if (key.isSpecial) 14f * sp else 21f * sp
                    canvas.drawText(label, cx, ty, textPaint)
                }
                hints[key]?.let {
                    canvas.drawText(it, rect.right - 5f * dp, rect.top + 12f * dp, hintPaint)
                }
            }
        }
    }

    // ===== Ikonkalar =====
    private enum class Icon { NONE, SHIFT, BACKSPACE, ENTER, GLOBE, EMOJI }

    private fun iconType(key: KeyData): Icon = when (key.code) {
        SpecialCodes.SHIFT -> Icon.SHIFT
        SpecialCodes.BACKSPACE -> Icon.BACKSPACE
        SpecialCodes.ENTER -> Icon.ENTER
        SpecialCodes.LANG_SWITCH -> Icon.GLOBE
        SpecialCodes.EMOJI, "🙂" -> Icon.EMOJI
        else -> Icon.NONE
    }

    private fun drawShift(c: Canvas, cx: Float, cy: Float, active: Boolean) {
        val s = 9f * dp
        val p = Path().apply {
            moveTo(cx, cy - s)
            lineTo(cx + s, cy + 0.1f * s)
            lineTo(cx + s * 0.45f, cy + 0.1f * s)
            lineTo(cx + s * 0.45f, cy + s)
            lineTo(cx - s * 0.45f, cy + s)
            lineTo(cx - s * 0.45f, cy + 0.1f * s)
            lineTo(cx - s, cy + 0.1f * s)
            close()
        }
        if (active) c.drawPath(p, iconFill.apply { color = Color.WHITE })
        else {
            iconFill.color = iconColor
            c.drawPath(p, iconStroke)
        }
        iconFill.color = iconColor
    }

    private fun drawBackspace(c: Canvas, cx: Float, cy: Float) {
        val w = 11f * dp
        val h = 8f * dp
        val p = Path().apply {
            moveTo(cx - w, cy)
            lineTo(cx - w * 0.45f, cy - h)
            lineTo(cx + w, cy - h)
            lineTo(cx + w, cy + h)
            lineTo(cx - w * 0.45f, cy + h)
            close()
        }
        c.drawPath(p, iconStroke)
        val x = 3.5f * dp
        val mx = cx + 2f * dp
        c.drawLine(mx - x, cy - x, mx + x, cy + x, iconStroke)
        c.drawLine(mx - x, cy + x, mx + x, cy - x, iconStroke)
    }

    private fun drawEnter(c: Canvas, cx: Float, cy: Float) {
        val w = 9f * dp
        val h = 6f * dp
        val p = Path().apply {
            moveTo(cx + w, cy - h)
            lineTo(cx + w, cy + h * 0.4f)
            lineTo(cx - w, cy + h * 0.4f)
        }
        c.drawPath(p, iconStroke)
        val a = 4.5f * dp
        val head = Path().apply {
            moveTo(cx - w + a, cy + h * 0.4f - a)
            lineTo(cx - w, cy + h * 0.4f)
            lineTo(cx - w + a, cy + h * 0.4f + a)
        }
        c.drawPath(head, iconStroke)
    }

    private fun drawGlobe(c: Canvas, cx: Float, cy: Float) {
        val r = 9f * dp
        c.drawCircle(cx, cy, r, iconStroke)
        c.drawLine(cx - r, cy, cx + r, cy, iconStroke)
        c.drawOval(RectF(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r), iconStroke)
        // yuqori va pastki parallellar
        val dy = r * 0.5f
        val dx = kotlin.math.sqrt((r * r - dy * dy).toDouble()).toFloat()
        c.drawLine(cx - dx, cy - dy, cx + dx, cy - dy, iconStroke)
        c.drawLine(cx - dx, cy + dy, cx + dx, cy + dy, iconStroke)
    }

    private fun drawSmile(c: Canvas, cx: Float, cy: Float) {
        val r = 9f * dp
        c.drawCircle(cx, cy, r, iconStroke)
        c.drawCircle(cx - r * 0.35f, cy - r * 0.2f, 1.3f * dp, iconFill)
        c.drawCircle(cx + r * 0.35f, cy - r * 0.2f, 1.3f * dp, iconFill)
        val arc = RectF(cx - r * 0.55f, cy - r * 0.35f, cx + r * 0.55f, cy + r * 0.55f)
        c.drawArc(arc, 20f, 140f, false, iconStroke)
    }

    private fun displayLabel(key: KeyData): String {
        if (key.code == SpecialCodes.SPACE) return ""
        if (key.isSpecial) {
            return when (key.code) {
                SpecialCodes.SYMBOLS -> "123"
                SpecialCodes.LETTERS -> "ABC"
                else -> key.label
            }
        }
        return if (shiftOn) key.label.uppercase() else key.label
    }

    // ===== Touch =====
    private val repeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var repeatCount = 0
    private var backspaceHeld = false

    // Backspace bosib turilsa: 0.4s dan keyin ketma-ket o'chiradi, keyin tezlashadi
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val k = pressedKey ?: return
            if (!backspaceHeld || k.code != SpecialCodes.BACKSPACE) return
            onKeyPressed?.invoke(k)
            repeatCount++
            repeatHandler.postDelayed(this, if (repeatCount > 12) 30L else 65L)
        }
    }

    private fun stopRepeat() {
        repeatHandler.removeCallbacks(repeatRunnable)
        backspaceHeld = false
        repeatCount = 0
    }

    override fun onDetachedFromWindow() {
        stopRepeat()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val key = findKeyAt(event.x, event.y)
                downTime = event.eventTime
                stopRepeat()
                pressedKey = key
                if (key?.code == SpecialCodes.BACKSPACE) {
                    backspaceHeld = true
                    onKeyPressed?.invoke(key)
                    repeatHandler.postDelayed(repeatRunnable, 400L)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val key = findKeyAt(event.x, event.y)
                if (key != pressedKey) {
                    stopRepeat()
                    pressedKey = key
                    downTime = event.eventTime
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val key = findKeyAt(event.x, event.y)
                val held = event.eventTime - downTime
                val wasBackspace = backspaceHeld
                stopRepeat()
                pressedKey = null
                invalidate()
                if (key != null && !wasBackspace) {
                    val hint = hints[key]
                    if (hint != null && held > 380) {
                        onKeyPressed?.invoke(KeyData(label = hint, code = hint, row = key.row, col = key.col))
                    } else {
                        onKeyPressed?.invoke(key)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                stopRepeat()
                pressedKey = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKeyAt(x: Float, y: Float): KeyData? {
        // Tugmalar orasidagi bo'shliqqa tushsa ham eng yaqin tugmani tanlaymiz
        var best: KeyData? = null
        var bestDist = Float.MAX_VALUE
        for ((key, rect) in keyRects) {
            if (rect.contains(x, y)) return key
            val dx = maxOf(rect.left - x, 0f, x - rect.right)
            val dy = maxOf(rect.top - y, 0f, y - rect.bottom)
            val d = dx * dx + dy * dy
            if (d < bestDist) { bestDist = d; best = key }
        }
        return if (bestDist <= (10f * dp) * (10f * dp)) best else null
    }
}
