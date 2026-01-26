package io.github.kdroidfilter.seforimapp.features.settings.general

import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GeneralSettingsViewModelTest {
    // GeneralSettingsState tests
    @Test
    fun `GeneralSettingsState has correct databasePath`() {
        val state = GeneralSettingsState(
            databasePath = "/path/to/db",
            closeTreeOnNewBook = false,
            persistSession = false,
            showZmanimWidgets = false,
            useOpenGl = false,
            resetDone = false,
        )
        assertEquals("/path/to/db", state.databasePath)
    }

    @Test
    fun `GeneralSettingsState has correct closeTreeOnNewBook`() {
        val state = GeneralSettingsState(
            databasePath = "",
            closeTreeOnNewBook = true,
            persistSession = false,
            showZmanimWidgets = false,
            useOpenGl = false,
            resetDone = false,
        )
        assertTrue(state.closeTreeOnNewBook)
    }

    @Test
    fun `GeneralSettingsState has correct persistSession`() {
        val state = GeneralSettingsState(
            databasePath = "",
            closeTreeOnNewBook = false,
            persistSession = true,
            showZmanimWidgets = false,
            useOpenGl = false,
            resetDone = false,
        )
        assertTrue(state.persistSession)
    }

    @Test
    fun `GeneralSettingsState has correct showZmanimWidgets`() {
        val state = GeneralSettingsState(
            databasePath = "",
            closeTreeOnNewBook = false,
            persistSession = false,
            showZmanimWidgets = true,
            useOpenGl = false,
            resetDone = false,
        )
        assertTrue(state.showZmanimWidgets)
    }

    @Test
    fun `GeneralSettingsState has correct useOpenGl`() {
        val state = GeneralSettingsState(
            databasePath = "",
            closeTreeOnNewBook = false,
            persistSession = false,
            showZmanimWidgets = false,
            useOpenGl = true,
            resetDone = false,
        )
        assertTrue(state.useOpenGl)
    }

    @Test
    fun `GeneralSettingsState has correct resetDone`() {
        val state = GeneralSettingsState(
            databasePath = "",
            closeTreeOnNewBook = false,
            persistSession = false,
            showZmanimWidgets = false,
            useOpenGl = false,
            resetDone = true,
        )
        assertTrue(state.resetDone)
    }

    @Test
    fun `GeneralSettingsState copy works correctly`() {
        val original = GeneralSettingsState(
            databasePath = "/old/path",
            closeTreeOnNewBook = false,
            persistSession = false,
            showZmanimWidgets = false,
            useOpenGl = false,
            resetDone = false,
        )
        val copied = original.copy(databasePath = "/new/path", persistSession = true)

        assertEquals("/new/path", copied.databasePath)
        assertTrue(copied.persistSession)
        assertFalse(copied.closeTreeOnNewBook)
    }

    @Test
    fun `GeneralSettingsState equality`() {
        val state1 = GeneralSettingsState(
            databasePath = "/path",
            closeTreeOnNewBook = true,
            persistSession = true,
            showZmanimWidgets = false,
            useOpenGl = false,
            resetDone = false,
        )
        val state2 = GeneralSettingsState(
            databasePath = "/path",
            closeTreeOnNewBook = true,
            persistSession = true,
            showZmanimWidgets = false,
            useOpenGl = false,
            resetDone = false,
        )
        assertEquals(state1, state2)
    }

    @Test
    fun `GeneralSettingsState inequality`() {
        val state1 = GeneralSettingsState(
            databasePath = "/path1",
            closeTreeOnNewBook = false,
            persistSession = false,
            showZmanimWidgets = false,
            useOpenGl = false,
            resetDone = false,
        )
        val state2 = GeneralSettingsState(
            databasePath = "/path2",
            closeTreeOnNewBook = false,
            persistSession = false,
            showZmanimWidgets = false,
            useOpenGl = false,
            resetDone = false,
        )
        assertNotEquals(state1, state2)
    }

    // GeneralSettingsEvents tests
    @Test
    fun `SetCloseTreeOnNewBook has correct value`() {
        val event = GeneralSettingsEvents.SetCloseTreeOnNewBook(true)
        assertTrue(event.value)
    }

    @Test
    fun `SetPersistSession has correct value`() {
        val event = GeneralSettingsEvents.SetPersistSession(true)
        assertTrue(event.value)
    }

    @Test
    fun `SetShowZmanimWidgets has correct value`() {
        val event = GeneralSettingsEvents.SetShowZmanimWidgets(true)
        assertTrue(event.value)
    }

    @Test
    fun `SetUseOpenGl has correct value`() {
        val event = GeneralSettingsEvents.SetUseOpenGl(true)
        assertTrue(event.value)
    }

    @Test
    fun `ResetApp is singleton object`() {
        val event1 = GeneralSettingsEvents.ResetApp
        val event2 = GeneralSettingsEvents.ResetApp
        assertEquals(event1, event2)
    }

    @Test
    fun `events are sealed class subtypes`() {
        val closeTree: GeneralSettingsEvents = GeneralSettingsEvents.SetCloseTreeOnNewBook(false)
        val persist: GeneralSettingsEvents = GeneralSettingsEvents.SetPersistSession(false)
        val zmanim: GeneralSettingsEvents = GeneralSettingsEvents.SetShowZmanimWidgets(false)
        val openGl: GeneralSettingsEvents = GeneralSettingsEvents.SetUseOpenGl(false)
        val reset: GeneralSettingsEvents = GeneralSettingsEvents.ResetApp

        assertTrue(closeTree is GeneralSettingsEvents)
        assertTrue(persist is GeneralSettingsEvents)
        assertTrue(zmanim is GeneralSettingsEvents)
        assertTrue(openGl is GeneralSettingsEvents)
        assertTrue(reset is GeneralSettingsEvents)
    }

    @Test
    fun `when expression covers all event types`() {
        fun describe(event: GeneralSettingsEvents): String = when (event) {
            is GeneralSettingsEvents.SetCloseTreeOnNewBook -> "closeTree: ${event.value}"
            is GeneralSettingsEvents.SetPersistSession -> "persist: ${event.value}"
            is GeneralSettingsEvents.SetShowZmanimWidgets -> "zmanim: ${event.value}"
            is GeneralSettingsEvents.SetUseOpenGl -> "openGl: ${event.value}"
            GeneralSettingsEvents.ResetApp -> "reset"
        }

        assertEquals("closeTree: true", describe(GeneralSettingsEvents.SetCloseTreeOnNewBook(true)))
        assertEquals("persist: false", describe(GeneralSettingsEvents.SetPersistSession(false)))
        assertEquals("zmanim: true", describe(GeneralSettingsEvents.SetShowZmanimWidgets(true)))
        assertEquals("openGl: false", describe(GeneralSettingsEvents.SetUseOpenGl(false)))
        assertEquals("reset", describe(GeneralSettingsEvents.ResetApp))
    }
}
