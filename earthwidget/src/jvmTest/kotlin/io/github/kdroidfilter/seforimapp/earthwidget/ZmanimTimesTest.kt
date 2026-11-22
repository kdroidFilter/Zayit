package io.github.kdroidfilter.seforimapp.earthwidget

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import java.time.LocalDate
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.minutes

class ZmanimTimesTest {
    private val jerusalem = EarthWidgetLocation(31.7683, 35.2137, 800.0, TimeZone.getTimeZone("Asia/Jerusalem"))

    @Test
    fun `both luachs give every zman of the day in order`() {
        for (opinion in ZmanimOpinion.entries) {
            val t = computeZmanimTimes(LocalDate.of(2026, 9, 30), jerusalem, opinion, inIsrael = true)
            val day =
                listOf(
                    t.alosHashachar,
                    t.sunrise,
                    t.sofZmanShmaMga,
                    t.sofZmanShmaGra,
                    t.chatzosHayom,
                    t.minchaGedola,
                    t.minchaKetana,
                    t.plagHamincha,
                    t.sunset,
                    t.tzais,
                    t.tzaisRabbeinuTam,
                ).map { requireNotNull(it) { "$opinion" }.time }
            assertEquals(day.sorted(), day, "$opinion")
        }
    }

    @Test
    fun `luachs really differ`() {
        val date = LocalDate.of(2026, 9, 30)
        val standard = computeZmanimTimes(date, jerusalem, ZmanimOpinion.DEFAULT)
        val ohr = computeZmanimTimes(date, jerusalem, ZmanimOpinion.OHR_HACHAIM)
        assertNotEquals(standard.alosHashachar, ohr.alosHashachar)
        assertNotEquals(standard.tzais, ohr.tzais)
    }

    @Test
    fun `previous hebrew month crosses the year and the leap Adar`() {
        val tishrei = JewishCalendar(5787, HebrewMonth.TISHREI, 10)
        goToPreviousHebrewMonth(tishrei)
        assertEquals(Triple(5786L, HebrewMonth.ELUL, 1), Triple(tishrei.jewishYear, tishrei.jewishMonth, tishrei.jewishDayOfMonth))

        val nissan = JewishCalendar(5787, HebrewMonth.NISSAN, 5) // 5787 is a leap year
        goToPreviousHebrewMonth(nissan)
        assertEquals(HebrewMonth.ADAR_II, nissan.jewishMonth)
    }
}

class OhrHaChaimPrintedTest {
    // Printed luach אור החיים, Tel Aviv, Friday 2 October 2026 (calendar.2net.co.il, methodid=3)
    @Test
    fun `matches the printed Tel Aviv Shabbat`() {
        val telAviv = EarthWidgetLocation(32.0853, 34.7818, 5.0, TimeZone.getTimeZone("Asia/Jerusalem"))
        val c = zmanimCalendar(LocalDate.of(2026, 10, 2), telAviv, ZmanimOpinion.OHR_HACHAIM, inIsrael = true)
        c.ateretTorahSunsetOffset = 30.0

        fun hm(instant: kotlin.time.Instant?) =
            java.time.Instant
                .ofEpochMilli(requireNotNull(instant).toEpochMilliseconds())
                .atZone(java.time.ZoneId.of("Asia/Jerusalem"))
                .toLocalTime()
                .withSecond(0)
                .withNano(0)
                .toString()

        assertEquals("18:05", hm(c.ohrHaChaimSunset?.minus(20.minutes)))
        assertEquals("18:55", hm(c.tzaisAteretTorah.momentOfOccurrence))
        assertEquals("19:36", hm(c.tzais72Zmanis.momentOfOccurrence))
    }
}
