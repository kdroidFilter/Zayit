package io.github.kdroidfilter.seforimapp.earthwidget

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KiddushLevanaWindowTest {
    private fun noon(date: LocalDate) = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun midMonthFallsInTheWindow() {
        // 10 Tishrei 5787
        val date = LocalDate.of(2026, 9, 21)
        val (start, end) =
            kiddushLevanaWindow(date, KiddushLevanaEarliestOpinion.DAYS_3, KiddushLevanaLatestOpinion.BETWEEN_MOLDOS)
        assertTrue(noon(date) in start..end)
        // 3 days after the molad → half a lunation after it
        assertEquals(((11L * 24 + 18) * 60 + 22) * 60_000L + 1_667L, end - start)
    }

    @Test
    fun beforeTheMoladItIsLastMonthsWindow() {
        // 29 Elul 5786: this month's molad hasn't come yet, so the window is Elul's, long over
        val date = LocalDate.of(2026, 9, 11)
        val (_, end) = kiddushLevanaWindow(date, KiddushLevanaEarliestOpinion.DAYS_7, KiddushLevanaLatestOpinion.DAYS_15)
        assertTrue(end < noon(date))
    }
}
