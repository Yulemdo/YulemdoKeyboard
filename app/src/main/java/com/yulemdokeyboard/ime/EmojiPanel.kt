package com.yulemdokeyboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.yulemdokeyboard.model.KeyData
import com.yulemdokeyboard.model.KeyboardLayout
import com.yulemdokeyboard.model.SpecialCodes

/**
 * Qurilmaning o'z emojilarini ko'rsatadigan panel.
 * Faqat shu qurilma shrifti chiza oladigan emojilar ko'rsatiladi (Paint.hasGlyph).
 * Pastda: ABC, kategoriya tablari va backspace (bosib turilsa ketma-ket o'chiradi).
 */
class EmojiPanel(
    context: Context,
    private val onEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onBack: () -> Unit
) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private val scroll = ScrollView(context)
    private val bar = KeyboardView(context)
    private val grids = HashMap<Int, GridLayout>()

    private val categories: List<Pair<String, List<String>>> by lazy {
        EmojiData.categories.map { (tab, raw) -> tab to supported(raw.split(" ").filter { it.isNotBlank() }) }
    }

    init {
        orientation = VERTICAL
        try {
            background = androidx.core.content.ContextCompat.getDrawable(
                context, com.yulemdokeyboard.R.drawable.bg_app_gradient
            )
        } catch (e: Exception) {
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = OVER_SCROLL_NEVER
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        bar.background = null
        bar.layoutModel = buildBar()
        bar.onKeyPressed = { key ->
            when {
                key.code == SpecialCodes.BACKSPACE -> onBackspace()
                key.code == SpecialCodes.LETTERS -> onBack()
                key.code.startsWith(CAT) -> select(key.code.removePrefix(CAT).toInt())
            }
        }
        addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        select(0)
    }

    private fun buildBar(): KeyboardLayout {
        val keys = mutableListOf<KeyData>()
        keys.add(KeyData("ABC", SpecialCodes.LETTERS, row = 0, col = 0, width = 1.4f, isSpecial = true))
        EmojiData.categories.forEachIndexed { i, (tab, _) ->
            keys.add(KeyData(tab, "$CAT$i", row = 0, col = i + 1, isSpecial = true))
        }
        keys.add(KeyData("", SpecialCodes.BACKSPACE, row = 0, col = EmojiData.categories.size + 1, width = 1.4f, isSpecial = true))
        return KeyboardLayout("emoji", rows = 1, cols = keys.size, keys = keys)
    }

    private fun select(index: Int) {
        val grid = grids.getOrPut(index) { buildGrid(index) }
        scroll.removeAllViews()
        scroll.addView(grid)
        scroll.scrollTo(0, 0)
        bar.highlightCode = "$CAT$index"
        bar.invalidate()
    }

    private fun buildGrid(index: Int): GridLayout {
        val columns = 8
        val cellW = resources.displayMetrics.widthPixels / columns
        val cellH = (46 * dp).toInt()
        val grid = GridLayout(context)
        grid.columnCount = columns
        categories[index].second.forEach { e ->
            val tv = TextView(context)
            tv.text = e
            tv.textSize = 26f
            tv.gravity = Gravity.CENTER
            tv.setOnClickListener { onEmoji(e) }
            val lp = GridLayout.LayoutParams()
            lp.width = cellW
            lp.height = cellH
            grid.addView(tv, lp)
        }
        return grid
    }

    private fun supported(list: List<String>): List<String> {
        if (Build.VERSION.SDK_INT < 23) return list
        val p = Paint()
        return list.filter { p.hasGlyph(it) }
    }

    private companion object {
        const val CAT = "\u0001CAT"
    }
}

object EmojiData {
    /** (tab belgisi, emojilar bo'sh joy bilan ajratilgan) */
    val categories: List<Pair<String, String>> = listOf(
        "😀" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 👹 👺 🤡 💩 👻 💀 👽 👾 🤖",
        "👋" to "👋 🤚 ✋ 🖖 👌 🤌 🤏 ✌ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍ 💅 🤳 💪 🦾 🦵 🦶 👂 👃 🧠 👀 👅 👄 💋 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🙇 🤦 🤷",
        "🐶" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦏 🐪 🐫 🦒 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🐇 🐁 🐀 🌵 🎄 🌲 🌳 🌴 🌱 🌿 🍀 🍁 🍂 🍃 🌺 🌻 🌹 🌷 🌼 🌸 💐 🍄 🌍 🌙 ⭐ 🌟 ✨ ⚡ 🔥 🌈 ☀ ☁ ⛄ ❄ 💧 🌊",
        "🍎" to "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🍞 🥖 🧀 🥚 🍳 🥞 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🥛 ☕ 🍵 🧃 🥤 🍺 🍻 🥂 🍷 🍸 🍹 🍾",
        "⚽" to "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥅 🏒 🏏 ⛳ 🏹 🎣 🥊 🥋 ⛸ 🎿 🏂 🏆 🥇 🥈 🥉 🏅 🎫 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 🎯 🎳 🎮 🎰 🧩",
        "🚗" to "🚗 🚕 🚙 🚌 🚎 🚓 🚑 🚒 🚚 🚜 🚲 🛴 🚂 🚆 🚇 🚊 ✈ 🛫 🛬 🚀 🛸 🚁 ⛵ 🚤 ⚓ 🗼 🏰 🏯 🏠 🏢 🏥 🏦 🏨 🏫 🕌 ⛪ 🗽 🗺 ⛰ 📱 💻 ⌨ 🖥 📷 📹 📺 📻 ⏰ ⌚ 💡 🔦 🔋 🔌 💰 💳 💎 🔧 🔨 🔑 🔒 🔓 📦 ✉ 📧 📝 📚 📖 📌 📎 ✂ 🔍",
        "❤" to "❤ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 💕 💞 💓 💗 💖 💘 💝 💯 💢 💥 💫 💦 💨 💤 ✅ ❌ ❎ ❗ ❓ ❕ ❔ ⭕ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ ⬆ ➡ ⬇ ⬅ 🔄 ➕ ➖ ➗ ♻ ⚠ 🚫 ✔ 🔔 🔕 🎵 🎶"
    )
}
