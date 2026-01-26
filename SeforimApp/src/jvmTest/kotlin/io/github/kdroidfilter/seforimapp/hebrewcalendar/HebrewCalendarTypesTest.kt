package io.github.kdroidfilter.seforimapp.hebrewcalendar

import io.github.kdroidfilter.seforimapp.hebrewcalendar.CalendarMode
import io.github.kdroidfilter.seforimapp.hebrewcalendar.HebrewYearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HebrewCalendarTypesTest {
    // CalendarMode tests
    @Test
    fun `CalendarMode has GREGORIAN value`() {
        val mode = CalendarMode.GREGORIAN
        assertEquals(CalendarMode.GREGORIAN, mode)
    }

    @Test
    fun `CalendarMode has HEBREW value`() {
        val mode = CalendarMode.HEBREW
        assertEquals(CalendarMode.HEBREW, mode)
    }

    @Test
    fun `CalendarMode values are distinct`() {
        assertNotEquals(CalendarMode.GREGORIAN, CalendarMode.HEBREW)
    }

    @Test
    fun `CalendarMode entries contains all modes`() {
        val entries = CalendarMode.entries
        assertEquals(2, entries.size)
        assertTrue(entries.contains(CalendarMode.GREGORIAN))
        assertTrue(entries.contains(CalendarMode.HEBREW))
    }

    @Test
    fun `CalendarMode name returns correct string`() {
        assertEquals("GREGORIAN", CalendarMode.GREGORIAN.name)
        assertEquals("HEBREW", CalendarMode.HEBREW.name)
    }

    @Test
    fun `CalendarMode valueOf returns correct mode`() {
        assertEquals(CalendarMode.GREGORIAN, CalendarMode.valueOf("GREGORIAN"))
        assertEquals(CalendarMode.HEBREW, CalendarMode.valueOf("HEBREW"))
    }

    // HebrewYearMonth tests
    @Test
    fun `HebrewYearMonth has correct year`() {
        val yearMonth = HebrewYearMonth(year = 5785, month = 1)
        assertEquals(5785, yearMonth.year)
    }

    @Test
    fun `HebrewYearMonth has correct month`() {
        val yearMonth = HebrewYearMonth(year = 5785, month = 7)
        assertEquals(7, yearMonth.month)
    }

    @Test
    fun `HebrewYearMonth with Tishrei (month 1)`() {
        val yearMonth = HebrewYearMonth(year = 5785, month = 1)
        assertEquals(1, yearMonth.month)
    }

    @Test
    fun `HebrewYearMonth with Adar II (month 13) in leap year`() {
        val yearMonth = HebrewYearMonth(year = 5784, month = 13)
        assertEquals(13, yearMonth.month)
    }

    @Test
    fun `HebrewYearMonth equality`() {
        val ym1 = HebrewYearMonth(5785, 1)
        val ym2 = HebrewYearMonth(5785, 1)
        assertEquals(ym1, ym2)
    }

    @Test
    fun `HebrewYearMonth inequality by year`() {
        val ym1 = HebrewYearMonth(5785, 1)
        val ym2 = HebrewYearMonth(5784, 1)
        assertNotEquals(ym1, ym2)
    }

    @Test
    fun `HebrewYearMonth inequality by month`() {
        val ym1 = HebrewYearMonth(5785, 1)
        val ym2 = HebrewYearMonth(5785, 2)
        assertNotEquals(ym1, ym2)
    }

    @Test
    fun `HebrewYearMonth hashCode consistency`() {
        val ym1 = HebrewYearMonth(5785, 1)
        val ym2 = HebrewYearMonth(5785, 1)
        assertEquals(ym1.hashCode(), ym2.hashCode())
    }

    @Test
    fun `HebrewYearMonth copy creates new instance`() {
        val original = HebrewYearMonth(5785, 1)
        val copy = original.copy()
        assertEquals(original, copy)
    }

    @Test
    fun `HebrewYearMonth copy with modified year`() {
        val original = HebrewYearMonth(5785, 1)
        val copy = original.copy(year = 5786)
        assertEquals(5786, copy.year)
        assertEquals(1, copy.month)
    }

    @Test
    fun `HebrewYearMonth copy with modified month`() {
        val original = HebrewYearMonth(5785, 1)
        val copy = original.copy(month = 7)
        assertEquals(5785, copy.year)
        assertEquals(7, copy.month)
    }

    @Test
    fun `HebrewYearMonth toString contains year and month`() {
        val yearMonth = HebrewYearMonth(5785, 7)
        val str = yearMonth.toString()
        assertTrue(str.contains("5785"))
        assertTrue(str.contains("7"))
    }

    @Test
    fun `HebrewYearMonth component1 returns year`() {
        val yearMonth = HebrewYearMonth(5785, 7)
        val (year, _) = yearMonth
        assertEquals(5785, year)
    }

    @Test
    fun `HebrewYearMonth component2 returns month`() {
        val yearMonth = HebrewYearMonth(5785, 7)
        val (_, month) = yearMonth
        assertEquals(7, month)
    }
}
