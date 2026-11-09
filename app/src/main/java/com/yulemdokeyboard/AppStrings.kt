package com.yulemdokeyboard

import android.content.Context

/** Ilova matnlari 4 tilda. O'zbekcha matn yangi alifbo bo'yicha: oʻ→ö, gʻ→ğ, sh→ş, ch→ç, tutuq→ʼ. */
data class Pack(
    val welcomeTitle: String,
    val welcomeBody: String,
    val step1Title: String,
    val step1Body: String,
    val step2Title: String,
    val step2Body: String,
    val tipsTitle: String,
    val tipsBody: String,
    val next: String,
    val back: String,
    val start: String,
    val enableBtn: String,
    val switchBtn: String,
    val editorBtn: String
)

object AppStrings {
    private const val PREFS = "yulemdo_app"
    private const val KEY_LANG = "app_lang"
    private const val KEY_INTRO = "intro_done"

    const val UZ = "uz"
    const val RU = "ru"
    const val EN = "en"
    const val KAA = "kaa"

    fun getLang(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, null)

    fun setLang(ctx: Context, lang: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANG, lang).apply()
    }

    fun isIntroDone(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_INTRO, false)

    fun setIntroDone(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_INTRO, true).apply()
    }

    /**
     * Yangi: foydalanuvchi "Tilni qayta tanlash" tugmasini bosganda chaqiriladi.
     * intro_done belgisini o'chiradi, shunda IntroActivity yana til tanlash
     * sahifasidan (0-sahifa) boshlanadi; MainActivity bu holatda Intro'ni qayta ochadi.
     */
    fun resetIntro(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_INTRO, false).apply()
    }

    fun pack(lang: String?): Pack = when (lang) {
        RU -> ru
        EN -> en
        KAA -> kaa
        else -> uz
    }

    // ===== O'zbekcha (yangi alifbo, V3) =====
    private val uz = Pack(
        welcomeTitle = "Xuş kelibsiz!",
        welcomeBody = "Yulemdo Klaviatura — özbek tili uçun yaratilgan çiroyli va qulay klaviatura.\n\n" +
            "Yangi alifbo harflari — ö, ğ, ş, ç va tutuq belgisi ʼ — bir bosişda tayyor.",
        step1Title = "1-qadam: klaviaturani yoqing",
        step1Body = "«Klaviaturani yoqiş» tugmasini bosing va röyxatdan Yulemdo Klaviatura ni yoqib qöying.",
        step2Title = "2-qadam: klaviaturani tanlang",
        step2Body = "Yozmoqçi bölgan joyni oçing, «Klaviaturani almaştiriş» tugmasini bosing va Yulemdo Klaviatura ni tanlang.",
        tipsTitle = "Foydali maslahatlar",
        tipsBody = "• Shift ni tez ikki marta bossangiz — CAPS LOCK yoqiladi.\n" +
            "• Globus tugmasi tilni almaştiradi: özbek, qoraqalpoq, ingliz, rus.\n" +
            "• Öçiriş tugmasini bosib tursangiz, matn ketma-ket öçadi.\n" +
            "• Harfni uzoq bossangiz, ustidagi raqam yoziladi.\n" +
            "• «Klaviatura muharriri» orqali tugmalarni özingiz joylaştirasiz.",
        next = "Keyingisi",
        back = "Orqaga",
        start = "Boşlaş",
        enableBtn = "Klaviaturani yoqiş",
        switchBtn = "Klaviaturani almaştiriş",
        editorBtn = "Klaviatura muharriri"
    )

    // ===== Русский =====
    private val ru = Pack(
        welcomeTitle = "Добро пожаловать!",
        welcomeBody = "Yulemdo Клавиатура — удобная и красивая клавиатура для узбекского языка.\n\n" +
            "Буквы нового алфавита — ö, ğ, ş, ç и апостроф ʼ — одним касанием.",
        step1Title = "Шаг 1: включите клавиатуру",
        step1Body = "Нажмите «Включить клавиатуру» и в списке включите Yulemdo Клавиатура.",
        step2Title = "Шаг 2: выберите клавиатуру",
        step2Body = "Откройте любое поле ввода, нажмите «Сменить клавиатуру» и выберите Yulemdo Клавиатура.",
        tipsTitle = "Полезные советы",
        tipsBody = "• Два быстрых нажатия на Shift — включается CAPS LOCK.\n" +
            "• Кнопка глобуса переключает язык: узбекский, каракалпакский, английский, русский.\n" +
            "• Удерживайте кнопку удаления — текст стирается непрерывно.\n" +
            "• Долгое нажатие на букву верхнего ряда вводит цифру.\n" +
            "• В «Редакторе клавиатуры» клавиши можно расставить по-своему.",
        next = "Далее",
        back = "Назад",
        start = "Начать",
        enableBtn = "Включить клавиатуру",
        switchBtn = "Сменить клавиатуру",
        editorBtn = "Редактор клавиатуры"
    )

    // ===== English =====
    private val en = Pack(
        welcomeTitle = "Welcome!",
        welcomeBody = "Yulemdo Keyboard is a beautiful, easy-to-use keyboard made for the Uzbek language.\n\n" +
            "Letters of the new alphabet — ö, ğ, ş, ç and the apostrophe ʼ — are one tap away.",
        step1Title = "Step 1: enable the keyboard",
        step1Body = "Tap “Enable keyboard” and turn on Yulemdo Keyboard in the list.",
        step2Title = "Step 2: choose the keyboard",
        step2Body = "Open any text field, tap “Switch keyboard” and select Yulemdo Keyboard.",
        tipsTitle = "Handy tips",
        tipsBody = "• Double-tap Shift to turn on CAPS LOCK.\n" +
            "• The globe key switches language: Uzbek, Karakalpak, English, Russian.\n" +
            "• Hold the delete key to erase continuously.\n" +
            "• Long-press a letter in the top row to type its number.\n" +
            "• Use the “Keyboard editor” to arrange the keys your own way.",
        next = "Next",
        back = "Back",
        start = "Get started",
        enableBtn = "Enable keyboard",
        switchBtn = "Switch keyboard",
        editorBtn = "Keyboard editor"
    )

    // ===== Qaraqalpaqsha (lotin) =====
    private val kaa = Pack(
        welcomeTitle = "Xosh keldińiz!",
        welcomeBody = "Yulemdo Klaviatura — qaraqalpaq, ózbek, rus hám ingliz tilleri ushín jaratílǵan qolaylí klaviatura.\n\n" +
            "Qaraqalpaq álipbesiniń á, ǵ, í, ń, ó, ú háripleri bir basíwda tayar.",
        step1Title = "1-qádem: klaviaturanı qosíń",
        step1Body = "«Klaviaturanı qosíw» túymesin basíń hám dizimnen Yulemdo Klaviatura-nı qosíń.",
        step2Title = "2-qádem: klaviaturanı saylań",
        step2Body = "Jazíw maydanın ashíń, «Klaviaturanı almastíríw» túymesin basíń hám Yulemdo Klaviatura-nı saylań.",
        tipsTitle = "Paydalí keńesler",
        tipsBody = "• Shift túymesin tez eki ret bassańíz — CAPS LOCK qosíladí.\n" +
            "• Globus túymesi tildi almastíradí: ózbek, qaraqalpaq, ingliz, rus.\n" +
            "• Óshiriw túymesin basíp tursańíz, tekst ketpe-ket óshedi.\n" +
            "• Háripti uzaq bassańíz, ústindegi san jazíladí.\n" +
            "• «Klaviatura redaktorí» arqalí túymelerdi ózińiz jaylastíra alasíz.",
        next = "Keyingi",
        back = "Artqa",
        start = "Baslaw",
        enableBtn = "Klaviaturanı qosíw",
        switchBtn = "Klaviaturanı almastíríw",
        editorBtn = "Klaviatura redaktorí"
    )
}
