package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// The daily limudim, after hebcal-learning (its schedules are in resources/limud, see NOTICE.txt there): each cycle
// counted from its first day, or set by the Hebrew day of the year. Their places are spelled as the library's lines'
// references ("משנה ברכות א, ב"), so a click lands on the very mishnah, seif or paragraph.

/** The menu's groups of limudim. */
internal enum class LimudGroup(
    val title: String,
) {
    MIKRA("מקרא"),
    SHAS("משנה ותלמוד"),
    HALACHA("הלכה"),
    SHMIRAS_HALASHON("שמירת הלשון"),
}

/** A daily limud the Limud widget can show; [id] is what its options save, [kicker] its tag on the card. */
internal enum class Limud(
    val id: String,
    val title: String,
    val kicker: String,
    val group: LimudGroup,
) {
    PARSHA("parsha", "פרשת השבוע", "פרשה", LimudGroup.MIKRA),
    BAVLI("bavli", "דף יומי בבלי", "בבלי", LimudGroup.SHAS),
    YERUSHALMI("yerushalmi", "דף יומי ירושלמי", "ירושלמי", LimudGroup.SHAS),
    MISHNAH("mishnah", "משנה יומית", "משנה", LimudGroup.SHAS),
    PEREK_MISHNAH("perek_mishnah", "פרק משנה ליום", "פרק משנה", LimudGroup.SHAS),
    PIRKEI_AVOS("pirkei_avos", "פרקי אבות (בשבתות הקיץ)", "אבות", LimudGroup.SHAS),
    RAMBAM3("rambam3", "רמב״ם – ג׳ פרקים", "רמב״ם", LimudGroup.HALACHA),
    RAMBAM1("rambam1", "רמב״ם – פרק אחד", "רמב״ם", LimudGroup.HALACHA),
    SEFER_HAMITZVOS("sefer_hamitzvos", "ספר המצוות", "ספה״מ", LimudGroup.HALACHA),
    KITZUR("kitzur", "קיצור שולחן ערוך", "קצש״ע", LimudGroup.HALACHA),
    ARUCH_HASHULCHAN("aruch_hashulchan", "ערוך השולחן", "ערוה״ש", LimudGroup.HALACHA),
    TEHILLIM("tehillim", "תהילים לחודש", "תהילים", LimudGroup.MIKRA),
    TEHILLIM_WEEK("tehillim_week", "תהילים לשבוע", "תהילים", LimudGroup.MIKRA),
    NACH("nach", "נ״ך יומי", "נ״ך", LimudGroup.MIKRA),
    CHOFETZ_CHAIM("chofetz_chaim", "חפץ חיים", "ח״ח", LimudGroup.SHMIRAS_HALASHON),
    SHMIRAS_HALASHON("shmiras_halashon", "שמירת הלשון", "שמה״ל", LimudGroup.SHMIRAS_HALASHON),
    ;

    companion object {
        /** Shown until the user picks: the card's three of old, then the most learned ones. */
        val defaults = listOf(PARSHA, BAVLI, YERUSHALMI, MISHNAH, RAMBAM3, TEHILLIM, CHOFETZ_CHAIM)

        fun decode(options: String?): Set<Limud> =
            options?.split(',')?.mapNotNull { id -> entries.firstOrNull { it.id == id } }?.toSet() ?: defaults.toSet()

        fun encode(shown: Set<Limud>): String = entries.filter { it in shown }.joinToString(",") { it.id }
    }
}

/** [n] in Hebrew letters, as the library's references write it: "קיט", "טו", "רע". */
internal fun hebrewNumeral(n: Int): String {
    val out = StringBuilder()
    var rest = n
    while (rest >= 400) {
        out.append('ת')
        rest -= 400
    }
    if (rest >= 100) {
        out.append("קרש"[rest / 100 - 1])
        rest %= 100
    }
    when (rest) {
        15 -> return out.append("טו").toString()
        16 -> return out.append("טז").toString()
    }
    if (rest >= 10) {
        out.append("יכלמנסעפצ"[rest / 10 - 1])
        rest %= 10
    }
    if (rest > 0) out.append("אבגדהוזחט"[rest - 1])
    return out.toString()
}

private fun h(n: Int) = hebrewNumeral(n)

/** "א-ט", or "א" for one. */
private fun range(
    first: Int,
    last: Int,
) = if (first == last) h(first) else "${h(first)}-${h(last)}"

/** The day of a cycle of [length] days begun on [start]. */
private fun cycleDay(
    date: LocalDate,
    start: LocalDate,
    length: Int,
) = Math.floorMod(ChronoUnit.DAYS.between(start, date), length.toLong()).toInt()

private fun JewishDate.monthValue() = jewishMonth.value

private fun jsonResource(name: String): JsonElement =
    Json.parseToJsonElement(
        checkNotNull(LimudGroup::class.java.getResourceAsStream("/limud/$name")) { "Missing limud schedule $name" }
            .bufferedReader()
            .use { it.readText() },
    )

private fun JsonElement.text(): String? = jsonPrimitive.contentOrNull

// --- Mishnah -------------------------------------------------------------------------------------------------------

private class MishnahRef(
    val masechta: String,
    val perek: Int,
    val mishnah: Int,
)

private val mishnayos: List<MishnahRef> by lazy {
    MISHNAH.flatMap { (masechta, counts) ->
        counts.withIndex().flatMap { (i, count) -> (1..count).map { MishnahRef(masechta, i + 1, it) } }
    }
}

private val MISHNAH_YOMIS_START: LocalDate = LocalDate.of(1947, 5, 20)

