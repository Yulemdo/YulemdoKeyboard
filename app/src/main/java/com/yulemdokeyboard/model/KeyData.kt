package com.yulemdokeyboard.model

/**
 * Bitta klaviatura tugmasini ifodalaydi.
 * row/col - standart gridda joylashuv (drag-drop uchun grid-snap qilinadi)
 * label - tugmada ko'rinadigan belgi
 * code - chiqariladigan belgi (label bilan bir xil bo'lishi mumkin)
 * width - necha ustunni egallashi (1 = oddiy tugma, 1.5, 2 ... kabi kengroq tugmalar uchun)
 */
data class KeyData(
    var label: String,
    var code: String = label,
    var row: Int,
    var col: Int,
    var width: Float = 1f,
    var isSpecial: Boolean = false // backspace, enter, shift, space, 123 kabi maxsus tugmalar
)

/**
 * Butun klaviatura joylashuvi - bir nechta qator (row) tugmalardan iborat.
 */
data class KeyboardLayout(
    var name: String,
    var rows: Int = 4,
    var cols: Int = 10,
    var keys: MutableList<KeyData> = mutableListOf()
)

object SpecialCodes {
    const val BACKSPACE = "\u0008"
    const val ENTER = "\n"
    const val SPACE = " "
    const val SHIFT = "⇧"
    const val SYMBOLS = "?123"
    const val LETTERS = "ABC"
    const val LANG_SWITCH = "\u0001LANG"
    const val EMOJI = "\u0001EMOJI"
}

/**
 * Standart (default) o'zbek lotin alifbosi klaviaturasi.
 * Joylashuv namunadagi rasmga mos: 1-qatorda 11 ta tugma (oxirida oʻ, gʻ),
 * 2-qatorda 9 harf + apostrof, 3-qatorda shift + 7 harf + ç/ş + backspace,
 * 4-qatorda 123 + emoji + globus + bo'shliq (yozuvsiz) + vergul + nuqta + enter.
 */
fun defaultUzbekLayout(): KeyboardLayout {
    val keys = mutableListOf<KeyData>()

    // 1-qator: q e r t y u i o p oʻ gʻ  (11 ta tugma, w olib tashlandi)
    val row1 = listOf("q", "e", "r", "t", "y", "u", "i", "o", "p", "oʻ", "gʻ")
    row1.forEachIndexed { i, c -> keys.add(KeyData(c, c, row = 0, col = i)) }

    // 2-qator: a s d f g h j k l '
    val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "'")
    row2.forEachIndexed { i, c -> keys.add(KeyData(c, c, row = 1, col = i)) }

    // 3-qator: shift + z x c v b n m + ç ş + backspace
    keys.add(KeyData("", SpecialCodes.SHIFT, row = 2, col = 0, width = 1.3f, isSpecial = true))
    val row3 = listOf("z", "x", "c", "v", "b", "n", "m", "ç", "ş")
    row3.forEachIndexed { i, c -> keys.add(KeyData(c, c, row = 2, col = i + 1)) }
    keys.add(KeyData("", SpecialCodes.BACKSPACE, row = 2, col = 10, width = 1.3f, isSpecial = true))

    // 4-qator: 123, emoji, globus, bo'shliq (matnsiz), vergul, nuqta, enter
    keys.add(KeyData(SpecialCodes.SYMBOLS, SpecialCodes.SYMBOLS, row = 3, col = 0, width = 1.3f, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.EMOJI, row = 3, col = 1, width = 1f, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.LANG_SWITCH, row = 3, col = 2, width = 1f, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.SPACE, row = 3, col = 3, width = 3.4f, isSpecial = true))
    keys.add(KeyData(",", ",", row = 3, col = 7, width = 1f))
    keys.add(KeyData(".", ".", row = 3, col = 8, width = 1f))
    keys.add(KeyData("", SpecialCodes.ENTER, row = 3, col = 9, width = 1.6f, isSpecial = true))

    return KeyboardLayout(name = "O'zbek (standart)", rows = 4, cols = 11, keys = keys)
}
