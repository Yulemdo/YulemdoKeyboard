package com.yulemdokeyboard.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * KeyboardLayout obyektini JSON ko'rinishida SharedPreferences ichida saqlaydi/o'qiydi.
 * Shunday qilib foydalanuvchi ilova ichida drag-drop bilan tuzgan custom klaviaturasi
 * ilova qayta ochilganda ham, haqiqiy IME service ichida ham ishlatiladi.
 */
object LayoutStorage {
    private const val PREFS = "uz_keyboard_prefs"
    private const val KEY_CUSTOM_LAYOUT = "custom_layout_json"
    private const val KEY_ACTIVE_LAYOUT = "active_layout" // "default" yoki "custom"

    fun saveCustomLayout(context: Context, layout: KeyboardLayout) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_LAYOUT, toJson(layout).toString()).apply()
    }

    fun loadCustomLayout(context: Context): KeyboardLayout? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CUSTOM_LAYOUT, null) ?: return null
        return try {
            fromJson(JSONObject(raw))
        } catch (e: Exception) {
            null
        }
    }

    fun setActiveLayout(context: Context, which: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_LAYOUT, which).apply()
    }

    fun getActiveLayout(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_LAYOUT, "default") ?: "default"
    }

    /** IME shu funksiyani chaqiradi: foydalanuvchi tanlagan (default yoki custom) layoutni qaytaradi. */
    fun getCurrentLayout(context: Context): KeyboardLayout {
        return if (getActiveLayout(context) == "custom") {
            loadCustomLayout(context) ?: defaultUzbekLayout()
        } else {
            defaultUzbekLayout()
        }
    }

    /** Eski saqlangan layoutlarni yangi belgilarga (ö, ğ, ʼ, ikonkali tugmalar) o'tkazadi. */
    private fun migrateKey(k: KeyData) {
        when (k.code) {
            "o\u02BB" -> { k.label = "ö"; k.code = "ö" }
            "g\u02BB" -> { k.label = "ğ"; k.code = "ğ" }
            "'" -> if (!k.isSpecial) { k.label = "\u02BC"; k.code = "\u02BC" }
            "\uD83D\uDE42" -> { k.label = ""; k.code = SpecialCodes.EMOJI }
            SpecialCodes.SHIFT, SpecialCodes.BACKSPACE, SpecialCodes.ENTER, SpecialCodes.LANG_SWITCH -> k.label = ""
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

    private fun fromJson(obj: JSONObject): KeyboardLayout {
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
        keys.forEach { migrateKey(it) }
        return KeyboardLayout(
            name = obj.optString("name", "Custom"),
            rows = obj.optInt("rows", 4),
            cols = obj.optInt("cols", 10),
            keys = keys
        )
    }
}
