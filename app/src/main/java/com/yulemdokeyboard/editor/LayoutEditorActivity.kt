package com.yulemdokeyboard.editor

import android.content.ClipData
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yulemdokeyboard.AppStrings
import com.yulemdokeyboard.Ui
import com.yulemdokeyboard.model.KeyData
import com.yulemdokeyboard.model.KeyboardLayout
import com.yulemdokeyboard.model.Languages
import com.yulemdokeyboard.model.LayoutStorage
import com.yulemdokeyboard.model.SpecialCodes

/**
 * Klaviatura muharriri. Yuqoridagi til tugmalari orqali istalgan tilning (uz, kaa, en, ru)
 * layoutini tanlab tahrirlash mumkin. Har bir til uchun layout alohida saqlanadi.
 */
class LayoutEditorActivity : AppCompatActivity() {

    private lateinit var gridView: EditorGridView
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button
    private lateinit var langBar: LinearLayout
    private lateinit var palette: LinearLayout

    private var curLang = Languages.UZ
    private var baseSig = ""

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tr(uz: String, ru: String, en: String, kaa: String): String =
        when (AppStrings.getLang(this)) {
            AppStrings.RU -> ru
            AppStrings.EN -> en
            AppStrings.KAA -> kaa
            else -> uz
        }

    // ===== Palitra tarkibi =====
    private val latin = ('a'..'z').map { it.toString() }
    private val cyrillic = listOf(
        "й", "ц", "у", "к", "е", "ё", "н", "г", "ш", "щ", "з", "х", "ъ",
        "ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э",
        "я", "ч", "с", "м", "и", "т", "ь", "б", "ю"
    )
    private val uzExtra = listOf("ö", "ğ", "ç", "ş", "\u02BC", "ng")
    private val kaaExtra = listOf("\u00E1", "\u01F5", "\u00ED", "\u0144", "\u00F3", "\u00FA")
    private val specials = listOf(
        SpecialCodes.SHIFT, SpecialCodes.BACKSPACE, SpecialCodes.ENTER,
        SpecialCodes.SPACE, SpecialCodes.SYMBOLS, SpecialCodes.LANG_SWITCH
    )
    private val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val punct = listOf(",", ".", "!", "?", "-", "@", "#", "%", "&", "(", ")")

    private fun sectionsFor(lang: String): List<Pair<String, List<String>>> {
        val out = mutableListOf<Pair<String, List<String>>>()
        when (lang) {
            Languages.RU -> out.add(tr("Harflar", "Буквы", "Letters", "Háripler") to cyrillic)
            Languages.UZ -> {
                out.add(tr("Harflar", "Буквы", "Letters", "Háripler") to latin)
                out.add(tr("Özbekka xos", "Узбекские", "Uzbek letters", "Ózbekshe") to uzExtra)
            }
            Languages.KAA -> {
                out.add(tr("Harflar", "Буквы", "Letters", "Háripler") to latin)
                out.add(tr("Qaraqalpaqqa xos", "Каракалпакские", "Karakalpak letters", "Qaraqalpaqsha") to kaaExtra)
            }
            else -> out.add(tr("Harflar", "Буквы", "Letters", "Háripler") to latin)
        }
        out.add(tr("Tugmalar", "Клавиши", "Keys", "Túymeler") to specials)
        out.add(tr("Raqamlar", "Цифры", "Digits", "Sanlar") to digits)
        out.add(tr("Belgilar", "Знаки", "Symbols", "Belgiler") to punct)
        return out
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.appBackground(this@LayoutEditorActivity)
            setPadding(dp(10), dp(20), dp(10), dp(10))
        }

