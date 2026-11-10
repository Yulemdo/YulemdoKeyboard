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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            setPadding(p, p, p, p)
        }

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

        addLanguageCard(root)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(scroll)
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
        AppStrings.KAA -> "Kerekli tillerdi qosíń. Globus túymesi tek qosílǵan tillerdi almastíradí."
        else -> "Kerakli tillarni yoqing. Globus tugmasi faqat yoqilgan tillarni almaştiradi."
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
            intArrayOf(Ui.FIRE_1, Color.parseColor("#BDC1C6"))
        )
        val track = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.parseColor("#66FFC107"), Color.parseColor("#33FFFFFF"))
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
        root.addView(
            card,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@MainActivity, 20)
            }
        )
    }

    private fun relangLabel(): String = when (AppStrings.getLang(this)) {
        AppStrings.RU -> "Сменить язык"
        AppStrings.EN -> "Change language"
        AppStrings.KAA -> "Tildi almastíríw"
        else -> "Tilni qayta tanlash"
    }
}
