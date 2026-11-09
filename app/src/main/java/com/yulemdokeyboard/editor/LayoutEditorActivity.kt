package com.yulemdokeyboard.editor

import android.content.ClipData
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yulemdokeyboard.R
import com.yulemdokeyboard.model.KeyboardLayout
import com.yulemdokeyboard.model.LayoutStorage
import com.yulemdokeyboard.model.defaultUzbekLayout

class LayoutEditorActivity : AppCompatActivity() {

    private lateinit var gridView: EditorGridView
    private lateinit var paletteContainer: LinearLayout

    // Palitrada ko'rsatiladigan barcha belgilar: lotin alifbosi + o'zbekka xos harflar.
    private val paletteChars = listOf(
        "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
        "a", "s", "d", "f", "g", "h", "j", "k", "l",
        "z", "x", "c", "v", "b", "n", "m",
        "oʻ", "gʻ", "sh", "ch", "ng"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_layout_editor)

        gridView = findViewById(R.id.gridFrame)
        paletteContainer = findViewById(R.id.paletteContainer)

        // gridFrame xml'da oddiy FrameLayout - uni dasturiy ravishda EditorGridView bilan almashtiramiz
        // chunki xml'dan maxsus View turini chaqirish uchun to'liq class path kerak edi; soddalik uchun
        // shu yerda dinamik qo'shamiz.
        replaceGridFrameWithEditorGrid()

        buildPalette()

        val saved = LayoutStorage.loadCustomLayout(this)
        if (saved != null) {
            gridView.rows = saved.rows
            gridView.cols = saved.cols
            gridView.post { gridView.loadKeys(saved.keys) }
        } else {
            val def = defaultUzbekLayout()
            gridView.rows = def.rows
            gridView.cols = def.cols
            gridView.post { gridView.loadKeys(def.keys) }
        }

        findViewById<android.widget.Button>(R.id.btnReset).setOnClickListener {
            val def = defaultUzbekLayout()
            gridView.rows = def.rows
            gridView.cols = def.cols
            gridView.loadKeys(def.keys)
            Toast.makeText(this, "Standart joylashuv tiklandi", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.widget.Button>(R.id.btnSave).setOnClickListener {
            val keys = gridView.exportKeys()
            if (keys.isEmpty()) {
                Toast.makeText(this, "Avval kamida bitta tugma joylashtiring", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val layout = KeyboardLayout(
                name = "Mening klaviaturam",
                rows = gridView.rows,
                cols = gridView.cols,
                keys = keys.toMutableList()
            )
            LayoutStorage.saveCustomLayout(this, layout)
            LayoutStorage.setActiveLayout(this, "custom")
            Toast.makeText(this, "Saqlandi! Endi klaviaturada shu joylashuv ishlatiladi.", Toast.LENGTH_LONG).show()
        }
    }

    private fun replaceGridFrameWithEditorGrid() {
        val oldFrame = findViewById<android.widget.FrameLayout>(R.id.gridFrame)
        val parent = oldFrame.parent as LinearLayout
        val index = parent.indexOfChild(oldFrame)
        val lp = oldFrame.layoutParams
        parent.removeView(oldFrame)

        gridView = EditorGridView(this)
        gridView.id = R.id.gridFrame
        gridView.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_glass_button)
        parent.addView(gridView, index, lp)
    }

    private fun buildPalette() {
        paletteChars.forEach { ch ->
            val chip = TextView(this)
            chip.text = ch
            chip.setTextColor(Color.WHITE)
            chip.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_fire_button)
            chip.gravity = Gravity.CENTER
            chip.textSize = 15f
            val sizePx = (52 * resources.displayMetrics.density).toInt()
            val params = LinearLayout.LayoutParams(sizePx, sizePx)
            params.marginEnd = 8
            params.topMargin = 6
            params.bottomMargin = 10
            chip.layoutParams = params
            chip.elevation = 2f

            chip.setOnLongClickListener { v ->
                val clip = ClipData.newPlainText("key", ch)
                val shadow = View.DragShadowBuilder(v)
                // localState null -> grid buni "palitradan yangi tugma" deb tushunadi
                v.startDragAndDrop(clip, shadow, null, 0)
                true
            }
            paletteContainer.addView(chip)
        }
    }
}
