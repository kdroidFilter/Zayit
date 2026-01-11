package io.github.kdroidfilter.seforimapp.hebrewcalendar

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar

/**
 * Converts a Gregorian [LocalDate] to a [HebrewYearMonth].
 */
fun hebrewYearMonthFromLocalDate(date: LocalDate): HebrewYearMonth {
    val jewishDate = JewishDate(date)
    return HebrewYearMonth(
        year = jewishDate.jewishYear,
        month = jewishDate.jewishMonth,
    )
}

/**
 * Returns the previous Hebrew month.
 */
fun previousHebrewYearMonth(yearMonth: HebrewYearMonth): HebrewYearMonth {
    val jewishDate = JewishDate()
    jewishDate.setJewishDate(yearMonth.year, yearMonth.month, 1)
    jewishDate.back()
    return HebrewYearMonth(
        year = jewishDate.jewishYear,
        month = jewishDate.jewishMonth,
    )
}

/**
 * Returns the next Hebrew month.
 */
fun nextHebrewYearMonth(yearMonth: HebrewYearMonth): HebrewYearMonth {
    val jewishDate = JewishDate()
    jewishDate.setJewishDate(yearMonth.year, yearMonth.month, 1)
    jewishDate.forward(Calendar.MONTH, 1)
    return HebrewYearMonth(
        year = jewishDate.jewishYear,
        month = jewishDate.jewishMonth,
    )
}

/**
 * Formats a [HebrewYearMonth] as a title string (e.g., "תשרי תשפ״ה").
 */
fun formatHebrewMonthTitle(
    yearMonth: HebrewYearMonth,
    formatter: HebrewDateFormatter,
): String {
    val jewishDate = JewishDate()
    jewishDate.setJewishDate(yearMonth.year, yearMonth.month, 1)
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
    val firstOfMonth = JewishDate()
    firstOfMonth.setJewishDate(yearMonth.year, yearMonth.month, 1)

    val startOffset = (firstOfMonth.dayOfWeek - 1).coerceIn(0, 6) // Sunday = 0
    val daysInMonth = firstOfMonth.daysInJewishMonth

    val cells = ArrayList<HebrewGridDay?>(startOffset + daysInMonth + 7)
    repeat(startOffset) { cells.add(null) }

    val current = JewishDate()
    current.setJewishDate(yearMonth.year, yearMonth.month, 1)
    for (day in 1..daysInMonth) {
        cells.add(
            HebrewGridDay(
                localDate = current.localDate,
                label = formatter.formatHebrewNumber(day),
            ),
        )
        if (day != daysInMonth) {
            current.forward(Calendar.DATE, 1)
        }
    }

    while (cells.size % 7 != 0) {
        cells.add(null)
    }
    return cells.chunked(7)
}