/** Two mishnayos a day, from Berachos on 20 May 1947. */
internal fun mishnahYomis(date: LocalDate): LimudItem {
    val day = cycleDay(date, MISHNAH_YOMIS_START, mishnayos.size / 2)
    val first = mishnayos[day * 2]
    val second = mishnayos[day * 2 + 1]
    val value =
        when {
            first.masechta != second.masechta ->
                "${first.masechta} ${h(first.perek)}, ${h(first.mishnah)} – ${second.masechta} ${h(second.perek)}, ${h(second.mishnah)}"
            first.perek != second.perek ->
                "${first.masechta} ${h(first.perek)}, ${h(first.mishnah)} – ${h(second.perek)}, ${h(second.mishnah)}"
            else -> "${first.masechta} ${h(first.perek)}, ${range(first.mishnah, second.mishnah)}"
        }
    return LimudItem(Limud.MISHNAH.kicker, value, first.place())
}

private fun MishnahRef.place() =
    LibraryPlace(
        "משנה $masechta",
        toc = listOf("פרק ${h(perek)}"),
        ref = "משנה $masechta ${h(perek)}, ${h(mishnah)}",
    )

private val PEREK_YOMI_START: LocalDate = LocalDate.of(2002, 2, 9)

private val mishnahPerakim: List<Pair<String, Int>> by lazy {
    MISHNAH.flatMap { (masechta, counts) -> counts.indices.map { masechta to it + 1 } }
}

/** A perek of Mishnah a day, from Berachos on 9 February 2002. */
internal fun perekMishnah(date: LocalDate): LimudItem {
    val (masechta, perek) = mishnahPerakim[cycleDay(date, PEREK_YOMI_START, mishnahPerakim.size)]
    return LimudItem(
        Limud.PEREK_MISHNAH.kicker,
        "$masechta ${h(perek)}",
        LibraryPlace("משנה $masechta", toc = listOf("פרק ${h(perek)}")),
    )
}

// --- Rambam --------------------------------------------------------------------------------------------------------

// Both cycles begun on Sunday 27 Nisan 5744; the three perakim a day in 339 days, the one in 1017
private val RAMBAM_START: LocalDate = LocalDate.of(1984, 4, 29)
private const val RAMBAM1_DAYS = 1017
private const val RAMBAM3_DAYS = RAMBAM1_DAYS / 3

// The four books before the halachos are learned by their paragraphs, in three days each
private val RAMBAM_INTRO_DAYS =
    listOf(
        listOf(1 to 21, 22 to 33, 34 to 45),
        listOf(1 to 83, 84 to 166, 167 to 248),
        listOf(1 to 122, 123 to 245, 246 to 365),
    )

// תוכן החיבור, by its perakim and halachos: perek, halacha to perek, halacha
private val RAMBAM_CONTENTS_DAYS = listOf(intArrayOf(1, 1, 4, 8), intArrayOf(5, 1, 9, 9), intArrayOf(10, 1, 14, 10))

private const val SEDER_HATEFILAH = 15
private const val CHAMETZ_UMATZAH = 20

/** A day's perek of [book] (an index in [MISHNEH_TORAH]): [perek] the day of it, or its perakim ("ד-ה"). */
private class RambamDay(
    val book: Int,
    val perek: Int,
    val perakim: String? = null,
)

private fun rambamDay(
    index: Int,
    counts: List<Int>,
): RambamDay {
    var rest = index
    for ((book, count) in counts.withIndex()) {
        if (rest < count) return RambamDay(book, rest + 1)
        rest -= count
    }
    error("Past the Mishneh Torah's end")
}

private val rambam1Counts by lazy { MISHNEH_TORAH.map { it.second } }

// The three-perakim cycle gives סדר התפילה a fifth day, and the last two of חמץ ומצה one
private val rambam3Counts by lazy {
    rambam1Counts.toMutableList().apply {
        this[SEDER_HATEFILAH] = 5
        this[CHAMETZ_UMATZAH] = 8
    }
}

private fun RambamDay.title() = MISHNEH_TORAH[book].first.removePrefix("הלכות ")

/** What it reads of its book: perakim, or the paragraphs of the four first books. */
private fun RambamDay.reading(): String =
    perakim ?: when (book) {
        in RAMBAM_INTRO_DAYS.indices -> RAMBAM_INTRO_DAYS[book][perek - 1].let { (a, b) -> range(a, b) }
        RAMBAM_INTRO_DAYS.size -> RAMBAM_CONTENTS_DAYS[perek - 1].let { "${h(it[0])}, ${h(it[1])} – ${h(it[2])}, ${h(it[3])}" }
        else -> h(perek)
    }

private fun RambamDay.place(): LibraryPlace {
    val title = "משנה תורה, ${MISHNEH_TORAH[book].first}"
    return when (book) {
        in RAMBAM_INTRO_DAYS.indices -> LibraryPlace(title, ref = "$title ${h(RAMBAM_INTRO_DAYS[book][perek - 1].first)}")
        RAMBAM_INTRO_DAYS.size -> LibraryPlace(title, toc = listOf("פרק ${h(RAMBAM_CONTENTS_DAYS[perek - 1][0])}"))
        else -> LibraryPlace(title, toc = listOf("פרק ${h(perek)}"))
    }
}

/** One perek a day. */
internal fun rambam1(date: LocalDate): LimudItem {
    var day = rambamDay(cycleDay(date, RAMBAM_START, RAMBAM1_DAYS), rambam1Counts)
    if (day.book == SEDER_HATEFILAH && day.perek == 4) day = RambamDay(day.book, 4, "ד-ה")
    return LimudItem(Limud.RAMBAM1.kicker, "${day.title()} ${day.reading()}", day.place())
}

