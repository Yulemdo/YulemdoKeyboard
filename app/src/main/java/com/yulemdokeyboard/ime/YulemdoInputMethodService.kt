package com.yulemdokeyboard.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.widget.GridLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.yulemdokeyboard.MainActivity
import com.yulemdokeyboard.model.KeyData
import com.yulemdokeyboard.model.KeyboardLayout
import com.yulemdokeyboard.model.Languages
import com.yulemdokeyboard.model.LayoutStorage
import com.yulemdokeyboard.model.SpecialCodes
import com.yulemdokeyboard.model.englishLayout
import com.yulemdokeyboard.model.karakalpakLayout
import com.yulemdokeyboard.model.russianLayout
import com.yulemdokeyboard.model.symbolsLayout
import com.yulemdokeyboard.model.symbolsLayout2

class YulemdoInputMethodService : InputMethodService() {

    private enum class Page { LETTERS, SYM1, SYM2 }

    private lateinit var root: LinearLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var emojiPanel: EmojiPanel
    private lateinit var clipPanel: ClipboardPanel
    private var fontStyle: FontStyles.Style? = null
    // Emoji qidiruvi: yozilgan harflar kiritish maydoniga emas, qidiruv so'rovi sifatida yig'iladi
    private var searchMode = false
    private val searchQuery = StringBuilder()
    private var toolbarView: View? = null
    private var panelHeightPx = 0
    private lateinit var fontIcon: IconView
    private var fontMenu: PopupWindow? = null
    private var lastLen = 1
    private var clipManager: android.content.ClipboardManager? = null
    private val clipListener = android.content.ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    private var page = Page.LETTERS
    private var lang = Languages.UZ

    private var shiftOn = false
    private var capsLock = false
    private var lastShiftTime = 0L

    // Layoutlar bir marta yaratiladi (silliq ishlashi uchun)
    private val sym1 by lazy { symbolsLayout() }
    private val sym2 by lazy { symbolsLayout2() }
    private val kaa by lazy { karakalpakLayout() }
    private val en by lazy { englishLayout() }
    private val ru by lazy { russianLayout() }

    override fun onCreate() {
        super.onCreate()
        try {
            clipManager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipManager?.addPrimaryClipChangedListener(clipListener)
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        try { clipManager?.removePrimaryClipChangedListener(clipListener) } catch (_: Exception) { }
        super.onDestroy()
    }

    /** Nusxalangan matnni tarixga yozadi (oxirgi 30 ta). */
    private fun captureClip() {
        try {
            val cm = clipManager ?: return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val t = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            ClipStorage.addRecent(this, t)
            if (::clipPanel.isInitialized && clipPanel.visibility == View.VISIBLE) clipPanel.refresh()
        } catch (_: Exception) { }
    }

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this)
        keyboardView.onKeyPressed = { key -> handleKey(key) }

