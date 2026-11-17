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
    private var nextBtn: TextView? = null
    private var hintView: TextView? = null

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.applyTheme(this)
        page = savedInstanceState?.getInt("page") ?: 0   // rejim almashganda sahifa saqlanadi

        val root = FrameLayout(this)
        root.background = Ui.appBackground(this)
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(24), px(56), px(24), px(32))
        }
        scroll.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        // burchakda matnsiz rejim tugmasi (quyosh / oy)
        root.addView(Ui.themeToggle(this), FrameLayout.LayoutParams(px(40), px(40), Gravity.TOP or Gravity.END).apply {
            topMargin = px(14); marginEnd = px(14)
        })
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (page > 0) { page--; render() } else { isEnabled = false; finish() }
            }
        })

        lang = AppStrings.getLang(this)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", page)
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
        content.addView(text("Tilni tanlang · Выберите язык · Choose language · Tildi saylań", 14f, Ui.TEXT_SECONDARY).apply {
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
                LayoutStorage.setLanguageEnabled(this@IntroActivity, keyboardLang, true)
                page = 1
                render()
            }
        }
        row.addView(text(label, 19f, Ui.TEXT_PRIMARY).apply { typeface = Typeface.DEFAULT_BOLD },
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
        card.addView(text(body, 16f, Ui.TEXT_PRIMARY).apply { setLineSpacing(0f, 1.25f) })
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
        hintView = null
        if (page == 2 || page == 3) {
            hintView = text(lockedHint(page), 13f, Ui.ACCENT).apply {
                gravity = Gravity.CENTER
                setPadding(0, px(12), 0, 0)
            }
            content.addView(hintView)
        }

        content.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))   // bo'shliq: tugmalarni pastga suradi
        content.addView(dots())

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(
            pill(p.back, filled = false) { page--; render() },
            LinearLayout.LayoutParams(0, px(54), 1f).apply { marginEnd = px(10) }
        )
        val last = page == 4
        val nextPill = pill(if (last) p.start else p.next, filled = true) {
            if (!isStepDone(page)) return@pill   // amal bajarilmagan bo'lsa o'tib bo'lmaydi
            if (last) {
                AppStrings.setIntroDone(this)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                page++
                render()
            }
        }
        nextBtn = nextPill
        buttons.addView(nextPill, LinearLayout.LayoutParams(0, px(54), 1.4f))
        content.addView(buttons, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(18)
        })
        updateNextState()
    }

    // ===== Qadam bajarilganini tekshirish =====
    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun isKeyboardSelected(): Boolean {
        val cur = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return cur.startsWith("$packageName/")
    }

    /** 2-sahifa: klaviatura yoqilgan bo'lishi, 3-sahifa: tanlangan bo'lishi shart. Qolganlarida shart yo'q. */
    private fun isStepDone(p: Int): Boolean = when (p) {
        2 -> isKeyboardEnabled()
        3 -> isKeyboardEnabled() && isKeyboardSelected()
        else -> true
    }

    private fun lockedHint(p: Int): String = when (lang) {
        AppStrings.RU -> if (p == 2) "Чтобы продолжить, сначала включите клавиатуру" else "Чтобы продолжить, сначала выберите клавиатуру"
        AppStrings.EN -> if (p == 2) "To continue, enable the keyboard first" else "To continue, select the keyboard first"
        AppStrings.KAA -> if (p == 2) "Dawam etiw ushín aldín klaviaturaní qosíń" else "Dawam etiw ushín aldín klaviaturaní saylań"
        else -> if (p == 2) "Davom etiş uçun avval klaviaturani yoqing" else "Davom etiş uçun avval klaviaturani tanlang"
    }

    /** Keyingi tugmani faollashtiradi yoki xiralashtirib o'chirib qo'yadi. */
    private fun updateNextState() {
        if (page == 0) return
        val btn = nextBtn ?: return
        val ok = isStepDone(page)
        btn.isEnabled = ok
        btn.alpha = if (ok) 1f else 0.45f
        btn.setTextColor(if (ok) Ui.ACCENT else Ui.TEXT_SECONDARY)
        btn.background = Ui.glass(this, 27, accent = ok)
        hintView?.visibility = if (ok) View.GONE else View.VISIBLE
    }

    // Sozlamalardan yoki tanlagich oynasidan qaytganda holatni qayta tekshiramiz
    override fun onResume() {
        super.onResume()
        updateNextState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateNextState()
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
    private fun text(s: String, sp: Float, color: Int) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
    }

    private fun gradientText(s: String, sp: Float) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Ui.ACCENT)
    }

    private fun glass(radiusDp: Int) = Ui.glass(this, radiusDp)

    private fun pill(label: String, filled: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (filled) Ui.ACCENT else Ui.TEXT_PRIMARY)
        background = Ui.glass(this@IntroActivity, 27, accent = filled)
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
                setColor(if (i == page) Ui.ACCENT else Ui.RIM)
            }
            row.addView(d, LinearLayout.LayoutParams(px(if (i == page) 10 else 7), px(if (i == page) 10 else 7)).apply {
                marginStart = px(4); marginEnd = px(4)
            })
        }
        return row.apply { setPadding(0, px(20), 0, 0) }
    }
}