/** Three perakim a day, the ones of a book run together: "שבת ג-ה", "יסודי התורה י · דעות א-ב". */
internal fun rambam3(date: LocalDate): LimudItem {
    val start = cycleDay(date, RAMBAM_START, RAMBAM3_DAYS) * 3
    val days =
        (start until start + 3).map { index ->
            val day = rambamDay(index, rambam3Counts)
            if (day.book == CHAMETZ_UMATZAH && day.perek == 8) RambamDay(day.book, 8, "ח-ט") else day
        }
    val value =
        days
            .groupBy { it.book }
            .values
            .joinToString(" · ") { ofBook ->
                val first = ofBook.first()
                val last = ofBook.last()
                val reading =
                    when {
                        ofBook.size == 1 -> first.reading()
                        first.book == RAMBAM_INTRO_DAYS.size -> {
                            val from = RAMBAM_CONTENTS_DAYS[first.perek - 1]
                            val to = RAMBAM_CONTENTS_DAYS[last.perek - 1]
                            "${h(from[0])}, ${h(from[1])} – ${h(to[2])}, ${h(to[3])}"
                        }
                        // "ג", "ד", "ה" to "ג-ה"; "א-כא", "כב-לג" to "א-לג"
                        else -> "${first.reading().substringBefore('-')}-${last.reading().substringAfter('-')}"
                    }
                "${first.title()} $reading"
            }
    return LimudItem(Limud.RAMBAM3.kicker, value, days.first().place())
}

// --- Sefer HaMitzvos, the Rambam's mitzvos learned with the three-perakim cycle -----------------------------------

private val seferHamitzvos: List<String> by lazy { jsonResource("sefer_hamitzvot.json").jsonArray.map { it.jsonPrimitive.content } }

private val MITZVAH = Regex("""([PN])(\d+)(?:-(\d+))?""")
private val PRINCIPLES = Regex("""Principle (\d+)-(\d+)""")
private val MEGILLAH = Regex("""Laws of Megillah and Chanukah Chapters (\d+)-(\d+)""")

/** A part of the day's reading: its words, and where it is. */
private fun seferHamitzvosPart(part: String): Pair<String, LibraryPlace>? {
    MITZVAH.matchEntire(part)?.let { match ->
        val (kind, first, last) = match.destructured
        val positive = kind == "P"
        val number = first.toInt()
        val words = (if (positive) "עשה " else "ל״ת ") + if (last.isEmpty()) h(number) else range(number, last.toInt())
        val section = if (positive) "מצוות עשה" else "מצוות לא תעשה"
        return words to LibraryPlace("ספר המצוות", toc = listOf(section), ref = "ספר המצוות, $section, ${h(number)}")
    }
    PRINCIPLES.matchEntire(part)?.let { match ->
        val (first, last) = match.destructured.toList().map { it.toInt() }
        return "שורשים ${range(first, last)}" to LibraryPlace("ספר המצוות", toc = listOf("שורשים", "שורש ${h(first)}"))
    }
    MEGILLAH.matchEntire(part)?.let { match ->
        val (first, last) = match.destructured.toList().map { it.toInt() }
        return "מגילה וחנוכה ${range(first, last)}" to
            LibraryPlace("משנה תורה, הלכות מגילה וחנוכה", toc = listOf("פרק ${h(first)}"))
    }
    return when (part) {
        "Maimonides’ Introduction to Sefer Hamitzvot" ->
            "הקדמת הרמב״ם" to LibraryPlace("ספר המצוות", toc = listOf("הקדמות", "הקדמת הרמב\"ם"))
        "Nusach HaTefila" -> "נוסח התפילה" to LibraryPlace("משנה תורה, סדר התפילה")
        "Order of Prayer" -> "סדר התפילה" to LibraryPlace("משנה תורה, סדר התפילה")
        "Text of the Haggadah" -> "נוסח ההגדה" to LibraryPlace("משנה תורה, הלכות חמץ ומצה", toc = listOf("פרק ט"))
        else -> null
    }
}

internal fun seferHamitzvos(date: LocalDate): LimudItem {
    val parts = seferHamitzvos[cycleDay(date, RAMBAM_START, seferHamitzvos.size)].split(", ").mapNotNull(::seferHamitzvosPart)
    return LimudItem(Limud.SEFER_HAMITZVOS.kicker, parts.joinToString(", ") { it.first }, parts.firstOrNull()?.second)
}

// --- Tehillim and Nach ---------------------------------------------------------------------------------------------

// By the day of the month; קיט split over two days
private val TEHILLIM_MONTH =
    listOf(
        1 to 9,
        10 to 17,
        18 to 22,
        23 to 28,
        29 to 34,
        35 to 38,
        39 to 43,
        44 to 48,
        49 to 54,
        55 to 59,
        60 to 65,
        66 to 68,
        69 to 71,
        72 to 76,
        77 to 78,
        79 to 82,
        83 to 87,
        88 to 89,
        90 to 96,
        97 to 103,
        104 to 105,
        106 to 107,
        108 to 112,
        113 to 118,
        119 to 119,
        119 to 119,
        120 to 134,
        135 to 139,
        140 to 144,
        145 to 150,
    )

private const val TEHILLIM_119 = 119

/** The month's division; a month of 29 days finishes it on its last day. */
internal fun tehillimOfMonth(date: LocalDate): LimudItem {
    val jewish = JewishDate(date.toKotlinLocalDate())
    val day = jewish.jewishDayOfMonth
    return when {
        day == 29 && jewish.daysInJewishMonth == 29 -> tehillim(140, 150)
        day == 25 -> tehillim119(1, 96)
        day == 26 -> tehillim119(97, 176)
        else -> TEHILLIM_MONTH[day - 1].let { (first, last) -> tehillim(first, last) }
    }
}

// From Sunday to Shabbos
private val TEHILLIM_WEEK = listOf(1 to 29, 30 to 50, 51 to 72, 73 to 89, 90 to 106, 107 to 119, 120 to 150)

internal fun tehillimOfWeek(date: LocalDate): LimudItem =
    TEHILLIM_WEEK[date.dayOfWeek.value % 7].let { (first, last) -> tehillim(first, last, Limud.TEHILLIM_WEEK) }