        emojiPanel = EmojiPanel(
            this,
            onEmoji = { currentInputConnection?.commitText(it, 1) },
            onBackspace = { deleteBackward() },
            onBack = { hideEmoji() },
            onSearch = { enterEmojiSearch() },
            onExitSearch = { exitEmojiSearch() }
        )

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(KeyboardView.backgroundColorFor(this@YulemdoInputMethodService))
        }
        root.addView(
            buildToolbar(),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * resources.displayMetrics.density).toInt())
        )
        panelHeightPx = keyboardView.desiredHeight(4)
        // Vertikal: emoji paneli (qidiruvda yuqorida), nusxalar paneli, klaviatura
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(
            emojiPanel,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, panelHeightPx)
        )
        clipPanel = ClipboardPanel(
            this,
            baseHeight = panelHeightPx,
            onPaste = { text -> currentInputConnection?.commitText(text, 1); hideEmoji() },
            onBack = { hideEmoji() }
        )
        body.addView(
            clipPanel,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, panelHeightPx)
        )
        body.addView(
            keyboardView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        clipPanel.visibility = View.GONE
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        emojiPanel.visibility = View.GONE

        loadLang()
        applySpaceLabel()
        showPage(Page.LETTERS)
        loadFont()
        return root
    }

    // ===== Klaviatura tepasidagi panel: emoji, klaviatura almashtirish, nusxalanganlar, sozlamalar =====
    private fun buildToolbar(): View {
        val night = KeyboardView.isNight(this)
        val iconColor = if (night) Color.parseColor("#F2F2F2") else Color.parseColor("#111111")
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(KeyboardView.backgroundColorFor(this@YulemdoInputMethodService))
        }
        fun btn(type: IconView.Type, onClick: () -> Unit) {
            val v = IconView(this, type, iconColor).apply {
                isClickable = true
                setOnClickListener {
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            }
            bar.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        btn(IconView.Type.SMILE) { if (emojiPanel.visibility == View.VISIBLE) hideEmoji() else showEmoji() }
        // Shrift tugmasi: bosish - yoqish/o'chirish, bosib turish - shrift tanlash
        fontIcon = IconView(this, IconView.Type.FONT, iconColor).apply {
            isClickable = true
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                toggleFont()
            }
            setOnLongClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                showFontMenu(this)
                true
            }
        }
        bar.addView(fontIcon, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        refreshFontIcon()
        btn(IconView.Type.CLIPBOARD) { if (clipPanel.visibility == View.VISIBLE) hideEmoji() else showClipboard() }
        btn(IconView.Type.GEAR) { openApp(MainActivity::class.java) }
        toolbarView = bar
        return bar
    }

    // ===== Shriftlar =====
    private fun loadFont() {
        val st = FontStyles.byId(FontStyles.getActive(this), lang)
        if (st == null) FontStyles.setActive(this, "")   // bu alifboda mavjud bo'lmasa, o'chiriladi
        fontStyle = st
        if (::fontIcon.isInitialized) refreshFontIcon()
    }

    private fun refreshFontIcon() {
        val night = KeyboardView.isNight(this)
        val normal = if (night) Color.parseColor("#F2F2F2") else Color.parseColor("#111111")
        fontIcon.color = if (fontStyle != null) Color.parseColor("#FFC107") else normal
        fontIcon.invalidate()
    }

    private fun setFont(st: FontStyles.Style?) {
        fontStyle = st
        FontStyles.setActive(this, st?.id ?: "")
        lastLen = 1
        refreshFontIcon()
    }

    private fun toggleFont() {
        if (fontStyle != null) { setFont(null); return }
        val last = FontStyles.byId(FontStyles.getLast(this), lang)
        if (last == null) showFontMenu(fontIcon) else setFont(last)
    }

    private fun normalLabel() = when (lang) {
        Languages.RU -> "Обычный"
        Languages.EN -> "Normal"
        Languages.KAA -> "Ápiwayí"
        else -> "Oddiy"
    }

    private fun showFontMenu(anchor: View) {
        try { fontMenu?.dismiss() } catch (_: Exception) { }
        val d = resources.displayMetrics.density
        val night = KeyboardView.isNight(this)
        val fg = if (night) Color.parseColor("#F2F2F2") else Color.parseColor("#111111")
        val border = if (night) Color.parseColor("#44FFFFFF") else Color.parseColor("#33000000")
        val selBg = if (night) Color.parseColor("#454548") else Color.parseColor("#E4E4E4")
        val screenW = resources.displayMetrics.widthPixels
        val menuW = minOf(screenW - (16 * d).toInt(), (340 * d).toInt())
        val pad = (6 * d).toInt()
        val margin = (3 * d).toInt()
        val cellW = (menuW - pad * 2) / 2 - margin * 2

        val sample = if (lang == Languages.RU) "Клавиатура" else "Yulemdo"
        val paint = android.graphics.Paint()
        val styles = FontStyles.forLang(lang).filter { st ->
            // Qurilma shu belgilarni chiza olmasa (kvadratcha chiqsa), ro'yxatdan chiqarib tashlaymiz
            st.mark != null || android.os.Build.VERSION.SDK_INT < 23 || paint.hasGlyph(FontStyles.apply(st, "A"))
        }

        val grid = GridLayout(this).apply { columnCount = 2 }
        fun cell(label: String, active: Boolean, onClick: () -> Unit) {
            val tv = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 16f
                maxLines = 1
                setTextColor(fg)
                setPadding((6 * d).toInt(), (11 * d).toInt(), (6 * d).toInt(), (11 * d).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 10 * d
                    setColor(if (active) selBg else Color.TRANSPARENT)
                    setStroke(((if (active) 2f else 1f) * d).toInt().coerceAtLeast(1), if (active) fg else border)
                }
                setOnClickListener { try { fontMenu?.dismiss() } catch (_: Exception) { }; onClick() }
            }
            grid.addView(tv, GridLayout.LayoutParams().apply {
                width = cellW
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(margin, margin, margin, margin)
            })
        }
        cell(normalLabel(), fontStyle == null) { setFont(null) }
        styles.forEach { st -> cell(FontStyles.apply(st, sample), fontStyle?.id == st.id) { setFont(st) } }

        val box = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            setPadding(pad, pad, pad, pad)
            elevation = 8 * d
            background = GradientDrawable().apply {
                cornerRadius = 16 * d
                setColor(if (night) Color.parseColor("#2C2C2E") else Color.WHITE)
                setStroke((1 * d).toInt(), border)
            }
            addView(grid)
        }
        grid.measure(
            View.MeasureSpec.makeMeasureSpec(menuW - pad * 2, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.UNSPECIFIED
        )
        val h = minOf(grid.measuredHeight + pad * 2, (260 * d).toInt())
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val x = ((screenW - menuW) / 2)
        val y = (loc[1] - h - 4 * d).toInt().coerceAtLeast(0)
        try {
            fontMenu = PopupWindow(box, menuW, h, false).apply {
                isOutsideTouchable = true
                isTouchable = true
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                animationStyle = 0
                showAtLocation(root, Gravity.NO_GRAVITY, x, y)
            }
        } catch (_: Exception) { fontMenu = null }
    }

    private fun openApp(cls: Class<*>) {
        val i = Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        hideEmoji()
        loadLang()
        applySpaceLabel()
        showPage(Page.LETTERS)
        loadFont()
    }

    // ===== Sahifalar / tillar =====
    private fun letterLayout(): KeyboardLayout = when (lang) {
        Languages.KAA -> kaa
        Languages.EN -> en
        Languages.RU -> ru
        else -> LayoutStorage.getCurrentLayout(this)
    }

    private fun showPage(p: Page) {
        page = p
        shiftOn = false
        capsLock = false
        syncShift()
        keyboardView.altKeys =
            if (lang == Languages.RU && p == Page.LETTERS) mapOf("е" to "ё", "ь" to "ъ") else emptyMap()
        keyboardView.layoutModel = when (p) {
            Page.LETTERS -> letterLayout()
            Page.SYM1 -> sym1
            Page.SYM2 -> sym2
        }
    }

    private fun applySpaceLabel() {
        keyboardView.spaceLabel = Languages.spaceLabel(lang)
        keyboardView.spaceBadge = Languages.badge(lang)
        keyboardView.invalidate()
    }

    /** Saqlangan tilni o'qiydi; agar u endi o'chirilgan bo'lsa, birinchi yoniq tilga o'tadi. */
    private fun loadLang() {
        lang = LayoutStorage.getLanguage(this)
        val enabled = LayoutStorage.getEnabledLanguages(this)
        if (lang !in enabled) {
            lang = enabled.first()
            LayoutStorage.setLanguage(this, lang)
        }
    }

    private fun cycleLanguage() {
        switchTo(Languages.next(lang, LayoutStorage.getEnabledLanguages(this)))
    }

    private fun switchTo(l: String) {
        lang = l
        LayoutStorage.setLanguage(this, lang)
        loadFont()
        applySpaceLabel()
        if (page == Page.LETTERS) showPage(Page.LETTERS)
    }

    private var langMenu: PopupWindow? = null

    private fun otherKeyboardLabel() = when (lang) {
        Languages.RU -> "Другая клавиатура"
        Languages.EN -> "Other keyboard"
        Languages.KAA -> "Basqa klaviatura"
        else -> "Boşqa klaviatura"
    }

    /**
     * Globus tugmasi bosib turilganda: yoqilgan tillar ro'yxati globus ustida ochiladi.
     * Barmoqni ko'tarmasdan ro'yxat bo'ylab surib, kerakli tilda qo'yib yuborsangiz - shu tilga o'tadi.
     * Qo'yib yuborganda hech qaysi qatorga tushmasa, menyu ochiq qoladi va qatorni bosib tanlash mumkin.
     */
    private fun showLangMenu() {
        try { langMenu?.dismiss() } catch (_: Exception) { }
        if (keyboardView.windowToken == null) return
        val d = resources.displayMetrics.density
        val night = KeyboardView.isNight(this)
        val fg = if (night) Color.parseColor("#F2F2F2") else Color.parseColor("#111111")
        val sub = if (night) Color.parseColor("#9AA0A6") else Color.parseColor("#5F6368")
        val border = if (night) Color.parseColor("#44FFFFFF") else Color.parseColor("#33000000")
        val selBg = if (night) Color.parseColor("#454548") else Color.parseColor("#E4E4E4")
        val hoverBg = Color.parseColor("#FFC107")
        val menuW = (230 * d).toInt()
        val pad = (6 * d).toInt()
        val screenW = resources.displayMetrics.widthPixels

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            elevation = 8 * d
            background = GradientDrawable().apply {
                cornerRadius = 16 * d
                setColor(if (night) Color.parseColor("#2C2C2E") else Color.WHITE)
                setStroke((1 * d).toInt(), border)
            }
        }

        class MenuRow(val tv: TextView, val active: Boolean, val muted: Boolean, val onClick: () -> Unit)
        val rows = ArrayList<MenuRow>()

        fun baseBg(active: Boolean) = GradientDrawable().apply {
            cornerRadius = 10 * d
            setColor(if (active) selBg else Color.TRANSPARENT)
            if (active) setStroke((1.5f * d).toInt(), fg)
        }

        fun row(label: String, active: Boolean, muted: Boolean, onClick: () -> Unit) {
            val tv = TextView(this).apply {
                text = label
                textSize = if (muted) 14f else 17f
                typeface = if (active) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                setTextColor(if (muted) sub else fg)
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
                background = baseBg(active)
                setOnClickListener { try { langMenu?.dismiss() } catch (_: Exception) { }; onClick() }
            }
            box.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (46 * d).toInt()).apply {
                setMargins(0, (2 * d).toInt(), 0, (2 * d).toInt())
            })
            rows.add(MenuRow(tv, active, muted, onClick))
        }
        LayoutStorage.getEnabledLanguages(this).forEach { l ->
            val name = Languages.nativeName(l) + if (Languages.badge(l).isNotEmpty()) "  " + Languages.badge(l) else ""
            row(name, l == lang, false) { switchTo(l) }
        }
        row(otherKeyboardLabel(), false, true) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        box.measure(
            View.MeasureSpec.makeMeasureSpec(menuW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.UNSPECIFIED
        )
        val h = box.measuredHeight

        // Menyu globus tugmasi ustida, uning chap chetiga tekislab ochiladi (barmoq tagida qolmaydi)
        val loc = IntArray(2)
        keyboardView.getLocationOnScreen(loc)
        val keyRect = keyboardView.keyRectFor(SpecialCodes.LANG_SWITCH)
        val x = ((loc[0] + (keyRect?.left ?: 0f)).toInt()).coerceIn(0, (screenW - menuW).coerceAtLeast(0))
        val keyTop = loc[1] + (keyRect?.top ?: (keyboardView.height / 2f))
        val y = (keyTop - h - 6 * d).toInt().coerceAtLeast(0)

        // Barmoq ustidagi qatorni ajratib ko'rsatish
        fun rowAt(rx: Float, ry: Float): MenuRow? {
            val p = IntArray(2)
            for (r in rows) {
                r.tv.getLocationOnScreen(p)
                if (rx >= p[0] && rx <= p[0] + r.tv.width && ry >= p[1] && ry <= p[1] + r.tv.height) return r
            }
            return null
        }
        fun highlight(target: MenuRow?) {
            rows.forEach { r ->
                if (r === target) {
                    r.tv.background = GradientDrawable().apply { cornerRadius = 10 * d; setColor(hoverBg) }
                    r.tv.setTextColor(Color.BLACK)
                } else {
                    r.tv.background = baseBg(r.active)
                    r.tv.setTextColor(if (r.muted) sub else fg)
                }
            }
        }
        keyboardView.langMenuMove = { rx, ry -> highlight(rowAt(rx, ry)) }
        keyboardView.langMenuRelease = { rx, ry ->
            val target = rowAt(rx, ry)
            if (target != null) {
                try { langMenu?.dismiss() } catch (_: Exception) { }
                target.onClick()
            } else {
                highlight(null)   // menyu ochiq qoladi - qatorni bosib tanlash mumkin
            }
        }
        try {
            langMenu = PopupWindow(box, menuW, h, false).apply {
                isOutsideTouchable = true
                isTouchable = true
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                animationStyle = 0
                showAtLocation(root, Gravity.NO_GRAVITY, x, y)
            }
        } catch (_: Exception) { langMenu = null }
    }

    // ===== Emoji qidiruvi =====
    private fun setEmojiPanelHeight(h: Int) {
        val lp = emojiPanel.layoutParams ?: return
        lp.height = h
        emojiPanel.layoutParams = lp
    }

    private fun enterEmojiSearch() {
        searchMode = true
        searchQuery.setLength(0)
        emojiPanel.beginSearch(lang)
        setEmojiPanelHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
        toolbarView?.visibility = View.GONE
        keyboardView.visibility = View.VISIBLE
        showPage(Page.LETTERS)
    }

    /** Qidiruv rejimidan chiqadi (emoji katalogiga qaytadi). */
    private fun leaveSearchMode() {
        searchMode = false
        searchQuery.setLength(0)
        emojiPanel.endSearch()
        setEmojiPanelHeight(panelHeightPx)
        toolbarView?.visibility = View.VISIBLE
    }

    private fun exitEmojiSearch() {
        leaveSearchMode()
        keyboardView.visibility = View.GONE
        emojiPanel.visibility = View.VISIBLE
    }

    /** true - tugma qidiruv uchun ishlatildi; false - odatdagidek ishlanadi (shift, til almashtirish, belgilar sahifasi). */
    private fun handleSearchKey(key: KeyData): Boolean {
        when (key.code) {
            SpecialCodes.BACKSPACE -> {
                if (searchQuery.isNotEmpty()) {
                    val cp = Character.codePointBefore(searchQuery, searchQuery.length)
                    searchQuery.setLength(searchQuery.length - Character.charCount(cp))
                    emojiPanel.setQuery(searchQuery.toString())
                }
                return true
            }
            SpecialCodes.ENTER, SpecialCodes.EMOJI, "\uD83D\uDE42", SpecialCodes.LANG_PICKER -> return true
            SpecialCodes.SHIFT, SpecialCodes.SYMBOLS, SpecialCodes.SYMBOLS1, SpecialCodes.SYMBOLS2,
            SpecialCodes.LETTERS -> return false
            SpecialCodes.LANG_SWITCH -> {
                cycleLanguage()
                emojiPanel.setLang(lang)
                return true
            }
            SpecialCodes.SPACE -> searchQuery.append(' ')
            else -> {
                searchQuery.append(if (shiftOn) key.code.lowercase() else key.code)
                if (shiftOn && !capsLock) { shiftOn = false; syncShift() }
            }
        }
        emojiPanel.setQuery(searchQuery.toString())
        return true
    }

    private fun showEmoji() {
        keyboardView.visibility = View.GONE
        clipPanel.visibility = View.GONE
        emojiPanel.visibility = View.VISIBLE
    }

    private fun showClipboard() {
        captureClip()
        clipPanel.setLang(lang)
        keyboardView.visibility = View.GONE
        emojiPanel.visibility = View.GONE
        clipPanel.visibility = View.VISIBLE
    }

    private fun hideEmoji() {
        if (!::emojiPanel.isInitialized) return
        if (searchMode) leaveSearchMode()
        emojiPanel.visibility = View.GONE
        if (::clipPanel.isInitialized) clipPanel.visibility = View.GONE
        keyboardView.visibility = View.VISIBLE
    }

    private fun syncShift() {
        keyboardView.shiftOn = shiftOn
        keyboardView.capsLock = capsLock
        keyboardView.invalidate()
    }

    // ===== Yozish =====
    private fun deleteBackward() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else if (deleteWithMarks(ic)) {
            // shriftli harf (asosiy belgi + birlashtiruvchi belgilar) bittada o'chirildi
        } else if (Build.VERSION.SDK_INT >= 24) {
            ic.deleteSurroundingTextInCodePoints(1, 0)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    /** Kursor oldida birlashtiruvchi belgilar (ö = o + ̈, tagiga chizilgan harf va h.k.) bo'lsa, harfni belgilari bilan birga o'chiradi. */
    private fun deleteWithMarks(ic: android.view.inputmethod.InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(10, 0) ?: return false
        var i = before.length
        while (i > 0) {
            val cp = Character.codePointBefore(before, i)
            val t = Character.getType(cp)
            if (t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt() ||
                t == Character.ENCLOSING_MARK.toInt()) i -= Character.charCount(cp) else break
        }
        if (i <= 0 || i == before.length) return false
        i -= Character.charCount(Character.codePointBefore(before, i))
        ic.deleteSurroundingText(before.length - i, 0)
        return true
    }

    // sh->ş, ch->ç, oʻ/o'->ö, gʻ/g'->ğ. Faqat uz tilida, harflar ketma-ket yozilganda ishlaydi.
    private data class Digraph(val first: Char, val second: Char, val result: Char)
    private val digraphs = listOf(
        Digraph('s', 'h', 'ş'), Digraph('S', 'h', 'Ş'), Digraph('S', 'H', 'Ş'),
        Digraph('c', 'h', 'ç'), Digraph('C', 'h', 'Ç'), Digraph('C', 'H', 'Ç'),
        Digraph('o', '\u02BB', 'ö'), Digraph('o', '\'', 'ö'), Digraph('O', '\u02BB', 'Ö'), Digraph('O', '\'', 'Ö'),
        Digraph('g', '\u02BB', 'ğ'), Digraph('g', '\'', 'ğ'), Digraph('G', '\u02BB', 'Ğ'), Digraph('G', '\'', 'Ğ')
    )
    private var lastChar: Char = ' '

    private fun commitWithDigraphCheck(ic: android.view.inputmethod.InputConnection, text: String) {
        if (lang == Languages.UZ && text.length == 1) {
            val nextChar = text[0]
            val match = digraphs.find { it.first == lastChar && it.second == nextChar }
            if (match != null) {
                // Oldingi harf shriftlangan bo'lsa, u bir necha belgidan iborat bo'lishi mumkin
                ic.deleteSurroundingText(lastLen, 0)
                val out = FontStyles.apply(fontStyle, match.result.toString())
                ic.commitText(out, 1)
                lastChar = match.result
                lastLen = out.length
                return
            }
        }
        val out = FontStyles.apply(fontStyle, text)
        ic.commitText(out, 1)
        lastChar = if (text.length == 1) text[0] else ' '
        lastLen = out.length
    }

    private fun handleKey(key: KeyData) {
        if (searchMode && handleSearchKey(key)) return
        val ic = currentInputConnection ?: return
        when (key.code) {
            SpecialCodes.BACKSPACE -> { deleteBackward(); lastChar = ' ' }
            SpecialCodes.ENTER -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                lastChar = ' '
            }
            SpecialCodes.SHIFT -> {
                // 1 marta bosish: keyingi harf katta. Tez ikki marta bosish: CAPS LOCK. Caps paytida bosilsa o'chadi.
                val now = SystemClock.uptimeMillis()
                when {
                    capsLock -> { capsLock = false; shiftOn = false }
                    shiftOn && now - lastShiftTime < 350 -> capsLock = true
                    else -> shiftOn = !shiftOn
                }
                lastShiftTime = now
                syncShift()
            }
            SpecialCodes.SYMBOLS, SpecialCodes.SYMBOLS1 -> showPage(Page.SYM1)
            SpecialCodes.SYMBOLS2 -> showPage(Page.SYM2)
            SpecialCodes.LETTERS -> showPage(Page.LETTERS)
            SpecialCodes.LANG_SWITCH -> cycleLanguage()
            SpecialCodes.LANG_PICKER -> showLangMenu()
            SpecialCodes.EMOJI, "\uD83D\uDE42" -> showEmoji()
            SpecialCodes.SPACE -> { ic.commitText(" ", 1); lastChar = ' ' }
            else -> {
                val text = if (shiftOn) key.code.uppercase() else key.code
                commitWithDigraphCheck(ic, text)
                if (shiftOn && !capsLock) {
                    shiftOn = false
                    syncShift()
                }
            }
        }
    }
}
