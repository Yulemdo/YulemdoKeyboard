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
            onBack = { hideEmoji() }
        )

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(KeyboardView.backgroundColorFor(this@YulemdoInputMethodService))
        }
        root.addView(
            buildToolbar(),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * resources.displayMetrics.density).toInt())
        )
        val body = FrameLayout(this)
        body.addView(
            keyboardView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        body.addView(
            emojiPanel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyboardView.desiredHeight(4))
        )
        clipPanel = ClipboardPanel(
            this,
            baseHeight = keyboardView.desiredHeight(4),
            onPaste = { text -> currentInputConnection?.commitText(text, 1); hideEmoji() },
            onBack = { hideEmoji() }
        )
        body.addView(
            clipPanel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyboardView.desiredHeight(4))
        )
        clipPanel.visibility = View.GONE
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        emojiPanel.visibility = View.GONE

        lang = LayoutStorage.getLanguage(this)
        applySpaceLabel()
        showPage(Page.LETTERS)
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
        btn(IconView.Type.KEYBOARD) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        btn(IconView.Type.CLIPBOARD) { if (clipPanel.visibility == View.VISIBLE) hideEmoji() else showClipboard() }
        btn(IconView.Type.GEAR) { openApp(MainActivity::class.java) }
        return bar
    }

    private fun openApp(cls: Class<*>) {
        val i = Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        hideEmoji()
        lang = LayoutStorage.getLanguage(this)
        applySpaceLabel()
        showPage(Page.LETTERS)
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
