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
        Languages.UZ -> defaultUzbekLayout()
        else -> blankLayout(Languages.nativeName(lang))   // o'z til: standart = bo'sh
    }

    // ===== O'z tillar (foydalanuvchi 0 dan yaratadi) =====
    fun getCustomLanguages(context: Context): List<Pair<String, String>> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("custom_langs", null)
        val list = mutableListOf<Pair<String, String>>()
        if (raw != null) try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(o.getString("id") to o.getString("name"))
            }
        } catch (e: Exception) { }
        Languages.setCustom(list)
        return list
    }

    private fun saveCustomLanguages(context: Context, list: List<Pair<String, String>>) {
        val arr = JSONArray()
        list.forEach { (id, name) -> arr.put(JSONObject().put("id", id).put("name", name)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("custom_langs", arr.toString()).apply()
        Languages.setCustom(list)
    }

    /** Yangi til yaratadi, layoutini saqlaydi va yoqadi. Til id'sini qaytaradi. */
    fun addCustomLanguage(context: Context, name: String, layout: KeyboardLayout): String {
        val list = getCustomLanguages(context).toMutableList()
        val id = "x" + java.lang.Long.toString(System.currentTimeMillis(), 36)
        list.add(id to name)
        saveCustomLanguages(context, list)
        saveCustomLayout(context, id, KeyboardLayout(name, layout.rows, layout.cols, layout.keys))
        setLanguageEnabled(context, id, true)
        return id
    }

    fun deleteCustomLanguage(context: Context, id: String) {
        val list = getCustomLanguages(context).filter { it.first != id }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = getEnabledLanguages(context).filter { it != id }
        prefs.edit().remove(customKey(id))
            .putString("enabled_langs", enabled.joinToString(","))
            .apply()
        saveCustomLanguages(context, list)
        if (getLanguage(context) == id) setLanguage(context, Languages.UZ)
    }

    // ===== Mening belgilarim (paste qilingan, barcha tillar uchun umumiy) =====
    fun getMySymbols(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("my_symbols", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    private fun putMySymbols(context: Context, list: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("my_symbols", JSONArray(list).toString()).apply()
    }

    fun addMySymbols(context: Context, add: List<String>) {
        putMySymbols(context, (getMySymbols(context) + add).distinct())
    }

    fun removeMySymbol(context: Context, s: String) {
        putMySymbols(context, getMySymbols(context).filter { it != s })
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
        getCustomLanguages(context)   // o'z tillar ro'yxatini Languages'ga yuklaydi
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
