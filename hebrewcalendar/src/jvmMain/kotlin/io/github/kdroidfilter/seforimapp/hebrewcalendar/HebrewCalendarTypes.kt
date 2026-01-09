package io.github.kdroidfilter.seforimapp.hebrewcalendar

import java.time.LocalDate

/**
 * Calendar display mode - Hebrew or Gregorian.
 */
enum class CalendarMode {
    GREGORIAN,
    HEBREW,
}

/**
 * Represents a Hebrew year and month.
 *
 * @property year Hebrew year (e.g., 5785)
 * @property month Hebrew month (1-13, where 13 is Adar II in leap years)
 */
data class HebrewYearMonth(
    val year: Int,
    val month: Int,
)

/**
 * Represents a day in a Hebrew calendar grid.
 *
 * @property localDate The corresponding Gregorian date
 * @property label The Hebrew numeral label for this day
 */
internal data class HebrewGridDay(
    val localDate: LocalDate,
    val label: String,
)
