package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractPhase
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtractViewModelTest {
    // ExtractState tests
    @Test
    fun `default ExtractState has IDLE phase`() {
        val state = ExtractState()
        assertEquals(ExtractPhase.IDLE, state.phase)
    }

    @Test
    fun `default ExtractState has zero progress`() {
        val state = ExtractState()
        assertEquals(0f, state.progressFloat)
    }

    @Test
    fun `default ExtractState has no error`() {
        val state = ExtractState()
        assertNull(state.errorMessage)
    }

    @Test
    fun `default ExtractState can retry`() {
        val state = ExtractState()
        assertTrue(state.canRetry)
    }

    @Test
    fun `default ExtractState has empty currentFile`() {
        val state = ExtractState()
        assertEquals("", state.currentFile)
    }

    @Test
    fun `ExtractState with extracting phase`() {
        val state = ExtractState(
            phase = ExtractPhase.EXTRACTING,
            progressFloat = 0.5f,
            currentFile = "data.db",
        )
        assertEquals(ExtractPhase.EXTRACTING, state.phase)
        assertEquals(0.5f, state.progressFloat)
        assertEquals("data.db", state.currentFile)
    }

    @Test
    fun `ExtractState with error`() {
        val state = ExtractState(
            phase = ExtractPhase.ERROR,
            errorMessage = "Extraction failed",
            canRetry = true,
        )
        assertEquals(ExtractPhase.ERROR, state.phase)
        assertEquals("Extraction failed", state.errorMessage)
        assertTrue(state.canRetry)
    }

    @Test
    fun `ExtractState with completed phase`() {
        val state = ExtractState(phase = ExtractPhase.COMPLETED)
        assertEquals(ExtractPhase.COMPLETED, state.phase)
    }

    @Test
    fun `ExtractState copy changes specified values`() {
        val original = ExtractState()
        val copied = original.copy(
            phase = ExtractPhase.EXTRACTING,
            progressFloat = 0.75f,
            currentFile = "index.lucene",
        )
        assertEquals(ExtractPhase.EXTRACTING, copied.phase)
        assertEquals(0.75f, copied.progressFloat)
        assertEquals("index.lucene", copied.currentFile)
    }

    @Test
    fun `ExtractState equality`() {
        val state1 = ExtractState(phase = ExtractPhase.EXTRACTING, progressFloat = 0.5f)
        val state2 = ExtractState(phase = ExtractPhase.EXTRACTING, progressFloat = 0.5f)
        assertEquals(state1, state2)
    }

    @Test
    fun `ExtractState inequality`() {
        val state1 = ExtractState(phase = ExtractPhase.EXTRACTING)
        val state2 = ExtractState(phase = ExtractPhase.COMPLETED)
        assertNotEquals(state1, state2)
    }

    // ExtractPhase tests
    @Test
    fun `ExtractPhase has all expected values`() {
        val phases = ExtractPhase.entries
        assertTrue(phases.contains(ExtractPhase.IDLE))
        assertTrue(phases.contains(ExtractPhase.EXTRACTING))
        assertTrue(phases.contains(ExtractPhase.COMPLETED))
        assertTrue(phases.contains(ExtractPhase.ERROR))
    }

    @Test
    fun `ExtractPhase IDLE name is correct`() {
        assertEquals("IDLE", ExtractPhase.IDLE.name)
    }

    @Test
    fun `ExtractPhase EXTRACTING name is correct`() {
        assertEquals("EXTRACTING", ExtractPhase.EXTRACTING.name)
    }

    @Test
    fun `ExtractPhase COMPLETED name is correct`() {
        assertEquals("COMPLETED", ExtractPhase.COMPLETED.name)
    }

    @Test
    fun `ExtractPhase ERROR name is correct`() {
        assertEquals("ERROR", ExtractPhase.ERROR.name)
    }

    // ExtractEvents tests
    @Test
    fun `StartExtraction event exists`() {
        val event = ExtractEvents.StartExtraction
        assertEquals(ExtractEvents.StartExtraction, event)
    }

    @Test
    fun `RetryExtraction event exists`() {
        val event = ExtractEvents.RetryExtraction
        assertEquals(ExtractEvents.RetryExtraction, event)
    }

    @Test
    fun `events are sealed class subtypes`() {
        val start: ExtractEvents = ExtractEvents.StartExtraction
        val retry: ExtractEvents = ExtractEvents.RetryExtraction

        assertTrue(start is ExtractEvents)
        assertTrue(retry is ExtractEvents)
    }

    @Test
    fun `when expression covers all event types`() {
        fun describe(event: ExtractEvents): String = when (event) {
            ExtractEvents.StartExtraction -> "start"
            ExtractEvents.RetryExtraction -> "retry"
        }

        assertEquals("start", describe(ExtractEvents.StartExtraction))
        assertEquals("retry", describe(ExtractEvents.RetryExtraction))
    }
}
