package com.yulemdokeyboard.model

/** Qo'shimcha tillar uchun klaviatura joylashuvlari: qoraqalpoq (lotin), ingliz, rus. */

object Languages {
    const val UZ = "uz"
    const val KAA = "kaa"
    const val EN = "en"
    const val RU = "ru"

    /** O'rnatilgan tillar. */
    val builtin = listOf(UZ, KAA, EN, RU)

    /** Foydalanuvchi o'zi yaratgan tillar (id, nom). LayoutStorage.getCustomLanguages() to'ldiradi. */
    @Volatile
    private var custom: List<Pair<String, String>> = emptyList()

    fun setCustom(list: List<Pair<String, String>>) { custom = list }
    fun isBuiltin(lang: String) = lang in builtin

    /** Globus tugmasi bosilganda aylanish tartibi (o'rnatilgan + o'z tillar). */
    val order: List<String> get() = builtin + custom.map { it.first }

    fun next(current: String): String = order[(order.indexOf(current) + 1).mod(order.size)]

    /** Faqat yoqilgan tillar orasida aylanadi. */
    fun next(current: String, enabled: List<String>): String {
        val list = order.filter { it in enabled }.ifEmpty { listOf(UZ) }
        val i = list.indexOf(current)
        return if (i < 0) list[0] else list[(i + 1) % list.size]
    }

    /** Til ro'yxatida va sozlamalarda ko'rinadigan nom. */
    fun nativeName(lang: String): String = when (lang) {
        UZ -> "Özbekça"
        KAA -> "Qaraqalpaqsha"
        EN -> "English"
        RU -> "Русский"
        else -> custom.firstOrNull { it.first == lang }?.second ?: lang
    }

    /** Space tugmasida ko'rinadigan nom (yangi alifbo bo'yicha: Özbekça). */
    fun spaceLabel(lang: String): String = when (lang) {
        UZ -> "ÖZBEKÇA"
        KAA -> "QARAQALPAQSHA"
        EN -> "ENGLISH"
        RU -> "РУССКИЙ"
        else -> custom.firstOrNull { it.first == lang }?.second?.uppercase() ?: ""
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
    // "ъ" alohida tugma emas: "ь" ni bosib tursangiz "ъ" yoziladi. "ё" ham alohida emas: "е" ni bosib turing.
    listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю")
)

/**
 * Bo'sh (0 dan) layout: faqat Shift, Backspace va pastki qator (123, til, vergul, space, nuqta, enter).
 * Harflarni foydalanuvchi muharrirda o'zi joylaydi.
 */
fun blankLayout(name: String): KeyboardLayout {
    val keys = mutableListOf<KeyData>()
    keys.add(KeyData("", SpecialCodes.SHIFT, row = 2, col = 0, width = 1.3f, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.BACKSPACE, row = 2, col = 10, width = 1.3f, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.SYMBOLS, row = 3, col = 0, width = 1.3f, isSpecial = true))
    keys.add(KeyData("", SpecialCodes.LANG_SWITCH, row = 3, col = 1, isSpecial = true))
    keys.add(KeyData(",", ",", row = 3, col = 2))
    keys.add(KeyData("", SpecialCodes.SPACE, row = 3, col = 3, width = 4.4f, isSpecial = true))
    keys.add(KeyData(".", ".", row = 3, col = 4))
    keys.add(KeyData("", SpecialCodes.ENTER, row = 3, col = 5, width = 1.6f, isSpecial = true))
    return KeyboardLayout(name = name, rows = 4, cols = 11, keys = keys)
}
