package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar.Parsha
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShnayimMikraTest {
    private fun parsha(
        date: String,
        inIsrael: Boolean = false,
    ) = mikraWeek(LocalDate.parse(date), inIsrael).parsha

    @Test
    fun `the week's parsha, in Israel and abroad`() {
        // Abroad, Shabbat 27.4.2019 was Pesach's eighth day: Israel ran a week ahead until they were joined again
        assertEquals(Parsha.EMOR, parsha("2019-05-06", inIsrael = true))
        assertEquals(Parsha.KEDOSHIM, parsha("2019-05-06"))
        // Pesach: its eighth day reads none abroad, אחרי מות in Israel
        assertEquals(Parsha.ACHREI_MOS, parsha("2019-04-22", inIsrael = true))
        assertEquals(Parsha.ACHREI_MOS, parsha("2019-04-22"))
        assertEquals(Parsha.KEDOSHIM, parsha("2019-04-29", inIsrael = true))
        // Shabbat HaGadol
        assertEquals(Parsha.TZAV, parsha("2025-04-08"))
        // Rosh Hashana on Shabbat reads none: on to the next one
        assertEquals(LocalDate.parse("2023-09-23"), mikraWeek(LocalDate.parse("2023-09-12"), false).shabbat)
        assertEquals(Parsha.HAAZINU, parsha("2023-09-12"))
        // Read together in 5786, apart in 5785
        assertEquals(Parsha.VAYAKHEL_PEKUDEI, parsha("2026-03-10"))
        assertEquals(Parsha.VAYAKHEL, parsha("2025-03-18"))
        // Sukkot: וזאת הברכה until Simchat Torah, then בראשית
        assertEquals(Parsha.VZOS_HABERACHA, parsha("2026-10-01", inIsrael = true))
        assertEquals(Parsha.VZOS_HABERACHA, parsha("2026-10-04"))
        assertEquals(Parsha.BERESHIS, parsha("2026-10-04", inIsrael = true))
        // Yom Kippur on Shabbat (5785): האזינו on Shabbat Shuva, then וזאת הברכה from 4 Tishrei
        assertEquals(Parsha.HAAZINU, parsha("2024-10-03"))
        assertEquals(Parsha.VZOS_HABERACHA, parsha("2024-10-06"))
        // The year it is read in: וזאת הברכה on Sukkot is the new year's, as is the בראשית after it
        assertEquals(5787, mikraWeek(LocalDate.parse("2026-10-01"), true).year)
        assertEquals(5787, mikraWeek(LocalDate.parse("2026-10-05"), true).year)
        assertEquals(5786, mikraWeek(LocalDate.parse("2026-09-01"), true).year)
    }

    @Test
    fun `the aliyos of each parsha go forward, a double one's otherwise divided`() {
        for (parsha in Parsha.entries.filter { it != Parsha.NONE && parshaPlace(it) != null }) {
            val verses = aliyosOf(parsha).flatMap { (from, to) -> listOf(from, to) }
            assertEquals(14, verses.size, "$parsha")
            assertTrue(verses.zipWithNext().all { (a, b) -> compareValuesBy(a, b, Verse::chapter, Verse::verse) <= 0 }, "$parsha")
        }
        assertEquals(LibraryPlace("שמות", ref = "שמות לה, ל", endRefs = listOf("שמות לז, טז")), aliyaPlace(Parsha.VAYAKHEL_PEKUDEI, 1))
        assertEquals("ב, ד–יט", aliyaRange(Verse(2, 4), Verse(2, 19)))
    }

    @Test
    fun `the limud card's parsha skips a Shabbat of Yom Tov`() {
        // Shabbat 3.10.2026 is Shemini Atzeret
        assertEquals("וזאת הברכה", limudOfDay(LocalDate.parse("2026-10-01"), inIsrael = true, setOf(Limud.PARSHA)).single().value)
        assertEquals("בראשית", limudOfDay(LocalDate.parse("2026-10-05"), inIsrael = false, setOf(Limud.PARSHA)).single().value)
    }
}
