package com.yulemdokeyboard

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.Button
import androidx.core.view.WindowCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Ilova dizayni: tekis (gradientsiz) fon, ichkariga qoraytirilgan "shisha" tugmalar,
 * tungi / quyoshli rejim. Rejim har bir Activity.onCreate boshida [applyTheme] bilan o'rnatiladi.
 */
object Ui {
    private const val PREFS = "yulemdo_app"
    private const val KEY_NIGHT = "theme_night"

    @Volatile
    var night: Boolean = true
        private set

    // ===== Rang palitrasi (rejimga qarab) =====
    val BG: Int get() = Color.parseColor(if (night) "#0F1012" else "#ECEEF1")
    val TEXT_PRIMARY: Int get() = Color.parseColor(if (night) "#ECEDEF" else "#16181C")
    val TEXT_SECONDARY: Int get() = Color.parseColor(if (night) "#9AA0A6" else "#5B6068")
    val ACCENT: Int get() = Color.parseColor(if (night) "#FFB300" else "#C26A00")
    val FIRE_1: Int get() = ACCENT                      // eski nom (moslik uchun)
    val RIM: Int get() = Color.parseColor(if (night) "#2EFFFFFF" else "#33000000")
    val GRID_LINE: Int get() = Color.parseColor(if (night) "#1AFFFFFF" else "#26000000")
    private val GLASS_BODY: Int get() = Color.parseColor(if (night) "#1FFFFFFF" else "#B3FFFFFF")
    private val GLASS_INNER: Int get() = Color.parseColor(if (night) "#70000000" else "#1A000000")
    private val GLASS_INNER_PRESSED: Int get() = Color.parseColor(if (night) "#99000000" else "#2E000000")

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    // ===== Rejim =====
    fun applyTheme(activity: Activity) {
        night = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_NIGHT, true)
        val w = activity.window
        w.statusBarColor = BG
        w.navigationBarColor = BG
        WindowCompat.getInsetsController(w, w.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }

    fun toggleTheme(activity: Activity) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_NIGHT, !night).apply()
        activity.recreate()
    }

    /** Burchakka qo'yiladigan matnsiz tugma: tunda - quyosh, kunduzi - oy ikonkasi. */
    fun themeToggle(activity: Activity): View = ThemeToggleView(activity).apply {
        setOnClickListener { toggleTheme(activity) }
    }

    // ===== Fon va tugmalar =====
    /** Tekis, jiddiy fon. */
    fun appBackground(ctx: Context): Drawable = ColorDrawable(BG)

    /**
     * Shisha uslubi: tashqi yengil yarim shaffof qobiq + ichkariga qoraytirilgan ichki qatlam.
     * accent = true bo'lsa, chegara (ramka) urg'u rangida bo'ladi.
     */
    fun glass(ctx: Context, radiusDp: Int = 18, accent: Boolean = false): Drawable {
        val r = dp(ctx, radiusDp).toFloat()
        val inset = dp(ctx, 2)
        fun layer(pressed: Boolean): Drawable {
            val shell = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = r
                setColor(if (accent) withAlpha(ACCENT, if (night) 38 else 46) else GLASS_BODY)
                setStroke(dp(ctx, 1), if (accent) ACCENT else RIM)
            }
            val inner = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (r - inset).coerceAtLeast(0f)
                setColor(if (pressed) GLASS_INNER_PRESSED else GLASS_INNER)
            }
            return LayerDrawable(arrayOf(shell, inner)).apply { setLayerInset(1, inset, inset, inset, inset) }
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), layer(true))
            addState(intArrayOf(), layer(false))
        }
    }

    /** Asosiy (urg'uli) tugma - endi gradientsiz, urg'u ramkali shisha. */
    fun fire(ctx: Context, radiusDp: Int = 18): Drawable = glass(ctx, radiusDp, accent = true)

    fun button(ctx: Context, label: String, fire: Boolean): Button = Button(ctx).apply {
        text = label
        isAllCaps = false
        stateListAnimator = null
        elevation = 0f
        typeface = if (fire) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(if (fire) ACCENT else TEXT_PRIMARY)
        background = glass(ctx, 14, accent = fire)
    }
}

/** Matnsiz rejim tugmasi (quyosh / oy), shisha fonda. */
class ThemeToggleView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        background = Ui.glass(context, 14)
        isClickable = true
        contentDescription = "Theme"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val s = min(width, height) * 0.5f
        p.color = Ui.TEXT_PRIMARY
        if (Ui.night) {
            // tun rejimi: kunduzga o'tish uchun quyosh
            p.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, s * 0.34f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.12f
            p.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val r1 = s * 0.58f
                val r2 = s * 0.82f
                canvas.drawLine(
                    cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(),
                    cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat(), p
                )
            }
        } else {
            // kunduzgi rejim: tunga o'tish uchun oy
            p.style = Paint.Style.FILL
            val moon = Path().apply { addCircle(cx, cy, s * 0.62f, Path.Direction.CW) }
            val cut = Path().apply { addCircle(cx + s * 0.36f, cy - s * 0.26f, s * 0.52f, Path.Direction.CW) }
            moon.op(cut, Path.Op.DIFFERENCE)
            canvas.drawPath(moon, p)
        }
    }
}
