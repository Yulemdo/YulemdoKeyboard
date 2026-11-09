package com.yulemdokeyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.yulemdokeyboard.model.KeyData
import com.yulemdokeyboard.model.LayoutStorage
import com.yulemdokeyboard.model.SpecialCodes

class YulemdoInputMethodService : InputMethodService() {

    private lateinit var keyboardView: KeyboardView
    private var shiftOn = false

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this)
        keyboardView.layoutModel = LayoutStorage.getCurrentLayout(this)
        keyboardView.onKeyPressed = { key -> handleKey(key) }
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Har safar klaviatura ko'rsatilganda, foydalanuvchi editorda tanlagan
        // (default yoki custom) layoutni qayta yuklaymiz — editor orqali o'zgargan bo'lishi mumkin.
        keyboardView.layoutModel = LayoutStorage.getCurrentLayout(this)
        shiftOn = false
        keyboardView.shiftOn = false
    }

    private fun handleKey(key: KeyData) {
        val ic = currentInputConnection ?: return
        when (key.code) {
            SpecialCodes.BACKSPACE -> ic.deleteSurroundingText(1, 0)
            SpecialCodes.ENTER -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
            }
            SpecialCodes.EMOJI, "🙂" -> {
                // Emoji paneli keyingi bosqichda qo'shiladi.
            }
            SpecialCodes.SHIFT -> {
                shiftOn = !shiftOn
                keyboardView.shiftOn = shiftOn
                keyboardView.invalidate()
            }
            SpecialCodes.SYMBOLS -> {
                // Keyingi bosqichda raqam/belgilar klaviaturasiga o'tish shu yerga qo'shiladi.
            }
            SpecialCodes.LANG_SWITCH -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
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
