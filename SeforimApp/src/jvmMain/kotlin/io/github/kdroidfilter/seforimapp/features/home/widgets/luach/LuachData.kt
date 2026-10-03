package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar.Parsha
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.TefilaRules
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetLocation
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaEarliestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaLatestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.ZmanimOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim.candleLightingTime
import io.github.kdroidfilter.seforimapp.features.home.widgets.zmanim.havdalahTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Date

// What the luach widgets show, after the KosherKotlin demo's luach: plain data, computed from the day and the place.

internal val hebrewFormatter =
    HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = true
    }

// The library's TOC headings write their numbers without geresh: "דף כא."
private val tocNumbers =
    HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = false
    }

internal val HEBREW_WEEKDAYS = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")

internal fun LocalDate.hebrewWeekday() = HEBREW_WEEKDAYS[dayOfWeek.value % 7]

/**
 * Where a limud is in the library: [bookTitle], under its TOC headings [toc] (each one under the one before), at the
 * line whose reference is [ref] or starts it ("משנה ברכות א, ב"), or at the [parashaIndex]th entry of its Parasha
 * alternative TOC; at its start when none is set or found.
 *
 * Where it ends, for the book to mark it: through the last line of the first of [endRefs] found (as [ref] finds it), or
 * to the end of the first TOC entry of [endTocs] found (a heading beside [toc]'s last), or of [parashaCount] parshiyos;
 * the alternatives cover the library's editions ("דף סד." without its ":", two paragraphs in one line).
 */
@Immutable
internal data class LibraryPlace(
    val bookTitle: String,
    val toc: List<String> = emptyList(),
    val ref: String? = null,
    val parashaIndex: Int? = null,
    val endRefs: List<String> = emptyList(),
    val endTocs: List<String> = emptyList(),
    val parashaCount: Int = 1,
)

@Immutable
internal data class LimudItem(
    val kicker: String,
    val value: String,
    val place: LibraryPlace?,
)

/** The day's limudim of [shown], in the menu's order; the ones the day has none of (Avos in the winter) left out. */
internal fun limudOfDay(
    date: LocalDate,
    inIsrael: Boolean,
    shown: Set<Limud> = Limud.defaults.toSet(),
): List<LimudItem> {
    val day by lazy { JewishCalendar(date.toKotlinLocalDate(), inIsrael) }
    return Limud.entries.filter { it in shown }.mapNotNull { limud ->
        when (limud) {
            Limud.PARSHA -> parshaItem(date, inIsrael)
            Limud.BAVLI ->
                day.dafYomiBavli.let { bavli ->
                    LimudItem(
                        kicker = limud.kicker,
                        value = bavli?.let(hebrewFormatter::formatDafYomiBavli) ?: "—",
                        place = bavli?.let { bavliPlace(it.masechtaNumber, it.masechta, it.daf) },
                    )
                }
            Limud.YERUSHALMI ->
                day.dafYomiYerushalmi.let { yerushalmi ->
                    LimudItem(
                        kicker = limud.kicker,
                        value = hebrewFormatter.formatDafYomiYerushalmi(yerushalmi),
                        place = yerushalmi?.let { LibraryPlace("תלמוד ירושלמי ${yerushalmiTitle(it.yerushalmiMasechta)}") },
                    )
                }
            Limud.MISHNAH -> mishnahYomis(date)
            Limud.PEREK_MISHNAH -> perekMishnah(date)
            Limud.PIRKEI_AVOS -> pirkeiAvosItem(date, inIsrael)
            Limud.RAMBAM3 -> rambam3(date)
            Limud.RAMBAM1 -> rambam1(date)
            Limud.SEFER_HAMITZVOS -> seferHamitzvos(date)
            Limud.TEHILLIM -> tehillimOfMonth(date)
            Limud.TEHILLIM_WEEK -> tehillimOfWeek(date)
            Limud.NACH -> nachYomi(date)
            Limud.KITZUR -> kitzur(date)
            Limud.ARUCH_HASHULCHAN -> aruchHashulchan(date)
            Limud.CHOFETZ_CHAIM -> chofetzChaim(date)
            Limud.SHMIRAS_HALASHON -> shmirasHalashon(date)
        }
    }
}

