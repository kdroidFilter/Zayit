package io.github.kdroidfilter.seforimapp.hebrewcalendar

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewDateFormatter
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate
import java.time.YearMonth

/**
 * Converts a Gregorian [LocalDate] to a [HebrewYearMonth].
 */
fun hebrewYearMonthFromLocalDate(date: LocalDate): HebrewYearMonth {
    val jewishDate = JewishDate(date.toKotlinLocalDate())
    return HebrewYearMonth(
        year = jewishDate.jewishYear.toInt(),
        month = jewishDate.jewishMonth.value,
    )
}

/**
 * Returns the previous Hebrew month.
 */
fun previousHebrewYearMonth(yearMonth: HebrewYearMonth): HebrewYearMonth {
    val jewishDate = JewishDate(yearMonth.year, yearMonth.month, 1)
    jewishDate.back()
    return HebrewYearMonth(
        year = jewishDate.jewishYear.toInt(),
        month = jewishDate.jewishMonth.value,
    )
}

/**
 * Returns the next Hebrew month.
 */
fun nextHebrewYearMonth(yearMonth: HebrewYearMonth): HebrewYearMonth {
    val jewishDate = JewishDate(yearMonth.year, yearMonth.month, 1)
    jewishDate.forward(DateTimeUnit.MONTH, 1)
    return HebrewYearMonth(
        year = jewishDate.jewishYear.toInt(),
        month = jewishDate.jewishMonth.value,
    )
}

/**
 * The same month in Hebrew [year]; Adar II (13) becomes Adar in a year without it.
 */
fun HebrewYearMonth.inYear(year: Int): HebrewYearMonth {
    val leap = JewishDate(year, HebrewMonth.TISHREI, 1).isJewishLeapYear
    val adarII = HebrewMonth.ADAR_II.value
    return HebrewYearMonth(year = year, month = if (month == adarII && !leap) HebrewMonth.ADAR.value else month)
}

/**
 * Formats a [HebrewYearMonth] as a title string (e.g., "תשרי תשפ״ה").
 */
fun formatHebrewMonthTitle(
    yearMonth: HebrewYearMonth,
    formatter: HebrewDateFormatter,
): String {
    val jewishDate = JewishDate(yearMonth.year, yearMonth.month, 1)
    val monthName = formatter.formatMonth(jewishDate)
    val yearName = formatter.formatHebrewNumber(jewishDate.jewishYear)
    return "$monthName $yearName"
}

/**
 * Builds a grid of weeks for a Gregorian month.
 * Each week is a list of 7 days (Sunday to Saturday).
 * Days outside the month are null.
 */
internal fun buildMonthGrid(month: YearMonth): List<List<LocalDate?>> {
    val firstOfMonth = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val startOffset = firstOfMonth.dayOfWeek.value % 7 // Sunday = 0

    val cells = ArrayList<LocalDate?>(startOffset + daysInMonth + 7)
    repeat(startOffset) { cells.add(null) }
    for (day in 1..daysInMonth) {
        cells.add(month.atDay(day))
    }
    while (cells.size % 7 != 0) {
        cells.add(null)
    }
    return cells.chunked(7)
}

/**
 * Builds a grid of weeks for a Hebrew month.
 * Each week is a list of 7 days (Sunday to Saturday).
 * Days outside the month are null.
 */
internal fun buildHebrewMonthGrid(
    yearMonth: HebrewYearMonth,
    formatter: HebrewDateFormatter,
): List<List<HebrewGridDay?>> {
    val firstOfMonth = JewishDate(yearMonth.year, yearMonth.month, 1)

    val startOffset = firstOfMonth.gregorianLocalDate.dayOfWeek.isoDayNumber % 7 // Sunday = 0
    val daysInMonth = firstOfMonth.daysInJewishMonth

    val cells = ArrayList<HebrewGridDay?>(startOffset + daysInMonth + 7)
    repeat(startOffset) { cells.add(null) }

    val current = JewishDate(yearMonth.year, yearMonth.month, 1)
    for (day in 1..daysInMonth) {
        cells.add(
            HebrewGridDay(
                localDate = current.gregorianLocalDate.toJavaLocalDate(),
                label = formatter.formatHebrewNumber(day),
            ),
        )
        if (day != daysInMonth) {
            current.forward(DateTimeUnit.DAY, 1)
        }
    }

    while (cells.size % 7 != 0) {
        cells.add(null)
    }
    return cells.chunked(7)
}
