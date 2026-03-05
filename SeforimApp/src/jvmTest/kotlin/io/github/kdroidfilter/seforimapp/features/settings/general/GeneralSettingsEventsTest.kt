package io.github.kdroidfilter.seforimapp.features.settings.general

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class GeneralSettingsEventsTest {
    @Test
    fun `SetCloseTreeOnNewBook stores value`() {
        val eventTrue = GeneralSettingsEvents.SetCloseTreeOnNewBook(true)
        val eventFalse = GeneralSettingsEvents.SetCloseTreeOnNewBook(false)
        assertEquals(true, eventTrue.value)
        assertEquals(false, eventFalse.value)
    }

    @Test
    fun `SetCloseTreeOnNewBook implements GeneralSettingsEvents`() {
        val event: GeneralSettingsEvents = GeneralSettingsEvents.SetCloseTreeOnNewBook(true)
        assertIs<GeneralSettingsEvents.SetCloseTreeOnNewBook>(event)
    }

    @Test
    fun `SetPersistSession stores value`() {
        val event = GeneralSettingsEvents.SetPersistSession(true)
        assertEquals(true, event.value)
    }

    @Test
    fun `SetPersistSession implements GeneralSettingsEvents`() {
        val event: GeneralSettingsEvents = GeneralSettingsEvents.SetPersistSession(false)
        assertIs<GeneralSettingsEvents.SetPersistSession>(event)
    }

    @Test
    fun `ResetApp is singleton`() {
        val event1 = GeneralSettingsEvents.ResetApp
        val event2 = GeneralSettingsEvents.ResetApp
        assertSame(event1, event2)
    }

    @Test
    fun `ResetApp implements GeneralSettingsEvents`() {
        val event: GeneralSettingsEvents = GeneralSettingsEvents.ResetApp
        assertIs<GeneralSettingsEvents.ResetApp>(event)
    }

    @Test
    fun `all event types are exhaustive in when expression`() {
        val events: List<GeneralSettingsEvents> =
            listOf(
                GeneralSettingsEvents.SetCloseTreeOnNewBook(true),
                GeneralSettingsEvents.SetPersistSession(true),
                GeneralSettingsEvents.ResetApp,
            )

        events.forEach { event ->
            when (event) {
                is GeneralSettingsEvents.SetCloseTreeOnNewBook -> assertEquals(true, event.value)
                is GeneralSettingsEvents.SetPersistSession -> assertEquals(true, event.value)
                GeneralSettingsEvents.ResetApp -> { /* ok */ }
            }
        }
    }
}