/** The parsha of the week, as the Shnayim Mikra widget reads it: past a Shabbat of Yom Tov, the next one read. */
private fun parshaItem(
    date: LocalDate,
    inIsrael: Boolean,
): LimudItem {
    val week = mikraWeek(date, inIsrael)
    return LimudItem(kicker = Limud.PARSHA.kicker, value = week.name, place = parshaPlace(week.parsha))
}

// Parsha.BERESHIS..VZOS_HABERACHA are 1..54, in the order of the Chumash's Parasha TOCs
private val CHUMASH = listOf("בראשית" to 12, "שמות" to 11, "ויקרא" to 10, "במדבר" to 10, "דברים" to 11)

private val DOUBLE_PARSHIYOS =
    mapOf(
        Parsha.VAYAKHEL_PEKUDEI to Parsha.VAYAKHEL,
        Parsha.TAZRIA_METZORA to Parsha.TAZRIA,
        Parsha.ACHREI_MOS_KEDOSHIM to Parsha.ACHREI_MOS,
        Parsha.BEHAR_BECHUKOSAI to Parsha.BEHAR,
        Parsha.CHUKAS_BALAK to Parsha.CHUKAS,
        Parsha.MATOS_MASEI to Parsha.MATOS,
        Parsha.NITZAVIM_VAYEILECH to Parsha.NITZAVIM,
    )

internal fun parshaPlace(parsha: Parsha): LibraryPlace? {
    var index = (DOUBLE_PARSHIYOS[parsha] ?: parsha).ordinal - 1
    val count = if (parsha in DOUBLE_PARSHIYOS) 2 else 1
    for ((book, parshiyos) in CHUMASH) {
        if (index in 0 until parshiyos) return LibraryPlace(book, parashaIndex = index, parashaCount = count)
        index -= parshiyos
    }
    return null
}

private const val SHEKALIM = 4
private const val KINNIM = 36
private const val MIDOS = 38

internal fun bavliPlace(
    masechtaNumber: Int,
    masechta: String,
    daf: Int,
): LibraryPlace =
    when (masechtaNumber) {
        // No Bavli on these: the daf yomi learns the Yerushalmi's Shekalim and the Mishnah's Kinnim and Middos
        SHEKALIM -> LibraryPlace("תלמוד ירושלמי שקלים")
        KINNIM -> LibraryPlace("משנה קינים")
        MIDOS -> LibraryPlace("משנה מדות")
        else -> {
            val number = tocNumbers.formatHebrewNumber(daf)
            // A masechta's last daf may have no amud ב
            LibraryPlace(masechta, toc = listOf("דף $number."), endTocs = listOf("דף $number:", "דף $number."))
        }
    }

// KosherKotlin spells three masechtos otherwise than the library's titles
private fun yerushalmiTitle(masechta: String) =
    when (masechta) {
        "פיאה" -> "פאה"
        "ביכורים" -> "בכורים"
        "נידה" -> "נדה"
        else -> masechta
    }

@Immutable
internal data class TefilaLine(
    val label: String,
    val value: String,
    /** An addition of the day, not an every-day choice. */
    val special: Boolean = false,
)

