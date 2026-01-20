package io.github.kdroidfilter.seforimapp.integration

import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadState
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for Settings and Download state classes.
 * Tests state initialization, transitions, and data integrity.
 */
class SettingsAndDownloadStateIntegrationTest {
    // ==================== GeneralSettingsState Tests ====================

    @Test
    fun `GeneralSettingsState has sensible defaults`() {
        val state = GeneralSettingsState()

        assertNull(state.databasePath)
        assertFalse(state.closeTreeOnNewBook)
        assertTrue(state.persistSession)
        assertTrue(state.showZmanimWidgets)
        assertFalse(state.useOpenGl)
        assertFalse(state.resetDone)
    }

    @Test
    fun `GeneralSettingsState can be created with custom values`() {
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
    fun `GeneralSettingsState copy preserves unmodified fields`() {
        val original =
            GeneralSettingsState(
                databasePath = "/path/db.sqlite",
                closeTreeOnNewBook = true,
                persistSession = true,
                showZmanimWidgets = true,
                useOpenGl = false,
                resetDone = false,
            )

        val modified = original.copy(closeTreeOnNewBook = false)

        assertEquals("/path/db.sqlite", modified.databasePath)
        assertFalse(modified.closeTreeOnNewBook)
        assertTrue(modified.persistSession)
        assertTrue(modified.showZmanimWidgets)
        assertFalse(modified.useOpenGl)
    }

    @Test
    fun `GeneralSettingsState preview has expected values`() {
        val preview = GeneralSettingsState.preview

        assertEquals("/Users/you/.zayit/seforim.db", preview.databasePath)
        assertTrue(preview.closeTreeOnNewBook)
        assertTrue(preview.persistSession)
        assertTrue(preview.showZmanimWidgets)
        assertFalse(preview.useOpenGl)
    }

    // ==================== GeneralSettingsEvents Tests ====================

    @Test
    fun `SetCloseTreeOnNewBook event contains correct value`() {
        val eventTrue = GeneralSettingsEvents.SetCloseTreeOnNewBook(true)
        val eventFalse = GeneralSettingsEvents.SetCloseTreeOnNewBook(false)

        assertTrue(eventTrue.value)
        assertFalse(eventFalse.value)
    }

    @Test
    fun `SetPersistSession event contains correct value`() {
        val eventTrue = GeneralSettingsEvents.SetPersistSession(true)
        val eventFalse = GeneralSettingsEvents.SetPersistSession(false)

        assertTrue(eventTrue.value)
        assertFalse(eventFalse.value)
    }

    @Test
    fun `SetShowZmanimWidgets event contains correct value`() {
        val eventTrue = GeneralSettingsEvents.SetShowZmanimWidgets(true)
        val eventFalse = GeneralSettingsEvents.SetShowZmanimWidgets(false)

        assertTrue(eventTrue.value)
        assertFalse(eventFalse.value)
    }

    @Test
    fun `SetUseOpenGl event contains correct value`() {
        val eventTrue = GeneralSettingsEvents.SetUseOpenGl(true)
        val eventFalse = GeneralSettingsEvents.SetUseOpenGl(false)

        assertTrue(eventTrue.value)
        assertFalse(eventFalse.value)
    }

    @Test
    fun `ResetApp is a singleton event`() {
        val event1 = GeneralSettingsEvents.ResetApp
        val event2 = GeneralSettingsEvents.ResetApp

        assertEquals(event1, event2)
    }

    // ==================== DownloadState Tests ====================

    @Test
    fun `DownloadState has sensible initial values`() {
        val state =
            DownloadState(
                inProgress = false,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = null,
                speedBytesPerSec = 0L,
            )

        assertFalse(state.inProgress)
        assertEquals(0f, state.progress)
        assertEquals(0L, state.downloadedBytes)
        assertNull(state.totalBytes)
        assertEquals(0L, state.speedBytesPerSec)
        assertNull(state.errorMessage)
        assertFalse(state.completed)
    }

    @Test
    fun `DownloadState reflects download in progress`() {
        val state =
            DownloadState(
                inProgress = true,
                progress = 0.5f,
                downloadedBytes = 50_000_000L,
                totalBytes = 100_000_000L,
                speedBytesPerSec = 1_000_000L,
            )

        assertTrue(state.inProgress)
        assertEquals(0.5f, state.progress)
        assertEquals(50_000_000L, state.downloadedBytes)
        assertEquals(100_000_000L, state.totalBytes)
        assertEquals(1_000_000L, state.speedBytesPerSec)
    }

    @Test
    fun `DownloadState reflects completed download`() {
        val state =
            DownloadState(
                inProgress = false,
                progress = 1f,
                downloadedBytes = 100_000_000L,
                totalBytes = 100_000_000L,
                speedBytesPerSec = 0L,
                completed = true,
            )

        assertFalse(state.inProgress)
        assertEquals(1f, state.progress)
        assertTrue(state.completed)
        assertNull(state.errorMessage)
    }

    @Test
    fun `DownloadState reflects error state`() {
        val state =
            DownloadState(
                inProgress = false,
                progress = 0.3f,
                downloadedBytes = 30_000_000L,
                totalBytes = 100_000_000L,
                speedBytesPerSec = 0L,
                errorMessage = "Network connection failed",
                completed = false,
            )

        assertFalse(state.inProgress)
        assertFalse(state.completed)
        assertEquals("Network connection failed", state.errorMessage)
    }

    @Test
    fun `DownloadState handles unknown total size`() {
        val state =
            DownloadState(
                inProgress = true,
                progress = 0f,
                downloadedBytes = 10_000_000L,
                totalBytes = null, // Unknown total
                speedBytesPerSec = 500_000L,
            )

        assertTrue(state.inProgress)
        assertNull(state.totalBytes)
        assertEquals(10_000_000L, state.downloadedBytes)
    }

    @Test
    fun `DownloadState copy preserves unmodified fields`() {
        val original =
            DownloadState(
                inProgress = true,
                progress = 0.5f,
                downloadedBytes = 50_000_000L,
                totalBytes = 100_000_000L,
                speedBytesPerSec = 1_000_000L,
                errorMessage = null,
                completed = false,
            )

        val modified = original.copy(progress = 0.75f, downloadedBytes = 75_000_000L)

        assertTrue(modified.inProgress)
        assertEquals(0.75f, modified.progress)
        assertEquals(75_000_000L, modified.downloadedBytes)
        assertEquals(100_000_000L, modified.totalBytes)
        assertEquals(1_000_000L, modified.speedBytesPerSec)
    }

    // ==================== State Transition Scenarios ====================

    @Test
    fun `download state progression scenario`() {
        // Initial state
        var state =
            DownloadState(
                inProgress = false,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = null,
                speedBytesPerSec = 0L,
            )
        assertFalse(state.inProgress)

        // Start download
        state =
            state.copy(
                inProgress = true,
                totalBytes = 100_000_000L,
            )
        assertTrue(state.inProgress)
        assertEquals(100_000_000L, state.totalBytes)

        // Progress updates
        state =
            state.copy(
                progress = 0.25f,
                downloadedBytes = 25_000_000L,
                speedBytesPerSec = 2_000_000L,
            )
        assertEquals(0.25f, state.progress)

        state =
            state.copy(
                progress = 0.5f,
                downloadedBytes = 50_000_000L,
            )
        assertEquals(0.5f, state.progress)

        state =
            state.copy(
                progress = 0.75f,
                downloadedBytes = 75_000_000L,
            )
        assertEquals(0.75f, state.progress)

        // Complete
        state =
            state.copy(
                inProgress = false,
                progress = 1f,
                downloadedBytes = 100_000_000L,
                speedBytesPerSec = 0L,
                completed = true,
            )
        assertFalse(state.inProgress)
        assertTrue(state.completed)
        assertEquals(1f, state.progress)
    }

    @Test
    fun `download state error recovery scenario`() {
        // Start download
        var state =
            DownloadState(
                inProgress = true,
                progress = 0.3f,
                downloadedBytes = 30_000_000L,
                totalBytes = 100_000_000L,
                speedBytesPerSec = 1_000_000L,
            )

        // Error occurs
        state =
            state.copy(
                inProgress = false,
                speedBytesPerSec = 0L,
                errorMessage = "Connection timeout",
            )
        assertFalse(state.inProgress)
        assertEquals("Connection timeout", state.errorMessage)
        assertFalse(state.completed)

        // Retry - clear error and restart
        state =
            state.copy(
                inProgress = true,
                progress = 0f,
                downloadedBytes = 0L,
                errorMessage = null,
            )
        assertTrue(state.inProgress)
        assertNull(state.errorMessage)
    }

    // ==================== Settings State Transition Tests ====================

    @Test
    fun `settings state modification scenario`() {
        var state = GeneralSettingsState()

        // User enables close tree on new book
        state = state.copy(closeTreeOnNewBook = true)
        assertTrue(state.closeTreeOnNewBook)

        // User disables persist session
        state = state.copy(persistSession = false)
        assertFalse(state.persistSession)

        // User enables OpenGL
        state = state.copy(useOpenGl = true)
        assertTrue(state.useOpenGl)

        // Verify all changes persisted
        assertTrue(state.closeTreeOnNewBook)
        assertFalse(state.persistSession)
        assertTrue(state.useOpenGl)
    }

    @Test
    fun `settings state handles database path changes`() {
        var state = GeneralSettingsState()

        // Initial - no path
        assertNull(state.databasePath)

        // User sets custom path
        state = state.copy(databasePath = "/Users/custom/seforim.db")
        assertEquals("/Users/custom/seforim.db", state.databasePath)

        // User changes path again
        state = state.copy(databasePath = "/another/location/db.sqlite")
        assertEquals("/another/location/db.sqlite", state.databasePath)
    }
}
