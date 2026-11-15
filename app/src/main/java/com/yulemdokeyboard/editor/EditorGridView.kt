package com.yulemdokeyboard.editor

import android.content.ClipData
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
import com.yulemdokeyboard.Ui
import com.yulemdokeyboard.model.KeyData
import com.yulemdokeyboard.model.SpecialCodes

/**
 * Drag-drop qilib tugmalar joylashtiriladigan ixcham to'r (grid).
 * - Palitradagi chip shu yerga tortib tashlansa (yoki chipga bosilsa - birinchi bo'sh katakka) yangi tugma qo'shiladi.
 * - To'rdagi tugmani bosib turib surilsa -> boshqa katakka ko'chadi.
 * - To'rdagi tugmaga oddiy bosilsa (tap) -> tugma o'chadi (Undo bilan qaytariladi).
 * - Har bir o'zgarish tarixga yoziladi -> undo()/redo().
 * Katak balandligi qat'iy (ixcham), to'r balandligi = qatorlar soni * katak balandligi.
 */
class EditorGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var rows = 4
        private set
    var cols = 10
        private set

    /** Har bir o'zgarishdan keyin chaqiriladi (Undo/Redo tugmalarini yangilash uchun). */
    var onHistoryChanged: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val cellHeightPx = 42f * density

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1AFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val placed = mutableMapOf<Pair<Int, Int>, TextView>()

    private val undoStack = ArrayDeque<List<KeyData>>()
    private val redoStack = ArrayDeque<List<KeyData>>()
    private var suppressHistory = false

    init {
        setWillNotDraw(false)
        setOnDragListener { _, event -> handleDrag(event) }
    }

    /** O'lchov bosqichida aniqlanadi, shunda tugmalar birinchi chizilishdayoq to'g'ri o'lchamda bo'ladi. */
    private var gridW = 0

    private fun cellWidth(): Float {
        val w = if (gridW > 0) gridW else width
        return if (cols > 0) w.toFloat() / cols else 0f
    }
    private fun cellHeight() = cellHeightPx

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (rows * cellHeightPx).toInt()
        gridW = MeasureSpec.getSize(widthMeasureSpec)
        relayoutKeys(request = false)   // bolalar o'lchanishidan oldin to'g'ri o'lcham beriladi
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gridW = w
        relayoutKeys(request = false)
    }

    /** Qator/ustunlar sonini o'zgartiradi (tugmalar qayta joylanadi). */
    fun setGrid(newRows: Int, newCols: Int) {
        rows = newRows.coerceAtLeast(1)
        cols = newCols.coerceAtLeast(1)
        relayoutKeys()
        requestLayout()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cw = cellWidth()
        val ch = cellHeight()
        for (r in 0..rows) canvas.drawLine(0f, r * ch, width.toFloat(), r * ch, gridPaint)
        for (c in 0..cols) canvas.drawLine(c * cw, 0f, c * cw, rows * ch, gridPaint)
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
                val text = event.clipData?.getItemAt(0)?.text?.toString() ?: return false
                val draggedView = event.localState as? TextView
                val cell = cellAt(event.x, event.y) ?: return false

                if (draggedView != null && placed.values.contains(draggedView)) {
                    // Mavjud tugma: o'z joyiga yoki boshqa tugma ustiga tashlansa - o'chadi
                    if (placed[cell] != null) {
                        removeKeyView(draggedView)
                        return true
                    }
                    pushUndoSnapshot()
                    val oldKey = placed.entries.find { it.value == draggedView }?.key
                    if (oldKey != null) placed.remove(oldKey)
                    placeKey(draggedView, cell, draggedView.tag as? KeyData)
                } else {
                    // Palitradan yangi tugma: band katakka qo'shilmaydi
                    if (placed.containsKey(cell)) return false
                    pushUndoSnapshot()
                    val key = keyFromCode(text, cell.first, cell.second)
                    placeKey(createKeyView(editorName(key)), cell, key)
                }
                return true
            }
            else -> return true
        }
    }

    /** Birinchi bo'sh katakka tugma qo'shadi. To'r to'la bo'lsa false. */
    fun addKey(key: KeyData): Boolean {
        for (r in 0 until rows) for (c in 0 until cols) {
            if (!placed.containsKey(r to c)) {
                pushUndoSnapshot()
                key.row = r; key.col = c
                placeKey(createKeyView(editorName(key)), r to c, key)
                return true
            }
        }
        return false
    }

    private fun applyBounds(view: View, cell: Pair<Int, Int>, request: Boolean = true) {
        val cw = cellWidth()
        val ch = cellHeight()
        val inset = (2 * density).toInt()
        val lp = (view.layoutParams as? LayoutParams) ?: LayoutParams(1, 1)
        lp.width = maxOf(1, (cw - inset * 2).toInt())
        lp.height = maxOf(1, (ch - inset * 2).toInt())
        lp.leftMargin = (cell.second * cw).toInt() + inset
        lp.topMargin = (cell.first * ch).toInt() + inset
        if (view.layoutParams !== lp) view.layoutParams = lp
        else if (request) view.requestLayout()
    }

    private fun relayoutKeys(request: Boolean = true) {
        placed.forEach { (cell, view) -> applyBounds(view, cell, request) }
    }

    private fun placeKey(view: TextView, cell: Pair<Int, Int>, data: KeyData?) {
        data?.row = cell.first
        data?.col = cell.second
        view.tag = data
        placed[cell] = view
        applyBounds(view, cell)
        if (view.parent == null) addView(view)
        notifyHistoryChanged()
    }

    private fun createKeyView(name: String): TextView {
        val tv = TextView(context)
        tv.text = name
        tv.setTextColor(Color.WHITE)
        tv.background = Ui.glass(context, 8)
        tv.gravity = Gravity.CENTER
        tv.maxLines = 1
        tv.includeFontPadding = false
        tv.textSize = if (name.length > 2) 9f else 14f
        tv.setPadding(0, 0, 0, 0)
        tv.setOnLongClickListener { v ->
            startKeyDrag(v as TextView)
            true
        }
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
        val code = (view.tag as? KeyData)?.code ?: view.text.toString()
        val clip = ClipData.newPlainText("key", code)
        view.startDragAndDrop(clip, DragShadowBuilder(view), view, 0)
    }

    fun removeAll() {
        placed.values.forEach { removeView(it) }
        placed.clear()
    }

    /** Tugmalarni yuklaydi; kerak bo'lsa to'r o'lchamini kengaytiradi. */
    fun loadKeys(keys: List<KeyData>, resetHistory: Boolean = true) {
        removeAll()
        val needRows = (keys.maxOfOrNull { it.row } ?: 0) + 1
        val needCols = (keys.maxOfOrNull { it.col } ?: 0) + 1
        if (needRows > rows || needCols > cols) setGrid(maxOf(rows, needRows), maxOf(cols, needCols))
        val prev = suppressHistory
        suppressHistory = true
        keys.forEach { k ->
            val copy = k.copy()
            placeKey(createKeyView(editorName(copy)), copy.row to copy.col, copy)
        }
        suppressHistory = prev
        if (resetHistory) {
            undoStack.clear()
            redoStack.clear()
        }
        notifyHistoryChanged()
    }

    /** Hamma tugmani almashtiradi (Undo bilan qaytariladi). */
    fun replaceKeys(keys: List<KeyData>) {
        pushUndoSnapshot()
        loadKeys(keys, resetHistory = false)
    }

    fun exportKeys(): List<KeyData> = placed.values.mapNotNull { it.tag as? KeyData }

    // ===== Undo / Redo =====
    private fun pushUndoSnapshot() {
        if (suppressHistory) return
        undoStack.addLast(exportKeys().map { it.copy() })
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = exportKeys().map { it.copy() }
        val prev = undoStack.removeLast()
        redoStack.addLast(current)
        loadKeys(prev, resetHistory = false)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = exportKeys().map { it.copy() }
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        loadKeys(next, resetHistory = false)
    }

    private fun notifyHistoryChanged() {
        if (!suppressHistory) onHistoryChanged?.invoke()
    }
}

