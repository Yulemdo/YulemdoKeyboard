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
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
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
    private val baseHeight: Int,
    private val onPaste: (String) -> Unit,
    private val onBack: () -> Unit
) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private var lang = Languages.UZ
    private var longMode = false   // false: bitta qatorli qisqa ro'yxat, true: uzun (katta) setka

    private val night = KeyboardView.isNight(context)
    private val fg = if (night) Color.parseColor("#F2F2F2") else Color.parseColor("#111111")
    private val sub = if (night) Color.parseColor("#9AA0A6") else Color.parseColor("#5F6368")
    private val tileBg = if (night) Color.parseColor("#2A2A2C") else Color.WHITE
    private val tileBorder = if (night) Color.parseColor("#44FFFFFF") else Color.parseColor("#33000000")

    private val tile = (66 * dp).toInt()
    private val gap = (4 * dp).toInt()

    private val recentTitle = TextView(context)
    private val pinnedTitle = TextView(context)
    private val toggleBtn = TextView(context)
    private val clearBtn = IconView(context, IconView.Type.TRASH, fg)
    private val recentHolder = FrameLayout(context)
    private val pinnedHolder = HorizontalScrollView(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(KeyboardView.backgroundColorFor(context))

        // 1-sarlavha: ABC | Oxirgi nusxalanganlar (n/30) | Uzun/Qisqa | chiqitga
        val h1 = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        h1.addView(pill("ABC").apply { setPadding(0, 0, 0, 0); setOnClickListener { onBack() } },
            LayoutParams((50 * dp).toInt(), (30 * dp).toInt()).apply { marginStart = (8 * dp).toInt() })
        recentTitle.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(sub)
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setPadding((10 * dp).toInt(), 0, (6 * dp).toInt(), 0)
        }
        h1.addView(recentTitle, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        stylePill(toggleBtn)
        toggleBtn.setOnClickListener { setLongMode(!longMode) }
        h1.addView(toggleBtn, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (30 * dp).toInt()).apply { marginEnd = (4 * dp).toInt() })
        clearBtn.setOnClickListener { ClipStorage.clearRecent(context); refresh() }
        h1.addView(clearBtn, LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))
        addView(h1, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (40 * dp).toInt()))

        addView(recentHolder, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Pinlanganlar doim pastda
        pinnedTitle.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(sub)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), 0, 0, 0)
        }
        addView(pinnedTitle, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (26 * dp).toInt()))
        pinnedHolder.isHorizontalScrollBarEnabled = false
        pinnedHolder.overScrollMode = OVER_SCROLL_NEVER
        addView(pinnedHolder, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tile + 2 * gap + (4 * dp).toInt()))
    }

    fun setLang(l: String) { lang = l; refresh() }

    private fun setLongMode(v: Boolean) {
        longMode = v
        layoutParams?.let {
            it.height = if (v) baseHeight * 7 / 4 else baseHeight
            layoutParams = it
        }
        refresh()
    }

    private fun stylePill(t: TextView): TextView = t.apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(fg)
        setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
        background = GradientDrawable().apply {
            cornerRadius = 15 * dp
            setColor(tileBg)
            setStroke((1 * dp).toInt(), tileBorder)
        }
    }

    private fun pill(label: String) = stylePill(TextView(context)).apply { text = label }

    fun refresh() {
        val s = clipStrings(lang)
        val recent = ClipStorage.recent(context)
        val pinned = ClipStorage.pinned(context)
        recentTitle.text = "${s.recent} (${recent.size}/${ClipStorage.MAX_RECENT})"
        pinnedTitle.text = "${s.pinned} (${pinned.size}/${ClipStorage.MAX_PINNED})"
        toggleBtn.text = if (longMode) s.showLess else s.showFull
        clearBtn.visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE

        // ----- oxirgilar -----
        recentHolder.removeAllViews()
        if (recent.isEmpty()) {
            recentHolder.addView(emptyText(s.empty), FrameLayout.LayoutParams(-1, -1))
        } else if (longMode) {
            val cols = ((resources.displayMetrics.widthPixels - 8 * dp) / (tile + 2 * gap)).toInt().coerceAtLeast(3)
            val grid = GridLayout(context).apply { columnCount = cols }
            recent.forEach { t ->
                grid.addView(tileView(t, false, s), GridLayout.LayoutParams().apply {
                    width = tile; height = tile
                    setMargins(gap, gap, gap, gap)
                })
            }
            val sv = ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_NEVER
                setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                addView(grid)
            }
            recentHolder.addView(sv, FrameLayout.LayoutParams(-1, -1))
        } else {
            recentHolder.addView(rowOf(recent, false, s), FrameLayout.LayoutParams(-1, -1))
        }

        // ----- pinlangan -----
        pinnedHolder.removeAllViews()
        if (pinned.isEmpty()) pinnedHolder.addView(emptyText(s.empty).apply {
            layoutParams = ViewGroup.LayoutParams(resources.displayMetrics.widthPixels, tile)
        })
        else pinnedHolder.addView(rowInner(pinned, true, s))
    }

    private fun emptyText(t: String) = TextView(context).apply {
        text = t
        setTextColor(sub)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
    }

    private fun rowOf(items: List<String>, isPinned: Boolean, s: ClipStrings): View =
        HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(rowInner(items, isPinned, s))
        }

    private fun rowInner(items: List<String>, isPinned: Boolean, s: ClipStrings): LinearLayout =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(gap * 2, gap, gap * 2, gap)
            items.forEach { addView(tileView(it, isPinned, s), LayoutParams(tile, tile).apply { setMargins(gap, 0, gap, 0) }) }
        }

    /** Kvadrat katakcha: matn (3 qatorgacha, uzun bo'lsa "...") + pastda pin va o'chirish. */
    private fun tileView(text: String, isPinned: Boolean, s: ClipStrings): View {
        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 10 * dp
                setColor(tileBg)
                setStroke(((if (isPinned) 1.6f else 1f) * dp).toInt(), if (isPinned) fg else tileBorder)
            }
            setPadding((6 * dp).toInt(), (5 * dp).toInt(), (3 * dp).toInt(), (2 * dp).toInt())
            setOnClickListener { onPaste(text) }
        }
        box.addView(TextView(context).apply {
            this.text = text
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(context).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val sz = (22 * dp).toInt()
        bar.addView(IconView(context, IconView.Type.PIN, if (isPinned) fg else sub, filled = isPinned).apply {
            setOnClickListener {
                if (isPinned) ClipStorage.unpin(context, text)
                else if (!ClipStorage.pin(context, text)) Toast.makeText(context, s.limit, Toast.LENGTH_SHORT).show()
                refresh()
            }
        }, LayoutParams(sz, sz))
        bar.addView(IconView(context, IconView.Type.CLOSE, sub).apply {
            setOnClickListener {
                if (isPinned) ClipStorage.removePinned(context, text) else ClipStorage.removeRecent(context, text)
                refresh()
            }
        }, LayoutParams(sz, sz))
        box.addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sz))
        return box
    }
}