private fun tehillim(
    first: Int,
    last: Int,
    limud: Limud = Limud.TEHILLIM,
) = LimudItem(limud.kicker, range(first, last), LibraryPlace("תהילים", toc = listOf("פרק ${h(first)}")))

private fun tehillim119(
    first: Int,
    last: Int,
) = LimudItem(
    Limud.TEHILLIM.kicker,
    "${h(TEHILLIM_119)}, ${range(first, last)}",
    LibraryPlace("תהילים", toc = listOf("פרק ${h(TEHILLIM_119)}"), ref = "תהילים ${h(TEHILLIM_119)}, ${h(first)}"),
)

private val NACH_YOMI_START: LocalDate = LocalDate.of(2007, 11, 1)

private val nachPerakim: List<Pair<String, Int>> by lazy { NACH.flatMap { (book, count) -> (1..count).map { book to it } } }

/** A perek of Nevi'im and Kesuvim a day, from Yehoshua on 1 November 2007. */
internal fun nachYomi(date: LocalDate): LimudItem {
    val (book, perek) = nachPerakim[cycleDay(date, NACH_YOMI_START, nachPerakim.size)]
    return LimudItem(Limud.NACH.kicker, "$book ${h(perek)}", LibraryPlace(book, toc = listOf("פרק ${h(perek)}")))
}

// --- Pirkei Avos, on the Shabbosos from Pesach to Rosh Hashanah ----------------------------------------------------

private fun hebrewDay(
    year: Long,
    month: HebrewMonth,
    day: Int,
): LocalDate = JewishDate(year, month, day).gregorianLocalDate.toJavaLocalDate()

private fun weeks(days: Long) = Math.ceilDiv(days, 7L).toInt()

/**
 * The perek, or perakim, of Avos read on [shabbos]: one a week from the Shabbos after Pesach, six weeks a round, but
 * none on a Yom Tov or on Tisha B'Av; the fourth round doubles up to end on the Shabbos before Rosh Hashanah.
 */
internal fun pirkeiAvos(
    shabbos: LocalDate,
    inIsrael: Boolean,
): List<Int>? {
    if (shabbos.dayOfWeek != DayOfWeek.SATURDAY) return null
    val year = JewishDate(shabbos.toKotlinLocalDate()).jewishYear
    val pesach7 = hebrewDay(year, HebrewMonth.NISSAN, 21)
    if (!shabbos.isAfter(pesach7)) return null
    val first = pesach7.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
    var round = weeks(ChronoUnit.DAYS.between(first, shabbos))
    val av8 = hebrewDay(year, HebrewMonth.AV, 8)
    val skipped =
        buildList {
            // Yom Tov's eighth day of Pesach and second of Shavuos are kept only abroad
            if (!inIsrael) {
                add(pesach7.plusDays(1))
                add(hebrewDay(year, HebrewMonth.SIVAN, 7))
            }
            add(av8)
            add(av8.plusDays(1))
        }
    for (day in skipped) {
        if (day == shabbos) return null
        if (!day.isAfter(shabbos) && day.dayOfWeek == DayOfWeek.SATURDAY) round -= 1
    }
    if (round < 0) return null
    if (round < 18) return listOf(round % 6 + 1)
    val lastShabbos = hebrewDay(year + 1, HebrewMonth.TISHREI, 1).with(TemporalAdjusters.previous(DayOfWeek.SATURDAY))
    return when (weeks(ChronoUnit.DAYS.between(shabbos, lastShabbos))) {
        0 -> listOf(5, 6)
        1 -> listOf(3, 4)
        2 -> if (round % 6 == 1) listOf(2) else listOf(1, 2)
        3 -> listOf(1)
        else -> null
    }
}

/** The coming Shabbos's Avos, in the summer. */
internal fun pirkeiAvosItem(
    date: LocalDate,
    inIsrael: Boolean,
): LimudItem? {
    val perakim = pirkeiAvos(date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)), inIsrael) ?: return null
    val value = if (perakim.size == 1) "פרק ${h(perakim[0])}" else "פרקים ${range(perakim.first(), perakim.last())}"
    return LimudItem(Limud.PIRKEI_AVOS.kicker, value, LibraryPlace("משנה אבות", toc = listOf("פרק ${h(perakim[0])}")))
}

// --- Kitzur Shulchan Aruch and Aruch HaShulchan --------------------------------------------------------------------

// By the Hebrew month (KosherKotlin's numbers, Adar II after Adar) and its day: "133:17-133:21", "135:13-135:E"
private val kitzurMonths: Map<Int, List<String>> by lazy {
    jsonResource("kitzur_shulchan_aruch.json").jsonObject.mapKeys { it.key.toInt() }.mapValues { (_, days) ->
        days.jsonArray.map { it.jsonPrimitive.content }
    }
}

/** The Kitzur's yearly cycle; none on a 30th of Cheshvan or Adar I, which it has no reading for. */
internal fun kitzur(date: LocalDate): LimudItem? {
    val jewish = JewishDate(date.toKotlinLocalDate())
    val day = jewish.jewishDayOfMonth
    val month = jewish.monthValue()
    if (day == 30 && (month == HebrewMonth.ADAR.value || month == HebrewMonth.CHESHVAN.value)) return null
    val reading = kitzurMonths[month]?.getOrNull(day - 1) ?: return null
    // The day of its closing rules, which the library's Kitzur doesn't have apart
    if (!reading.first().isDigit()) return LimudItem(Limud.KITZUR.kicker, "כללים", null)
    val (first, last) = reading.split('-').let { it.first() to it.last() }
    val siman = first.substringBefore(':').toInt()
    val seif = first.substringAfter(':').toInt()
    val lastSiman = last.substringBefore(':').toInt()
    val lastSeif = last.substringAfter(':')
    val value =
        when {
            lastSiman != siman && lastSeif == "E" -> "${h(siman)}, ${h(seif)} – ${h(lastSiman)}"
            lastSiman != siman -> "${h(siman)}, ${h(seif)} – ${h(lastSiman)}, ${h(lastSeif.toInt())}"
            lastSeif == "E" -> "${h(siman)}, ${h(seif)}-סוף"
            else -> "${h(siman)}, ${range(seif, lastSeif.toInt())}"
        }
    return LimudItem(
        Limud.KITZUR.kicker,
        value,
        LibraryPlace("קיצור שלחן ערוך", toc = listOf("סימן ${h(siman)}"), ref = "קיצור שלחן ערוך ${h(siman)}, ${h(seif)}"),
    )
}