private val SPECIAL_SET = setOf(
    SpecialCodes.SHIFT, SpecialCodes.BACKSPACE, SpecialCodes.ENTER, SpecialCodes.SPACE,
    SpecialCodes.SYMBOLS, SpecialCodes.LETTERS, SpecialCodes.LANG_SWITCH, SpecialCodes.EMOJI,
    SpecialCodes.SYMBOLS1, SpecialCodes.SYMBOLS2
)

/** Palitradagi kod bo'yicha yangi tugma yaratadi (maxsus tugmalar uchun to'g'ri kenglik bilan). */
internal fun keyFromCode(code: String, row: Int = 0, col: Int = 0): KeyData {
    val special = code in SPECIAL_SET
    val w = when (code) {
        SpecialCodes.SHIFT, SpecialCodes.BACKSPACE, SpecialCodes.SYMBOLS -> 1.3f
        SpecialCodes.LETTERS, SpecialCodes.SYMBOLS1, SpecialCodes.SYMBOLS2 -> 1.4f
        SpecialCodes.ENTER -> 1.6f
        SpecialCodes.SPACE -> 4.4f
        else -> 1f
    }
    val label = when (code) {
        SpecialCodes.LETTERS -> "ABC"
        SpecialCodes.SYMBOLS1 -> "123"
        SpecialCodes.SYMBOLS2 -> "=\\<"
        else -> if (special) "" else code
    }
    return KeyData(label, code, row, col, w, special)
}

/** Editorda tugma ustida ko'rinadigan nom (asosiy klaviaturada ikonka chiziladi, bu yerda matn). */
internal fun editorName(k: KeyData): String = when (k.code) {
    SpecialCodes.SHIFT -> "⇧"
    SpecialCodes.BACKSPACE -> "⌫"
    SpecialCodes.ENTER -> "⏎"
    SpecialCodes.SPACE -> "space"
    SpecialCodes.SYMBOLS -> "123"
    SpecialCodes.LETTERS -> "ABC"
    SpecialCodes.SYMBOLS1 -> "123"
    SpecialCodes.SYMBOLS2 -> "=\\<"
    SpecialCodes.LANG_SWITCH -> "🌐"
    SpecialCodes.EMOJI, "\uD83D\uDE42" -> "☺"
    else -> if (k.label.isNotEmpty()) k.label else k.code
}
