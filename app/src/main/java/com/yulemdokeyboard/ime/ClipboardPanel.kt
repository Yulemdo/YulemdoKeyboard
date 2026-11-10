package com.yulemdokeyboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.yulemdokeyboard.model.Languages
import org.json.JSONArray

/** Nusxalangan matnlar: oxirgi 30 ta va pinlangan 25 ta (SharedPreferences, JSON). */
object ClipStorage {
    const val MAX_RECENT = 30
    const val MAX_PINNED = 25
    private const val PREFS = "yulemdo_clipboard"

    private fun load(ctx: Context, key: String): MutableList<String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null) ?: return mutableListOf()
        return try {
            val a = JSONArray(raw)
            MutableList(a.length()) { a.getString(it) }
        } catch (e: Exception) { mutableListOf() }
    }

    private fun save(ctx: Context, key: String, list: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key, JSONArray(list).toString()).apply()
    }

    fun recent(ctx: Context) = load(ctx, "recent")
    fun pinned(ctx: Context) = load(ctx, "pinned")

    /** Yangi nusxalangan matn: ro'yxat boshiga qo'shiladi, takrori olib tashlanadi, 30 tadan oshsa eskisi ketadi. */
    fun addRecent(ctx: Context, text: String) {
        if (text.isBlank()) return
        val t = text.take(5000)
        if (pinned(ctx).contains(t)) return
        val list = recent(ctx)
        list.remove(t)
        list.add(0, t)
        while (list.size > MAX_RECENT) list.removeAt(list.size - 1)
        save(ctx, "recent", list)
    }

    fun removeRecent(ctx: Context, text: String) {
        val l = recent(ctx); l.remove(text); save(ctx, "recent", l)
    }

    fun clearRecent(ctx: Context) = save(ctx, "recent", emptyList())

    /** false qaytarsa - limit (25) to'lgan. */
    fun pin(ctx: Context, text: String): Boolean {
        val p = pinned(ctx)
        if (p.contains(text)) return true
        if (p.size >= MAX_PINNED) return false
        p.add(0, text)
        save(ctx, "pinned", p)
        removeRecent(ctx, text)
        return true
    }

    fun unpin(ctx: Context, text: String) {
        val p = pinned(ctx); p.remove(text); save(ctx, "pinned", p)
        addRecent(ctx, text)
    }

    fun removePinned(ctx: Context, text: String) {
        val p = pinned(ctx); p.remove(text); save(ctx, "pinned", p)
    }
}

/** Klaviatura tilida (uz / kaa / en / ru) yozuvlar. */
private class ClipStrings(
    val recent: String, val pinned: String, val clear: String, val empty: String,
    val showFull: String, val showLess: String, val limit: String
)

private fun clipStrings(lang: String) = when (lang) {
    Languages.RU -> ClipStrings(
        "Недавно скопированное", "Закреплённые", "Очистить", "Пока пусто",
        "Показать полностью", "Свернуть", "Лимит закреплённых — 25. Сначала удалите одну запись"
    )
    Languages.EN -> ClipStrings(
        "Recently copied", "Pinned", "Clear", "Nothing here yet",
        "Show full", "Show less", "Pin limit is 25. Remove one first"
    )
    Languages.KAA -> ClipStrings(
        "Sońǵı nusqalanǵanlar", "Bekitilgen", "Tazalaw", "Házirshe bos",
        "Tolıq kóriw", "Qısqartıw", "Bekitiw limiti — 25. Aldın birewin óshiriń"
    )
    else -> ClipStrings(
        "Oxirgi nusxalanganlar", "Pinlangan", "Tozalaş", "Hozircha hiç narsa yöq",
        "Töliq köriş", "Qisqartiriş", "Pinlar limiti — 25 ta. Avval birini öçiring"
    )
}