private val AH_SECTIONS = listOf("אורח חיים", "יורה דעה", "אבן העזר", "חושן משפט")
private val AH_SHORT = listOf("או״ח", "יו״ד", "אה״ע", "חו״מ")

private val AH_START: LocalDate = LocalDate.of(2020, 5, 29)

// Its section (1 to 4) and "siman.seif-seif" or "siman.seif-siman.seif"
private val aruchHashulchanDays: List<Pair<Int, String>> by lazy {
    jsonResource("arukh_hashulchan.json").jsonArray.map { day ->
        day.jsonArray.let { it[0].jsonPrimitive.int to it[1].jsonPrimitive.content }
    }
}

/** The Aruch HaShulchan Yomi, from 29 May 2020. */
internal fun aruchHashulchan(date: LocalDate): LimudItem {
    val (sectionNumber, reading) = aruchHashulchanDays[cycleDay(date, AH_START, aruchHashulchanDays.size)]
    val section = AH_SECTIONS[sectionNumber - 1]
    val (first, last) = reading.split('-').let { it.first() to it.last() }
    val siman = first.substringBefore('.').toInt()
    val seif = first.substringAfter('.').toInt()
    val end =
        when {
            '-' !in reading -> ""
            '.' in last -> " – ${h(last.substringBefore('.').toInt())}, ${h(last.substringAfter('.').toInt())}"
            else -> "-${h(last.toInt())}"
        }
    return LimudItem(
        Limud.ARUCH_HASHULCHAN.kicker,
        "${AH_SHORT[sectionNumber - 1]} ${h(siman)}, ${h(seif)}$end",
        LibraryPlace(
            "ערוך השולחן",
            toc = listOf(section, "סימן ${h(siman)}"),
            ref = "ערוך השולחן, $section, ${h(siman)}, ${h(seif)}",
        ),
    )
}

// --- Chofetz Chaim and Shmiras HaLashon, three times and once a year by the Hebrew date ---------------------------

/** A day of a schedule by the Hebrew date: its [section], from [first] to [last] ("1.3", or "28" and "33,34"). */
private class ScheduleDay(
    val book: Int,
    val section: String,
    val first: String?,
    val last: String?,
)

private class ChofetzChaimRow(
    val dates: List<Int>,
    val section: String,
    val first: String?,
    val last: String?,
)

private val chofetzChaimRows: Map<Boolean, List<ChofetzChaimRow>> by lazy {
    val json = jsonResource("chofetz_chaim.json").jsonObject
    mapOf(false to "simple", true to "leap").mapValues { (_, key) ->
        json.getValue(key).jsonArray.map { row ->
            val cells = row.jsonArray
            ChofetzChaimRow(
                cells[0].jsonArray.map { it.jsonPrimitive.int },
                cells[1].jsonPrimitive.content,
                cells[2].text(),
                cells[3].text(),
            )
        }
    }
}

/** hebcal's lookup: the first row of the day gives its start, the last its end. */
private fun chofetzChaimDay(
    rows: List<ChofetzChaimRow>,
    day: Int,
    month: Int,
): ScheduleDay? {
    var found: ChofetzChaimRow? = null
    var last: String? = null
    for (row in rows) {
        val on = row.dates.chunked(2).any { (d, m) -> d == day && m == month }
        if (!on) continue
        if (found == null) found = row
        // The preface's day with two parts ends where its last part does
        last = if (last?.contains(',') == true) last.substringAfterLast(',') else row.last
    }
    return found?.let { ScheduleDay(0, it.section, it.first, last) }
}

/** A short month's 29th also reads its 30th. */
private fun JewishDate.readsThirtieth() =
    jewishDayOfMonth == 29 &&
        (
            (jewishMonth == HebrewMonth.KISLEV && isKislevShort) ||
                (jewishMonth == HebrewMonth.CHESHVAN && !isCheshvanLong)
        )

private const val CC_PSICHAH = "חפץ חיים, פתיחה להלכות לשון הרע ורכילות"

