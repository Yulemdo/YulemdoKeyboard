package com.yulemdokeyboard.editor

import android.content.ClipData
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yulemdokeyboard.R
import com.yulemdokeyboard.model.KeyboardLayout
import com.yulemdokeyboard.model.LayoutStorage
import com.yulemdokeyboard.model.defaultUzbekLayout

class LayoutEditorActivity : AppCompatActivity() {

    private lateinit var gridView: EditorGridView
    private lateinit var paletteContainer: ViewGroup

    // Palitrada: lotin harflari + o'zbekka xos (turkcha shakldagi) harflar
    private val paletteChars = listOf(
        "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
        "a", "s", "d", "f", "g", "h", "j", "k", "l",
        "z", "x", "c", "v", "b", "n", "m",
        "ö", "ğ", "ç", "ş", "\u02BC", "ng"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_layout_editor)

        // MUHIM: avval findViewById<EditorGridView> chaqirilgan edi, lekin XML'da bu oddiy FrameLayout —
        // shu sababli ClassCastException bilan ilova yopilib qolardi. Endi umumiy View sifatida olinadi.
        replaceGridFrameWithEditorGrid()
        paletteContainer = resolvePaletteContainer()
        buildPalette()

        val saved = LayoutStorage.loadCustomLayout(this)
        val start = saved ?: defaultUzbekLayout()
        gridView.rows = start.rows
        gridView.cols = start.cols
        gridView.post { gridView.loadKeys(start.keys) }

        findViewById<View>(R.id.btnReset).setOnClickListener {
            val def = defaultUzbekLayout()
            gridView.rows = def.rows
            gridView.cols = def.cols
            gridView.loadKeys(def.keys)
            gridView.invalidate()
            Toast.makeText(this, "Standart joylashuv tiklandi", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnSave).setOnClickListener {
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
        val oldFrame = findViewById<View>(R.id.gridFrame)
        val parent = oldFrame.parent as ViewGroup
        val index = parent.indexOfChild(oldFrame)
        val lp = oldFrame.layoutParams
        parent.removeView(oldFrame)

        gridView = EditorGridView(this)
        gridView.id = R.id.gridFrame
        gridView.background = ContextCompat.getDrawable(this, R.drawable.bg_glass_button)
        parent.addView(gridView, index, lp)
    }

    /** paletteContainer XML'da LinearLayout, HorizontalScrollView yoki ScrollView bo'lishidan qat'i nazar ishlaydi. */
    private fun resolvePaletteContainer(): ViewGroup {
        val v = findViewById<ViewGroup>(R.id.paletteContainer)
        if (v is HorizontalScrollView || v is ScrollView) {
            val child = v.getChildAt(0)
            if (child is ViewGroup) return child
            val ll = LinearLayout(this)
            ll.orientation = if (v is HorizontalScrollView) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            v.addView(ll)
            return ll
        }
        return v
    }

    private fun buildPalette() {
        val sizePx = (52 * resources.displayMetrics.density).toInt()
        paletteChars.forEach { ch ->
            val chip = TextView(this)
            chip.text = ch
            chip.setTextColor(Color.WHITE)
            chip.background = ContextCompat.getDrawable(this, R.drawable.bg_fire_button)
            chip.gravity = Gravity.CENTER
            chip.textSize = 15f
            val params = LinearLayout.LayoutParams(sizePx, sizePx)
            params.marginEnd = 8
            params.topMargin = 6
            params.bottomMargin = 10
            chip.layoutParams = params
            chip.elevation = 2f

            chip.setOnLongClickListener { v ->
                val clip = ClipData.newPlainText("key", ch)
                // localState null -> grid buni "palitradan yangi tugma" deb tushunadi
                v.startDragAndDrop(clip, View.DragShadowBuilder(v), null, 0)
                true
            }
            paletteContainer.addView(chip)
        }
    }
}
