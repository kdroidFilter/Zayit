package io.github.kdroidfilter.seforimapp.luach

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarMonthTest {
    // 30 September 2026 is 19 Tishrei 5787; 1 Tishrei (Rosh Hashana) is 12 September
    private val month = calendarMonth(LocalDate.of(2026, 9, 30), inIsrael = true)
    private val days = month.weeks.flatten().filterNotNull()

    @Test
    fun `the month runs from 1 Tishrei to its last day, one cell a day`() {
        assertEquals(LocalDate.of(2026, 9, 12), month.first)
        assertEquals(30, days.size)
        assertEquals(month.last, days.last().date)
        assertEquals("א׳", days.first().hebrewDay)
    }

    @Test
    fun `weeks are Sunday-first and whole`() {
        assertTrue(month.weeks.all { it.size == 7 })
        month.weeks.forEach { week ->
            week.forEachIndexed { column, day ->
                if (day != null) assertEquals((day.date.dayOfWeek.value % 7), column)
            }
        }
        assertTrue(days.filter { it.isShabbat }.all { it.date.dayOfWeek == DayOfWeek.SATURDAY })
    }

    @Test
    fun `festivals and rosh chodesh are tagged`() {
        assertTrue(days.first().tag.isNotBlank()) // Rosh Hashana
        assertTrue(days.first { it.date == LocalDate.of(2026, 9, 21) }.tag.isNotBlank()) // Yom Kippur
        assertEquals("", days.first { it.date == LocalDate.of(2026, 9, 15) }.tag) // 4 Tishrei, after Tzom Gedalia
        // Cheshvan opens on Rosh Chodesh
        val cheshvan = calendarMonth(month.last.plusDays(1), inIsrael = true)
        assertTrue(
            cheshvan.weeks
                .flatten()
                .filterNotNull()
                .first()
                .tag
                .isNotBlank(),
        )
    }

    @Test
    fun `a civil month runs from its 1st, Sunday-first, with its Hebrew days`() {
        val october = civilMonth(LocalDate.of(2026, 10, 15), inIsrael = true)
        assertEquals("אוקטובר 2026", october.title)
        assertEquals("כ׳ תשרי – כ׳ חשון", october.subtitle)
        // 1 October 2026 is a Thursday
        assertEquals(listOf(null, null, null, null, 1, 2, 3), october.weeks.first().map { it?.gregorianDay })
        assertEquals(31, october.weeks.flatten().count { it != null })
        assertEquals("כ׳", october.weeks.first()[4]?.hebrewDay)
    }
}