        // Til tanlash paneli
        langBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val langScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(langBar)
        }
        root.addView(langScroll, LinearLayout.LayoutParams(-1, dp(38)))

        // Qisqa yo'riqnoma
        root.addView(TextView(this).apply {
            text = tr(
                "Tugmani bossangiz o'çadi · bosib turib suring · palitradagi tugmani bossangiz bo'ş katakka qöşiladi",
                "Нажатие на клавишу удаляет её · удерживайте и перетащите · нажатие на палитру добавляет в свободную клетку",
                "Tap a key to remove it · hold and drag to move · tap a palette key to add it to the first free cell",
                "Túymeni bassańız óshedi · uslap sürkeń · palitradaǵı túymeni bassańız bos katekke qosíladí"
            )
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 11f
            setPadding(dp(2), dp(8), dp(2), dp(6))
        })

        // Ixcham to'r
        gridView = EditorGridView(this).apply { background = Ui.glass(this@LayoutEditorActivity, 12) }
        root.addView(gridView, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Palitra (scroll)
        palette = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val paletteScroll = ScrollView(this).apply { addView(palette) }
        root.addView(paletteScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })

        // Pastki tugmalar (ixcham)
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun bar(label: String, fire: Boolean = false): Button =
            Ui.button(this, label, fire).also {
                it.minHeight = 0
                it.minimumHeight = 0
                it.setPadding(0, 0, 0, 0)
                it.textSize = 13f
                bottom.addView(it, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    marginStart = dp(3); marginEnd = dp(3)
                })
            }
        btnUndo = bar("↶ " + tr("Ortga", "Назад", "Undo", "Artqa"))
        btnRedo = bar("↷ " + tr("Oldinga", "Вперёд", "Redo", "Aldına"))
        val btnReset = bar(tr("Standart", "Сброс", "Reset", "Standart"))
        val btnSave = bar(tr("Saqlaş", "Сохранить", "Save", "Saqlaw"), fire = true)
        root.addView(bottom, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        setContentView(root)

        gridView.onHistoryChanged = { updateUndoRedoState() }
        btnUndo.setOnClickListener { gridView.undo(); updateUndoRedoState() }
        btnRedo.setOnClickListener { gridView.redo(); updateUndoRedoState() }

        btnReset.setOnClickListener {
            val def = LayoutStorage.defaultLayoutFor(curLang)
            gridView.setGrid(def.rows, def.cols)
            gridView.replaceKeys(def.keys)
            updateUndoRedoState()
            toast(tr("Standart joylaşuv tiklandi (saqlash uçun «Saqlaş»)",
                "Стандартная раскладка восстановлена (нажмите «Сохранить»)",
                "Default layout restored (tap Save to keep it)",
                "Standart jaylasıw tiklendi (saqlaw ushín «Saqlaw»)"))
        }

        btnSave.setOnClickListener { save() }

        val start = LayoutStorage.getLanguage(this).let { if (it in Languages.order) it else Languages.UZ }
        loadLanguage(start)
    }

    // ===== Til almashtirish =====
    private fun selectLang(l: String) {
        if (l == curLang) return
        if (!isDirty()) { loadLanguage(l); return }
        AlertDialog.Builder(this)
            .setTitle(tr("Saqlanmagan o'zgarişlar", "Несохранённые изменения", "Unsaved changes", "Saqlanbaǵan ózgerisler"))
            .setMessage(Languages.nativeName(curLang))
            .setPositiveButton(tr("Saqlaş", "Сохранить", "Save", "Saqlaw")) { _, _ -> if (save()) loadLanguage(l) }
            .setNegativeButton(tr("Saqlamasdan", "Не сохранять", "Discard", "Saqlamastan")) { _, _ -> loadLanguage(l) }
            .setNeutralButton(tr("Bekor", "Отмена", "Cancel", "Biykar")) { d, _ -> d.dismiss() }
            .show()
    }

    private fun loadLanguage(lang: String) {
        curLang = lang
        val layout = LayoutStorage.getLayoutFor(this, lang)
        gridView.setGrid(layout.rows, layout.cols)
        gridView.loadKeys(layout.keys)
        baseSig = signature(gridView.exportKeys())
        renderLangBar()
        renderPalette()
        updateUndoRedoState()
    }

    private fun signature(keys: List<KeyData>): String =
        keys.sortedWith(compareBy({ it.row }, { it.col }))
            .joinToString(";") { "${it.row},${it.col},${it.code},${it.width}" }

    private fun isDirty() = signature(gridView.exportKeys()) != baseSig

    private fun save(): Boolean {
        val keys = gridView.exportKeys()
        if (keys.isEmpty()) {
            toast(tr("Avval kamida bitta tugma joylaştiring", "Сначала добавьте хотя бы одну клавишу",
                "Add at least one key first", "Aldın keminde bir túyme jaylastırıń"))
            return false
        }
        val def = LayoutStorage.defaultLayoutFor(curLang)
        if (signature(keys) == signature(def.keys)) {
            LayoutStorage.clearCustomLayout(this, curLang)   // standartga teng - custom kerak emas
        } else {
            LayoutStorage.saveCustomLayout(
                this, curLang,
                KeyboardLayout(Languages.nativeName(curLang), gridView.rows, gridView.cols, keys.toMutableList())
            )
        }
        baseSig = signature(keys)
        renderLangBar()
        toast(tr("Saqlandi! Endi klaviaturada şu joylaşuv işlatiladi.", "Сохранено! Раскладка применена.",
            "Saved! The keyboard now uses this layout.", "Saqlandı! Endi klaviaturada usı jaylasıw isleydi."))
        return true
    }

    // ===== UI qismlari =====
    private fun renderLangBar() {
        langBar.removeAllViews()
        Languages.order.forEach { l ->
            val sel = l == curLang
            val custom = LayoutStorage.hasCustomLayout(this, l)
            val tv = TextView(this).apply {
                text = Languages.nativeName(l) + if (custom) " •" else ""
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(14), 0)
                setTextColor(if (sel) Color.WHITE else Ui.TEXT_PRIMARY)
                typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                background = if (sel) Ui.fire(this@LayoutEditorActivity, 16) else Ui.glass(this@LayoutEditorActivity, 16)
                setOnClickListener { selectLang(l) }
            }
            langBar.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, -1).apply {
                marginEnd = dp(6)
            })
        }
    }

    private fun renderPalette() {
        palette.removeAllViews()
        val chip = dp(40)
        val m = dp(2)
        val avail = resources.displayMetrics.widthPixels - dp(20)
        val colCount = (avail / (chip + 2 * m)).coerceAtLeast(4)

        sectionsFor(curLang).forEach { (title, codes) ->
            palette.addView(TextView(this).apply {
                text = title
                setTextColor(Color.parseColor("#B3FFFFFF"))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(3), dp(8), dp(3), dp(2))
            })
            val grid = GridLayout(this).apply { columnCount = colCount }
            codes.forEach { code ->
                val name = editorName(keyFromCode(code))
                val tv = TextView(this).apply {
                    text = name
                    setTextColor(Color.WHITE)
                    background = Ui.glass(this@LayoutEditorActivity, 10)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    includeFontPadding = false
                    textSize = if (name.length > 2) 9f else 14f
                    // tap: birinchi bo'sh katakka qo'shadi
                    setOnClickListener {
                        if (!gridView.addKey(keyFromCode(code))) {
                            toast(tr("To'r to'la", "Сетка заполнена", "Grid is full", "Tor tolǵan"))
                        }
                        updateUndoRedoState()
                    }
                    // uzoq bosish: sudrab tashlash
                    setOnLongClickListener { v ->
                        v.startDragAndDrop(ClipData.newPlainText("key", code), View.DragShadowBuilder(v), null, 0)
                        true
                    }
                }
                grid.addView(tv, GridLayout.LayoutParams().apply {
                    width = chip; height = chip
                    setMargins(m, m, m, m)
                })
            }
            palette.addView(grid)
        }
    }

    private fun updateUndoRedoState() {
        if (!::btnUndo.isInitialized) return
        btnUndo.alpha = if (gridView.canUndo()) 1f else 0.4f
        btnUndo.isEnabled = gridView.canUndo()
        btnRedo.alpha = if (gridView.canRedo()) 1f else 0.4f
        btnRedo.isEnabled = gridView.canRedo()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
