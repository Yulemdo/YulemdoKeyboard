package com.yulemdokeyboard.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.yulemdokeyboard.model.KeyData
import com.yulemdokeyboard.model.KeyboardLayout
import com.yulemdokeyboard.model.LayoutStorage
import com.yulemdokeyboard.model.SpecialCodes
import com.yulemdokeyboard.model.symbolsLayout
import com.yulemdokeyboard.model.symbolsLayout2

class YulemdoInputMethodService : InputMethodService() {

    private enum class Page { LETTERS, SYM1, SYM2 }

    private lateinit var keyboardView: KeyboardView
    private var shiftOn = false
    private var page = Page.LETTERS

    // Sahifalarni har safar qayta yaratmaslik uchun keshlaymiz (silliq ishlashi uchun)
    private val sym1 by lazy { symbolsLayout() }
    private val sym2 by lazy { symbolsLayout2() }

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this)
        keyboardView.onKeyPressed = { key -> handleKey(key) }
        showPage(Page.LETTERS)
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        showPage(Page.LETTERS)
    }

    private fun showPage(p: Page) {
        page = p
        shiftOn = false
        keyboardView.shiftOn = false
        val layout: KeyboardLayout = when (p) {
            Page.LETTERS -> LayoutStorage.getCurrentLayout(this)
            Page.SYM1 -> sym1
            Page.SYM2 -> sym2
        }
        keyboardView.layoutModel = layout
    }

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

    private fun handleKey(key: KeyData) {
        val ic = currentInputConnection ?: return
        when (key.code) {
            SpecialCodes.BACKSPACE -> deleteBackward()
            SpecialCodes.ENTER -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            SpecialCodes.SHIFT -> {
                shiftOn = !shiftOn
                keyboardView.shiftOn = shiftOn
                keyboardView.invalidate()
            }
            SpecialCodes.SYMBOLS, SpecialCodes.SYMBOLS1 -> showPage(Page.SYM1)
            SpecialCodes.SYMBOLS2 -> showPage(Page.SYM2)
            SpecialCodes.LETTERS -> showPage(Page.LETTERS)
            SpecialCodes.LANG_SWITCH -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
            SpecialCodes.EMOJI, "\uD83D\uDE42" -> {
                // Emoji paneli keyingi bosqichda qo'shiladi.
            }
            SpecialCodes.SPACE -> ic.commitText(" ", 1)
            else -> {
                val text = if (shiftOn) key.code.uppercase() else key.code
                ic.commitText(text, 1)
                if (shiftOn) {
                    shiftOn = false
                    keyboardView.shiftOn = false
                    keyboardView.invalidate()
                }
            }
        }
    }
}
