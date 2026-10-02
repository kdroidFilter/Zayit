package io.github.kdroidfilter.seforimapp.earthwidget

import java.util.Date
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class HebrewDayCacheTest {
    /** The day-cached Hebrew calendar gives what the per-instant one did, through a year of 37-minute steps. */
    @Test
    fun matchesThePerInstantCalendar() {
        for (zone in listOf("Asia/Jerusalem", "America/New_York")) {
            val tz = TimeZone.getTimeZone(zone)
            var t = 1_767_225_600_000L // 2026-01-01
            val end = t + 366L * 86_400_000L
            while (t < end) {
                val day = hebrewDayAt(t, tz)
                val calendar = jewishCalendarAt(Date(t), tz)
                assertEquals(calendar.jewishDayOfMonth, day.dayOfMonth, "day at $t $zone")
                assertEquals(calendar.daysInJewishMonth, day.daysInMonth, "month at $t $zone")
                assertEquals(computeHalakhicPhaseAngle(Date(t), tz), day.phaseAngleAt(t), 1e-3f, "phase at $t $zone")
                t += 37L * 60_000L
            }
        }
    }
}
