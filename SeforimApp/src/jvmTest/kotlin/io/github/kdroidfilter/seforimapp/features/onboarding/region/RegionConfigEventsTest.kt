package io.github.kdroidfilter.seforimapp.features.onboarding.region

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RegionConfigEventsTest {
    @Test
    fun `SelectCountry stores index`() {
        val event = RegionConfigEvents.SelectCountry(5)
        assertEquals(5, event.index)
    }

    @Test
    fun `SelectCountry implements RegionConfigEvents`() {
        val event: RegionConfigEvents = RegionConfigEvents.SelectCountry(0)
        assertIs<RegionConfigEvents.SelectCountry>(event)
    }

    @Test
    fun `SelectCountry equality works`() {
        val event1 = RegionConfigEvents.SelectCountry(3)
        val event2 = RegionConfigEvents.SelectCountry(3)
        assertEquals(event1, event2)
    }

    @Test
    fun `SelectCity stores index`() {
        val event = RegionConfigEvents.SelectCity(10)
        assertEquals(10, event.index)
    }

    @Test
    fun `SelectCity implements RegionConfigEvents`() {
        val event: RegionConfigEvents = RegionConfigEvents.SelectCity(0)
        assertIs<RegionConfigEvents.SelectCity>(event)
    }

    @Test
    fun `SelectCity equality works`() {
        val event1 = RegionConfigEvents.SelectCity(7)
        val event2 = RegionConfigEvents.SelectCity(7)
        assertEquals(event1, event2)
    }

    @Test
    fun `all event types are exhaustive in when expression`() {
        val events: List<RegionConfigEvents> =
            listOf(
                RegionConfigEvents.SelectCountry(1),
                RegionConfigEvents.SelectCity(2),
            )

        events.forEach { event ->
            when (event) {
                is RegionConfigEvents.SelectCountry -> assertEquals(1, event.index)
                is RegionConfigEvents.SelectCity -> assertEquals(2, event.index)
            }
        }
    }
}
