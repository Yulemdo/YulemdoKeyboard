package com.yulemdokeyboard.model

/**
 * Bitta klaviatura tugmasi.
 * row/col - gridda joylashuv, label - tugmadagi yozuv, code - chiqariladigan belgi,
 * width - necha birlik kenglikni egallashi, isSpecial - shift/backspace/enter/space kabi maxsus tugma.
 */
data class KeyData(
    var label: String,
    var code: String = label,
    var row: Int,
    var col: Int,
    var width: Float = 1f,
    var isSpecial: Boolean = false
)

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
    const val SYMBOLS = "?123"          // harflar sahifasidagi "123" tugmasi
    const val LETTERS = "ABC"
    const val LANG_SWITCH = "\u0001LANG"
    const val LANG_PICKER = "\u0001LANGPICK"
    const val EMOJI = "\u0001EMOJI"
    const val SYMBOLS1 = "\u0001SYM1"   // belgilar 1-sahifa (raqamlar)
    const val SYMBOLS2 = "\u0001SYM2"   // belgilar 2-sahifa (=\<)
}

/** Rasmiy o'zbek tutuq belgisi (U+02BC). */
private const val TUTUQ = "\u02BC"

/**
 * Standart o'zbek lotin klaviaturasi (turkcha harf shakllari bilan):
 * oʻ -> ö, gʻ -> ğ, sh -> ş, ch -> ç, tutuq -> ʼ
 */
fun defaultUzbekLayout(): KeyboardLayout {
    val keys = mutableListOf<KeyData>()

    listOf("q", "e", "r", "t", "y", "u", "i", "o", "p", "ö", "ğ")
        .forEachIndexed { i, c -> keys.add(KeyData(c, c, row = 0, col = i)) }

    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", TUTUQ)
        .forEachIndexed { i, c -> keys.add(KeyData(c, c, row = 1, col = i)) }

    keys.add(KeyData("", SpecialCodes.SHIFT, row = 2, col = 0, width = 1.3f, isSpecial = true))
    listOf("z", "x", "c", "v", "b", "n", "m", "ç", "ş")
        .forEachIndexed { i, c -> keys.add(KeyData(c, c, row = 2, col = i + 1)) }
    keys.add(KeyData("", SpecialCodes.BACKSPACE, row = 2, col = 10, width = 1.3f, isSpecial = true))

    keys.add(KeyData("", SpecialCodes.SYMBOLS, row = 3, col = 0, width = 1.3f, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.EMOJI, row = 3, col = 1, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.LANG_SWITCH, row = 3, col = 2, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.SPACE, row = 3, col = 3, width = 3.4f, isSpecial = true))
    keys.add(KeyData(",", ",", row = 3, col = 7))
    keys.add(KeyData(".", ".", row = 3, col = 8))
    keys.add(KeyData("", SpecialCodes.ENTER, row = 3, col = 9, width = 1.6f, isSpecial = true))

    return KeyboardLayout(name = "Özbek (standart)", rows = 4, cols = 11, keys = keys)
}

private fun symbolsPage(
    name: String,
    r0: List<String>,
    r1: List<String>,
    r2: List<String>,
    toggleLabel: String,
    toggleCode: String
): KeyboardLayout {
    val keys = mutableListOf<KeyData>()
    r0.forEachIndexed { i, c -> keys.add(KeyData(c, c, row = 0, col = i)) }
    r1.forEachIndexed { i, c -> keys.add(KeyData(c, c, row = 1, col = i)) }

    keys.add(KeyData(toggleLabel, toggleCode, row = 2, col = 0, width = 1.4f, isSpecial = true))
    r2.forEachIndexed { i, c -> keys.add(KeyData(c, c, row = 2, col = i + 1)) }
    keys.add(KeyData("", SpecialCodes.BACKSPACE, row = 2, col = r2.size + 1, width = 1.4f, isSpecial = true))

    keys.add(KeyData("ABC", SpecialCodes.LETTERS, row = 3, col = 0, width = 1.4f, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.EMOJI, row = 3, col = 1, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.LANG_SWITCH, row = 3, col = 2, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.SPACE, row = 3, col = 3, width = 6f, isSpecial = true))
    keys.add(KeyData(".", ".", row = 3, col = 4))
    keys.add(KeyData("", SpecialCodes.ENTER, row = 3, col = 5, width = 1.4f, isSpecial = true))

    return KeyboardLayout(name = name, rows = 4, cols = 12, keys = keys)
}

/** 1-sahifa: raqamlar va keng tarqalgan belgilar. */
fun symbolsLayout(): KeyboardLayout = symbolsPage(
    "Belgilar 1",
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "+"),
    listOf("@", "#", "\$", "€", "£", "&", "_", "(", ")", "[", "]", "/"),
    listOf("*", "\"", "'", ":", ";", "!", "?", "~", "^", "="),
    "=\\<", SpecialCodes.SYMBOLS2
)

/** 2-sahifa: qo'shimcha belgilar. */
fun symbolsLayout2(): KeyboardLayout = symbolsPage(
    "Belgilar 2",
    listOf("`", "|", "•", "√", "π", "÷", "×", "§", "∆", "°", "±", "∞"),
    listOf("¢", "¥", "₽", "₴", "™", "©", "®", "✓", "{", "}", "<", ">"),
    listOf("%", "‰", "¶", "†", "‡", "…", "≈", "≠", "≤", "\\"),
    "123", SpecialCodes.SYMBOLS1
)
