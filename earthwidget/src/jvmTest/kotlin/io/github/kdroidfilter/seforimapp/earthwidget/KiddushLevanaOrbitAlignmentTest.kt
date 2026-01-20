package io.github.kdroidfilter.seforimapp.earthwidget

import kotlin.test.Test
import kotlin.test.assertEquals

class KiddushLevanaOrbitAlignmentTest {
    @Test
    fun daysSinceMoladAlignWithOrbitLabels() {
        val daysInMonth = 30
        val stepDegrees = 360f / daysInMonth.toFloat()
        // Matches ORBIT_DAY_LABEL_START_DEGREES in EarthWidgetZmanimView.
        val baseDegrees = 90f

        val startDegrees = orbitDegreesForDaysSinceMolad(7.0, daysInMonth)
        val endDegrees = orbitDegreesForDaysSinceMolad(15.0, daysInMonth)

        val expectedStart = normalizeForTest(baseDegrees + (7 - 1) * stepDegrees)
        val expectedEnd = normalizeForTest(baseDegrees + (15 - 1) * stepDegrees)

        assertEquals(expectedStart, startDegrees, 0.0001f)
        assertEquals(expectedEnd, endDegrees, 0.0001f)
    }
}

private fun normalizeForTest(angleDegrees: Float): Float = ((angleDegrees % 360f) + 360f) % 360f
