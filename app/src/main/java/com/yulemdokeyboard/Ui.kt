package com.yulemdokeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button

/**
 * Avval XML'da turgan hamma narsa (ranglar, fon gradienti, tugma shakllari) endi shu yerda.
 * bg_app_gradient.xml, bg_fire_button.xml, bg_glass_button.xml va colors.xml o'rnini bosadi.
 */
object Ui {
    val FIRE_1 = Color.parseColor("#FFC107")
    val FIRE_2 = Color.parseColor("#FF9800")
    val FIRE_4 = Color.parseColor("#FF3D00")
    val TEXT_PRIMARY = Color.parseColor("#E8EAED")
    val TEXT_SECONDARY = Color.parseColor("#BDC1C6")

    private val GLASS_FILL = Color.parseColor("#26FFFFFF")
    private val GLASS_PRESSED = Color.parseColor("#40FFFFFF")
    private val GLASS_BORDER = Color.parseColor("#33FFFFFF")

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /** Qora fon + yuqori-chapda to'q sariq va pastki-o'ngda sariq nur (radial gradient). */
    fun appBackground(ctx: Context): Drawable {
        val d = ctx.resources.displayMetrics.density
        fun glow(cx: Float, cy: Float, radiusDp: Float, start: String) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = radiusDp * d
            setGradientCenter(cx, cy)
            colors = intArrayOf(Color.parseColor(start), Color.parseColor("#00000000"))
        }
        val base = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(Color.BLACK) }
        return LayerDrawable(arrayOf(
            base,
            glow(0.15f, 0.05f, 520f, "#33FF6F00"),
            glow(0.9f, 1.0f, 560f, "#2EFFC107")
        ))
    }

    /** Shisha tugma/karta: bosilganda yorqinroq bo'ladi. */
    fun glass(ctx: Context, radiusDp: Int = 18): Drawable {
        fun shape(fill: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            setStroke(dp(ctx, 1), GLASS_BORDER)
            cornerRadius = dp(ctx, radiusDp).toFloat()
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(GLASS_PRESSED))
            addState(intArrayOf(), shape(GLASS_FILL))
        }
    }

    /** Olov gradientli asosiy tugma. */
    fun fire(ctx: Context, radiusDp: Int = 18): Drawable =
        GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(FIRE_4, FIRE_2, FIRE_1)).apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
        }

    fun button(ctx: Context, label: String, fire: Boolean): Button = Button(ctx).apply {
        text = label
        isAllCaps = false
        if (fire) {
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = fire(ctx)
            elevation = 2f * ctx.resources.displayMetrics.density
        } else {
            setTextColor(TEXT_PRIMARY)
            background = glass(ctx)
        }
    }
}
