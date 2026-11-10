package com.yulemdokeyboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Qurilmaning o'z emojilarini ko'rsatadigan panel.
 * Faqat shu qurilma shrifti chiza oladigan emojilar ko'rsatiladi (Paint.hasGlyph).
 * Pastki qator: klaviatura ikonkasi (harflarga qaytish), kategoriya ikonkalari
 * (oxirgilar, yuzlar, odamlar, hayvonlar, ovqat, faoliyat, sayohat, buyumlar, belgilar, bayroqlar)
 * va o'chirish tugmasi (bosib turilsa ketma-ket o'chiradi).
 */
class EmojiPanel(
    context: Context,
    private val onEmoji: (String) -> Unit,
    private val onBackspace: () -> Unit,
    private val onBack: () -> Unit,
    private val onSearch: () -> Unit = {},
    private val onExitSearch: () -> Unit = {}
) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private val scroll = ScrollView(context)
    private val grids = HashMap<Int, GridLayout>()
    private val tabViews = LinkedHashMap<Int, IconView>()
    private var currentIndex = 0
    private var lang = "uz"
    private var searching = false
    private var query = ""
    private val handler = Handler(Looper.getMainLooper())

    private val night = KeyboardView.isNight(context)
    private val iconColor = if (night) Color.parseColor("#F2F2F2") else Color.parseColor("#111111")
    private val accent = Color.parseColor("#FFC107")

    private val prefs = context.getSharedPreferences("yulemdo_emoji", Context.MODE_PRIVATE)

    private val categories: List<List<String>> by lazy {
        EmojiData.categories.map { supported(it.emojis.split(" ").filter { e -> e.isNotBlank() }) }
    }

    private val browseBox = LinearLayout(context).apply { orientation = VERTICAL }
    private val queryText = TextView(context)
    private val closeIcon = IconView(context, IconView.Type.CLOSE, iconColor)
    private val resultsRow = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val resultsScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(resultsRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(KeyboardView.backgroundColorFor(context))
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = OVER_SCROLL_NEVER

        // Qidiruv maydoni (tepada): bossangiz qidiruv rejimi ochiladi
        addView(buildSearchRow(), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (46 * dp).toInt()))

        browseBox.addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // ingichka ajratuvchi chiziq
        val line = View(context).apply {
            setBackgroundColor(if (night) Color.parseColor("#33FFFFFF") else Color.parseColor("#1F000000"))
        }
        browseBox.addView(line, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        browseBox.addView(buildBar(), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * dp).toInt()))
        addView(browseBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Qidiruv natijalari: bitta qatorli gorizontal ro'yxat
        addView(resultsScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (56 * dp).toInt()))

        select(if (getRecent().isNotEmpty()) RECENT_INDEX else 0)
    }

    // ===== Qidiruv =====
    private fun buildSearchRow(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(if (night) Color.parseColor("#2C2C2E") else Color.WHITE)
                setStroke((1 * dp).toInt().coerceAtLeast(1), if (night) Color.parseColor("#3A3A3C") else Color.parseColor("#D3D6DA"))
            }
            setPadding((6 * dp).toInt(), 0, (2 * dp).toInt(), 0)
            isClickable = true
            setOnClickListener { if (!searching) onSearch() }
        }
        row.addView(IconView(context, IconView.Type.SEARCH, iconColor), LayoutParams((30 * dp).toInt(), (30 * dp).toInt()))
        queryText.apply {
            textSize = 15f
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * dp).toInt(), 0, 0, 0)
        }
        row.addView(queryText, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        closeIcon.apply {
            isClickable = true
            visibility = GONE
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onExitSearch() }
        }
        row.addView(closeIcon, LayoutParams((38 * dp).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
        updateQueryText()

        return LinearLayout(context).apply {
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun searchHint() = when (lang) {
        "ru" -> "Поиск эмодзи"
        "en" -> "Search emoji"
        "kaa" -> "Emoji izlew"
        else -> "Emoji qidiriş"
    }

    private fun noResults() = when (lang) {
        "ru" -> "Ничего не найдено"
        "en" -> "Nothing found"
        "kaa" -> "Eshteńe tabılmadı"
        else -> "Heç narsa topilmadi"
    }

    private fun updateQueryText() {
        if (searching && query.isNotEmpty()) {
            queryText.text = query + "\u2502"
            queryText.setTextColor(iconColor)
        } else {
            queryText.text = searchHint()
            queryText.setTextColor(if (night) Color.parseColor("#8E8E93") else Color.parseColor("#8A8A8E"))
        }
    }

    fun setLang(l: String) { lang = l; updateQueryText(); if (searching) renderResults() }

    /** Qidiruv rejimi boshlanadi (klaviatura pastda ko'rinib turadi, yozilgan harflar shu maydonga tushadi). */
    fun beginSearch(l: String) {
        lang = l
        searching = true
        query = ""
        browseBox.visibility = GONE
        resultsScroll.visibility = VISIBLE
        closeIcon.visibility = VISIBLE
        updateQueryText()
        renderResults()
    }

    fun endSearch() {
        searching = false
        query = ""
        browseBox.visibility = VISIBLE
        resultsScroll.visibility = GONE
        closeIcon.visibility = GONE
        updateQueryText()
    }

    fun setQuery(q: String) {
        query = q
        updateQueryText()
        renderResults()
    }

    private fun renderResults() {
        resultsRow.removeAllViews()
        resultsScroll.scrollTo(0, 0)
        val list: List<String> = if (query.isBlank()) {
            val r = getRecent()
            if (r.isNotEmpty()) r else categories[0].take(40)
        } else {
            supported(EmojiSearch.search(query, EmojiData.categories.map { it.emojis })).take(80)
        }
        if (list.isEmpty()) {
            resultsRow.addView(TextView(context).apply {
                text = noResults()
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(if (night) Color.parseColor("#8E8E93") else Color.parseColor("#8A8A8E"))
                setPadding((16 * dp).toInt(), 0, 0, 0)
            }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            return
        }
        list.forEach { e ->
            val tv = TextView(context).apply {
                text = e
                textSize = 26f
                gravity = Gravity.CENTER
                setOnClickListener { selectEmoji(e) }
            }
            resultsRow.addView(tv, LayoutParams((48 * dp).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    // ===== Pastki panel =====
    private fun buildBar(): View {
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun add(v: View, weight: Float) {
            bar.addView(v, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight))
        }

        // Klaviatura ikonkasi - harflarga qaytish (avval "ABC" yozuvi edi)
        add(IconView(context, IconView.Type.KEYBOARD, iconColor).apply {
            isClickable = true
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onBack() }
        }, 1.4f)

        // Oxirgi ishlatilganlar
        addTab(bar, RECENT_INDEX, IconView.Type.CLOCK)
        // Oddiy kategoriyalar
        EmojiData.categories.forEachIndexed { i, cat -> addTab(bar, i, cat.icon) }

        // O'chirish (bosib turilsa ketma-ket)
        add(IconView(context, IconView.Type.BACKSPACE, iconColor).apply {
            isClickable = true
            setOnTouchListener { v, ev -> handleBackspaceTouch(v, ev) }
        }, 1.4f)
        return bar
    }

    private fun addTab(bar: LinearLayout, index: Int, type: IconView.Type) {
        val v = IconView(context, type, iconColor).apply {
            isClickable = true
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); select(index) }
        }
        tabViews[index] = v
        bar.addView(v, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private var repeating = false
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (!repeating) return
            onBackspace()
            handler.postDelayed(this, 45L)
        }
    }

    private fun handleBackspaceTouch(v: View, ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onBackspace()
                repeating = true
                handler.postDelayed(repeatRunnable, 400L)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                repeating = false
                handler.removeCallbacks(repeatRunnable)
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        repeating = false
        handler.removeCallbacks(repeatRunnable)
        super.onDetachedFromWindow()
    }

    // ===== Kategoriya tanlash =====
    private fun select(index: Int) {
        currentIndex = index
        val grid = if (index == RECENT_INDEX) buildRecentGrid()
        else grids.getOrPut(index) { buildGridFromList(categories[index]) }
        scroll.removeAllViews()
        (grid.parent as? ViewGroup)?.removeView(grid)
        scroll.addView(grid)
        scroll.scrollTo(0, 0)
        highlightTab()
    }

    private fun highlightTab() {
        tabViews.forEach { (i, v) ->
            val sel = i == currentIndex
            v.color = if (sel) accent else iconColor
            v.background = if (sel) GradientDrawable().apply {
                cornerRadius = 10 * dp
                setColor(Color.parseColor("#26FFC107"))
            } else null
            v.invalidate()
        }
    }

    private fun buildRecentGrid(): GridLayout {
        val recent = getRecent()
        if (recent.isEmpty()) {
            // Hali hech narsa ishlatilmagan bo'lsa ham bo'sh joy ko'rinmasin
            return GridLayout(context).apply {
                addView(TextView(context).apply {
                    text = "🕐"
                    textSize = 26f
                    alpha = 0.4f
                    gravity = Gravity.CENTER
                }, GridLayout.LayoutParams().apply {
                    width = resources.displayMetrics.widthPixels
                    height = (80 * dp).toInt()
                })
            }
        }
        return buildGridFromList(recent)
    }

    private fun buildGridFromList(list: List<String>): GridLayout {
        val columns = 8
        val cellW = resources.displayMetrics.widthPixels / columns
        val cellH = (46 * dp).toInt()
        val grid = GridLayout(context)
        grid.columnCount = columns
        list.forEach { e ->
            val tv = TextView(context)
            tv.text = e
            tv.textSize = 26f
            tv.gravity = Gravity.CENTER
            tv.setOnClickListener { selectEmoji(e) }
            val lp = GridLayout.LayoutParams()
            lp.width = cellW
            lp.height = cellH
            grid.addView(tv, lp)
        }
        return grid
    }

    private fun selectEmoji(e: String) {
        addRecent(e)
        onEmoji(e)
    }

    // ===== Oxirgi ishlatilgan emojilar (SharedPreferences, vergul bilan ajratilgan) =====
    private fun getRecent(): List<String> {
        val raw = prefs.getString(KEY_RECENT, "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }
    }

    private fun addRecent(e: String) {
        val list = getRecent().toMutableList()
        list.remove(e)
        list.add(0, e)
        while (list.size > MAX_RECENT) list.removeAt(list.size - 1)
        prefs.edit().putString(KEY_RECENT, list.joinToString(",")).apply()
        // Hozir "oxirgilar" tabida bo'lsak, ro'yxatni darhol yangilaymiz
        if (currentIndex == RECENT_INDEX) {
            scroll.removeAllViews()
            scroll.addView(buildRecentGrid())
        }
    }

    private fun supported(list: List<String>): List<String> {
        if (Build.VERSION.SDK_INT < 23) return list
        val p = Paint()
        return list.filter { p.hasGlyph(it) }
    }

    private companion object {
        const val RECENT_INDEX = -1
        const val KEY_RECENT = "recent_emojis"
        const val MAX_RECENT = 40
    }
}

object EmojiData {
    class Category(val icon: IconView.Type, val emojis: String)

    /** Davlat kodlaridan bayroq emojisi (ikki "regional indicator" belgisi). */
    private fun flags(codes: String): String = codes.split(" ").filter { it.length == 2 }.joinToString(" ") { c ->
        String(Character.toChars(0x1F1E6 + (c[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (c[1] - 'A')))
    }

    val categories: List<Category> = listOf(
        // Yuzlar va hissiyotlar
        Category(
            IconView.Type.SMILE,
            "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 🥲 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 🥸 😎 🤓 🧐 😕 😟 🙁 ☹ 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 ☠ 💩 🤡 👹 👺 👻 👽 👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊 💋 💌 💘 💝 💖 💗 💓 💞 💕 💟 💔 💯 💢 💥 💫 💦 💨 💬 💭 💤"
        ),
        // Qo'llar, tana, odamlar
        Category(
            IconView.Type.HAND,
            "👋 🤚 🖐 ✋ 🖖 👌 🤌 🤏 ✌ 🤞 🤟 🤘 🤙 👈 👉 👆 🖕 👇 ☝ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍ 💅 🤳 💪 🦾 🦿 🦵 🦶 👂 🦻 👃 🧠 🫀 🫁 🦷 🦴 👀 👁 👅 👄 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 👮 🕵 💂 🥷 👷 🤴 👸 👳 👲 🧕 🤵 👰 🤰 🤱 👼 🎅 🤶 🦸 🦹 🧙 🧚 🧛 🧜 🧝 🧞 🧟 💆 💇 🚶 🧍 🧎 🏃 💃 🕺 👯 🧖 🧗 🏇 🏂 🏄 🚣 🏊 🚴 🚵 🤸 🤼 🤽 🤾 🤹 🧘 🛀 🛌 👭 👫 👬 💏 💑 👪 👣"
        ),
        // Hayvonlar va tabiat
        Category(
            IconView.Type.PAW,
            "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐽 🐸 🐵 🐒 🦍 🦧 🐔 🐧 🐦 🐤 🐣 🐥 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🪱 🐛 🦋 🐌 🐞 🐜 🪰 🪲 🦟 🦗 🕷 🕸 🦂 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦐 🦞 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦒 🦘 🐘 🦣 🦏 🦛 🐪 🐫 🦙 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐩 🦮 🐈 🐓 🦃 🦚 🦜 🦢 🦩 🕊 🐇 🦝 🦨 🦡 🦫 🦦 🦥 🐁 🐀 🐿 🦔 🐾 🐉 🐲 🌵 🎄 🌲 🌳 🌴 🪵 🌱 🌿 ☘ 🍀 🎍 🎋 🍃 🍂 🍁 🍄 🐚 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌜 🌚 🌕 🌖 🌗 🌘 🌑 🌒 🌓 🌔 🌙 🌎 🌍 🌏 🪐 ⭐ 🌟 ✨ ⚡ ☄ 🔥 🌪 🌈 ☀ 🌤 ⛅ 🌥 ☁ 🌦 🌧 ⛈ 🌩 🌨 ❄ ☃ ⛄ 🌬 💧 ☔ ☂ 🌊 🌫"
        ),
        // Ovqat va ichimliklar
        Category(
            IconView.Type.FOOD,
            "🍇 🍈 🍉 🍊 🍋 🍌 🍍 🥭 🍎 🍏 🍐 🍑 🍒 🍓 🫐 🥝 🍅 🫒 🥥 🥑 🍆 🥔 🥕 🌽 🌶 🫑 🥒 🥬 🥦 🧄 🧅 🥜 🌰 🍞 🥐 🥖 🫓 🥨 🥯 🥞 🧇 🧀 🍖 🍗 🥩 🥓 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🫔 🥙 🧆 🥚 🍳 🥘 🍲 🫕 🥣 🥗 🍿 🧈 🧂 🥫 🍱 🍘 🍙 🍚 🍛 🍜 🍝 🍠 🍢 🍣 🍤 🍥 🥮 🍡 🥟 🥠 🥡 🦪 🍦 🍧 🍨 🍩 🍪 🎂 🍰 🧁 🥧 🍫 🍬 🍭 🍮 🍯 🍼 🥛 ☕ 🫖 🍵 🍶 🍾 🍷 🍸 🍹 🍺 🍻 🥂 🥃 🥤 🧋 🧃 🧉 🧊 🥢 🍽 🍴 🥄"
        ),
        // Sport va o'yin-kulgi
        Category(
            IconView.Type.BALL,
            "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🪀 🏓 🏸 🏒 🏑 🥍 🏏 🥅 ⛳ 🪁 🏹 🎣 🤿 🥊 🥋 🎽 🛹 🛼 🛷 ⛸ 🥌 🎿 ⛷ 🏂 🪂 🏋 🤺 ⛹ 🏌 🏆 🥇 🥈 🥉 🏅 🎖 🏵 🎗 🎫 🎟 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🪘 🎷 🎺 🎸 🪕 🎻 🎲 ♟ 🎯 🎳 🎮 🎰 🧩 🎁 🎈 🎉 🎊 🎀 🎃 🎆 🎇 🧨 🎐 🎏 🎎 🎑"
        ),
        // Sayohat va joylar
        Category(
            IconView.Type.CAR,
            "🚗 🚕 🚙 🚌 🚎 🏎 🚓 🚑 🚒 🚐 🛻 🚚 🚛 🚜 🦯 🦽 🦼 🛴 🚲 🛵 🏍 🛺 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈ 🛫 🛬 🛩 💺 🛰 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥 🛳 ⛴ 🚢 ⚓ ⛽ 🚧 🚦 🚥 🚏 🗺 🗿 🗽 🗼 🏰 🏯 🏟 🎡 🎢 🎠 ⛲ ⛱ 🏖 🏝 🏜 🌋 ⛰ 🏔 🗻 🏕 ⛺ 🏠 🏡 🏘 🏚 🏗 🏭 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫 🏩 💒 🏛 ⛪ 🕌 🛕 🕍 🕋 ⛩ 🛤 🛣 🗾 🏞 🌅 🌄 🌠 🌇 🌆 🏙 🌃 🌌 🌉 🌁"
        ),
        // Buyumlar
        Category(
            IconView.Type.BULB,
            "⌚ 📱 📲 💻 ⌨ 🖥 🖨 🖱 🕹 💽 💾 💿 📀 📼 📷 📸 📹 🎥 📽 🎞 📞 ☎ 📟 📠 📺 📻 🎙 🎚 🎛 🧭 ⏱ ⏲ ⏰ 🕰 ⌛ ⏳ 📡 🔋 🔌 💡 🔦 🕯 🪔 🧯 💸 💵 💴 💶 💷 💰 💳 💎 ⚖ 🧰 🔧 🔨 ⚒ 🛠 ⛏ 🔩 ⚙ 🧱 ⛓ 🧲 🔫 💣 🪓 🔪 🗡 ⚔ 🛡 🚬 ⚰ 🏺 🔮 📿 🧿 💈 ⚗ 🔭 🔬 🩹 🩺 💊 💉 🩸 🧬 🦠 🧫 🧪 🌡 🧹 🧺 🧻 🚽 🚰 🚿 🛁 🧼 🪒 🧽 🧴 🛎 🔑 🗝 🚪 🪑 🛋 🛏 🧸 🖼 🛍 🛒 ✉ 📩 📨 📧 📥 📤 📦 🏷 📪 📫 📬 📭 📮 📜 📃 📄 📑 🧾 📊 📈 📉 🗒 🗓 📆 📅 🗑 📇 🗃 🗳 🗄 📋 📁 📂 🗂 🗞 📰 📓 📔 📒 📕 📗 📘 📙 📚 📖 🔖 🧷 🔗 📎 🖇 📐 📏 🧮 📌 📍 ✂ 🖊 🖋 ✒ 🖌 🖍 📝 ✏ 🔍 🔎 🔏 🔐 🔒 🔓"
        ),
        // Belgilar
        Category(
            IconView.Type.HEART,
            "❤ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 ❣ ☮ ✝ ☪ 🕉 ☸ ✡ 🔯 🕎 ☯ ⛎ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛ ☢ ☣ 📴 📳 ✴ 🆚 💮 🉐 ㊙ ㊗ 🅰 🅱 🆎 🆑 🅾 🆘 ❌ ⭕ 🛑 ⛔ 📛 🚫 ♨ 🚷 🚯 🚳 🚱 🔞 📵 🚭 ❗ ❕ ❓ ❔ ‼ ⁉ 🔅 🔆 〽 ⚠ 🚸 🔱 ⚜ 🔰 ♻ ✅ 💹 ❇ ✳ ❎ 🌐 💠 Ⓜ 🌀 🏧 🚾 ♿ 🅿 🛗 🛂 🛃 🛄 🛅 🚹 🚺 🚼 🚻 🚮 🎦 📶 ℹ 🔤 🔡 🔠 🆖 🆗 🆙 🆒 🆕 🆓 ▶ ⏸ ⏯ ⏹ ⏺ ⏭ ⏮ ⏩ ⏪ ⏫ ⏬ ◀ 🔼 🔽 ➡ ⬅ ⬆ ⬇ ↗ ↘ ↙ ↖ ↕ ↔ ↩ ↪ ⤴ ⤵ 🔀 🔁 🔂 🔄 🔃 🎵 🎶 ➕ ➖ ➗ ✖ ♾ 💲 💱 ™ © ® 〰 ➰ ➿ 🔚 🔙 🔛 🔝 🔜 ✔ ☑ 🔘 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟤 🔺 🔻 🔸 🔹 🔶 🔷 🔳 🔲 ▪ ▫ ◾ ◽ ◼ ◻ 🟥 🟧 🟨 🟩 🟦 🟪 ⬛ ⬜ 🟫 🔈 🔇 🔉 🔊 🔔 🔕 📣 📢 🗯 ♠ ♣ ♥ ♦ 🃏 🎴 🀄 🕐 🕑 🕒 🕓 🕔 🕕 🕖 🕗 🕘 🕙 🕚 🕛"
        ),
        // Bayroqlar
        Category(
            IconView.Type.FLAG,
            "🏁 🚩 🎌 🏴 🏳 " + flags(
                "UZ KZ KG TJ TM RU UA BY AZ AM GE TR IR AF PK IN CN JP KR MN SA AE EG QA KW JO IQ IL SY LB " +
                    "US CA MX BR AR GB IE FR DE IT ES PT NL BE CH AT SE NO DK FI PL CZ GR BG RO HU RS HR LT LV EE " +
                    "AU NZ ZA NG MA DZ TN ID MY SG TH VN PH BD KP CU CL CO PE VE"
            )
        )
    )
}
