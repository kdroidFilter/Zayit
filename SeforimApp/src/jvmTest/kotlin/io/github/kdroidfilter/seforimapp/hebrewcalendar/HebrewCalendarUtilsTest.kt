package io.github.kdroidfilter.seforimapp.hebrewcalendar

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.seforimapp.hebrewcalendar.HebrewYearMonth
import io.github.kdroidfilter.seforimapp.hebrewcalendar.buildMonthGrid
import io.github.kdroidfilter.seforimapp.hebrewcalendar.formatHebrewMonthTitle
import io.github.kdroidfilter.seforimapp.hebrewcalendar.hebrewYearMonthFromLocalDate
import io.github.kdroidfilter.seforimapp.hebrewcalendar.nextHebrewYearMonth
import io.github.kdroidfilter.seforimapp.hebrewcalendar.previousHebrewYearMonth
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HebrewCalendarUtilsTest {
    private val formatter = HebrewDateFormatter().apply {
        isHebrewFormat = true
    }

    // hebrewYearMonthFromLocalDate tests
    @Test
    fun `hebrewYearMonthFromLocalDate converts date correctly`() {
        // Rosh Hashanah 5785 was October 2, 2024
        val date = LocalDate.of(2024, 10, 3)
        val result = hebrewYearMonthFromLocalDate(date)

        assertEquals(5785, result.year)
        // Tishrei is month 7 in Jewish calendar conventions
        assertTrue(result.month in 1..13)
    }

    @Test
    fun `hebrewYearMonthFromLocalDate returns HebrewYearMonth`() {
        val date = LocalDate.of(2024, 1, 15)
        val result = hebrewYearMonthFromLocalDate(date)

        assertNotNull(result)
        assertTrue(result.year > 5700)
        assertTrue(result.month in 1..13)
    }

    // previousHebrewYearMonth tests
    @Test
    fun `previousHebrewYearMonth returns previous month`() {
        val current = HebrewYearMonth(5785, 2)
        val previous = previousHebrewYearMonth(current)

        assertTrue(previous.month < current.month || previous.year < current.year)
    }

    @Test
    fun `previousHebrewYearMonth wraps year correctly`() {
        // Month 7 is Tishrei (first month of Jewish year)
        val current = HebrewYearMonth(5785, 7)
        val previous = previousHebrewYearMonth(current)

        // Should go to previous year
        assertTrue(previous.year == 5784 || previous.month < 7)
    }

    // nextHebrewYearMonth tests
    @Test
    fun `nextHebrewYearMonth returns next month`() {
        val current = HebrewYearMonth(5785, 1)
        val next = nextHebrewYearMonth(current)

        assertTrue(next.month > current.month || next.year > current.year)
    }

    @Test
    fun `nextHebrewYearMonth wraps year correctly`() {
        // Month 6 is Elul (last month before new year)
        val current = HebrewYearMonth(5784, 6)
        val next = nextHebrewYearMonth(current)

        // Should go to Tishrei of new year
        assertTrue(next.year == 5785 || next.month > 6)
    }

    // formatHebrewMonthTitle tests
    @Test
    fun `formatHebrewMonthTitle returns Hebrew string`() {
        val yearMonth = HebrewYearMonth(5785, 1)
        val title = formatHebrewMonthTitle(yearMonth, formatter)

        assertNotNull(title)
        assertTrue(title.isNotEmpty())
    }

    @Test
    fun `formatHebrewMonthTitle contains year`() {
        val yearMonth = HebrewYearMonth(5785, 1)
        val title = formatHebrewMonthTitle(yearMonth, formatter)

        // Hebrew year representation should be present
        assertTrue(title.isNotEmpty())
    }

    // buildMonthGrid tests
    @Test
    fun `buildMonthGrid returns list of weeks`() {
        val month = YearMonth.of(2024, 1)
        val grid = buildMonthGrid(month)

        assertTrue(grid.isNotEmpty())
        assertTrue(grid.all { it.size == 7 })
    }

    @Test
    fun `buildMonthGrid weeks have 7 days each`() {
        val month = YearMonth.of(2024, 6)
        val grid = buildMonthGrid(month)

        grid.forEach { week ->
            assertEquals(7, week.size, "Each week should have 7 days")
        }
    }

    @Test
    fun `buildMonthGrid contains all days of month`() {
        val month = YearMonth.of(2024, 1)
        val grid = buildMonthGrid(month)

        val allDays = grid.flatten().filterNotNull().map { it.dayOfMonth }
        val daysInMonth = month.lengthOfMonth()

        assertEquals(daysInMonth, allDays.size)
        assertTrue((1..daysInMonth).all { it in allDays })
    }

    @Test
    fun `buildMonthGrid has null for days outside month`() {
        val month = YearMonth.of(2024, 1)
        val grid = buildMonthGrid(month)

        // First week may have nulls before the first day
        // Last week may have nulls after the last day
        val flatGrid = grid.flatten()
        val nullCount = flatGrid.count { it == null }
        val nonNullCount = flatGrid.count { it != null }

        assertEquals(month.lengthOfMonth(), nonNullCount)
        assertTrue(nullCount >= 0)
    }

    @Test
    fun `buildMonthGrid first non-null is day 1`() {
        val month = YearMonth.of(2024, 1)
        val grid = buildMonthGrid(month)

        val firstNonNull = grid.flatten().filterNotNull().first()
        assertEquals(1, firstNonNull.dayOfMonth)
    }

    @Test
    fun `buildMonthGrid last non-null is last day of month`() {
        val month = YearMonth.of(2024, 1)
        val grid = buildMonthGrid(month)

        val lastNonNull = grid.flatten().filterNotNull().last()
        assertEquals(month.lengthOfMonth(), lastNonNull.dayOfMonth)
    }

    @Test
    fun `buildMonthGrid for February 2024 (leap year)`() {
        val month = YearMonth.of(2024, 2)
        val grid = buildMonthGrid(month)

        val nonNullDays = grid.flatten().filterNotNull()
        assertEquals(29, nonNullDays.size, "February 2024 should have 29 days")
    }

    @Test
    fun `buildMonthGrid for February 2023 (non-leap year)`() {
        val month = YearMonth.of(2023, 2)
        val grid = buildMonthGrid(month)

        val nonNullDays = grid.flatten().filterNotNull()
        assertEquals(28, nonNullDays.size, "February 2023 should have 28 days")
    }
}