internal fun chofetzChaim(date: LocalDate): LimudItem? {
    val jewish = JewishDate(date.toKotlinLocalDate())
    val rows = chofetzChaimRows.getValue(jewish.isJewishLeapYear)
    var day = chofetzChaimDay(rows, jewish.jewishDayOfMonth, jewish.monthValue()) ?: return null
    if (jewish.readsThirtieth()) {
        chofetzChaimDay(rows, 30, jewish.monthValue())?.let { day = ScheduleDay(0, day.section, day.first, it.last) }
    }
    val (name, refBase) =
        when (day.section) {
            "Hakdamah" -> "הקדמה" to "חפץ חיים, הקדמה"
            "Psichah" -> "פתיחה" to "$CC_PSICHAH, הקדמה"
            "Lavin" -> "לאוין" to "$CC_PSICHAH, לאוין"
            "Asin" -> "עשין" to "$CC_PSICHAH, עשיין"
            "Arurin" -> "ארורין" to "$CC_PSICHAH, ארורין"
            "HilchosLH" -> "לשון הרע" to "חפץ חיים, חלק ראשון: הלכות איסורי לשון הרע"
            "HilchosRechilus" -> "רכילות" to "חפץ חיים, חלק שני: הלכות איסורי רכילות"
            "Tziyurim" -> "ציור" to "חפץ חיים, ציורים"
            else -> return null
        }
    val first = day.first
    val (value, ref) =
        when {
            first == null -> name to refBase
            '.' in first -> {
                // כלל.סעיף
                val klal = first.substringBefore('.').toInt()
                val seif = first.substringAfter('.').toInt()
                val last = day.last
                val end =
                    when {
                        last == null || last == first -> ""
                        last.substringBefore('.').toInt() != klal ->
                            " – ${h(last.substringBefore('.').toInt())}, ${h(last.substringAfter('.').toInt())}"
                        else -> "-${h(last.substringAfter('.').toInt())}"
                    }
                "$name כלל ${h(klal)}, ${h(seif)}$end" to "$refBase, כלל ${h(klal)}, ${h(seif)}"
            }
            else -> {
                val start = first.toInt()
                val last = day.last
                val reading =
                    when {
                        last == null -> h(start)
                        // The preface's two parts: "כח, לג-לד"
                        ',' in last -> "${h(start)}, ${last.split(',').map { it.toInt() }.let { range(it.first(), it.last()) }}"
                        else -> range(start, last.toInt())
                    }
                val ref = if (day.section == "Tziyurim") "$refBase, ציור ${h(start)}" else "$refBase, ${h(start)}"
                "$name $reading" to ref
            }
        }
    return LimudItem(Limud.CHOFETZ_CHAIM.kicker, value, LibraryPlace("חפץ חיים", ref = ref))
}

private class ShmirasHalashonRow(
    val normal: Pair<Int, Int>,
    val leap: Pair<Int, Int>,
    val day: ScheduleDay,
)

private val shmirasHalashonRows: List<ShmirasHalashonRow> by lazy {
    jsonResource("shmirat_halashon.json").jsonArray.map { row ->
        val cells = row.jsonArray

        fun date(cell: JsonElement) = cell.jsonArray.let { it[0].jsonPrimitive.int to it[1].jsonPrimitive.int }
        ShmirasHalashonRow(
            date(cells[0]),
            date(cells[1]),
            ScheduleDay(cells[2].jsonPrimitive.int, cells[3].jsonPrimitive.content, cells[4].text(), cells[5].text()),
        )
    }
}

private fun shmirasHalashonDay(
    day: Int,
    month: Int,
    leap: Boolean,
): ScheduleDay? {
    var found: ScheduleDay? = null
    var last: String? = null
    for (row in shmirasHalashonRows) {
        if ((if (leap) row.leap else row.normal) != day to month) continue
        if (found == null) found = row.day
        last = row.day.last ?: row.day.first
    }
    return found?.let { ScheduleDay(it.book, it.section, it.first, last) }
}

/** Its yearly cycle, by the Hebrew date. */
internal fun shmirasHalashon(date: LocalDate): LimudItem? {
    val jewish = JewishDate(date.toKotlinLocalDate())
    val leap = jewish.isJewishLeapYear
    var day = shmirasHalashonDay(jewish.jewishDayOfMonth, jewish.monthValue(), leap) ?: return null
    if (jewish.readsThirtieth()) {
        shmirasHalashonDay(30, jewish.monthValue(), leap)?.let { day = ScheduleDay(day.book, day.section, day.first, it.last) }
    }
    val partOne = day.book == 1
    val gate =
        when (day.section) {
            "Hakdamah" -> "הקדמה"
            "Shar Hazechira" -> "שער הזכירה"
            "Shar Hatvuna" -> "שער התבונה"
            "Shar Hatorah" -> "שער התורה"
            "Chasimas Hasefer" -> "חתימת הספר"
            else -> null
        }
    val refBase = listOfNotNull("שמירת הלשון", if (partOne) "חלק ראשון" else "חלק שני", gate).joinToString(", ")
    val name = listOfNotNull(if (partOne) null else "ח״ב", gate).joinToString(" ")
    // A day of a note only: "19.Footnote_in_11", the note in אות יא
    val footnote = day.first?.contains(FOOTNOTE) == true
    val first = day.first?.replace(FOOTNOTE, "") ?: return null
    val last = day.last?.replace(FOOTNOTE, "") ?: first
    // "פרק.אות", or the introduction's paragraphs
    val (perek, os) = if ('.' in first) first.substringBefore('.').toInt() to first.substringAfter('.').toInt() else null to first.toInt()
    val reading =
        when {
            perek == null -> range(os, last.substringAfter('.').toInt())
            last
                .substringBefore(
                    '.',
                ).toInt() != perek -> "${h(
                perek,
            )}, ${h(os)} – ${h(last.substringBefore('.').toInt())}, ${h(last.substringAfter('.').toInt())}"
            else -> "${h(perek)}, ${range(os, last.substringAfter('.').toInt())}"
        }
    val ref = if (perek == null) "$refBase, ${h(os)}" else "$refBase, ${h(perek)}, ${h(os)}"
    val value = "$name $reading".trim() + if (footnote) " (הגה״ה)" else ""
    return LimudItem(Limud.SHMIRAS_HALASHON.kicker, value, LibraryPlace("שמירת הלשון", ref = ref))
}

private const val FOOTNOTE = "Footnote_in_"

