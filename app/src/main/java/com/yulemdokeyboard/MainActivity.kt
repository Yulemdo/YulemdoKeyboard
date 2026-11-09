package com.yulemdokeyboard

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
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

        setContentView(R.layout.activity_main)

        val pack = AppStrings.pack(AppStrings.getLang(this))
        val btnEnable = findViewById<Button>(R.id.btnEnableIme)
        val btnSwitch = findViewById<Button>(R.id.btnSwitchIme)
        val btnEditor = findViewById<Button>(R.id.btnEditor)
        btnEnable.text = pack.enableBtn
        btnSwitch.text = pack.switchBtn
        btnEditor.text = pack.editorBtn

        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        btnSwitch.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        btnEditor.setOnClickListener {
            startActivity(Intent(this, LayoutEditorActivity::class.java))
        }

        // Yangi: tilni qayta tanlash tugmasi. Intro ekraniga qaytaradi, lekin
        // "intro_done" belgisini o'chirmaydi - faqat til sahifasini qayta ko'rsatadi.
        addRelangBtn(btnEditor)
    }

    private fun addRelangBtn(after: Button) {
        val parent = after.parent as? ViewGroup ?: return
        val dp = resources.displayMetrics.density

        val btn = Button(this).apply {
            text = relangLabel()
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#3DFFFFFF"), Color.parseColor("#14FFFFFF"))
            ).apply {
                cornerRadius = 18 * dp
                setStroke((1 * dp).toInt(), Color.parseColor("#33FFFFFF"))
            }
            gravity = Gravity.CENTER
            setOnClickListener {
                // Faqat til tanlash sahifasini ko'rsatish uchun intro_done ni vaqtincha o'chiramiz
                AppStrings.resetIntro(this@MainActivity)
                startActivity(Intent(this@MainActivity, IntroActivity::class.java))
                finish()
            }
        }

        val index = parent.indexOfChild(after)
        val lp = (after.layoutParams as? LinearLayout.LayoutParams)?.let {
            LinearLayout.LayoutParams(it)
        } ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (54 * dp).toInt())
        lp.topMargin = (12 * dp).toInt()
        parent.addView(btn, index + 1, lp)
    }

    private fun relangLabel(): String = when (AppStrings.getLang(this)) {
        AppStrings.RU -> "Сменить язык"
        AppStrings.EN -> "Change language"
        AppStrings.KAA -> "Tildi almastíríw"
        else -> "Tilni qayta tanlash"
    }
}
