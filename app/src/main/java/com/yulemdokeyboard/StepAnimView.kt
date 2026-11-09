package com.yulemdokeyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Har bir Intro qadamida "nima qilish kerakligini" statik rasm emas,
 * balki kichik tsiklik animatsiya bilan ko'rsatadi:
 *  - ENABLE: sozlamalardagi svitch POS -> ON bo'lishini, barmoq bosganini ko'rsatadi
 *  - SWITCH: klaviatura almashtirish paneli ochilib, ro'yxatdan tanlanishini ko'rsatadi
 *  - LONGPRESS: harfni uzoq bosganda ustidagi raqam chiqishini ko'rsatadi
 *  - DELETE: o'chirish tugmasini bosib turganda matn ketma-ket o'chishini ko'rsatadi
 */
class StepAnimView @JvmOverloads constructor(
    context: Context,
    val mode: Mode
) : View(context, null) {

    enum class Mode { ENABLE, SWITCH, LONGPRESS, DELETE, TIPS }

    private val dp = resources.displayMetrics.density
    private var t = 0f // 0..1, davriy

    private val fireStart = Color.parseColor("#FFFF3D00")
    private val fireEnd = Color.parseColor("#FFFFC107")

    private val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1FFFFFFF")
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
        color = Color.parseColor("#33FFFFFF")
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 13f * resources.displayMetrics.scaledDensity
    }
    private val fingerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F2FFFFFF")
    }
    private val fingerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.parseColor("#55FFC107")
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = if (mode == Mode.TIPS) 4400 else 2200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    // 0..1 ni bo'laklarga bo'lish: masalan 4 bosqichli tsikl uchun har biri 0.25
    private fun phase(from: Float, to: Float): Float =
        ((t - from) / (to - from)).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (mode) {
            Mode.ENABLE -> drawEnable(canvas)
            Mode.SWITCH -> drawSwitch(canvas)
            Mode.LONGPRESS -> drawLongPress(canvas)
            Mode.DELETE -> drawDelete(canvas)
            Mode.TIPS -> {
                val realT = t
                if (realT < 0.5f) {
                    t = realT * 2f
                    drawLongPress(canvas)
                } else {
                    t = (realT - 0.5f) * 2f
                    drawDelete(canvas)
                }
                t = realT
            }
        }
    }

    private fun roundRect(c: Canvas, l: Float, tp: Float, r: Float, b: Float, rad: Float, p: Paint) {
        c.drawRoundRect(RectF(l, tp, r, b), rad, rad, p)
    }

    private fun drawFinger(c: Canvas, cx: Float, cy: Float, pressed: Boolean) {
        val rPulse = (10f + 6f * (if (pressed) 0f else kotlin.math.abs(kotlin.math.sin(t * 6.28))).toFloat()) * dp
        if (!pressed) c.drawCircle(cx, cy, rPulse, fingerRing)
        c.drawCircle(cx, cy, 9f * dp, fingerPaint)
    }

    // ===== 1) Sozlamada svitchni yoqish =====
    private fun drawEnable(c: Canvas) {
        val pad = 16f * dp
        val rowH = 48f * dp
        val rowY = height / 2f - rowH / 2f
        roundRect(c, pad, rowY, width - pad, rowY + rowH, 14f * dp, glass)
        roundRect(c, pad, rowY, width - pad, rowY + rowH, 14f * dp, border)

        text.textAlign = Paint.Align.LEFT
        text.color = Color.parseColor("#DDFFFFFF")
        c.drawText("Yulemdo Keyboard", pad + 16f * dp, rowY + rowH / 2f + 5f * dp, text)
        text.textAlign = Paint.Align.CENTER

        // Svitch: 0..0.5 OFF->ON finger bosadi, 0.5..1 pauza, keyin reset (loop)
        val sw = 44f * dp; val sh = 24f * dp
        val sx = width - pad - 16f * dp - sw; val sy = rowY + rowH / 2f - sh / 2f
        val cyclePhase = phase(0f, 0.45f)
        val on = cyclePhase > 0.5f || t > 0.5f
        val trackColor = if (on) Color.parseColor("#FFFFC107") else Color.parseColor("#33FFFFFF")
        fill.color = trackColor
        roundRect(c, sx, sy, sx + sw, sy + sh, sh / 2f, fill)
        val knobX = sx + sh / 2f + (sw - sh) * (if (on) 1f else 0f)
        fill.color = Color.WHITE
        c.drawCircle(knobX, sy + sh / 2f, sh / 2f - 2f * dp, fill)

        // barmoq svitch ustiga kelib bosadi (0.15..0.4 oralig'ida)
        if (t in 0.1f..0.45f) {
            val fp = phase(0.1f, 0.3f)
            val fx = sx + sw * (0.3f + 0.4f * fp)
            val fy = sy + sh / 2f
            drawFinger(c, fx, fy, pressed = t in 0.28f..0.42f)
        }
    }

    // ===== 2) Klaviatura tanlagichidan tanlash =====
    private fun drawSwitch(c: Canvas) {
        val pad = 14f * dp
        // pastki panel: ro'yxat, 2 ta qator, birinchisi Yulemdo
        val rowH = 40f * dp
        val gap = 6f * dp
        val top = height - 2 * rowH - gap - pad
        val items = listOf("Yulemdo Keyboard", "Gboard")
        items.forEachIndexed { i, label ->
            val ry = top + i * (rowH + gap)
            val selected = i == 0 && t > 0.55f
            fill.shader = null
            glass.color = if (selected) Color.parseColor("#33FFC107") else Color.parseColor("#1FFFFFFF")
            roundRect(c, pad, ry, width - pad, ry + rowH, 12f * dp, glass)
            roundRect(c, pad, ry, width - pad, ry + rowH, 12f * dp, border)
            text.textAlign = Paint.Align.LEFT
            text.color = if (selected) fireEnd else Color.parseColor("#CCFFFFFF")
            c.drawText(label, pad + 14f * dp, ry + rowH / 2f + 5f * dp, text)
            text.textAlign = Paint.Align.CENTER
        }
        glass.color = Color.parseColor("#1FFFFFFF")

        // barmoq birinchi qatorga bosadi 0.3..0.55
        if (t in 0.25f..0.6f) {
            val fp = phase(0.3f, 0.45f)
            val ry = top
            val fx = width / 2f
            val fy = ry + rowH * (0.5f + (1f - fp) * 1.5f).coerceAtMost(rowH / 2f + 20f * dp)
            drawFinger(c, fx, (ry + rowH / 2f).coerceAtMost(fy), pressed = t in 0.42f..0.55f)
        }
    }

    // ===== 3) Harfni uzoq bosib raqam chiqarish =====
    private fun drawLongPress(c: Canvas) {
        val keySize = 56f * dp
        val kx = width / 2f - keySize / 2f
        val ky = height / 2f - keySize / 2f
        val pressed = t in 0.25f..0.65f
        glass.color = if (pressed) Color.parseColor("#33FFFFFF") else Color.parseColor("#1FFFFFFF")
        roundRect(c, kx, ky, kx + keySize, ky + keySize, 12f * dp, glass)
        roundRect(c, kx, ky, kx + keySize, ky + keySize, 12f * dp, border)
        glass.color = Color.parseColor("#1FFFFFFF")

        text.textSize = 22f * resources.displayMetrics.scaledDensity
        text.color = Color.WHITE
        c.drawText("a", kx + keySize / 2f, ky + keySize / 2f + 8f * dp, text)

        // raqam belgisi: uzoq bosilganda kattalashib yorqinlashadi
        val hintAlpha = (phase(0.25f, 0.55f) * 255).toInt().coerceIn(0, 255)
        text.textSize = (10f + 6f * phase(0.25f, 0.55f)) * resources.displayMetrics.scaledDensity
        text.color = Color.argb(hintAlpha, 255, 193, 7)
        c.drawText("2", kx + keySize / 2f, ky - 10f * dp, text)
        text.textSize = 13f * resources.displayMetrics.scaledDensity

        if (t in 0.1f..0.65f) {
            drawFinger(c, width / 2f, ky + keySize / 2f, pressed = pressed)
        }
    }

    // ===== 4) O'chirish tugmasini bosib turish =====
    private fun drawDelete(c: Canvas) {
        val sample = "salom dunyo"
        text.textAlign = Paint.Align.LEFT
        text.textSize = 16f * resources.displayMetrics.scaledDensity
        text.color = Color.parseColor("#DDFFFFFF")
        val heldFrac = phase(0.2f, 0.75f)
        val shown = (sample.length * (1f - heldFrac)).toInt().coerceIn(0, sample.length)
        val shownText = sample.substring(0, shown)
        val tx = 20f * dp
        val ty = height / 2f - 20f * dp
        c.drawText(if (shownText.isEmpty()) "|" else shownText, tx, ty, text)

        // o'chirish tugmasi pastda o'ngda
        val bw = 60f * dp; val bh = 40f * dp
        val bx = width - 20f * dp - bw; val by = height - 20f * dp - bh
        val pressed = t in 0.2f..0.78f
        glass.color = if (pressed) Color.parseColor("#33FFFFFF") else Color.parseColor("#1FFFFFFF")
        roundRect(c, bx, by, bx + bw, by + bh, 10f * dp, glass)
        roundRect(c, bx, by, bx + bw, by + bh, 10f * dp, border)
        glass.color = Color.parseColor("#1FFFFFFF")
        text.textAlign = Paint.Align.CENTER
        text.textSize = 13f * resources.displayMetrics.scaledDensity
        text.color = Color.parseColor("#DDFFFFFF")
        c.drawText("⌫", bx + bw / 2f, by + bh / 2f + 5f * dp, text)

        if (pressed) drawFinger(c, bx + bw / 2f, by + bh / 2f, pressed = true)
    }
}
