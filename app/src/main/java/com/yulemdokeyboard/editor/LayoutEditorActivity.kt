package com.yulemdokeyboard.editor

import android.content.ClipData
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import android.content.res.ColorStateList
import com.yulemdokeyboard.AppStrings
import com.yulemdokeyboard.Ui
import com.yulemdokeyboard.model.KeyData
import com.yulemdokeyboard.model.KeyboardLayout
import com.yulemdokeyboard.model.Languages
import com.yulemdokeyboard.model.LayoutStorage
import com.yulemdokeyboard.model.SpecialCodes
import com.yulemdokeyboard.model.blankLayout

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
    private var symDeleteMode = false
    private var curPage = 0            // 0 - harflar (tanlangan til), 1 - Belgilar 1, 2 - Belgilar 2
    private lateinit var pageBar: LinearLayout
    private lateinit var paletteScroll: ScrollView
    private lateinit var enabledSwitch: SwitchCompat
    private lateinit var sizeBar: LinearLayout
    private lateinit var rowsLabel: TextView
    private lateinit var colsLabel: TextView

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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

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
        // Yuqori qator: chiqish tugmasi + til tugmalari
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val btnClose = TextView(this).apply {
            text = "✕"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.glass(this@LayoutEditorActivity, 16)
            setOnClickListener { confirmLeave { finish() } }
        }
        topRow.addView(btnClose, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(8) })
        topRow.addView(langScroll, LinearLayout.LayoutParams(0, dp(38), 1f))
        root.addView(topRow, LinearLayout.LayoutParams(-1, dp(38)))

        // Sahifa tanlash: harflar / belgilar 1 / belgilar 2
        pageBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(pageBar, LinearLayout.LayoutParams(-1, dp(34)).apply { topMargin = dp(6) })

        // Qisqa yo'riqnoma
        root.addView(TextView(this).apply {
            text = tr(
                "Tugmani bossangiz o'çadi · bosib turib suring (palitraga yoki boshqa tugma ustiga tashlasangiz o'çadi) · palitradagi tugmani bossangiz bo'ş katakka qöşiladi · öz tilingiz uçun «Yangi til»",
                "Нажатие на клавишу удаляет её · удерживайте и перетащите · нажатие на палитру добавляет в свободную клетку",
                "Tap a key to remove it · hold and drag to move · tap a palette key to add it to the first free cell",
                "Túymeni bassańız óshedi · uslap sürkeń · palitradaǵı túymeni bassańız bos katekke qosíladí"
            )
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 11f
            setPadding(dp(2), dp(8), dp(2), dp(6))
        })

        // Qator / ustun soni
        sizeBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        rowsLabel = TextView(this)
        colsLabel = TextView(this)
        fun stepBtn(t: String, onClick: () -> Unit) = TextView(this).apply {
            text = t; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            background = Ui.glass(this@LayoutEditorActivity, 8)
            setOnClickListener { onClick() }
        }
        fun group(label: TextView, minus: () -> Unit, plus: () -> Unit) {
            label.setTextColor(Ui.TEXT_SECONDARY); label.textSize = 12f
            label.setPadding(0, 0, dp(6), 0)
            sizeBar.addView(label)
            sizeBar.addView(stepBtn("−", minus), LinearLayout.LayoutParams(dp(30), dp(28)).apply { marginEnd = dp(4) })
            sizeBar.addView(stepBtn("+", plus), LinearLayout.LayoutParams(dp(30), dp(28)).apply { marginEnd = dp(16) })
        }
        group(rowsLabel, { resize(-1, 0) }, { resize(1, 0) })
        group(colsLabel, { resize(0, -1) }, { resize(0, 1) })
        root.addView(sizeBar, LinearLayout.LayoutParams(-1, dp(32)))

        // Shu til klaviaturada (globus tugmasida) yoqilganmi
        enabledSwitch = SwitchCompat(this).apply {
            textSize = 13f
            setTextColor(Ui.TEXT_PRIMARY)
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Ui.FIRE_1, Color.parseColor("#BDC1C6"))
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.parseColor("#66FFC107"), Color.parseColor("#33FFFFFF"))
            )
        }
        root.addView(enabledSwitch, LinearLayout.LayoutParams(-1, dp(34)))

        // Ixcham to'r
        gridView = EditorGridView(this).apply { background = Ui.glass(this@LayoutEditorActivity, 12) }
        root.addView(gridView, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Palitra (scroll)
        palette = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        paletteScroll = ScrollView(this).apply {
            addView(palette)
            setOnDragListener { _, e -> handlePaletteDrag(e) }   // tugmani palitra ustiga tashlash = o'chirish
        }
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

        // Orqaga tugmasi ham xuddi shu popup'ni ochadi
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { confirmLeave { finish() } }
        })

        gridView.onHistoryChanged = { updateUndoRedoState() }
        btnUndo.setOnClickListener { gridView.undo(); updateUndoRedoState() }
        btnRedo.setOnClickListener { gridView.redo(); updateUndoRedoState() }

        btnReset.setOnClickListener {
            val def = if (curPage == 0) LayoutStorage.defaultLayoutFor(curLang) else LayoutStorage.defaultSymbolsLayout(curPage)
            gridView.setGrid(def.rows, def.cols)
            gridView.replaceKeys(def.keys)
            updateUndoRedoState()
            updateSizeLabels()
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
        if (l == curLang && curPage == 0) return
        confirmLeave { loadLanguage(l) }
    }

    /** Saqlanmagan o'zgarish bo'lsa so'raydi, keyin [then] ni bajaradi. */
    private fun confirmLeave(then: () -> Unit) {
        if (!isDirty()) { then(); return }
        AlertDialog.Builder(this)
            .setTitle(tr("Saqlanmagan o'zgarişlar", "Несохранённые изменения", "Unsaved changes", "Saqlanbaǵan ózgerisler"))
            .setMessage(currentTitle())
            .setPositiveButton(tr("Saqlaş", "Сохранить", "Save", "Saqlaw")) { _, _ -> if (save()) then() }
            .setNegativeButton(tr("Saqlamasdan", "Не сохранять", "Discard", "Saqlamastan")) { _, _ -> then() }
            .setNeutralButton(tr("Bekor", "Отмена", "Cancel", "Biykar")) { d, _ -> d.dismiss() }
            .show()
    }

    // ===== O'z til yaratish / o'chirish =====
    private fun promptNewLanguage() {
        confirmLeave {
            val input = EditText(this).apply {
                setSingleLine()
                hint = tr("Til nomi (masalan: Mening tilim)", "Название языка", "Language name", "Til atı")
                setPadding(dp(20), dp(14), dp(20), dp(14))
            }
            AlertDialog.Builder(this)
                .setTitle(tr("Yangi til", "Новый язык", "New language", "Taza til"))
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isEmpty()) toast(tr("Nom yozing", "Введите название", "Enter a name", "Atın jazıń"))
                    else chooseTemplate(name)
                }
                .setNegativeButton(tr("Bekor", "Отмена", "Cancel", "Biykar"), null)
                .show()
        }
    }

    private fun chooseTemplate(name: String) {
        val labels = arrayOf(
            tr("Bo'ş (0 dan boşlaş)", "Пустая (с нуля)", "Blank (from scratch)", "Bos (0 den)")
        ) + Languages.builtin.map { tr("Nusxa: ", "Копия: ", "Copy of: ", "Nusqa: ") + Languages.nativeName(it) }
        AlertDialog.Builder(this)
            .setTitle(tr("Boşlang'ich joylaşuv", "Начальная раскладка", "Starting layout", "Baslanǵısh jaylasıw"))
            .setItems(labels) { _, i ->
                val layout = if (i == 0) blankLayout(name) else {
                    val src = LayoutStorage.getLayoutFor(this, Languages.builtin[i - 1])
                    KeyboardLayout(name, src.rows, src.cols, src.keys.map { it.copy() }.toMutableList())
                }
                val id = LayoutStorage.addCustomLanguage(this, name, layout)
                loadLanguage(id)
            }
            .show()
    }

    private fun confirmDeleteLanguage(id: String) {
        AlertDialog.Builder(this)
            .setTitle(Languages.nativeName(id))
            .setMessage(tr("Bu til va uning joylaşuvi o'çiriladi.", "Этот язык и его раскладка будут удалены.",
                "This language and its layout will be deleted.", "Bul til hám onıń jaylasıwı óshiriledi."))
            .setPositiveButton(tr("O'çirish", "Удалить", "Delete", "Óshiriw")) { _, _ ->
                val wasCurrent = id == curLang
                LayoutStorage.deleteCustomLanguage(this, id)
                if (wasCurrent) loadLanguage(Languages.UZ) else renderLangBar()
            }
            .setNegativeButton(tr("Bekor", "Отмена", "Cancel", "Biykar"), null)
            .show()
    }

    // ===== Qator / ustun =====
    private fun resize(dr: Int, dc: Int) {
        val keys = gridView.exportKeys()
        val nr = gridView.rows + dr
        val nc = gridView.cols + dc
        if (nr < 2 || nr > 6 || nc < 4 || nc > 14) return
        if (dr < 0 && keys.any { it.row >= nr } || dc < 0 && keys.any { it.col >= nc }) {
            toast(tr("Avval oxirgi qator/ustundagi tugmalarni olib tashlang",
                "Сначала уберите клавиши из последней строки/столбца",
                "Remove the keys in the last row/column first",
                "Aldın sońǵı qatar/ustındaǵı túymelerdi alıp taslań"))
            return
        }
        gridView.setGrid(nr, nc)
        updateSizeLabels()
    }

    /** Joriy tilning "klaviaturada yoqilgan" holatini ko'rsatadi; o'zgartirilsa darhol saqlanadi. */
    private fun updateEnabledSwitch() {
        val l = curLang
        enabledSwitch.setOnCheckedChangeListener(null)
        enabledSwitch.text = tr("Klaviaturada yoniq (globus tugmasi)", "Включён в клавиатуре (кнопка глобуса)",
            "Enabled on the keyboard (globe key)", "Klaviaturada qosılǵan (globus túymesi)")
        enabledSwitch.isChecked = l in LayoutStorage.getEnabledLanguages(this)
        enabledSwitch.setOnCheckedChangeListener { v, checked ->
            if (!LayoutStorage.setLanguageEnabled(this, l, checked)) {
                v.setOnCheckedChangeListener(null)
                v.isChecked = true
                updateEnabledSwitch()
                toast(tr("Kamida bitta til yoniq qolişi kerak", "Должен остаться хотя бы один язык",
                    "At least one language must stay on", "Keminde bir til qosılǵan bolıwı kerek"))
            }
        }
    }

    private fun updateSizeLabels() {
        rowsLabel.text = tr("Qator ", "Строк ", "Rows ", "Qatar ") + gridView.rows
        colsLabel.text = tr("Ustun ", "Столбцов ", "Cols ", "Ustın ") + gridView.cols
    }

    private fun loadLanguage(lang: String) = loadTarget(lang, 0)

    private fun loadTarget(lang: String, page: Int) {
        curLang = lang
        curPage = page
        val layout = if (page == 0) LayoutStorage.getLayoutFor(this, lang)
                     else LayoutStorage.getSymbolsLayout(this, page)
        gridView.setGrid(layout.rows, layout.cols)
        gridView.loadKeys(layout.keys)
        baseSig = fullSig()
        updateSizeLabels()
        enabledSwitch.visibility = if (page == 0) View.VISIBLE else View.GONE
        updateEnabledSwitch()
        renderLangBar()
        renderPageBar()
        renderPalette()
        updateUndoRedoState()
    }

    private fun pageName(p: Int) = when (p) {
        1 -> tr("Belgilar 1", "Символы 1", "Symbols 1", "Belgiler 1")
        2 -> tr("Belgilar 2", "Символы 2", "Symbols 2", "Belgiler 2")
        else -> tr("Harflar", "Буквы", "Letters", "Háripler")
    }

    private fun currentTitle() =
        if (curPage == 0) Languages.nativeName(curLang) else pageName(curPage)

    private fun renderPageBar() {
        pageBar.removeAllViews()
        for (p in 0..2) {
            val sel = p == curPage
            pageBar.addView(TextView(this).apply {
                text = pageName(p)
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(if (sel) Color.WHITE else Ui.TEXT_PRIMARY)
                typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                background = if (sel) Ui.fire(this@LayoutEditorActivity, 14) else Ui.glass(this@LayoutEditorActivity, 14)
                val custom = p != 0 && LayoutStorage.hasCustomSymbols(this@LayoutEditorActivity, p)
                if (custom) text = "${pageName(p)} •"
                setOnClickListener {
                    if (p != curPage) confirmLeave { loadTarget(curLang, p) }
                }
            }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
    }

    private fun signature(keys: List<KeyData>): String =
        keys.sortedWith(compareBy({ it.row }, { it.col }))
            .joinToString(";") { "${it.row},${it.col},${it.code},${it.width}" }

    private fun fullSig() = "${gridView.rows}x${gridView.cols}|" + signature(gridView.exportKeys())

    private fun isDirty() = fullSig() != baseSig

    private fun save(): Boolean {
        val keys = gridView.exportKeys()
        if (keys.isEmpty()) {
            toast(tr("Avval kamida bitta tugma joylaştiring", "Сначала добавьте хотя бы одну клавишу",
                "Add at least one key first", "Aldın keminde bir túyme jaylastırıń"))
            return false
        }
        if (curPage != 0) {
            // Belgilar sahifasidan harflarga qaytish tugmasi (ABC) bo'lmasa klaviatura qotib qoladi
            if (keys.none { it.code == SpecialCodes.LETTERS }) {
                toast(tr("«ABC» tugmasini qoldiring - usiz harflarga qaytib bo'lmaydi",
                    "Оставьте клавишу «ABC» — без неё не вернуться к буквам",
                    "Keep the “ABC” key, otherwise you can't get back to letters",
                    "«ABC» túymesin qaldırıń — onsız háriplerge qaytıp bolmaydı"))
                return false
            }
            val def = LayoutStorage.defaultSymbolsLayout(curPage)
            if (signature(keys) == signature(def.keys)) LayoutStorage.clearSymbolsLayout(this, curPage)
            else LayoutStorage.saveSymbolsLayout(
                this, curPage, KeyboardLayout(pageName(curPage), gridView.rows, gridView.cols, keys.toMutableList())
            )
        } else {
            val def = LayoutStorage.defaultLayoutFor(curLang)
            if (signature(keys) == signature(def.keys)) {
                LayoutStorage.clearCustomLayout(this, curLang)   // standartga teng - custom kerak emas
            } else {
                LayoutStorage.saveCustomLayout(
                    this, curLang,
                    KeyboardLayout(Languages.nativeName(curLang), gridView.rows, gridView.cols, keys.toMutableList())
                )
            }
        }
        baseSig = fullSig()
        renderLangBar()
        renderPageBar()
        toast(tr("Saqlandi! Endi klaviaturada şu joylaşuv işlatiladi.", "Сохранено! Раскладка применена.",
            "Saved! The keyboard now uses this layout.", "Saqlandı! Endi klaviaturada usı jaylasıw isleydi."))
        return true
    }

    // ===== UI qismlari =====
    private fun renderLangBar() {
        langBar.removeAllViews()
        Languages.order.forEach { l ->
            val sel = l == curLang && curPage == 0
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
                if (!Languages.isBuiltin(l)) setOnLongClickListener { confirmDeleteLanguage(l); true }
            }
            langBar.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, -1).apply {
                marginEnd = dp(6)
            })
        }
        langBar.addView(TextView(this).apply {
            text = "＋ " + tr("Yangi til", "Новый язык", "New language", "Taza til")
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            setTextColor(Color.parseColor("#FFC107"))
            background = Ui.glass(this@LayoutEditorActivity, 16)
            setOnClickListener { promptNewLanguage() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, -1))
    }

    /** To'rdagi tugma palitra ustiga sudrab tashlansa o'chadi. */
    private fun handlePaletteDrag(e: DragEvent): Boolean {
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> return true
            DragEvent.ACTION_DROP -> {
                if (gridView.removeIfPlaced(e.localState)) updateUndoRedoState()
                return true
            }
        }
        return true
    }

    /** keepScroll = true bo'lsa palitra qayta chizilganda scroll joyida qoladi. */
    private fun renderPalette(keepScroll: Boolean = false) {
        val y = if (keepScroll) paletteScroll.scrollY else 0
        renderPaletteInner()
        if (keepScroll) paletteScroll.post { paletteScroll.scrollTo(0, y) }
    }

    private fun renderPaletteInner() {
        palette.removeAllViews()
        val chip = dp(40)
        val m = dp(2)
        val avail = resources.displayMetrics.widthPixels - dp(20)
        val colCount = (avail / (chip + 2 * m)).coerceAtLeast(4)

        renderMySymbols(chip, m, colCount)

        val secs = when {
            curPage != 0 -> sectionsForSymbols()
            Languages.isBuiltin(curLang) -> sectionsFor(curLang)
            else -> sectionsForCustom()
        }
        secs.forEach { (title, codes) ->
            palette.addView(sectionTitle(title))
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

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor("#B3FFFFFF"))
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(3), dp(8), dp(3), dp(2))
    }

    /** Belgilar sahifalari uchun palitra. */
    private fun sectionsForSymbols(): List<Pair<String, List<String>>> = listOf(
        tr("Tugmalar", "Клавиши", "Keys", "Túymeler") to listOf(
            SpecialCodes.BACKSPACE, SpecialCodes.ENTER, SpecialCodes.SPACE,
            SpecialCodes.LETTERS, SpecialCodes.SYMBOLS1, SpecialCodes.SYMBOLS2, SpecialCodes.LANG_SWITCH
        ),
        tr("Raqamlar", "Цифры", "Digits", "Sanlar") to digits,
        tr("Belgilar", "Знаки", "Symbols", "Belgiler") to symbolChars
    )

    private val symbolChars = listOf(
        "-", "+", "=", "_", "*", "/", "\\", "|", "&", "@", "#", "$", "%", "€", "£", "¥", "₽", "₴", "₸", "¢",
        "(", ")", "[", "]", "{", "}", "<", ">", "\"", "'", "`", "~", "^", ":", ";", ",", ".", "!", "?", "…",
        "•", "√", "π", "÷", "×", "§", "∆", "°", "±", "∞", "™", "©", "®", "✓", "‰", "¶", "†", "‡", "≈", "≠", "≤", "≥"
    )

    /** O'z til uchun: lotin + kirill + maxsus tugmalar + raqam/belgilar (hammasi tanlab olinadi). */
    private fun sectionsForCustom(): List<Pair<String, List<String>>> = listOf(
        tr("Lotin", "Латиница", "Latin", "Latın") to latin,
        tr("Kirill", "Кириллица", "Cyrillic", "Kirill") to cyrillic,
        tr("Tugmalar", "Клавиши", "Keys", "Túymeler") to specials,
        tr("Raqamlar", "Цифры", "Digits", "Sanlar") to digits,
        tr("Belgilar", "Знаки", "Symbols", "Belgiler") to punct
    )

    /** Belgilarni "bo'sh joy bilan ajratilgan" bo'lsa shu bo'yicha, aks holda harf-harf ajratadi. */
    private fun parseSymbols(s: String): List<String> {
        val t = s.trim()
        if (t.isEmpty()) return emptyList()
        if (t.any { it.isWhitespace() }) return t.split(Regex("\\s+")).filter { it.isNotEmpty() && it.length <= 12 }
        val bi = java.text.BreakIterator.getCharacterInstance()
        bi.setText(t)
        val out = mutableListOf<String>()
        var st = bi.first()
        var en = bi.next()
        while (en != java.text.BreakIterator.DONE) { out.add(t.substring(st, en)); st = en; en = bi.next() }
        return out
    }

    /** "Mening belgilarim": paste qilib o'z belgilarini qo'shish (barcha tillar uchun umumiy). */
    private fun renderMySymbols(chip: Int, m: Int, colCount: Int) {
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(sectionTitle(tr("Mening belgilarim", "Мои символы", "My symbols", "Meniń belgilerim")),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val mine = LayoutStorage.getMySymbols(this)
        if (mine.isNotEmpty()) {
            head.addView(TextView(this).apply {
                text = (if (symDeleteMode) "✓ " else "✕ ") + tr("O'çirish", "Удаление", "Delete mode", "Óshiriw")
                textSize = 11f
                setTextColor(if (symDeleteMode) Color.WHITE else Ui.TEXT_SECONDARY)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = if (symDeleteMode) Ui.fire(this@LayoutEditorActivity, 10) else Ui.glass(this@LayoutEditorActivity, 10)
                setOnClickListener { symDeleteMode = !symDeleteMode; renderPalette(true) }
            })
        }
        palette.addView(head)

        // paste maydoni
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = EditText(this).apply {
            hint = tr("Belgilarni shu yerga paste qiling", "Вставьте символы сюда", "Paste your symbols here", "Belgilerdi usı jerge paste etiń")
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 2
            setOnDragListener { _, e -> handlePaletteDrag(e) }   // matn maydoniga tushsa ham belgi yozilmaydi, o'chadi
            background = Ui.glass(this@LayoutEditorActivity, 10)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = tr("Qöşiş", "Добавить", "Add", "Qosıw")
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = Ui.fire(this@LayoutEditorActivity, 10)
            setOnClickListener {
                val list = parseSymbols(input.text.toString())
                if (list.isEmpty()) {
                    toast(tr("Avval belgilarni paste qiling", "Сначала вставьте символы", "Paste some symbols first", "Aldın belgilerdi paste etiń"))
                } else {
                    LayoutStorage.addMySymbols(this@LayoutEditorActivity, list)
                    symDeleteMode = false
                    renderPalette(true)
                }
            }
        }, LinearLayout.LayoutParams(dp(76), dp(40)).apply { marginStart = dp(6) })
        palette.addView(row, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (mine.isEmpty()) return
        val grid = GridLayout(this).apply { columnCount = colCount }
        mine.forEach { sym ->
            val tv = TextView(this).apply {
                text = sym
                setTextColor(Color.WHITE)
                background = Ui.glass(this@LayoutEditorActivity, 10)
                gravity = Gravity.CENTER
                maxLines = 1
                includeFontPadding = false
                textSize = if (sym.length > 2) 10f else 15f
                if (symDeleteMode) {
                    // o'chirish rejimida chip qizg'ish ko'rinadi
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(Color.parseColor("#55FF3D00"))
                        setStroke(dp(1), Color.parseColor("#FF3D00"))
                    }
                }
                setOnClickListener { v ->
                    if (symDeleteMode) {
                        // faqat shu chipni olib tashlaymiz (palitra qayta chizilmaydi, scroll joyida qoladi)
                        LayoutStorage.removeMySymbol(this@LayoutEditorActivity, sym)
                        grid.removeView(v)
                        if (grid.childCount == 0) {
                            symDeleteMode = false
                            renderPalette(true)
                        }
                    } else {
                        if (!gridView.addKey(keyFromCode(sym))) toast(tr("To'r to'la", "Сетка заполнена", "Grid is full", "Tor tolǵan"))
                        updateUndoRedoState()
                    }
                }
                setOnLongClickListener { v ->
                    if (symDeleteMode) return@setOnLongClickListener true
                    v.startDragAndDrop(ClipData.newPlainText("key", sym), View.DragShadowBuilder(v), null, 0)
                    true
                }
            }
            grid.addView(tv, GridLayout.LayoutParams().apply { width = chip; height = chip; setMargins(m, m, m, m) })
        }
        palette.addView(grid)
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
