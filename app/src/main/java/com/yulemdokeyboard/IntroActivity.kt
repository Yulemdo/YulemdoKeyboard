package com.yulemdokeyboard

import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.yulemdokeyboard.model.LayoutStorage

/**
 * Birinchi kirishdagi tanishtiruv:
 *  0-sahifa: til tanlash (O'zbekcha V3 / Русский / English / Qaraqalpaqsha)
 *  1-4-sahifalar: tanlangan tilda tushuntirish (xush kelibsiz, yoqish, tanlash, maslahatlar)
 */
class IntroActivity : AppCompatActivity() {

    private var page = 0
    private var lang: String? = null
    private lateinit var content: LinearLayout

    private val fireStart = Color.parseColor("#FFFF3D00")
    private val fireEnd = Color.parseColor("#FFFFC107")

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#2B1200"), Color.parseColor("#0D0D0D"), Color.parseColor("#2A1F00"))
        )
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(24), px(56), px(24), px(32))
        }
        scroll.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (page > 0) { page--; render() } else { isEnabled = false; finish() }
            }
        })

        lang = AppStrings.getLang(this)
        render()
    }

    private fun render() {
        content.removeAllViews()
        if (page == 0) renderLanguage() else renderStep()
        content.alpha = 0f
        content.animate().alpha(1f).setDuration(180).start()
    }

    // ===== 0-sahifa: til tanlash =====
    private fun renderLanguage() {
        content.addView(gradientText("Yulemdo", 38f))
        content.addView(text("Til tanlang · Выберите язык · Choose language · Til saylań", 14f, "#B3FFFFFF").apply {
            gravity = Gravity.CENTER
            setPadding(0, px(10), 0, px(28))
        })

        content.addView(languageButton("Özbekça", "V3", AppStrings.UZ, "uz"))
        content.addView(languageButton("Русский", "", AppStrings.RU, "ru"))
        content.addView(languageButton("English", "", AppStrings.EN, "en"))
        content.addView(languageButton("Qaraqalpaqsha", "", AppStrings.KAA, "kaa"))
    }

    private fun languageButton(label: String, badge: String, appLang: String, keyboardLang: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass(18)
            setPadding(px(22), 0, px(22), 0)
            isClickable = true
            setOnClickListener {
                lang = appLang
                AppStrings.setLang(this@IntroActivity, appLang)
                // Klaviaturaning boshlang'ich tili ham shu bo'ladi
                LayoutStorage.setLanguage(this@IntroActivity, keyboardLang)
                page = 1
                render()
            }
        }
        row.addView(text(label, 19f, "#FFFFFF").apply { typeface = Typeface.DEFAULT_BOLD },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (badge.isNotEmpty()) row.addView(gradientText(badge, 16f))
        row.layoutParams = LinearLayout.LayoutParams(-1, px(64)).apply { topMargin = px(12) }
        return row
    }

    // ===== 1-4 sahifalar: tushuntirish =====
    private fun renderStep() {
        val p = AppStrings.pack(lang)
        val (title, body) = when (page) {
            1 -> p.welcomeTitle to p.welcomeBody
            2 -> p.step1Title to p.step1Body
            3 -> p.step2Title to p.step2Body
            else -> p.tipsTitle to p.tipsBody
        }

        content.addView(gradientText(title, 28f).apply { gravity = Gravity.CENTER })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(20)
            setPadding(px(20), px(20), px(20), px(20))
        }
        card.addView(text(body, 16f, "#EBFFFFFF").apply { setLineSpacing(0f, 1.25f) })
        content.addView(card, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(24)
        })

        // Har bir qadamda nima qilish kerakligini ko'rsatuvchi tsiklik animatsiya
        stepAnimMode(page)?.let { mode ->
            val animCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = glass(20)
            }
            val anim = StepAnimView(this, mode)
            animCard.addView(anim, LinearLayout.LayoutParams(-1, px(180)))
            content.addView(animCard, LinearLayout.LayoutParams(-1, px(180)).apply {
                topMargin = px(16)
            })
        }

        // 2 va 3-sahifalarda tegishli tizim sozlamasini ochuvchi tugma
        if (page == 2) {
            content.addView(secondaryButton(p.enableBtn) {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            })
        } else if (page == 3) {
            content.addView(secondaryButton(p.switchBtn) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            })
        }

        content.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))   // bo'shliq: tugmalarni pastga suradi
        content.addView(dots())

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(
            pill(p.back, filled = false) { page--; render() },
            LinearLayout.LayoutParams(0, px(54), 1f).apply { marginEnd = px(10) }
        )
        val last = page == 4
        buttons.addView(
            pill(if (last) p.start else p.next, filled = true) {
                if (last) {
                    AppStrings.setIntroDone(this)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    page++
                    render()
                }
            },
            LinearLayout.LayoutParams(0, px(54), 1.4f)
        )
        content.addView(buttons, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(18)
        })
    }

    // 1-sahifa (welcome): animatsiya yo'q. 2: svitchni yoqish. 3: ro'yxatdan tanlash.
    // 4 (maslahatlar): uzoq bosish bilan raqam va o'chirishni bosib turish — ikkalasini almashtirib ko'rsatamiz.
    private fun stepAnimMode(p: Int): StepAnimView.Mode? = when (p) {
        2 -> StepAnimView.Mode.ENABLE
        3 -> StepAnimView.Mode.SWITCH
        4 -> StepAnimView.Mode.TIPS
        else -> null
    }

    // ===== Yordamchi view'lar =====
    private fun text(s: String, sp: Float, color: String) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(Color.parseColor(color))
    }

    private fun gradientText(s: String, sp: Float) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(fireEnd)
        val w = paint.measureText(s)
        paint.shader = LinearGradient(0f, 0f, w, 0f, fireStart, fireEnd, Shader.TileMode.CLAMP)
    }

    private fun glass(radiusDp: Int) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor("#3DFFFFFF"), Color.parseColor("#14FFFFFF"))
    ).apply {
        cornerRadius = px(radiusDp).toFloat()
        setStroke(px(1), Color.parseColor("#33FFFFFF"))
    }

    private fun pill(label: String, filled: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = if (filled) {
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(fireStart, fireEnd))
                .apply { cornerRadius = px(27).toFloat() }
        } else glass(27)
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(label: String, onClick: () -> Unit): View =
        pill(label, filled = false, onClick = onClick).apply {
            layoutParams = LinearLayout.LayoutParams(-1, px(52)).apply { topMargin = px(16) }
        }

    private fun dots(): View {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        for (i in 1..4) {
            val d = View(this)
            d.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (i == page) fireEnd else Color.parseColor("#33FFFFFF"))
            }
            row.addView(d, LinearLayout.LayoutParams(px(if (i == page) 10 else 7), px(if (i == page) 10 else 7)).apply {
                marginStart = px(4); marginEnd = px(4)
            })
        }
        return row.apply { setPadding(0, px(20), 0, 0) }
    }
}