/** What changes in the day's tefila. */
internal fun tefilaOfDay(
    date: LocalDate,
    inIsrael: Boolean,
): List<TefilaLine> {
    val day = JewishCalendar(date.toKotlinLocalDate(), inIsrael)
    val rules = TefilaRules()
    return buildList {
        // ponytail: Ashkenazim abroad say neither in the summer; the line shows the Sephardi and Israeli usage
        val gevuros =
            when {
                rules.isMashivHaruachStartDate(day) -> "מוריד הטל · במוסף משיב הרוח"
                rules.isMashivHaruachEndDate(day) -> "משיב הרוח · במוסף מוריד הטל"
                rules.isMashivHaruachRecited(day) -> "משיב הרוח ומוריד הגשם"
                else -> "מוריד הטל"
            }
        add(TefilaLine("גבורות", gevuros))
        add(TefilaLine("ברכת השנים", if (rules.isVeseinTalUmatarRecited(day)) "ותן טל ומטר לברכה" else "ותן ברכה"))
        add(TefilaLine("תחנון", if (rules.isTachanunRecitedShacharis(day)) "אומרים" else "אין אומרים"))
        when {
            rules.isHallelShalemRecited(day) -> add(TefilaLine("הלל", "הלל שלם", special = true))
            rules.isHallelRecited(day) -> add(TefilaLine("הלל", "חצי הלל", special = true))
        }
        if (rules.isYaalehVeyavoRecited(day)) add(TefilaLine("יעלה ויבוא", "אומרים", special = true))
        if (rules.isAlHanissimRecited(day)) add(TefilaLine("על הניסים", "אומרים", special = true))
        if (day.dayOfOmer != -1) add(TefilaLine("ספירת העומר", hebrewFormatter.formatOmer(day), special = true))
    }
}

@Immutable
internal data class LuachEvent(
    val date: LocalDate,
    val name: String,
    val hebrewDate: String,
    val times: List<Pair<String, Date?>>,
)

/** The coming Shabbatot, festivals, fasts and Rashei Chodashim, from [from] on. */
internal fun upcomingEvents(
    from: LocalDate,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion,
    cityLabel: String?,
    inIsrael: Boolean,
    limit: Int = 8,
    horizonDays: Long = 90,
): List<LuachEvent> {
    fun calendarOf(date: LocalDate) = JewishCalendar(date.toKotlinLocalDate(), inIsrael)
    val events = mutableListOf<LuachEvent>()
    for (offset in 0..horizonDays) {
        if (events.size >= limit) break
        val date = from.plusDays(offset)
        val day = calendarOf(date)
        val isShabbat = date.dayOfWeek == DayOfWeek.SATURDAY
        if (!isShabbat && !day.isYomTov && !day.isTaanis && !day.isRoshChodesh) continue

        val times =
            when {
                // A day off work: candles on the eve of the first one, havdala at the end of the last
                day.isAssurBemelacha ->
                    buildList {
                        if (!calendarOf(date.minusDays(1)).isAssurBemelacha) {
                            add("הדלקת נרות" to candleLightingTime(date.minusDays(1), location, opinion, cityLabel, inIsrael))
                        }
                        if (!calendarOf(date.plusDays(1)).isAssurBemelacha) {
                            add("יציאה" to havdalahTime(date, location, opinion, inIsrael))
                        }
                    }

                day.isTaanis -> {
                    val zmanim = computeZmanimTimes(date, location, opinion, inIsrael)
                    val start =
                        if (day.isTishaBav) {
                            computeZmanimTimes(date.minusDays(1), location, opinion, inIsrael).sunset
                        } else {
                            zmanim.alosHashachar
                        }
                    listOf("תחילת הצום" to start, "סוף הצום" to zmanim.tzais)
                }

                else -> emptyList()
            }
        val name =
            hebrewFormatter.formatYomTov(day).takeIf { it.isNotBlank() }
                ?: if (isShabbat) {
                    hebrewFormatter.formatParsha(day)?.takeIf { it.isNotBlank() }?.let { "שבת $it" } ?: "שבת"
                } else {
                    hebrewFormatter.formatRoshChodesh(day).ifBlank { "ראש חודש" }
                }
        // The year is the one being read; leaving it out keeps the line to one
        val hebrewDate = "${hebrewFormatter.formatHebrewNumber(day.jewishDayOfMonth)} ${hebrewFormatter.formatMonth(day)}"
        events += LuachEvent(date, name, hebrewDate, times)
    }
    return events
}

@Immutable
internal data class Zman(
    val name: String,
    val time: Date,
)

