package io.github.kdroidfilter.seforimapp.core.presentation.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntUiThemesTest {
    @Test
    fun `IntUiThemes has three values`() {
        val values = IntUiThemes.entries
        assertEquals(3, values.size)
    }

    @Test
    fun `Light theme exists`() {
        val light = IntUiThemes.Light
        assertEquals("Light", light.name)
    }

    @Test
    fun `Dark theme exists`() {
        val dark = IntUiThemes.Dark
        assertEquals("Dark", dark.name)
    }

    @Test
    fun `System theme exists`() {
        val system = IntUiThemes.System
        assertEquals("System", system.name)
    }

    @Test
    fun `entries are in expected order`() {
        val entries = IntUiThemes.entries
        assertEquals(IntUiThemes.Light, entries[0])
        assertEquals(IntUiThemes.Dark, entries[1])
        assertEquals(IntUiThemes.System, entries[2])
    }

    @Test
    fun `valueOf works for valid names`() {
        assertEquals(IntUiThemes.Light, IntUiThemes.valueOf("Light"))
        assertEquals(IntUiThemes.Dark, IntUiThemes.valueOf("Dark"))
        assertEquals(IntUiThemes.System, IntUiThemes.valueOf("System"))
    }

    @Test
    fun `ordinal values are correct`() {
        assertEquals(0, IntUiThemes.Light.ordinal)
        assertEquals(1, IntUiThemes.Dark.ordinal)
        assertEquals(2, IntUiThemes.System.ordinal)
    }

    @Test
    fun `themes are comparable`() {
        assertTrue(IntUiThemes.Light < IntUiThemes.Dark)
        assertTrue(IntUiThemes.Dark < IntUiThemes.System)
    }
}
