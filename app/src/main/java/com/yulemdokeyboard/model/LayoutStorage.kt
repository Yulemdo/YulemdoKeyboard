package com.yulemdokeyboard.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Har bir til (uz, kaa, en, ru) uchun alohida custom layout saqlanadi.
 * Custom layout yo'q bo'lsa - tilning standart layouti ishlatiladi.
 * (O'zbek tili eski kalit bilan saqlanadi, shuning uchun avvalgi saqlangan layout yo'qolmaydi.)
 */
object LayoutStorage {
    private const val PREFS = "uz_keyboard_prefs"
    private const val KEY_CUSTOM_LAYOUT = "custom_layout_json"
    private const val KEY_ACTIVE_LAYOUT = "active_layout" // faqat uz uchun: "default" yoki "custom"

    // ===== Tilga bog'liq layoutlar =====
    fun defaultLayoutFor(lang: String): KeyboardLayout = when (lang) {
        Languages.KAA -> karakalpakLayout()
        Languages.EN -> englishLayout()
        Languages.RU -> russianLayout()
        else -> defaultUzbekLayout()
    }

    private fun customKey(lang: String) =
        if (lang == Languages.UZ) KEY_CUSTOM_LAYOUT else "${KEY_CUSTOM_LAYOUT}_$lang"

    fun saveCustomLayout(context: Context, lang: String, layout: KeyboardLayout) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(customKey(lang), toJson(layout).toString()).apply()
        if (lang == Languages.UZ) setActiveLayout(context, "custom")
    }

    fun loadCustomLayout(context: Context, lang: String): KeyboardLayout? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(customKey(lang), null) ?: return null
        return try {
            fromJson(JSONObject(raw), lang)
        } catch (e: Exception) {
            null
        }
    }

    /** Custom layoutni o'chiradi - til yana standart layoutga qaytadi. */
    fun clearCustomLayout(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(customKey(lang)).apply()
        if (lang == Languages.UZ) setActiveLayout(context, "default")
    }

    fun hasCustomLayout(context: Context, lang: String): Boolean {
        if (lang == Languages.UZ && getActiveLayout(context) != "custom") return false
        return loadCustomLayout(context, lang) != null
    }

    /** IME shu funksiyani chaqiradi: tilning joriy layouti (custom bo'lsa custom, aks holda standart). */
    fun getLayoutFor(context: Context, lang: String): KeyboardLayout {
        val custom = if (hasCustomLayout(context, lang)) loadCustomLayout(context, lang) else null
        return custom ?: defaultLayoutFor(lang)
    }

    // ===== Eski API (moslik uchun, o'zbek tili) =====
    fun saveCustomLayout(context: Context, layout: KeyboardLayout) =
        saveCustomLayout(context, Languages.UZ, layout)

    fun loadCustomLayout(context: Context): KeyboardLayout? = loadCustomLayout(context, Languages.UZ)

    fun setActiveLayout(context: Context, which: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_LAYOUT, which).apply()
    }

    fun getActiveLayout(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_LAYOUT, "default") ?: "default"
    }

    fun getCurrentLayout(context: Context): KeyboardLayout = getLayoutFor(context, Languages.UZ)

    // ===== Til sozlamalari =====
    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("language", "uz") ?: "uz"

    fun setLanguage(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("language", lang).apply()
    }

    /** Klaviaturada yoqilgan tillar (globus bilan aylanadi). Hech narsa saqlanmagan bo'lsa - hammasi yoqiq. */
    fun getEnabledLanguages(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("enabled_langs", null)
            ?: return Languages.order
        val set = raw.split(",").toSet()
        return Languages.order.filter { it in set }.ifEmpty { Languages.order }
    }

    /** false qaytarsa - kamida bitta til yoniq qolishi kerak, shuning uchun o'chirilmadi. */
    fun setLanguageEnabled(context: Context, lang: String, enabled: Boolean): Boolean {
        val cur = getEnabledLanguages(context).toMutableList()
        if (enabled) {
            if (lang !in cur) cur.add(lang)
        } else {
            if (lang in cur && cur.size <= 1) return false
            cur.remove(lang)
        }
        val ordered = Languages.order.filter { it in cur }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("enabled_langs", ordered.joinToString(",")).apply()
        return true
    }

    /** Eski saqlangan layoutlarni yangi belgilarga o'tkazadi (harf migratsiyasi faqat o'zbek uchun). */
    private fun migrateKey(k: KeyData, uz: Boolean) {
        when (k.code) {
            SpecialCodes.SHIFT, SpecialCodes.BACKSPACE, SpecialCodes.ENTER, SpecialCodes.LANG_SWITCH -> k.label = ""
            "\uD83D\uDE42" -> { k.label = ""; k.code = SpecialCodes.EMOJI }
            "o\u02BB" -> if (uz) { k.label = "ö"; k.code = "ö" }
            "g\u02BB" -> if (uz) { k.label = "ğ"; k.code = "ğ" }
            "'" -> if (uz && !k.isSpecial) { k.label = "\u02BC"; k.code = "\u02BC" }
        }
    }

    private fun toJson(layout: KeyboardLayout): JSONObject {
        val obj = JSONObject()
        obj.put("name", layout.name)
        obj.put("rows", layout.rows)
        obj.put("cols", layout.cols)
        val arr = JSONArray()
        layout.keys.forEach { k ->
            val ko = JSONObject()
            ko.put("label", k.label)
            ko.put("code", k.code)
            ko.put("row", k.row)
            ko.put("col", k.col)
            ko.put("width", k.width)
            ko.put("special", k.isSpecial)
            arr.put(ko)
        }
        obj.put("keys", arr)
        return obj
    }

    private fun fromJson(obj: JSONObject, lang: String): KeyboardLayout {
        val keys = mutableListOf<KeyData>()
        val arr = obj.getJSONArray("keys")
        for (i in 0 until arr.length()) {
            val ko = arr.getJSONObject(i)
            keys.add(
                KeyData(
                    label = ko.getString("label"),
                    code = ko.getString("code"),
                    row = ko.getInt("row"),
                    col = ko.getInt("col"),
                    width = ko.optDouble("width", 1.0).toFloat(),
                    isSpecial = ko.optBoolean("special", false)
                )
            )
        }
        keys.forEach { migrateKey(it, lang == Languages.UZ) }
        return KeyboardLayout(
            name = obj.optString("name", "Custom"),
            rows = obj.optInt("rows", 4),
            cols = obj.optInt("cols", 10),
            keys = keys
        )
    }
}