// The Mishnah's masechtos in order, with the number of mishnayos of each perek
private val MISHNAH: List<Pair<String, IntArray>> =
    listOf(
        "ברכות" to intArrayOf(5, 8, 6, 7, 5, 8, 5, 8, 5),
        "פאה" to intArrayOf(6, 8, 8, 11, 8, 11, 8, 9),
        "דמאי" to intArrayOf(4, 5, 6, 7, 11, 12, 8),
        "כלאים" to intArrayOf(9, 11, 7, 9, 8, 9, 8, 6, 10),
        "שביעית" to intArrayOf(8, 10, 10, 10, 9, 6, 7, 11, 9, 9),
        "תרומות" to intArrayOf(10, 6, 9, 13, 9, 6, 7, 12, 7, 12, 10),
        "מעשרות" to intArrayOf(8, 8, 10, 6, 8),
        "מעשר שני" to intArrayOf(7, 10, 13, 12, 15),
        "חלה" to intArrayOf(9, 8, 10, 11),
        "ערלה" to intArrayOf(9, 17, 9),
        "ביכורים" to intArrayOf(11, 11, 12, 5),
        "שבת" to intArrayOf(11, 7, 6, 2, 4, 10, 4, 7, 7, 6, 6, 6, 7, 4, 3, 8, 8, 3, 6, 5, 3, 6, 5, 5),
        "עירובין" to intArrayOf(10, 6, 9, 11, 9, 10, 11, 11, 4, 15),
        "פסחים" to intArrayOf(7, 8, 8, 9, 10, 6, 13, 8, 11, 9),
        "שקלים" to intArrayOf(7, 5, 4, 9, 6, 6, 7, 8),
        "יומא" to intArrayOf(8, 7, 11, 6, 7, 8, 5, 9),
        "סוכה" to intArrayOf(11, 9, 15, 10, 8),
        "ביצה" to intArrayOf(10, 10, 8, 7, 7),
        "ראש השנה" to intArrayOf(9, 9, 8, 9),
        "תענית" to intArrayOf(7, 10, 9, 8),
        "מגילה" to intArrayOf(11, 6, 6, 10),
        "מועד קטן" to intArrayOf(10, 5, 9),
        "חגיגה" to intArrayOf(8, 7, 8),
        "יבמות" to intArrayOf(4, 10, 10, 13, 6, 6, 6, 6, 6, 9, 7, 6, 13, 9, 10, 7),
        "כתובות" to intArrayOf(10, 10, 9, 12, 9, 7, 10, 8, 9, 6, 6, 4, 11),
        "נדרים" to intArrayOf(4, 5, 11, 8, 6, 10, 9, 7, 10, 8, 12),
        "נזיר" to intArrayOf(7, 10, 7, 7, 7, 11, 4, 2, 5),
        "סוטה" to intArrayOf(9, 6, 8, 5, 5, 4, 8, 7, 15),
        "גיטין" to intArrayOf(6, 7, 8, 9, 9, 7, 9, 10, 10),
        "קידושין" to intArrayOf(10, 10, 13, 14),
        "בבא קמא" to intArrayOf(4, 6, 11, 9, 7, 6, 7, 7, 12, 10),
        "בבא מציעא" to intArrayOf(8, 11, 12, 12, 11, 8, 11, 9, 13, 6),
        "בבא בתרא" to intArrayOf(6, 14, 8, 9, 11, 8, 4, 8, 10, 8),
        "סנהדרין" to intArrayOf(6, 5, 8, 5, 5, 6, 11, 7, 6, 6, 6),
        "מכות" to intArrayOf(10, 8, 16),
        "שבועות" to intArrayOf(7, 5, 11, 13, 5, 7, 8, 6),
        "עדיות" to intArrayOf(14, 10, 12, 12, 7, 3, 9, 7),
        "עבודה זרה" to intArrayOf(9, 7, 10, 12, 12),
        "אבות" to intArrayOf(18, 16, 18, 22, 23, 11),
        "הוריות" to intArrayOf(5, 7, 8),
        "זבחים" to intArrayOf(4, 5, 6, 6, 8, 7, 6, 12, 7, 8, 8, 6, 8, 10),
        "מנחות" to intArrayOf(4, 5, 7, 5, 9, 7, 6, 7, 9, 9, 9, 5, 11),
        "חולין" to intArrayOf(7, 10, 7, 7, 5, 7, 6, 6, 8, 4, 2, 5),
        "בכורות" to intArrayOf(7, 9, 4, 10, 6, 12, 7, 10, 8),
        "ערכין" to intArrayOf(4, 6, 5, 4, 6, 5, 5, 7, 8),
        "תמורה" to intArrayOf(6, 3, 5, 4, 6, 5, 6),
        "כריתות" to intArrayOf(7, 6, 10, 3, 8, 9),
        "מעילה" to intArrayOf(4, 9, 8, 6, 5, 6),
        "תמיד" to intArrayOf(4, 5, 9, 3, 6, 3, 4),
        "מדות" to intArrayOf(9, 6, 8, 7, 4),
        "קינים" to intArrayOf(4, 5, 6),
        "כלים" to intArrayOf(9, 8, 8, 4, 11, 4, 6, 11, 8, 8, 9, 8, 8, 8, 6, 8, 17, 9, 10, 7, 3, 10, 5, 17, 9, 9, 12, 10, 8, 4),
        "אהלות" to intArrayOf(8, 7, 7, 3, 7, 7, 6, 6, 16, 7, 9, 8, 6, 7, 10, 5, 5, 10),
        "נגעים" to intArrayOf(6, 5, 8, 11, 5, 8, 5, 10, 3, 10, 12, 7, 12, 13),
        "פרה" to intArrayOf(4, 5, 11, 4, 9, 5, 12, 11, 9, 6, 9, 11),
        "טהרות" to intArrayOf(9, 8, 8, 13, 9, 10, 9, 9, 9, 8),
        "מקואות" to intArrayOf(8, 10, 4, 5, 6, 11, 7, 5, 7, 8),
        "נדה" to intArrayOf(7, 7, 7, 7, 9, 14, 5, 4, 11, 8),
        "מכשירין" to intArrayOf(6, 11, 8, 10, 11, 8),
        "זבים" to intArrayOf(6, 4, 3, 7, 12),
        "טבול יום" to intArrayOf(5, 8, 6, 7),
        "ידים" to intArrayOf(5, 4, 5, 8),
        "עוקצים" to intArrayOf(6, 10, 12),
    )

