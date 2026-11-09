package com.yulemdokeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
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
    }
}