/** The [count] zmanim after [now], in order. */
internal fun nextZmanim(
    now: Date,
    location: EarthWidgetLocation,
    opinion: ZmanimOpinion,
    inIsrael: Boolean,
    count: Int = 3,
): List<Zman> {
    val today = now.toInstant().atZone(location.timeZone.toZoneId()).toLocalDate()
    // Yesterday's חצות הלילה may still be ahead after midnight; tomorrow's alos once tonight's zmanim are past
    return (-1L..1L)
        .flatMap { offset ->
            val z = computeZmanimTimes(today.plusDays(offset), location, opinion, inIsrael)
            listOf(
                "עלות השחר" to z.alosHashachar,
                "הנץ החמה" to z.sunrise,
                "סוף זמן ק״ש מג״א" to z.sofZmanShmaMga,
                "סוף זמן ק״ש גר״א" to z.sofZmanShmaGra,
                "סוף זמן תפילה מג״א" to z.sofZmanTfilaMga,
                "סוף זמן תפילה גר״א" to z.sofZmanTfilaGra,
                "חצות היום" to z.chatzosHayom,
                "מנחה גדולה" to z.minchaGedola,
                "מנחה קטנה" to z.minchaKetana,
                "פלג המנחה" to z.plagHamincha,
                "שקיעה" to z.sunset,
                "צאת הכוכבים" to z.tzais,
                "צאת הכוכבים ר״ת" to z.tzaisRabbeinuTam,
                "חצות הלילה" to z.chatzosLayla,
            )
        }.mapNotNull { (name, time) -> time?.takeIf { it.after(now) }?.let { Zman(name, it) } }
        .distinctBy { it.name to it.time }
        .sortedBy { it.time }
        .take(count)
}

@Immutable
internal data class MoladInfo(
    val month: String,
    val weekday: String,
    val time: String,
    val chalakim: Int,
    val kiddushLevanaStart: Date,
    val kiddushLevanaEnd: Date,
)

/** The month's Kiddush Levana window, from its first time to its last after the opinions. */
internal fun JewishCalendar.kiddushLevanaWindow(
    earliest: KiddushLevanaEarliestOpinion,
    latest: KiddushLevanaLatestOpinion,
) = when (earliest) {
    KiddushLevanaEarliestOpinion.DAYS_3 -> tchilasZmanKidushLevana3Days
    KiddushLevanaEarliestOpinion.DAYS_7 -> tchilasZmanKidushLevana7Days
} to
    when (latest) {
        KiddushLevanaLatestOpinion.BETWEEN_MOLDOS -> sofZmanKidushLevanaBetweenMoldos
        KiddushLevanaLatestOpinion.DAYS_15 -> sofZmanKidushLevana15Days
    }

/**
 * The molad of the coming month, and the Kiddush Levana window still open or next to open after [now] — this
 * month's until it closes, then the coming month's.
 */
internal fun moladInfo(
    now: Date,
    date: LocalDate,
    inIsrael: Boolean,
    earliest: KiddushLevanaEarliestOpinion,
    latest: KiddushLevanaLatestOpinion,
): MoladInfo {
    val current =
        JewishCalendar(date.toKotlinLocalDate(), inIsrael).apply {
            setJewishDate(jewishYear, jewishMonth, 1)
        }
    val coming = JewishCalendar(current.gregorianLocalDate.plus(current.daysInJewishMonth, DateTimeUnit.DAY), inIsrael)
    val molad = coming.molad
    val thisMonth = current.kiddushLevanaWindow(earliest, latest)
    val (start, end) =
        if (thisMonth.second.toEpochMilliseconds() > now.time) thisMonth else coming.kiddushLevanaWindow(earliest, latest)

    return MoladInfo(
        month = hebrewFormatter.formatMonth(coming),
        weekday = molad.gregorianLocalDate.toJavaLocalDate().hebrewWeekday(),
        time = "${molad.moladHours.toString().padStart(2, '0')}:${molad.moladMinutes.toString().padStart(2, '0')}",
        chalakim = molad.moladChalakim,
        kiddushLevanaStart = Date(start.toEpochMilliseconds()),
        kiddushLevanaEnd = Date(end.toEpochMilliseconds()),
    )
}
