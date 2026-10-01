package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
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
import java.time.temporal.TemporalAdjusters
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
 * Where a limud is in the library: [bookTitle], at its TOC heading [heading] or at the [parashaIndex]th entry of
 * its Parasha alternative TOC; at its start when neither is set.
 */
@Immutable
internal data class LibraryPlace(
    val bookTitle: String,
    val heading: String? = null,
    val parashaIndex: Int? = null,
)

@Immutable
internal data class LimudItem(
    val kicker: String,
    val value: String,
    val place: LibraryPlace?,
)

/** This week's parsha and the day's dafim. */
internal fun limudOfDay(
    date: LocalDate,
    inIsrael: Boolean,
): List<LimudItem> {
    val day = JewishCalendar(date.toKotlinLocalDate(), inIsrael)
    // The parsha read on the coming Shabbat; none when it is a Yom Tov, rather than one weeks away
    val shabbat = JewishCalendar(date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).toKotlinLocalDate(), inIsrael)
    val parsha = shabbat.parshah
    val bavli = day.dafYomiBavli
    val yerushalmi = day.dafYomiYerushalmi
    return listOf(
        LimudItem(
            kicker = "פרשת השבוע",
            value = hebrewFormatter.formatParsha(shabbat)?.takeIf { it.isNotBlank() } ?: "אין פרשה",
            place = parshaPlace(parsha),
        ),
        LimudItem(
            kicker = "דף יומי · בבלי",
            value = bavli?.let(hebrewFormatter::formatDafYomiBavli) ?: "—",
            place = bavli?.let { bavliPlace(it.masechtaNumber, it.masechta, it.daf) },
        ),
        LimudItem(
            kicker = "דף יומי · ירושלמי",
            value = hebrewFormatter.formatDafYomiYerushalmi(yerushalmi),
            place = yerushalmi?.let { LibraryPlace("תלמוד ירושלמי ${yerushalmiTitle(it.yerushalmiMasechta)}") },
        ),
    )
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
    for ((book, count) in CHUMASH) {
        if (index in 0 until count) return LibraryPlace(book, parashaIndex = index)
        index -= count
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
        else -> LibraryPlace(masechta, heading = "דף ${tocNumbers.formatHebrewNumber(daf)}.")
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
                day.isMashivHaruachSeason() -> "משיב הרוח ומוריד הגשם"
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

/**
 * From after 22 Tishrei to before 15 Nissan. TefilaRules.isMashivHaruachRecited of KosherKotlin 3.0.0 builds its
 * bounds with the month as the year, and throws.
 */
private fun JewishCalendar.isMashivHaruachSeason(): Boolean {
    val day = jewishDayOfMonth
    return when (hebrewLocalDate.month) {
        HebrewMonth.TISHREI -> day > 22
        HebrewMonth.NISSAN -> day < 15
        HebrewMonth.CHESHVAN, HebrewMonth.KISLEV, HebrewMonth.TEVES, HebrewMonth.SHEVAT, HebrewMonth.ADAR, HebrewMonth.ADAR_II -> true
        else -> false
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

    fun JewishCalendar.window() =
        when (earliest) {
            KiddushLevanaEarliestOpinion.DAYS_3 -> tchilasZmanKidushLevana3Days
            KiddushLevanaEarliestOpinion.DAYS_7 -> tchilasZmanKidushLevana7Days
        } to
            when (latest) {
                KiddushLevanaLatestOpinion.BETWEEN_MOLDOS -> sofZmanKidushLevanaBetweenMoldos
                KiddushLevanaLatestOpinion.DAYS_15 -> sofZmanKidushLevana15Days
            }
    val thisMonth = current.window()
    val (start, end) = if (thisMonth.second.toEpochMilliseconds() > now.time) thisMonth else coming.window()

    return MoladInfo(
        month = hebrewFormatter.formatMonth(coming),
        weekday = molad.gregorianLocalDate.toJavaLocalDate().hebrewWeekday(),
        time = "${molad.moladHours.toString().padStart(2, '0')}:${molad.moladMinutes.toString().padStart(2, '0')}",
        chalakim = molad.moladChalakim,
        kiddushLevanaStart = Date(start.toEpochMilliseconds()),
        kiddushLevanaEnd = Date(end.toEpochMilliseconds()),
    )
}
