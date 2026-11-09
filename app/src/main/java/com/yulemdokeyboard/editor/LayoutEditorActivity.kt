package com.yulemdokeyboard.editor

import android.content.ClipData
import android.graphics.Color
import android.graphics.Typeface
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
import com.yulemdokeyboard.Ui
import com.yulemdokeyboard.model.KeyboardLayout
import com.yulemdokeyboard.model.LayoutStorage
import com.yulemdokeyboard.model.defaultUzbekLayout

class LayoutEditorActivity : AppCompatActivity() {

    private lateinit var gridView: EditorGridView
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button

    private val letterSection = listOf(
        "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
        "a", "s", "d", "f", "g", "h", "j", "k", "l",
        "z", "x", "c", "v", "b", "n", "m"
    )
    private val uzLetterSection = listOf("ö", "ğ", "ç", "ş", "\u02BC", "ng")
    private val digitSection = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val punctSection = listOf(",", ".", "!", "?", "-", "@", "#", "%", "&", "(", ")")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.appBackground(this@LayoutEditorActivity)
            setPadding((12 * dp).toInt(), (24 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
        }

        // To'r (grid)
        gridView = EditorGridView(this).apply {
            background = Ui.glass(this@LayoutEditorActivity, 12)
        }
        root.addView(gridView, LinearLayout.LayoutParams(-1, 0, 1f))

        // Palitra (vertikal, scroll)
        val palette = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addPaletteSection(palette, "Harflar", letterSection)
        addPaletteSection(palette, "Özbekka xos", uzLetterSection)
        addPaletteSection(palette, "Raqamlar", digitSection)
        addPaletteSection(palette, "Belgilar", punctSection)
        val paletteScroll = ScrollView(this).apply { addView(palette) }
        root.addView(
            paletteScroll,
            LinearLayout.LayoutParams(-1, (190 * dp).toInt()).apply { topMargin = (8 * dp).toInt() }
        )

        // Pastki tugmalar
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun bar(label: String, fire: Boolean = false): Button =
            Ui.button(this, label, fire).also {
                bottom.addView(it, LinearLayout.LayoutParams(0, (48 * dp).toInt(), 1f).apply {
                    marginStart = (3 * dp).toInt()
                    marginEnd = (3 * dp).toInt()
                })
            }
        btnUndo = bar("↶ Ortga")
        btnRedo = bar("↷ Oldinga")
        val btnReset = bar("Reset")
        val btnSave = bar("Saqlash", fire = true)
        root.addView(
            bottom,
            LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * dp).toInt()
            }
        )

        setContentView(root)

        gridView.onHistoryChanged = { updateUndoRedoState() }
        btnUndo.setOnClickListener { gridView.undo(); updateUndoRedoState() }
        btnRedo.setOnClickListener { gridView.redo(); updateUndoRedoState() }

        val start = LayoutStorage.loadCustomLayout(this) ?: defaultUzbekLayout()
        gridView.rows = start.rows
        gridView.cols = start.cols
        gridView.post { gridView.loadKeys(start.keys); updateUndoRedoState() }

        btnReset.setOnClickListener {
            val def = defaultUzbekLayout()
            gridView.rows = def.rows
            gridView.cols = def.cols
            gridView.loadKeys(def.keys)
            gridView.invalidate()
            updateUndoRedoState()
            Toast.makeText(this, "Standart joylaşuv tiklandi", Toast.LENGTH_SHORT).show()
        }

        btnSave.setOnClickListener {
            val keys = gridView.exportKeys()
            if (keys.isEmpty()) {
                Toast.makeText(this, "Avval kamida bitta tugma joylaştiring", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            LayoutStorage.saveCustomLayout(
                this,
                KeyboardLayout("Mening klaviaturam", gridView.rows, gridView.cols, keys.toMutableList())
            )
            LayoutStorage.setActiveLayout(this, "custom")
            Toast.makeText(this, "Saqlandi! Endi klaviaturada şu joylaşuv işlatiladi.", Toast.LENGTH_LONG).show()
        }
    }

    private fun addPaletteSection(parent: LinearLayout, title: String, chars: List<String>) {
        val label = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#B3FFFFFF"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(4, 10, 4, 4)
        }
        parent.addView(label)

        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sizePx = (48 * resources.displayMetrics.density).toInt()
        chars.forEach { ch ->
            val chip = TextView(this)
            chip.text = ch
            chip.setTextColor(Color.WHITE)
            chip.background = Ui.glass(this, 14)
            chip.gravity = Gravity.CENTER
            chip.textSize = 15f
            val params = LinearLayout.LayoutParams(sizePx, sizePx)
            params.marginEnd = 8
            params.topMargin = 4
            params.bottomMargin = 8
            chip.layoutParams = params
            chip.elevation = 2f

            chip.setOnLongClickListener { v ->
                val clip = ClipData.newPlainText("key", ch)
                // localState null -> grid buni "palitradan yangi tugma" deb tushunadi
                v.startDragAndDrop(clip, View.DragShadowBuilder(v), null, 0)
                true
            }
            row.addView(chip)
        }
        scroll.addView(row)
        parent.addView(scroll)
    }

    private fun updateUndoRedoState() {
        if (!::btnUndo.isInitialized) return
        btnUndo.alpha = if (gridView.canUndo()) 1f else 0.4f
        btnUndo.isEnabled = gridView.canUndo()
        btnRedo.alpha = if (gridView.canRedo()) 1f else 0.4f
        btnRedo.isEnabled = gridView.canRedo()
    }
}