// The Mishneh Torah's books in the daily Rambam's order, with their perakim (the first four: their days)
private val MISHNEH_TORAH: List<Pair<String, Int>> =
    listOf(
        "מסירת תורה שבעל פה" to 3,
        "מצוות עשה" to 3,
        "מצוות לא תעשה" to 3,
        "תוכן החיבור" to 3,
        "הלכות יסודי התורה" to 10,
        "הלכות דעות" to 7,
        "הלכות תלמוד תורה" to 7,
        "הלכות עבודה זרה וחוקות הגויים" to 12,
        "הלכות תשובה" to 10,
        "הלכות קריאת שמע" to 4,
        "הלכות תפילה וברכת כהנים" to 15,
        "הלכות תפילין ומזוזה וספר תורה" to 10,
        "הלכות ציצית" to 3,
        "הלכות ברכות" to 11,
        "הלכות מילה" to 3,
        "סדר התפילה" to 4,
        "הלכות שבת" to 30,
        "הלכות עירובין" to 8,
        "הלכות שביתת עשור" to 3,
        "הלכות שביתת יום טוב" to 8,
        "הלכות חמץ ומצה" to 9,
        "הלכות שופר וסוכה ולולב" to 8,
        "הלכות שקלים" to 4,
        "הלכות קידוש החודש" to 19,
        "הלכות תעניות" to 5,
        "הלכות מגילה וחנוכה" to 4,
        "הלכות אישות" to 25,
        "הלכות גירושין" to 13,
        "הלכות יבום וחליצה" to 8,
        "הלכות נערה בתולה" to 3,
        "הלכות סוטה" to 4,
        "הלכות איסורי ביאה" to 22,
        "הלכות מאכלות אסורות" to 17,
        "הלכות שחיטה" to 14,
        "הלכות שבועות" to 12,
        "הלכות נדרים" to 13,
        "הלכות נזירות" to 10,
        "הלכות ערכים וחרמין" to 8,
        "הלכות כלאים" to 10,
        "הלכות מתנות עניים" to 10,
        "הלכות תרומות" to 15,
        "הלכות מעשרות" to 14,
        "הלכות מעשר שני ונטע רבעי" to 11,
        "הלכות ביכורים ושאר מתנות כהונה שבגבולין" to 12,
        "הלכות שמיטה ויובל" to 13,
        "הלכות בית הבחירה" to 8,
        "הלכות כלי המקדש והעובדין בו" to 10,
        "הלכות ביאת מקדש" to 9,
        "הלכות איסורי המזבח" to 7,
        "הלכות מעשה הקרבנות" to 19,
        "הלכות תמידים ומוספין" to 10,
        "הלכות פסולי המוקדשין" to 19,
        "הלכות עבודת יום הכפורים" to 5,
        "הלכות מעילה" to 8,
        "הלכות קרבן פסח" to 10,
        "הלכות חגיגה" to 3,
        "הלכות בכורות" to 8,
        "הלכות שגגות" to 15,
        "הלכות מחוסרי כפרה" to 5,
        "הלכות תמורה" to 4,
        "הלכות טומאת מת" to 25,
        "הלכות פרה אדומה" to 15,
        "הלכות טומאת צרעת" to 16,
        "הלכות מטמאי משכב ומושב" to 13,
        "הלכות שאר אבות הטומאות" to 20,
        "הלכות טומאת אוכלים" to 16,
        "הלכות כלים" to 28,
        "הלכות מקואות" to 11,
        "הלכות נזקי ממון" to 14,
        "הלכות גניבה" to 9,
        "הלכות גזילה ואבידה" to 18,
        "הלכות חובל ומזיק" to 8,
        "הלכות רוצח ושמירת נפש" to 13,
        "הלכות מכירה" to 30,
        "הלכות זכייה ומתנה" to 12,
        "הלכות שכנים" to 14,
        "הלכות שלוחין ושותפין" to 10,
        "הלכות עבדים" to 9,
        "הלכות שכירות" to 13,
        "הלכות שאלה ופיקדון" to 8,
        "הלכות מלווה ולווה" to 27,
        "הלכות טוען ונטען" to 16,
        "הלכות נחלות" to 11,
        "הלכות סנהדרין והעונשין המסורין להם" to 26,
        "הלכות עדות" to 22,
        "הלכות ממרים" to 7,
        "הלכות אבל" to 14,
        "הלכות מלכים ומלחמות" to 12,
    )

// Nevi'im and Kesuvim, with their perakim
private val NACH: List<Pair<String, Int>> =
    listOf(
        "יהושע" to 24,
        "שופטים" to 21,
        "שמואל א" to 31,
        "שמואל ב" to 24,
        "מלכים א" to 22,
        "מלכים ב" to 25,
        "ישעיהו" to 66,
        "ירמיהו" to 52,
        "יחזקאל" to 48,
        "הושע" to 14,
        "יואל" to 4,
        "עמוס" to 9,
        "עובדיה" to 1,
        "יונה" to 4,
        "מיכה" to 7,
        "נחום" to 3,
        "חבקוק" to 3,
        "צפניה" to 3,
        "חגי" to 2,
        "זכריה" to 14,
        "מלאכי" to 3,
        "תהילים" to 150,
        "משלי" to 31,
        "איוב" to 42,
        "שיר השירים" to 8,
        "רות" to 4,
        "איכה" to 5,
        "קהלת" to 12,
        "אסתר" to 10,
        "דניאל" to 12,
        "עזרא" to 10,
        "נחמיה" to 13,
        "דברי הימים א" to 29,
        "דברי הימים ב" to 36,
    )
