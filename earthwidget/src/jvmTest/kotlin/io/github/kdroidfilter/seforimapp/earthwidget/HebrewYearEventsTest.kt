package io.github.kdroidfilter.seforimapp.earthwidget

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HebrewYearEventsTest {
    private val israel = computeHebrewYearEvents(5786, inIsrael = true)
    private val diaspora = computeHebrewYearEvents(5786, inIsrael = false)

    private fun List<SolarEvent>.startsOf(category: SolarEventCategory) = filter { it.category == category }.map { it.start }

    @Test
    fun yomTovimOf5786() {
        val succos = israel.first { it.start == LocalDate.of(2025, 10, 7) }
        // Sukkot runs with its Chol HaMoed and Hoshana Rabba as one event
        assertEquals(LocalDate.of(2025, 10, 13), succos.end)
        assertEquals(
            listOf(
                LocalDate.of(2025, 9, 23), // Rosh Hashana
                LocalDate.of(2025, 10, 2), // Yom Kippur
                LocalDate.of(2025, 10, 7), // Sukkot
                LocalDate.of(2025, 10, 14), // Shemini Atzeret
                LocalDate.of(2026, 4, 2), // Pesach
                LocalDate.of(2026, 5, 22), // Shavuot
            ),
            israel.startsOf(SolarEventCategory.YomTov),
        )
        assertEquals(LocalDate.of(2025, 9, 24), israel.first().end)
    }

    @Test
    fun diasporaAddsTheSecondDays() {
        val pesach = diaspora.first { it.start == LocalDate.of(2026, 4, 2) }
        assertEquals(LocalDate.of(2026, 4, 9), pesach.end) // 22 Nisan
        assertEquals(LocalDate.of(2026, 4, 8), israel.first { it.start == LocalDate.of(2026, 4, 2) }.end) // 21 Nisan
    }

    @Test
    fun fastsRabbinicAndRoshChodesh() {
        assertEquals(5, israel.startsOf(SolarEventCategory.Fast).size)
        val chanukah = israel.first { it.category == SolarEventCategory.Rabbinic }
        assertEquals(LocalDate.of(2025, 12, 15), chanukah.start)
        assertEquals("חנוכה", chanukah.name)
        // A regular year: Rosh Chodesh for every month but Tishrei, Tevet's included though it falls in Chanukah
        assertEquals(11, israel.startsOf(SolarEventCategory.RoshChodesh).size)
    }
}
