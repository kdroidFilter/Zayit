package io.github.kdroidfilter.seforimapp.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsWindowStateTest {
    @Test
    fun `default state has isVisible false`() {
        val state = SettingsWindowState()
        assertFalse(state.isVisible)
    }

    @Test
    fun `state can be created with isVisible true`() {
        val state = SettingsWindowState(isVisible = true)
        assertTrue(state.isVisible)
    }

    @Test
    fun `copy works correctly`() {
        val original = SettingsWindowState(isVisible = false)
        val modified = original.copy(isVisible = true)

        assertFalse(original.isVisible)
        assertTrue(modified.isVisible)
    }

    @Test
    fun `equals works correctly`() {
        val state1 = SettingsWindowState(isVisible = true)
        val state2 = SettingsWindowState(isVisible = true)
        val state3 = SettingsWindowState(isVisible = false)

        assertEquals(state1, state2)
        assertTrue(state1 != state3)
    }
}
