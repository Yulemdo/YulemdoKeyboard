package com.yulemdokeyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.PopupWindow
import android.widget.TextView
import android.util.TypedValue
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
    var capsLock = false
    var highlightCode: String? = null   // tanlangan tab (emoji paneli) uchun olovrang ajratish
    var spaceLabel = ""                 // space tugmasi ustidagi til nomi
    var spaceBadge = ""                 // til nomi yonidagi belgi (masalan V3)

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
    // Mavzuga qarab (oq / qora) o'zgaradigan ranglar - refreshTheme() to'ldiradi
    private var iconColor = Color.WHITE
    private var keyFillColor = 0
    private var specialFillColor = 0
    private var pressedFillColor = 0
    private var keyBorderColor = 0
    private var accentColor = Color.parseColor("#FF6D00")

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

    /** Telefon temasi: yorug' rejimda oq, qorong'i rejimda qora oddiy klaviatura. */
    private fun refreshTheme() {
        val night = isNight(context)
        setBackgroundColor(backgroundColorFor(context))
        if (night) {
            keyFillColor = Color.parseColor("#2C2C2E")
            specialFillColor = Color.parseColor("#1C1C1E")
            pressedFillColor = Color.parseColor("#48484A")
            keyBorderColor = Color.parseColor("#3A3A3C")
            textPaint.color = Color.parseColor("#FFFFFF")
            hintPaint.color = Color.parseColor("#8E8E93")
            iconColor = Color.parseColor("#F2F2F7")
            spacePaint.color = Color.parseColor("#AEAEB2")
        } else {
            keyFillColor = Color.WHITE
            specialFillColor = Color.parseColor("#E3E5E8")
            pressedFillColor = Color.parseColor("#C9CDD2")
            keyBorderColor = Color.parseColor("#D3D6DA")
            textPaint.color = Color.parseColor("#111111")
            hintPaint.color = Color.parseColor("#8A8A8E")
            iconColor = Color.parseColor("#2B2B2D")
            spacePaint.color = Color.parseColor("#6B6B70")
        }
        iconStroke.color = iconColor
        iconFill.color = iconColor
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        refreshTheme()
        invalidate()
    }

    companion object {
        fun isNight(ctx: Context): Boolean =
            (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        fun backgroundColorFor(ctx: Context): Int =
            if (isNight(ctx)) Color.parseColor("#101010") else Color.parseColor("#F2F3F5")
    }

    // ===== Qat'iy balandlik =====
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), desiredHeight(layoutModel?.rows ?: 4))
    }

    fun desiredHeight(rows: Int): Int =
        (vertPad * 2 + rowHeightPx * rows + rowGapPx * (rows - 1)).toInt()

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
        val isShiftActive = (key.code == SpecialCodes.SHIFT && shiftOn) || (highlightCode != null && key.code == highlightCode)
        val isPressed = key == pressedKey

        // Sodda, tekis (gradientsiz) tugma
        fillPaint.shader = null
        fillPaint.color = when {
            isShiftActive -> accentColor
            isPressed -> pressedFillColor
            key.isSpecial -> specialFillColor
            else -> keyFillColor
        }
        canvas.drawRoundRect(rect, corner, corner, fillPaint)
        if (!isShiftActive) {
            borderPaint.color = keyBorderColor
            canvas.drawRoundRect(rect, corner, corner, borderPaint)
        }

        val cx = rect.centerX()
        val cy = rect.centerY()
        if (key.code == SpecialCodes.SPACE) {
            drawSpaceLabel(canvas, rect)
            return
        }
        when (iconType(key)) {
            Icon.SHIFT -> drawShift(canvas, cx, cy, isShiftActive, capsLock)
            Icon.BACKSPACE -> drawBackspace(canvas, cx, cy)
            Icon.ENTER -> drawEnter(canvas, cx, cy)
            Icon.GLOBE -> drawGlobe(canvas, cx, cy)
            Icon.EMOJI -> drawSmile(canvas, cx, cy)
            Icon.NONE -> {
                val label = displayLabel(key)
                if (label.isNotEmpty()) {
                    val ty = cy - (textPaint.descent() + textPaint.ascent()) / 2
                    textPaint.textSize = when {
                        key.code.startsWith("\u0001CAT") -> 20f * sp
                        key.isSpecial -> 14f * sp
                        else -> 21f * sp
                    }
                    canvas.drawText(label, cx, ty, textPaint)
                }
                hints[key]?.let {
                    canvas.drawText(it, rect.right - 5f * dp, rect.top + 12f * dp, hintPaint)
                }
            }
        }
    }

    // ===== Space ustidagi til nomi =====
    private val spacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        letterSpacing = 0.16f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        letterSpacing = 0.05f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    init { refreshTheme() }

    private fun drawSpaceLabel(c: Canvas, rect: RectF) {
        if (spaceLabel.isEmpty()) return
        val gap = 7f * dp
        val maxW = rect.width() - 14f * dp
        var size = 11f * sp
        var labelW: Float
        var badgeW: Float
        var total: Float
        do {
            spacePaint.textSize = size
            badgePaint.textSize = size * 0.9f
            labelW = spacePaint.measureText(spaceLabel)
            badgeW = if (spaceBadge.isNotEmpty()) badgePaint.measureText(spaceBadge) else 0f
            total = labelW + if (badgeW > 0f) gap + badgeW else 0f
            size -= 0.5f * sp
        } while (total > maxW && size > 7f * sp)

        var x = rect.centerX() - total / 2f
        val ty = rect.centerY() - (spacePaint.descent() + spacePaint.ascent()) / 2f
        c.drawText(spaceLabel, x, ty, spacePaint)
        if (badgeW > 0f) {
            x += labelW + gap
            badgePaint.shader = LinearGradient(x, 0f, x + badgeW, 0f, fireStart, fireEnd, Shader.TileMode.CLAMP)
            val by = rect.centerY() - (badgePaint.descent() + badgePaint.ascent()) / 2f
            c.drawText(spaceBadge, x, by, badgePaint)
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

    private fun drawShift(c: Canvas, cx: Float, cy: Float, active: Boolean, caps: Boolean = false) {
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
        if (caps) {
            // Caps lock: o'q tagida chiziq
            c.drawRoundRect(
                RectF(cx - s * 0.45f, cy + s + 2.5f * dp, cx + s * 0.45f, cy + s + 4.5f * dp),
                dp, dp, iconFill
            )
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

    // ===== Bosilgan harf ustida kichik pop-up (preview) =====
    private var previewPopup: PopupWindow? = null
    private var previewText: TextView? = null
    private val previewW = (54f * dp).toInt()
    private val previewH = (64f * dp).toInt()
    private val previewHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var previewKey: KeyData? = null
    private var longPressConsumed = false   // uzoq bosishda raqam yozilib bo'lsa, qo'yib yuborganda qayta yozilmaydi

    // Uzoq bosilganda (380ms) pop-up raqamga almashadi - nima yozilishini oldindan ko'rsatadi
    private val hintPreviewRunnable = Runnable {
        val k = previewKey ?: return@Runnable
        val h = hints[k] ?: return@Runnable
        if (pressedKey != k) return@Runnable
        // Samsung kabi pop-up: [harf | raqam], raqam olov rangda ajralib turadi. Raqam darhol yoziladi
        showHintPopup(k, h)
        longPressConsumed = true
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        onKeyPressed?.invoke(KeyData(label = h, code = h, row = k.row, col = k.col))
    }

    private fun ensurePreview(): PopupWindow {
        previewPopup?.let { return it }
        val tv = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * dp
                setColor(if (isNight(context)) Color.parseColor("#3A3A3C") else Color.WHITE)
                setStroke((1f * dp).toInt(), if (isNight(context)) Color.parseColor("#55FFFFFF") else Color.parseColor("#D3D6DA"))
            }
            elevation = 6f * dp
        }
        previewText = tv
        return PopupWindow(tv, previewW, previewH, false).apply {
            isTouchable = false
            isFocusable = false
            isClippingEnabled = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            animationStyle = 0
            previewPopup = this
        }
    }

    private fun updatePreviewText(text: String, color: Int) {
        previewText?.apply {
            this.text = text
            setTextColor(color)
        }
    }

    private var hintPopup: PopupWindow? = null

    private fun dismissHint() {
        try { hintPopup?.dismiss() } catch (e: Exception) { }
        hintPopup = null
    }

    private fun showHintPopup(key: KeyData, number: String) {
        val rect = keyRects[key] ?: return
        if (windowToken == null) return
        try {
            try { previewPopup?.dismiss() } catch (e: Exception) { }
            dismissHint()
            val night = isNight(context)
            val cellW = (50f * dp).toInt()
            val cellH = (58f * dp).toInt()
            val pad = (4f * dp).toInt()
            val box = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(pad, pad, pad, pad)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16f * dp
                    setColor(if (night) Color.parseColor("#2C2C2E") else Color.WHITE)
                    setStroke((1f * dp).toInt(), if (night) Color.parseColor("#55FFFFFF") else Color.parseColor("#D3D6DA"))
                }
                elevation = 8f * dp
            }
            fun cell(t: String, highlighted: Boolean) = TextView(context).apply {
                text = t
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                if (highlighted) {
                    setTextColor(Color.WHITE)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR, intArrayOf(fireStart, fireEnd)
                    ).apply { cornerRadius = 12f * dp }
                } else {
                    setTextColor(textPaint.color)
                }
            }
            val letter = if (shiftOn) key.label.uppercase() else key.label
            box.addView(cell(letter, false), android.widget.LinearLayout.LayoutParams(cellW, cellH))
            box.addView(cell(number, true), android.widget.LinearLayout.LayoutParams(cellW, cellH))

            val w = cellW * 2 + pad * 2
            val h = cellH + pad * 2
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val screenW = resources.displayMetrics.widthPixels
            val margin = (4f * dp).toInt()
            val x = (loc[0] + rect.centerX() - w / 2f).toInt().coerceIn(margin, screenW - w - margin)
            val y = (loc[1] + rect.top - h - 4f * dp).toInt().coerceAtLeast(0)
            hintPopup = PopupWindow(box, w, h, false).apply {
                isTouchable = false
                isFocusable = false
                isClippingEnabled = false
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                animationStyle = 0
                showAtLocation(this@KeyboardView, Gravity.NO_GRAVITY, x, y)
            }
        } catch (e: Exception) {
            // pop-up chiqmasa ham raqam yoziladi
        }
    }

    private fun showPreview(key: KeyData?) {
        dismissHint()
        previewHandler.removeCallbacks(hintPreviewRunnable)
        previewKey = key
        if (key == null || key.isSpecial || key.label.isEmpty()) { hidePreview(); return }
        val rect = keyRects[key] ?: return
        if (windowToken == null) return
        val label = if (shiftOn) key.label.uppercase() else key.label
        try {
            val popup = ensurePreview()
            updatePreviewText(label, textPaint.color)
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val x = (loc[0] + rect.centerX() - previewW / 2f).toInt()
            val y = (loc[1] + rect.top - previewH - 4f * dp).toInt().coerceAtLeast(0)
            if (popup.isShowing) popup.update(x, y, previewW, previewH)
            else popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
            if (hints[key] != null) previewHandler.postDelayed(hintPreviewRunnable, 350L)
        } catch (e: Exception) {
            // pop-up ko'rsatib bo'lmasa, klaviatura ishlashda davom etadi
        }
    }

    private fun hidePreview() {
        previewHandler.removeCallbacks(hintPreviewRunnable)
        previewKey = null
        dismissHint()
        try { previewPopup?.dismiss() } catch (e: Exception) { }
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
        hidePreview()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val key = findKeyAt(event.x, event.y)
                downTime = event.eventTime
                stopRepeat()
                longPressConsumed = false
                pressedKey = key
                showPreview(key)
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
                    longPressConsumed = false
                    pressedKey = key
                    downTime = event.eventTime
                    showPreview(key)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val key = findKeyAt(event.x, event.y)
                val held = event.eventTime - downTime
                val wasBackspace = backspaceHeld
                val consumed = longPressConsumed
                longPressConsumed = false
                stopRepeat()
                pressedKey = null
                hidePreview()
                invalidate()
                if (key != null && !wasBackspace && !consumed) {
                    if (key.code == SpecialCodes.LANG_SWITCH && held > 450) {
                        // globus uzoq bosilsa: tizim klaviatura tanlagichi
                        onKeyPressed?.invoke(KeyData("", SpecialCodes.LANG_PICKER, key.row, key.col, isSpecial = true))
                    } else {
                        onKeyPressed?.invoke(key)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressConsumed = false
                stopRepeat()
                hidePreview()
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
