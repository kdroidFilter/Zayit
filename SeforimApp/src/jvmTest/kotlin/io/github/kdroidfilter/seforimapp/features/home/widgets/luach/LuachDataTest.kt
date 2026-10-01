package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar.Parsha
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetLocation
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaEarliestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.KiddushLevanaLatestOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.ZmanimOpinion
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LuachDataTest {
    private val jerusalem = EarthWidgetLocation(31.7683, 35.2137, 800.0, TimeZone.getTimeZone("Asia/Jerusalem"))

    // 1 October 2026 is 20 Tishrei 5787, Chol Hamoed Sukkot; Shemini Atzeret (Israel) is Shabbat 3 October
    private val cholHamoed = LocalDate.of(2026, 10, 1)

    @Test
    fun `parshiyos land on their Chumash and its Parasha TOC entry`() {
        assertEquals(LibraryPlace("בראשית", parashaIndex = 0), parshaPlace(Parsha.BERESHIS))
        assertEquals(LibraryPlace("שמות", parashaIndex = 0), parshaPlace(Parsha.SHEMOS))
        assertEquals(LibraryPlace("במדבר", parashaIndex = 5), parshaPlace(Parsha.CHUKAS_BALAK))
        assertEquals(LibraryPlace("דברים", parashaIndex = 10), parshaPlace(Parsha.VZOS_HABERACHA))
        assertNull(parshaPlace(Parsha.NONE))
    }

    @Test
    fun `a daf opens at its TOC heading, written without geresh`() {
        assertEquals("דף כא.", bavliPlace(0, "ברכות", 21).heading)
        assertEquals("דף טו.", bavliPlace(0, "ברכות", 15).heading)
        assertEquals(LibraryPlace("תלמוד ירושלמי שקלים"), bavliPlace(4, "שקלים", 3))
    }

    @Test
    fun `chol hamoed sukkot has hallel shalem and yaaleh veyavo, no tachanun`() {
        val lines = tefilaOfDay(cholHamoed, inIsrael = true).associate { it.label to it.value }
        assertEquals("הלל שלם", lines["הלל"])
        assertEquals("אומרים", lines["יעלה ויבוא"])
        assertEquals("אין אומרים", lines["תחנון"])
        assertNull(lines["ספירת העומר"])
        assertEquals("מוריד הטל", lines["גבורות"])
        assertEquals("ותן ברכה", lines["ברכת השנים"])
    }

    @Test
    fun `mashiv haruach is said in the winter`() {
        // 1 Shevat 5787
        val winter = tefilaOfDay(LocalDate.of(2027, 1, 9), inIsrael = true).associate { it.label to it.value }
        assertEquals("משיב הרוח ומוריד הגשם", winter["גבורות"])
        assertEquals("ותן טל ומטר לברכה", winter["ברכת השנים"])
        // Shemini Atzeret: from musaf
        assertEquals("מוריד הטל · במוסף משיב הרוח", tefilaOfDay(LocalDate.of(2026, 10, 3), true).first().value)
    }

    @Test
    fun `shemini atzeret on shabbat starts with candles on friday and ends with havdala`() {
        val atzeret = events.first { it.date == LocalDate.of(2026, 10, 3) }
        assertEquals(listOf("הדלקת נרות", "יציאה"), atzeret.times.map { it.first })
        assertTrue(atzeret.times.all { it.second != null })
    }

    @Test
    fun `next zmanim are the coming ones, in order`() {
        val noon = Date.from(cholHamoed.atTime(12, 30).atZone(ZoneId.of("Asia/Jerusalem")).toInstant())
        assertEquals(3, next.size)
        assertTrue(next.all { it.time.after(noon) })
        assertEquals(next.sortedBy { it.time }, next)
        assertEquals("מנחה גדולה", next.first().name)
    }

    @Test
    fun `kiddush levana window is still open or ahead`() {
        val now = Date.from(cholHamoed.atTime(12, 0).atZone(ZoneId.of("Asia/Jerusalem")).toInstant())
        val molad =
            moladInfo(now, cholHamoed, true, KiddushLevanaEarliestOpinion.DAYS_3, KiddushLevanaLatestOpinion.BETWEEN_MOLDOS)
        assertNotNull(molad.month)
        assertTrue(molad.kiddushLevanaEnd.after(now))
        assertTrue(molad.kiddushLevanaStart.before(molad.kiddushLevanaEnd))
    }
}
