package io.github.kdroidfilter.seforimapp.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SettingsWindowEventsTest {
    @Test
    fun `OnOpen is singleton`() {
        val event1 = SettingsWindowEvents.OnOpen
        val event2 = SettingsWindowEvents.OnOpen
        assertEquals(event1, event2)
        assertIs<SettingsWindowEvents>(event1)
    }

    @Test
    fun `OnClose is singleton`() {
        val event1 = SettingsWindowEvents.OnClose
        val event2 = SettingsWindowEvents.OnClose
        assertEquals(event1, event2)
        assertIs<SettingsWindowEvents>(event1)
    }

    @Test
    fun `OnOpen and OnClose are different`() {
        val open = SettingsWindowEvents.OnOpen
        val close = SettingsWindowEvents.OnClose
        assert(open != close)
    }
}
