package com.yulemdokeyboard.ime

import android.content.Context
import com.yulemdokeyboard.model.Languages
import java.text.Normalizer

/**
 * Yozilayotgan harflarni Unicode "shrift"larga aylantiradi (qalin, kursiv, qo'lyozma, doira ichida ...).
 * - Lotin alifbosi (özbek, qoraqalpoq, ingliz): hamma shriftlar. ö, ğ, ş, ç, á, ǵ ... kabi harflar
 *   asosiy harf + belgi (diakritik) ko'rinishida chiziladi.
 * - Kirill alifbosi (rus): faqat tagiga chizilgan / ustidan chizilgan kabi belgi qo'shadigan shriftlar,
 *   chunki kirill uchun Unicode'da qalin/kursiv alifbolar yo'q.
 */
object FontStyles {

    class Style(
        val id: String,
        val universal: Boolean,                 // true - istalgan alifboda ishlaydi
        val mark: String? = null,               // har bir belgidan keyin qo'shiladigan birlashtiruvchi belgi
        val base: ((Char) -> String)? = null    // ASCII harf/raqamni shriftli belgiga aylantirish
    )

    private fun cp(code: Int) = String(Character.toChars(code))

    private fun math(
        upper: Int, lower: Int, digit: Int? = null, ex: Map<Char, String> = emptyMap()
    ): (Char) -> String = { c ->
        ex[c] ?: when {
            c in 'A'..'Z' -> cp(upper + (c - 'A'))
            c in 'a'..'z' -> cp(lower + (c - 'a'))
            digit != null && c in '0'..'9' -> cp(digit + (c - '0'))
            else -> c.toString()
        }
    }

    private val scriptEx = mapOf(
        'B' to "\u212C", 'E' to "\u2130", 'F' to "\u2131", 'H' to "\u210B", 'I' to "\u2110",
        'L' to "\u2112", 'M' to "\u2133", 'R' to "\u211B", 'e' to "\u212F", 'g' to "\u210A", 'o' to "\u2134"
    )
    private val frakturEx = mapOf('C' to "\u212D", 'H' to "\u210C", 'I' to "\u2111", 'R' to "\u211C", 'Z' to "\u2128")
    private val doubleEx = mapOf(
        'C' to "\u2102", 'H' to "\u210D", 'N' to "\u2115", 'P' to "\u2119",
        'Q' to "\u211A", 'R' to "\u211D", 'Z' to "\u2124"
    )
    private val circledDigits: Map<Char, String> =
        mapOf('0' to "\u24EA") + ('1'..'9').associateWith { cp(0x2460 + (it - '1')) }

    private val smallCapsMap = "\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D\u0274\u1D0F\u1D18\u01EB\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21x\u028F\u1D22"

    val all: List<Style> = listOf(
        Style("bold", true, base = math(0x1D400, 0x1D41A, 0x1D7CE)),
        Style("italic", true, base = math(0x1D434, 0x1D44E, null, mapOf('h' to "\u210E"))),
        Style("bolditalic", true, base = math(0x1D468, 0x1D482)),
        Style("script", true, base = math(0x1D49C, 0x1D4B6, null, scriptEx)),
        Style("boldscript", true, base = math(0x1D4D0, 0x1D4EA)),
        Style("fraktur", true, base = math(0x1D504, 0x1D51E, null, frakturEx)),
        Style("boldfraktur", true, base = math(0x1D56C, 0x1D586)),
        Style("double", true, base = math(0x1D538, 0x1D552, 0x1D7D8, doubleEx)),
        Style("mono", true, base = math(0x1D670, 0x1D68A, 0x1D7F6)),
        Style("sansbold", true, base = math(0x1D5D4, 0x1D5EE, 0x1D7EC)),
        Style("sansitalic", true, base = math(0x1D608, 0x1D622)),
        Style("circled", true, base = math(0x24B6, 0x24D0, null, circledDigits)),
        Style("squared", true, base = { c ->
            when {
                c in 'A'..'Z' -> cp(0x1F130 + (c - 'A'))
                c in 'a'..'z' -> cp(0x1F130 + (c - 'a'))
                else -> c.toString()
            }
        }),
        Style("smallcaps", true, base = { c ->
            when {
                c in 'a'..'z' -> smallCapsMap[c - 'a'].toString()
                c in 'A'..'Z' -> smallCapsMap[c - 'A'].toString()
                else -> c.toString()
            }
        }),
        Style("fullwidth", true, base = math(0xFF21, 0xFF41, 0xFF10)),
        // Kirill alifbosida ham ishlaydigan, belgi qo'shuvchi shriftlar
        Style("underline", false, mark = "\u0332"),
        Style("doubleunderline", false, mark = "\u0333"),
        Style("strike", false, mark = "\u0336"),
        Style("tildestrike", false, mark = "\u0334")
    )

    /** Joriy klaviatura alifbosiga mos shriftlar: lotin - hammasi, kirill - faqat belgi qo'shuvchilar. */
    fun forLang(lang: String): List<Style> =
        if (lang == Languages.RU) all.filter { it.mark != null } else all

    fun byId(id: String?, lang: String): Style? = forLang(lang).firstOrNull { it.id == id }

    fun apply(style: Style?, text: String): String {
        if (style == null || text.isEmpty()) return text
        val sb = StringBuilder()
        if (style.mark != null) {
            text.forEach { c ->
                sb.append(c)
                if (!c.isWhitespace()) sb.append(style.mark)
            }
            return sb.toString()
        }
        val f = style.base ?: return text
        // ö -> o + ̈,  ş -> s + ̧ ...: asosiy harf shriftlanadi, belgi ustiga qo'shiladi
        for (c in Normalizer.normalize(text, Normalizer.Form.NFD)) {
            sb.append(if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9') f(c) else c.toString())
        }
        return sb.toString()
    }

    // ===== Tanlovni saqlash =====
    private const val PREFS = "yulemdo_font"
    fun getActive(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("active", "") ?: ""
    fun setActive(ctx: Context, id: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("active", id).apply()
        if (id.isNotEmpty()) ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("last", id).apply()
    }
    fun getLast(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("last", "") ?: ""
}
