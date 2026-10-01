package io.github.kdroidfilter.seforimapp.features.home.widgets.luach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** limud.pb holds every schedule whole: each cycle's days, each month of the Kitzur. */
class LimudSchedulesTest {
    @Test
    fun `limud pb holds every schedule`() {
        val schedules = limudSchedules
        assertEquals(339, schedules.seferHamitzvos.size)
        assertEquals(1719, schedules.aruchHashulchan.size)
        // Nisan to Adar, then Adar II
        assertEquals((1..13).toList(), schedules.kitzur.map { it.month }.sorted())
        assertTrue(schedules.kitzur.all { it.readings.size in 29..30 })
        assertTrue(schedules.chofetzChaimSimple.isNotEmpty() && schedules.chofetzChaimLeap.isNotEmpty())
        assertTrue(schedules.shmirasHalashon.all { it.book in 1..2 && it.month in 1..13 && it.leapMonth in 1..13 })
    }
}