class ClipboardPanel(
    context: Context,
    private val onPaste: (String) -> Unit,
    private val onBack: () -> Unit
) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private var lang = Languages.UZ
    private val expanded = HashSet<String>()

    private val night = KeyboardView.isNight(context)
    private val textColor = if (night) Color.parseColor("#E8EAED") else Color.parseColor("#202124")
    private val subColor = if (night) Color.parseColor("#9AA0A6") else Color.parseColor("#5F6368")
    private val itemBg = if (night) Color.parseColor("#2C2C2E") else Color.WHITE
    private val fire = Color.parseColor("#FFC107")

    private val recentTitle = TextView(context)
    private val pinnedTitle = TextView(context)
    private val clearBtn = TextView(context)
    private val recentList = LinearLayout(context).apply { orientation = VERTICAL }
    private val pinnedList = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        setBackgroundColor(KeyboardView.backgroundColorFor(context))

        // 1-sarlavha: ABC | Oxirgi nusxalanganlar (n/30) | Tozalash
        val h1 = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        h1.addView(pill("ABC", true).apply { setOnClickListener { onBack() } },
            LayoutParams((52 * dp).toInt(), (30 * dp).toInt()).apply { marginStart = (8 * dp).toInt() })
        styleHeader(recentTitle)
        h1.addView(recentTitle, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        clearBtn.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(fire)
            typeface = Typeface.DEFAULT_BOLD
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            setOnClickListener { ClipStorage.clearRecent(context); refresh() }
        }
        h1.addView(clearBtn)
        addView(h1, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (40 * dp).toInt()))

        addView(scroll(recentList), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // 2-sarlavha: Pinlangan (n/25)
        styleHeader(pinnedTitle)
        pinnedTitle.setPadding((14 * dp).toInt(), 0, 0, 0)
        addView(pinnedTitle, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (32 * dp).toInt()))
        addView(scroll(pinnedList), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun setLang(l: String) { lang = l; refresh() }

    private fun scroll(inner: View) = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(inner)
    }

    private fun styleHeader(tv: TextView) = tv.apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(subColor)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL
        setPadding((10 * dp).toInt(), 0, 0, 0)
    }

    private fun pill(label: String, filled: Boolean) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (filled) Color.WHITE else textColor)
        background = GradientDrawable().apply {
            cornerRadius = 15 * dp
            if (filled) {
                colors = intArrayOf(Color.parseColor("#FF3D00"), Color.parseColor("#FFC107"))
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            } else setColor(itemBg)
        }
    }

    fun refresh() {
        val s = clipStrings(lang)
        val recent = ClipStorage.recent(context)
        val pinned = ClipStorage.pinned(context)
        recentTitle.text = "${s.recent} (${recent.size}/${ClipStorage.MAX_RECENT})"
        pinnedTitle.text = "\uD83D\uDCCC ${s.pinned} (${pinned.size}/${ClipStorage.MAX_PINNED})"
        clearBtn.text = s.clear
        clearBtn.visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
        fill(recentList, recent, false, s)
        fill(pinnedList, pinned, true, s)
    }

    private fun fill(parent: LinearLayout, items: List<String>, isPinned: Boolean, s: ClipStrings) {
        parent.removeAllViews()
        if (items.isEmpty()) {
            parent.addView(TextView(context).apply {
                text = s.empty
                setTextColor(subColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
            }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        items.forEach { parent.addView(itemView(it, isPinned, s), itemLp()) }
    }

    private fun itemLp() = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        setMargins((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
    }

    private fun itemView(text: String, isPinned: Boolean, s: ClipStrings): View {
        val isLong = text.length > 60 || text.count { it == '\n' } >= 2
        val open = expanded.contains(text)

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(itemBg)
                if (isPinned) setStroke((1 * dp).toInt(), fire)
            }
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener { onPaste(text) }
        }

        val col = LinearLayout(context).apply { orientation = VERTICAL }
        col.addView(TextView(context).apply {
            this.text = text
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            if (open) maxLines = 30 else { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        })
        if (isLong) {
            col.addView(TextView(context).apply {
                this.text = if (open) s.showLess else s.showFull
                setTextColor(fire)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, (4 * dp).toInt(), 0, (2 * dp).toInt())
                setOnClickListener {
                    if (open) expanded.remove(text) else expanded.add(text)
                    refresh()
                }
            })
        }
        row.addView(col, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // pin / unpin
        row.addView(iconBtn("\uD83D\uDCCC", if (isPinned) 1f else 0.4f) {
            if (isPinned) ClipStorage.unpin(context, text)
            else if (!ClipStorage.pin(context, text)) {
                Toast.makeText(context, s.limit, Toast.LENGTH_SHORT).show()
            }
            refresh()
        })
        // o'chirish
        row.addView(iconBtn("\u2715", 0.7f) {
            if (isPinned) ClipStorage.removePinned(context, text) else ClipStorage.removeRecent(context, text)
            refresh()
        })
        return row
    }

    private fun iconBtn(glyph: String, alpha: Float, onClick: () -> Unit) = TextView(context).apply {
        text = glyph
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(textColor)
        this.alpha = alpha
        setOnClickListener { onClick() }
        layoutParams = LayoutParams((40 * dp).toInt(), (40 * dp).toInt())
    }
}
