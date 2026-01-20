package io.github.kdroidfilter.seforimapp.features.settings.general

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeneralSettingsStateTest {
    @Test
    fun `default state has correct values`() {
        val state = GeneralSettingsState()

        assertNull(state.databasePath)
        assertFalse(state.closeTreeOnNewBook)
        assertTrue(state.persistSession)
        assertTrue(state.showZmanimWidgets)
        assertFalse(state.useOpenGl)
        assertFalse(state.resetDone)
    }

    @Test
    fun `state can be created with custom values`() {
        val state =
            GeneralSettingsState(
                databasePath = "/custom/path/db.sqlite",
                closeTreeOnNewBook = true,
                persistSession = false,
                showZmanimWidgets = false,
                useOpenGl = true,
                resetDone = true,
            )

        assertEquals("/custom/path/db.sqlite", state.databasePath)
        assertTrue(state.closeTreeOnNewBook)
        assertFalse(state.persistSession)
        assertFalse(state.showZmanimWidgets)
        assertTrue(state.useOpenGl)
        assertTrue(state.resetDone)
    }

    @Test
    fun `copy preserves unchanged values`() {
        val original =
            GeneralSettingsState(
                databasePath = "/path/to/db",
                closeTreeOnNewBook = true,
                persistSession = true,
            )
        val modified = original.copy(closeTreeOnNewBook = false)

        assertEquals("/path/to/db", modified.databasePath)
        assertFalse(modified.closeTreeOnNewBook)
        assertTrue(modified.persistSession)
    }

    @Test
    fun `preview companion object is available`() {
        val preview = GeneralSettingsState.preview
        assertNotNull(preview)
        assertEquals("/Users/you/.zayit/seforim.db", preview.databasePath)
        assertTrue(preview.closeTreeOnNewBook)
        assertTrue(preview.persistSession)
        assertTrue(preview.showZmanimWidgets)
        assertFalse(preview.useOpenGl)
        assertFalse(preview.resetDone)
    }

    @Test
    fun `equals works correctly`() {
        val state1 = GeneralSettingsState(closeTreeOnNewBook = true)
        val state2 = GeneralSettingsState(closeTreeOnNewBook = true)
        val state3 = GeneralSettingsState(closeTreeOnNewBook = false)

        assertEquals(state1, state2)
        assertTrue(state1 != state3)
    }
}
