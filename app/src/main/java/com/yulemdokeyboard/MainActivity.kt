package com.yulemdokeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
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

        setContentView(root)
    }

    private fun relangLabel(): String = when (AppStrings.getLang(this)) {
        AppStrings.RU -> "Сменить язык"
        AppStrings.EN -> "Change language"
        AppStrings.KAA -> "Tildi almastíríw"
        else -> "Tilni qayta tanlash"
    }
}
