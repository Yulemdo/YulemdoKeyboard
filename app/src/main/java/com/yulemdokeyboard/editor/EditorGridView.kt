package com.yulemdokeyboard.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.yulemdokeyboard.model.KeyData

/**
 * Drag-drop qilib tugmalar joylashtiriladigan to'r (grid).
 * - Palette'dagi chip shu yerga tortib tashlansa -> yangi tugma qo'shiladi (eng yaqin katakka "snap" qilinadi).
 * - To'rda turgan tugma qayta tortilsa -> boshqa katakka ko'chadi.
 * - Tugmani uzoq bosib ushlab, chetga tortib chiqarib yuborilsa -> o'chadi (ACTION_DROP tashqarida bo'lsa).
 */
class EditorGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var rows = 4
    var cols = 10

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1FFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // row -> col -> placed key view (key dekorativ qiymatlari bilan)
    private val placed = mutableMapOf<Pair<Int, Int>, TextView>()

    init {
        setWillNotDraw(false)
        setOnDragListener { _, event -> handleDrag(event) }
    }

    private fun cellWidth() = width.toFloat() / cols
    private fun cellHeight() = height.toFloat() / rows

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cw = cellWidth()
        val ch = cellHeight()
        for (r in 0..rows) canvas.drawLine(0f, r * ch, width.toFloat(), r * ch, gridPaint)
        for (c in 0..cols) canvas.drawLine(c * cw, 0f, c * cw, height.toFloat(), gridPaint)
    }

    private fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val cw = cellWidth()
        val ch = cellHeight()
        if (cw <= 0 || ch <= 0) return null
        val col = (x / cw).toInt().coerceIn(0, cols - 1)
        val row = (y / ch).toInt().coerceIn(0, rows - 1)
        return row to col
    }

    private fun handleDrag(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DROP -> {
                val label = event.clipData?.getItemAt(0)?.text?.toString() ?: return false
                val draggedView = event.localState as? TextView
                val cell = cellAt(event.x, event.y) ?: return false

                // Agar shu katakda allaqachon boshqa tugma bo'lsa, eski joyni bo'shatamiz.
                if (placed.containsKey(cell) && placed[cell] != draggedView) {
                    return false // band katak - tashlashga ruxsat yo'q
                }

                if (draggedView != null && placed.values.contains(draggedView)) {
                    // Mavjud tugma ko'chmoqda - eski o'rnini bo'shatamiz
                    val oldKey = placed.entries.find { it.value == draggedView }?.key
                    if (oldKey != null) placed.remove(oldKey)
                    placeKey(draggedView, cell, (draggedView.tag as? KeyData))
                } else {
                    // Palette'dan yangi tugma qo'shilmoqda
                    val newView = createKeyView(label)
                    placeKey(newView, cell, KeyData(label = label, code = label, row = cell.first, col = cell.second))
                }
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                if (!event.result) {
                    // chetga tashlab yuborildi -> hech narsa qilmaymiz (joyida qoladi)
                }
                return true
            }
            else -> return true
        }
    }

    private fun placeKey(view: TextView, cell: Pair<Int, Int>, data: KeyData?) {
        if (view.parent == null) addView(view)
        val cw = cellWidth()
        val ch = cellHeight()
        val inset = (3 * resources.displayMetrics.density).toInt()
        val lp = LayoutParams((cw - inset * 2).toInt(), (ch - inset * 2).toInt())
        lp.leftMargin = (cell.second * cw).toInt() + inset
        lp.topMargin = (cell.first * ch).toInt() + inset
        view.layoutParams = lp
        data?.row = cell.first
        data?.col = cell.second
        view.tag = data
        placed[cell] = view
    }

    private fun createKeyView(label: String): TextView {
        val tv = TextView(context)
        tv.text = label
        tv.setTextColor(Color.WHITE)
        tv.background = androidx.core.content.ContextCompat.getDrawable(context, com.yulemdokeyboard.R.drawable.bg_glass_button)
        tv.gravity = Gravity.CENTER
        tv.textSize = 13f
        val pad = (4 * resources.displayMetrics.density).toInt()
        tv.setPadding(pad, pad, pad, pad)
        tv.setOnLongClickListener { v ->
            startKeyDrag(v as TextView)
            true
        }
        return tv
    }

    private fun startKeyDrag(view: TextView) {
        val clip = android.content.ClipData.newPlainText("key", view.text)
        val shadow = DragShadowBuilder(view)
        view.startDragAndDrop(clip, shadow, view, 0)
    }

    fun removeAll() {
        placed.values.forEach { removeView(it) }
        placed.clear()
    }

    fun loadKeys(keys: List<KeyData>) {
        removeAll()
        keys.forEach { k ->
            val tv = createKeyView(k.label)
            placeKey(tv, k.row to k.col, k.copy())
        }
    }

    fun exportKeys(): List<KeyData> {
        return placed.values.mapNotNull { it.tag as? KeyData }
    }
}
