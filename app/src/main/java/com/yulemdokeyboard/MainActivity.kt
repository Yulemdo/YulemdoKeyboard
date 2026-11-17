package com.yulemdokeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.yulemdokeyboard.model.Languages
import com.yulemdokeyboard.model.LayoutStorage
import androidx.appcompat.app.AppCompatActivity
import com.yulemdokeyboard.editor.LayoutEditorActivity

class MainActivity : AppCompatActivity() {
    private var rootRef: LinearLayout? = null
    private var cardRef: LinearLayout? = null

    // Muharrirda yangi til yaratilgan / yoqilgan bo'lishi mumkin - til kartasini yangilaymiz
    override fun onResume() {
        super.onResume()
        val r = rootRef ?: return
        cardRef?.let { r.removeView(it) }
        addLanguageCard(r)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.applyTheme(this)

        // Birinchi kirishda: til tanlash va tanishtiruv
        if (!AppStrings.isIntroDone(this)) {
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
            return
        }

        val pack = AppStrings.pack(AppStrings.getLang(this))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Ui.appBackground(this@MainActivity)
            val p = Ui.dp(this@MainActivity, 24)
            setPadding(p, p + Ui.dp(this@MainActivity, 36), p, p)
        }

        root.addView(TextView(this).apply {
            text = "Yulemdo"
            setTextColor(Ui.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.06f
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@MainActivity, 12)
        })

        fun addBtn(label: String, fire: Boolean, onClick: () -> Unit) {
            val b = Ui.button(this, label, fire)
            b.setOnClickListener { onClick() }
            root.addView(
                b,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54)).apply {
                    topMargin = Ui.dp(this@MainActivity, 12)
                }
            )
        }

        addBtn(pack.enableBtn, true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        addBtn(pack.switchBtn, false) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        addBtn(pack.editorBtn, false) {
            startActivity(Intent(this, LayoutEditorActivity::class.java))
        }
        addBtn(relangLabel(), false) {
            AppStrings.resetIntro(this)
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
        }

        rootRef = root
        addLanguageCard(root)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Orqa fon tekis; burchakda matnsiz rejim tugmasi (quyosh / oy)
        val frame = FrameLayout(this).apply { background = Ui.appBackground(this@MainActivity) }
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        frame.addView(
            Ui.themeToggle(this),
            FrameLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40), Gravity.TOP or Gravity.END).apply {
                topMargin = Ui.dp(this@MainActivity, 14)
                marginEnd = Ui.dp(this@MainActivity, 14)
            }
        )
        setContentView(frame)
    }

    // ===== Klaviatura tillarini yoqish / o'chirish =====
    private fun langTitle(): String = when (AppStrings.getLang(this)) {
        AppStrings.RU -> "Языки клавиатуры"
        AppStrings.EN -> "Keyboard languages"
        AppStrings.KAA -> "Klaviatura tilleri"
        else -> "Klaviatura tillari"
    }

    private fun langHint(): String = when (AppStrings.getLang(this)) {
        AppStrings.RU -> "Включите нужные языки. Кнопка глобуса переключает только включённые."
        AppStrings.EN -> "Turn on the languages you need. The globe key cycles only through enabled ones."
        AppStrings.KAA -> "Kerekli tillerdi qosíń. Globus túymesi tek qosílǵan tiller arasında almastíradí."
        else -> "Kerakli tillarni yoqing. Globus tugmasi faqat yoqilgan tillar orasida almaştiradi."
    }

    private fun lastLangMessage(): String = when (AppStrings.getLang(this)) {
        AppStrings.RU -> "Должен остаться хотя бы один язык"
        AppStrings.EN -> "At least one language must stay on"
        AppStrings.KAA -> "Keminde bir til qosílǵan bolíwí kerek"
        else -> "Kamida bitta til yoniq qolişi kerak"
    }

    private fun addLanguageCard(root: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.glass(this@MainActivity, 18)
            val p = Ui.dp(this@MainActivity, 16)
            setPadding(p, p, p, p)
        }
        card.addView(TextView(this).apply {
            text = langTitle()
            setTextColor(Ui.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        })
        card.addView(TextView(this).apply {
            text = langHint()
            setTextColor(Ui.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, Ui.dp(this@MainActivity, 4), 0, Ui.dp(this@MainActivity, 6))
        })

        val enabled = LayoutStorage.getEnabledLanguages(this)
        val thumb = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Ui.ACCENT, Ui.TEXT_SECONDARY)
        )
        val track = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Ui.withAlpha(Ui.ACCENT, 0x66), Ui.RIM)
        )
        Languages.order.forEach { l ->
            val sw = SwitchCompat(this).apply {
                text = Languages.nativeName(l)
                setTextColor(Ui.TEXT_PRIMARY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                thumbTintList = thumb
                trackTintList = track
                isChecked = l in enabled
                setPadding(0, Ui.dp(this@MainActivity, 8), 0, Ui.dp(this@MainActivity, 8))
                setOnCheckedChangeListener { v, checked ->
                    if (!LayoutStorage.setLanguageEnabled(this@MainActivity, l, checked)) {
                        v.isChecked = true   // oxirgi yoniq tilni o'chirib bo'lmaydi
                        Toast.makeText(this@MainActivity, lastLangMessage(), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            card.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        cardRef = card
        root.addView(
            card,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@MainActivity, 20)
            }
        )
    }

    private fun relangLabel(): String = when (AppStrings.getLang(this)) {
        AppStrings.RU -> "Изменить язык"
        AppStrings.EN -> "Change language"
        AppStrings.KAA -> "Tildi ózgertiw"
        else -> "Tilni özgartiriş"
    }
}
