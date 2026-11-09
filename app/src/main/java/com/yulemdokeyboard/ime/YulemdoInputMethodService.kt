package com.yulemdokeyboard.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
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

    private lateinit var root: FrameLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var emojiPanel: EmojiPanel

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

    // ===== Tizim klaviatura almashtirgichidagi til nomini sinxronlash =====
    // method.xml dagi har bir subtype o'z tilida (Özbekça/Qaraqalpaqsha/English/Русский)
    // yoziladi; shu funksiya ilova ichida tanlangan tilga mos subtype'ni tizimga bildiradi,
    // shunda "Yulemdo Keyboard" tepasidagi til nomi doim to'g'ri ko'rinadi.
    private fun syncSystemSubtype() {
        val locale = when (lang) {
            Languages.KAA -> "kaa"
            Languages.EN -> "en"
            Languages.RU -> "ru"
            else -> "uz"
        }
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            val subtypes = imm.getEnabledInputMethodSubtypeList(null, true)
            val match = subtypes.firstOrNull { it.locale == locale }
            if (match != null) {
                imm.setCurrentInputMethodSubtype(match)
            }
        } catch (_: Exception) {
            // Ba'zi qurilmalarda ruxsat yo'q bo'lishi mumkin - jim o'tkazib yuboramiz
        }
    }

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this)
        keyboardView.onKeyPressed = { key -> handleKey(key) }

        emojiPanel = EmojiPanel(
            this,
            onEmoji = { currentInputConnection?.commitText(it, 1) },
            onBackspace = { deleteBackward() },
            onBack = { hideEmoji() }
        )

        root = FrameLayout(this)
        root.addView(
            keyboardView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        root.addView(
            emojiPanel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyboardView.desiredHeight(4))
        )
        emojiPanel.visibility = View.GONE

        lang = LayoutStorage.getLanguage(this)
        applySpaceLabel()
        showPage(Page.LETTERS)
        syncSystemSubtype()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        hideEmoji()
        lang = LayoutStorage.getLanguage(this)
        applySpaceLabel()
        showPage(Page.LETTERS)
        syncSystemSubtype()
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

    private fun cycleLanguage() {
        lang = Languages.next(lang)
        LayoutStorage.setLanguage(this, lang)
        applySpaceLabel()
        if (page == Page.LETTERS) showPage(Page.LETTERS)
        syncSystemSubtype()
    }

    private fun showEmoji() {
        keyboardView.visibility = View.GONE
        emojiPanel.visibility = View.VISIBLE
    }

    private fun hideEmoji() {
        if (!::emojiPanel.isInitialized) return
        emojiPanel.visibility = View.GONE
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
        } else if (Build.VERSION.SDK_INT >= 24) {
            ic.deleteSurroundingTextInCodePoints(1, 0)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
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
                ic.deleteSurroundingText(1, 0)
                ic.commitText(match.result.toString(), 1)
                lastChar = match.result
                return
            }
        }
        ic.commitText(text, 1)
        lastChar = if (text.length == 1) text[0] else ' '
    }

    private fun handleKey(key: KeyData) {
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
            SpecialCodes.LANG_PICKER -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
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
