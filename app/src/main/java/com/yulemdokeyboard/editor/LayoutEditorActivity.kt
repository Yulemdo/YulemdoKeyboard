package com.yulemdokeyboard.editor

import android.content.ClipData
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button

    // Palitra bo'limlarga ajratilgan: harflar, o'zbekka xos harflar, raqamlar, maxsus tugmalar
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
        setContentView(R.layout.activity_layout_editor)

        // MUHIM: avval findViewById<EditorGridView> chaqirilgan edi, lekin XML'da bu oddiy FrameLayout —
        // shu sababli ClassCastException bilan ilova yopilib qolardi. Endi umumiy View sifatida olinadi.
        replaceGridFrameWithEditorGrid()
        paletteContainer = resolvePaletteContainer()
        buildPalette()
        addUndoRedoButtons()

        gridView.onHistoryChanged = { updateUndoRedoState() }

        val saved = LayoutStorage.loadCustomLayout(this)
        val start = saved ?: defaultUzbekLayout()
        gridView.rows = start.rows
        gridView.cols = start.cols
        gridView.post { gridView.loadKeys(start.keys) ; updateUndoRedoState() }

        findViewById<View>(R.id.btnReset).setOnClickListener {
            val def = defaultUzbekLayout()
            gridView.rows = def.rows
            gridView.cols = def.cols
            gridView.loadKeys(def.keys) // yangi boshlanish - tarix tozalanadi
            gridView.invalidate()
            updateUndoRedoState()
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
        // paletteContainer gorizontal bo'lishi mumkin edi — bo'limlar bilan vertikal ro'yxatga o'tkazamiz
        val outerParent = paletteContainer.parent as? ViewGroup
        val vertical = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (outerParent != null) {
            val scrollWrapper = paletteContainer.parent
            if (scrollWrapper is HorizontalScrollView) {
                val idx = (scrollWrapper.parent as ViewGroup).indexOfChild(scrollWrapper)
                val grandParent = scrollWrapper.parent as ViewGroup
                val lp = scrollWrapper.layoutParams
                grandParent.removeView(scrollWrapper)
                grandParent.addView(vertical, idx, lp)
            } else {
                val idx = outerParent.indexOfChild(paletteContainer)
                val lp = paletteContainer.layoutParams
                outerParent.removeView(paletteContainer)
                outerParent.addView(vertical, idx, lp)
            }
        }

        addPaletteSection(vertical, "Harflar", letterSection)
        addPaletteSection(vertical, "O'zbekka xos", uzLetterSection)
        addPaletteSection(vertical, "Raqamlar", digitSection)
        addPaletteSection(vertical, "Belgilar", punctSection)
        paletteContainer = vertical
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
            chip.background = glassChipDrawable()
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

    private fun glassChipDrawable(): GradientDrawable {
        val dp = resources.displayMetrics.density
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#3DFFFFFF"), Color.parseColor("#14FFFFFF"))
        ).apply {
            cornerRadius = 14 * dp
            setStroke((1 * dp).toInt(), Color.parseColor("#33FFFFFF"))
        }
    }

    /**
     * Yangi: "Standartga qaytarish" / "Saqlash" tugmalari joylashgan qatorga
     * Undo va Redo tugmalarini qo'shadi (XML o'zgartirilmaydi - dasturiy ravishda qo'shiladi).
     */
    private fun addUndoRedoButtons() {
        val resetBtn = findViewById<View>(R.id.btnReset)
        val bottomRow = resetBtn.parent as? ViewGroup ?: return
        val dp = resources.displayMetrics.density

        fun glassBtn(label: String): Button = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#3DFFFFFF"), Color.parseColor("#14FFFFFF"))
            ).apply {
                cornerRadius = 16 * dp
                setStroke((1 * dp).toInt(), Color.parseColor("#33FFFFFF"))
            }
        }

        btnUndo = glassBtn("↶ Ortga")
        btnRedo = glassBtn("↷ Oldinga")

        val lp = LinearLayout.LayoutParams(0, (48 * dp).toInt(), 1f).apply {
            marginStart = (6 * dp).toInt()
            marginEnd = (6 * dp).toInt()
        }

        val resetIndex = bottomRow.indexOfChild(resetBtn)
        bottomRow.addView(btnUndo, resetIndex, lp)
        bottomRow.addView(btnRedo, resetIndex + 1, lp)

        btnUndo.setOnClickListener {
            gridView.undo()
            updateUndoRedoState()
        }
        btnRedo.setOnClickListener {
            gridView.redo()
            updateUndoRedoState()
        }
    }

    private fun updateUndoRedoState() {
        if (!::btnUndo.isInitialized) return
        btnUndo.alpha = if (gridView.canUndo()) 1f else 0.4f
        btnUndo.isEnabled = gridView.canUndo()
        btnRedo.alpha = if (gridView.canRedo()) 1f else 0.4f
        btnRedo.isEnabled = gridView.canRedo()
    }
}
