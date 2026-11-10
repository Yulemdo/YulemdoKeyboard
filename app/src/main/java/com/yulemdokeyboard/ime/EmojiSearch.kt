package com.yulemdokeyboard.ime

import java.text.Normalizer

/**
 * Emoji qidiruvi: kalit so'zlar 4 tilda (ingliz, rus, özbek, qoraqalpoq) birga saqlanadi.
 * Foydalanuvchi qaysi tilda yozmasin (klaviatura tilidan qat'i nazar) natija chiqaveradi.
 * Qidiruv harf belgilariga befarq: ö=o, ş=s, ç=c, ğ=g, ё=е, й=и, ʼ e'tiborga olinmaydi,
 * "sh/ch/gh" ham "s/c/g" kabi qabul qilinadi (ş/ç/ğ o'rniga yozilsa ham topadi).
 */
object EmojiSearch {

    fun norm(s: String): String {
        val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder()
        for (c in d) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            if (c == '\'' || c == '\u02BC' || c == '\u02BB' || c == '`' || c == '\u2019') continue
            sb.append(if (c == '\u0131') 'i' else c)
        }
        return sb.toString().replace("sh", "s").replace("ch", "c").replace("gh", "g")
    }

    private val ws = Regex("\\s+")

    private class Entry(val emoji: String, val tokens: List<String>)

    private val entries: List<Entry> by lazy {
        DATA.lines().filter { it.isNotBlank() }.map { line ->
            val parts = line.split("|")
            Entry(parts[0].trim(), norm(parts.drop(1).joinToString(" ")).split(ws).filter { it.isNotBlank() })
        }
    }

    /** EmojiData.categories tartibi bilan bir xil: yuzlar, odamlar, hayvonlar, ovqat, sport, sayohat, buyumlar, belgilar, bayroqlar. */
    private val catTokens: List<List<String>> by lazy {
        CATEGORY_DATA.lines().filter { it.isNotBlank() }.map { norm(it.replace("|", " ")).split(ws).filter { t -> t.isNotBlank() } }
    }

    /** -1 - mos kelmadi; aks holda moslik darajasi (yuqori - yaxshiroq). Barcha so'zlar mos kelishi shart. */
    private fun score(qs: List<String>, toks: List<String>): Int {
        var total = 0
        for (q in qs) {
            var best = -1
            for (t in toks) {
                val m = when {
                    t == q -> 3
                    t.startsWith(q) -> 2
                    q.length >= 3 && t.contains(q) -> 1
                    else -> -1
                }
                if (m > best) best = m
            }
            if (best < 0) return -1
            total += best
        }
        return total
    }

    /** categories - EmojiData.categories[i].emojis (bo'sh joy bilan ajratilgan). */
    fun search(query: String, categories: List<String>): List<String> {
        val qs = norm(query).split(ws).filter { it.isNotBlank() }
        if (qs.isEmpty()) return emptyList()
        val result = LinkedHashSet<String>()
        entries.map { it to score(qs, it.tokens) }
            .filter { it.second >= 0 }
            .sortedByDescending { it.second }
            .forEach { result.add(it.first.emoji) }
        catTokens.forEachIndexed { i, toks ->
            if (i < categories.size && score(qs, toks) >= 0) {
                categories[i].split(" ").filter { it.isNotBlank() }.forEach { result.add(it) }
            }
        }
        return result.toList()
    }

    // emoji|English|Русский|Özbekça|Qaraqalpaqsha
    private const val CATEGORY_DATA = """
face emotion smile mood|лицо эмоция смайлик настроение|yüz hissiyot kayfiyat|júz sezim
hand gesture person people body|рука жест человек люди тело|qol imo odam tana|qol adam dene
animal nature plant flower weather|животное природа растение цветок погода|hayvon tabiat ösimlik gül ob havo|haywan tábiyat ósimlik gúl
food drink fruit vegetable|еда напиток фрукт овощ|ovqat içimlik meva sabzavot|awqat ishimlik miywe
sport game activity ball music art|спорт игра активность мяч музыка искусство|sport öyin musiqa|sport oyın
travel transport car place building|путешествие транспорт машина место здание|sayohat transport joy bino|sayaxat transport
object tool device office book|предмет инструмент устройство офис книга|buyum asbob qurilma ofis kitob|zat qural
symbol sign heart arrow|символ знак сердце стрелка|belgi yurak strelka|belgi júrek
flag country|флаг страна|bayroq davlat mamlakat|bayraq mámleket
"""

    private const val DATA = """
😀|smile grin happy joy|улыбка радость смайлик весело|kulgu tabassum xursand quvonç|kúlki quwanısh xosh
😂|laugh lol tears funny joy|смех ржу слезы смешно|kulgi kulmoq kulgili|kúlki kúliw
🤣|rofl laugh funny|ржака смех смешно|kulgi qahqaha|kúlki
😊|blush happy smile|смущение радость улыбка|xursand tabassum|quwanısh
😍|love heart eyes|любовь влюблен глаза сердечки|sevgi sevmoq oşiq|muhabbat súyiw
🥰|love adore hearts|любовь обожаю|sevgi mehr|muhabbat
😘|kiss love|поцелуй воздушный|öpiş sevgi|óbiw muhabbat
😉|wink|подмигивание|köz qisiş|kóz qısıw
😎|cool sunglasses|круто очки|zör közoynak|kózayna
😢|cry sad tear|плач грусть слеза|yiğlamoq qayğu|jılaw qayǵı
😭|sob cry loud|рыдать плач|yiğlamoq|jılaw
😡|angry mad|злой гнев|ğazab jahl|ǵázep
😱|scream shock fear|ужас страх шок|qörquv|qorqınısh
😴|sleep tired|сон спать усталость|uyqu|uyqı
🤔|think hmm|думать|oylamoq|oylaw
🤗|hug|объятия|quçoq|qushaq
🙄|roll eyes|закатить глаза|köz|kóz
😷|sick mask|болезнь маска|kasal niqob|awırıw
🤒|sick fever|температура болен|isitma kasal|qızıtpa
🥳|party celebrate|праздник вечеринка|bayram|bayram
🤯|mind blown|взрыв мозга|hayratda|tańlanıw
🥺|please puppy eyes|пожалуйста умоляю|iltimos|ótinish
😇|angel innocent|ангел|farişta|perishte
😜|wink tongue|язык шутка|til|til
🤪|crazy wild|сумасшедший|jinni|aqmaq
👍|thumbs up like ok good|класс лайк хорошо одобрить|yaxşi layk maqul|jaqsı layk
👎|thumbs down dislike bad|дизлайк плохо|yomon|jaman
👏|clap applause bravo|аплодисменты браво|qarsak|qarsaq
🙏|pray please thanks|молитва спасибо пожалуйста|duo rahmat iltimos|dwa raxmet
🤝|handshake deal|рукопожатие сделка|qol siqişiş kelişuv|qol alısıw
✌|victory peace|победа мир|zafar tinçlik|jeńis tınıshlıq
👌|ok perfect|окей отлично|ayla zör|jaqsı
💪|muscle strong|сила мышцы|küç kuçli|kúsh
👋|wave hello bye|привет пока махать|salom xayr|sálem
✋|stop hand|стоп рука|qol töxta|qol
🤞|fingers crossed luck|удача скрестил|omad|ámet
🤫|shh quiet secret|тише секрет|jim sir|sır
👀|eyes look see|глаза смотреть|köz qarash|kóz
🧠|brain mind|мозг ум|miya aql|miy
❤|heart love red|сердце любовь красный|yurak sevgi qizil|júrek muhabbat qızıl
💔|broken heart|разбитое сердце|yurak siniş|júrek
💯|hundred perfect score|сто баллов|yüz|júz
🔥|fire hot flame|огонь горячо|ot olov|ot
✨|sparkles magic shine|блеск магия|yaltirash|jarqıw
⭐|star|звезда|yulduz|juldız
🌟|star glow|звезда сияние|yulduz|juldız
🎉|party tada celebrate congrats|праздник поздравляю хлопушка|tabrik bayram|qutlıqlaw
🎂|birthday cake|торт день рождения|tuğilgan kun tort|tuwılǵan kún tort
🎁|gift present|подарок|sovğa|sıyı
🐶|dog puppy|собака щенок|it kuçuk|it
🐱|cat kitten|кошка кот|mushuk|mısıq
🐭|mouse|мышь|siçqon|sıshqan
🐰|rabbit bunny|кролик заяц|quyon|qoyan
🐻|bear|медведь|ayiq|ayıw
🦁|lion|лев|sher|arıstan
🐯|tiger|тигр|yölbars|jolbarıs
🐴|horse|лошадь конь|ot|at
🐮|cow|корова|sigir|sıyır
🐷|pig|свинья|çöçqa|shoshqa
🐑|sheep|овца баран|qoy|qoy
🐔|chicken hen|курица|tovuq|tawıq
🐦|bird|птица|quş|qus
🦅|eagle|орёл|bürkut|bürkit
🐍|snake|змея|ilon|jılan
🐢|turtle|черепаха|toşbaqa|tasbaqa
🐟|fish|рыба|baliq|balıq
🐘|elephant|слон|fil|pil
🐒|monkey|обезьяна|maymun|maymıl
🦋|butterfly|бабочка|kapalak|kebelek
🐝|bee honey|пчела мёд|asalari|ara
🌹|rose flower|роза цветок|gül|gúl
🌸|flower blossom cherry|цветок вишня|gül|gúl
🌻|sunflower|подсолнух|kungaboqar|kúnbaǵar
🌳|tree|дерево|daraxt|aǵash
🌍|earth world globe|земля мир|yer dunyo|jer dúnya
☀|sun sunny|солнце|quyoş|kún
🌙|moon night|луна ночь|oy tun|ay tún
⛄|snowman snow|снеговик снег|qor|qar
❄|snow cold winter|снег холод зима|qor sovuq qiş|qar suwıq qıs
🌈|rainbow|радуга|kamalak|kemirshek
☔|rain umbrella|дождь зонт|yomğir|jawın
⚡|lightning thunder|молния гром|çaqmaq|shaqmaq
🍎|apple|яблоко|olma|alma
🍌|banana|банан|banan|banan
🍉|watermelon|арбуз|tarvuz|garbız
🍇|grapes|виноград|uzum|júzim
🍊|orange|апельсин|apelsin|apelsin
🍓|strawberry|клубника|qulupnay|qulpınay
🍅|tomato|помидор|pomidor|pomidor
🥕|carrot|морковь|sabzi|sábiz
🍞|bread|хлеб|non|nan
🧀|cheese|сыр|pişloq|irimshik
🍕|pizza|пицца|pitsa|pitstsa
🍔|burger hamburger|бургер гамбургер|burger|burger
🍟|fries|картошка фри|kartoşka|kartoshka
🍰|cake dessert|торт десерт|tort|tort
🍫|chocolate|шоколад|şokolad|shokolad
🍦|ice cream|мороженое|muzqaymoq|muzqaymaq
☕|coffee tea hot|кофе чай|qahva çoy|qáhwe shay
🍵|tea green|чай|çoy|shay
🍺|beer|пиво|pivo|pivo
🍷|wine|вино|vino|vino
🥛|milk|молоко|süt|sút
🍚|rice plov|рис плов|guruç palov|gúrish palaw
🍲|soup|суп|şorva|sorpa
🍳|egg fry|яйцо яичница|tuxum|máyek
🥩|meat|мясо|göşt|et
⚽|soccer football|футбол мяч|futbol koptok|futbol
🏀|basketball|баскетбол|basketbol|basketbol
🏆|trophy win cup|кубок победа|kubok|kubok
🥇|gold medal first|золото медаль|oltin medal|altın
🎮|game controller|игра геймпад|öyin|oyın
🎵|music note|музыка нота|musiqa|muzıka
🎶|music notes|музыка|musiqa|muzıka
🎧|headphones music|наушники|quloqçin|qulaqshın
🎤|microphone sing|микрофон петь|mikrofon qöşiq|mikrofon
🎸|guitar|гитара|gitara|gitara
🎯|target dart|мишень|nişon|nıshan
🎬|movie film|кино фильм|kino|kino
🚗|car auto|машина авто|maşina avtomobil|mashina
🚕|taxi|такси|taksi|taksi
🚌|bus|автобус|avtobus|avtobus
🚲|bicycle bike|велосипед|velosiped|velosiped
✈|plane airplane flight|самолёт полёт|samolyot|samolyot
🚀|rocket space|ракета космос|raketa|raketa
🚂|train|поезд|poyezd|poyezd
⛵|boat sail|лодка парус|qayiq|qayıq
🏠|home house|дом|uy|úy
🏢|office building|офис здание|bino|imarat
🏥|hospital|больница|kasalxona|keselxana
🕌|mosque|мечеть|masjid|meshit
🏰|castle|замок|qasr|qala
⛰|mountain|гора|tağ|taw
🌋|volcano|вулкан|vulqon|vulkan
📱|phone mobile|телефон мобильный|telefon|telefon
💻|laptop computer|ноутбук компьютер|noutbuk kompyuter|noutbuk
📷|camera photo|камера фото|kamera surat|kamera
📺|tv television|телевизор|televizor|televizor
⏰|alarm clock time|будильник время|soat budilnik|saat
⌚|watch time|часы время|soat|saat
💡|idea bulb light|идея лампочка свет|fikr lampoçka|oy
🔋|battery|батарея|batareya|batareya
🔑|key|ключ|kalit|kilt
🔒|lock|замок|qulf|qulıp
💰|money bag|деньги|pul|aqsha
💵|dollar cash money|доллар деньги|dollar pul|dollar
💳|card credit|карта|karta|karta
💎|diamond gem|алмаз|olmos|almas
📚|books|книги|kitob|kitap
📖|book read|книга читать|kitob|kitap
✏|pencil write|карандаш|qalam|qálem
📝|note write memo|заметка|yozuv|jazıw
✂|scissors cut|ножницы|qayçi|qayshı
🔍|search magnifier find|поиск лупа|qidiruv|izlew
📦|box package parcel|коробка посылка|quti posilka|quti
💊|pill medicine|таблетка лекарство|dori|dári
🧸|teddy bear toy|игрушка мишка|öyinçoq|oyınshıq
✅|check done yes|галочка готово да|tayyor ha|tayar
❌|cross no wrong|крестик нет|yöq xato|joq
❓|question|вопрос|savol|sorawı
❗|exclamation warning|восклицание внимание|diqqat|dıqqat
⚠|warning|предупреждение|ogohlantiriş|eskertiw
🔔|bell notification|колокольчик уведомление|qöngiroq|qońıraw
🇺🇿|uzbekistan flag|узбекистан флаг|özbekiston bayroq|ózbekstan bayraq
🇰🇿|kazakhstan flag|казахстан флаг|qozoğiston bayroq|qazaqstan bayraq
🇷🇺|russia flag|россия флаг|rossiya bayroq|rossiya bayraq
🇺🇸|usa america flag|сша америка флаг|amerika aqş bayroq|amerika bayraq
🇬🇧|uk britain england flag|британия англия флаг|angliya bayroq|angliya bayraq
🇹🇷|turkey flag|турция флаг|turkiya bayroq|turkiya bayraq
🇰🇬|kyrgyzstan flag|киргизия флаг|qirğiziston bayroq|qırǵızstan bayraq
🇹🇯|tajikistan flag|таджикистан флаг|toçikiston bayroq|tájikstan bayraq
🇹🇲|turkmenistan flag|туркменистан флаг|turkmaniston bayroq|túrkmenstan bayraq
"""
}
