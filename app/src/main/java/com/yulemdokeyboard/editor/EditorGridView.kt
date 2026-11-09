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
import com.yulemdokeyboard.model.SpecialCodes

/**
 * Drag-drop qilib tugmalar joylashtiriladigan to'r (grid).
 * - Palette'dagi chip shu yerga tortib tashlansa -> yangi tugma qo'shiladi (eng yaqin katakka "snap" qilinadi).
 * - To'rda turgan tugma qayta tortilsa -> boshqa katakka ko'chadi.
 * - To'rdagi tugmaga oddiy bosilsa (tap) -> o'sha tugma o'chiriladi (undo bilan qaytarish mumkin).
 * - Har bir o'zgarish (qo'shish/ko'chirish/o'chirish) tarixga yoziladi -> undo()/redo().
 */
class EditorGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var rows = 4
    var cols = 10

    /** Har bir o'zgarishdan keyin chaqiriladi (Undo/Redo tugmalarini yangilash uchun). */
    var onHistoryChanged: (() -> Unit)? = null

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1FFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // row -> col -> placed key view (key dekorativ qiymatlari bilan)
    private val placed = mutableMapOf<Pair<Int, Int>, TextView>()

    // ===== Undo / Redo =====
    private val undoStack = ArrayDeque<List<KeyData>>()
    private val redoStack = ArrayDeque<List<KeyData>>()
    private var suppressHistory = false

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

                pushUndoSnapshot()
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
        // Yangi: oddiy bosish (tap) - tugmani o'chiradi. Undo bilan qaytarish mumkin.
        tv.setOnClickListener { v -> removeKeyView(v as TextView) }
        return tv
    }

    private fun removeKeyView(view: TextView) {
        val cell = placed.entries.find { it.value == view }?.key ?: return
        pushUndoSnapshot()
        placed.remove(cell)
        removeView(view)
        notifyHistoryChanged()
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

    /** resetHistory = true bo'lsa (masalan boshlang'ich yuklashda yoki "Standartga qaytarish"da) undo/redo tozalanadi. */
    fun loadKeys(keys: List<KeyData>, resetHistory: Boolean = true) {
        removeAll()
        keys.forEach { k ->
            val tv = createKeyView(editorName(k))
            placeKey(tv, k.row to k.col, k.copy())
        }
        if (resetHistory) {
            undoStack.clear()
            redoStack.clear()
            notifyHistoryChanged()
        }
    }

    fun exportKeys(): List<KeyData> {
        return placed.values.mapNotNull { it.tag as? KeyData }
    }

    // ===== Undo / Redo mantig'i =====
    private fun pushUndoSnapshot() {
        if (suppressHistory) return
        undoStack.addLast(exportKeys().map { it.copy() })
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
        notifyHistoryChanged()
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = exportKeys().map { it.copy() }
        val prev = undoStack.removeLast()
        redoStack.addLast(current)
        suppressHistory = true
        loadKeys(prev, resetHistory = false)
        suppressHistory = false
        notifyHistoryChanged()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = exportKeys().map { it.copy() }
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        suppressHistory = true
        loadKeys(next, resetHistory = false)
        suppressHistory = false
        notifyHistoryChanged()
    }

    private fun notifyHistoryChanged() {
        onHistoryChanged?.invoke()
    }
}

/** Editorda tugma ustida ko'rinadigan nom (asosiy klaviaturada ikonka chiziladi, bu yerda matn). */
internal fun editorName(k: KeyData): String = when (k.code) {
    SpecialCodes.SHIFT -> "⇧"
    SpecialCodes.BACKSPACE -> "⌫"
    SpecialCodes.ENTER -> "⏎"
    SpecialCodes.SPACE -> "space"
    SpecialCodes.SYMBOLS -> "123"
    SpecialCodes.LETTERS -> "ABC"
    SpecialCodes.LANG_SWITCH -> "🌐"
    SpecialCodes.EMOJI, "\uD83D\uDE42" -> "☺"
    else -> if (k.label.isNotEmpty()) k.label else k.code
}
