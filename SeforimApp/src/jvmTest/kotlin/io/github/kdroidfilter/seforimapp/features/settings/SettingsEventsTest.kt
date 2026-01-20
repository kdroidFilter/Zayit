package io.github.kdroidfilter.seforimapp.features.settings

import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsEventsTest {
    // FontsSettingsEvents
    @Test
    fun `FontsSettingsEvents SetBookFont stores code`() {
        val event = FontsSettingsEvents.SetBookFont(code = "DAVID")
        assertEquals("DAVID", event.code)
        assertIs<FontsSettingsEvents>(event)
    }

    @Test
    fun `FontsSettingsEvents SetCommentaryFont stores code`() {
        val event = FontsSettingsEvents.SetCommentaryFont(code = "NOTO")
        assertEquals("NOTO", event.code)
        assertIs<FontsSettingsEvents>(event)
    }

    @Test
    fun `FontsSettingsEvents SetTargumFont stores code`() {
        val event = FontsSettingsEvents.SetTargumFont(code = "ARIAL")
        assertEquals("ARIAL", event.code)
        assertIs<FontsSettingsEvents>(event)
    }

    @Test
    fun `FontsSettingsEvents SetSourceFont stores code`() {
        val event = FontsSettingsEvents.SetSourceFont(code = "TIMES")
        assertEquals("TIMES", event.code)
        assertIs<FontsSettingsEvents>(event)
    }

    @Test
    fun `FontsSettingsEvents ResetToDefaults is singleton`() {
        val event1 = FontsSettingsEvents.ResetToDefaults
        val event2 = FontsSettingsEvents.ResetToDefaults
        assertEquals(event1, event2)
        assertIs<FontsSettingsEvents>(event1)
    }

    @Test
    fun `FontsSettingsEvents SetBookFont equals works`() {
        val event1 = FontsSettingsEvents.SetBookFont("A")
        val event2 = FontsSettingsEvents.SetBookFont("A")
        val event3 = FontsSettingsEvents.SetBookFont("B")
        assertEquals(event1, event2)
        assertTrue(event1 != event3)
    }

    // GeneralSettingsEvents
    @Test
    fun `GeneralSettingsEvents SetCloseTreeOnNewBook stores value`() {
        val eventTrue = GeneralSettingsEvents.SetCloseTreeOnNewBook(value = true)
        val eventFalse = GeneralSettingsEvents.SetCloseTreeOnNewBook(value = false)

        assertTrue(eventTrue.value)
        assertFalse(eventFalse.value)
        assertIs<GeneralSettingsEvents>(eventTrue)
    }

    @Test
    fun `GeneralSettingsEvents SetPersistSession stores value`() {
        val event = GeneralSettingsEvents.SetPersistSession(value = true)
        assertTrue(event.value)
        assertIs<GeneralSettingsEvents>(event)
    }

    @Test
    fun `GeneralSettingsEvents SetShowZmanimWidgets stores value`() {
        val event = GeneralSettingsEvents.SetShowZmanimWidgets(value = false)
        assertFalse(event.value)
        assertIs<GeneralSettingsEvents>(event)
    }

    @Test
    fun `GeneralSettingsEvents SetUseOpenGl stores value`() {
        val event = GeneralSettingsEvents.SetUseOpenGl(value = true)
        assertTrue(event.value)
        assertIs<GeneralSettingsEvents>(event)
    }

    @Test
    fun `GeneralSettingsEvents ResetApp is singleton`() {
        val event1 = GeneralSettingsEvents.ResetApp
        val event2 = GeneralSettingsEvents.ResetApp
        assertEquals(event1, event2)
        assertIs<GeneralSettingsEvents>(event1)
    }

    @Test
    fun `GeneralSettingsEvents SetCloseTreeOnNewBook equals works`() {
        val event1 = GeneralSettingsEvents.SetCloseTreeOnNewBook(true)
        val event2 = GeneralSettingsEvents.SetCloseTreeOnNewBook(true)
        val event3 = GeneralSettingsEvents.SetCloseTreeOnNewBook(false)

        assertEquals(event1, event2)
        assertTrue(event1 != event3)
    }
}
