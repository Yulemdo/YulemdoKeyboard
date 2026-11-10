package com.yulemdokeyboard.model

/** Qo'shimcha tillar uchun klaviatura joylashuvlari: qoraqalpoq (lotin), ingliz, rus. */

object Languages {
    const val UZ = "uz"
    const val KAA = "kaa"
    const val EN = "en"
    const val RU = "ru"

    /** Globus tugmasi bosilganda aylanish tartibi. */
    val order = listOf(UZ, KAA, EN, RU)

    fun next(current: String): String = order[(order.indexOf(current) + 1).mod(order.size)]

    /** Space tugmasida ko'rinadigan nom (yangi alifbo bo'yicha: Özbekça). */
    fun spaceLabel(lang: String): String = when (lang) {
        UZ -> "ÖZBEKÇA"
        KAA -> "QARAQALPAQSHA"
        EN -> "ENGLISH"
        RU -> "РУССКИЙ"
        else -> ""
    }

    fun badge(lang: String): String = if (lang == UZ) "V3" else ""
}

private fun letterLayout(
    name: String,
    r0: List<String>,
    r1: List<String>,
    r2: List<String>,
    bottomExtras: List<String> = listOf(",", ".")
): KeyboardLayout {
    val keys = mutableListOf<KeyData>()
    r0.forEachIndexed { i, c -> keys.add(KeyData(c, c, row = 0, col = i)) }
    r1.forEachIndexed { i, c -> keys.add(KeyData(c, c, row = 1, col = i)) }

    keys.add(KeyData("", SpecialCodes.SHIFT, row = 2, col = 0, width = 1.3f, isSpecial = true))
    r2.forEachIndexed { i, c -> keys.add(KeyData(c, c, row = 2, col = i + 1)) }
    keys.add(KeyData("", SpecialCodes.BACKSPACE, row = 2, col = r2.size + 1, width = 1.3f, isSpecial = true))

    var col = 0
    keys.add(KeyData("", SpecialCodes.SYMBOLS, row = 3, col = col++, width = 1.3f, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.LANG_SWITCH, row = 3, col = col++, isSpecial = true))
    // Vergul space'ning chap tomonida, qolgan belgilar (".", "ё" ...) o'ng tomonida
    keys.add(KeyData(",", ",", row = 3, col = col++))
    val rightExtras = bottomExtras.filter { it != "," }
    val spaceW = 4.4f - 0.6f * (bottomExtras.size - 2)
    keys.add(KeyData("", SpecialCodes.SPACE, row = 3, col = col++, width = spaceW, isSpecial = true))
    rightExtras.forEach { keys.add(KeyData(it, it, row = 3, col = col++)) }
    keys.add(KeyData("", SpecialCodes.ENTER, row = 3, col = col, width = 1.6f, isSpecial = true))

    return KeyboardLayout(name = name, rows = 4, cols = r0.size, keys = keys)
}

/** Qoraqalpoq lotin alifbosi: á ǵ í ń ó ú harflari bilan. */
fun karakalpakLayout(): KeyboardLayout = letterLayout(
    "Qaraqalpaqsha",
    listOf("q", "w", "e", "r", "t", "y", "u", "\u00ED", "i", "o", "p"),
    listOf("a", "\u00E1", "s", "d", "f", "g", "\u01F5", "h", "j", "k", "l"),
    listOf("z", "x", "c", "v", "b", "n", "\u0144", "m", "\u00F3", "\u00FA")
)

fun englishLayout(): KeyboardLayout = letterLayout(
    "English",
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("z", "x", "c", "v", "b", "n", "m")
)

fun russianLayout(): KeyboardLayout = letterLayout(
    "Русский",
    listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х"),
    listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
    listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "ъ"),
    bottomExtras = listOf("ё", ",", ".")
)
