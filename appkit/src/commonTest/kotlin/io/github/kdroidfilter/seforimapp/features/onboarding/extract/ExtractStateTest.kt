package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtractStateTest {
    @Test
    fun `state can be created with required values`() {
        val state =
            ExtractState(
                inProgress = true,
                progress = 0.5f,
            )

        assertTrue(state.inProgress)
        assertEquals(0.5f, state.progress)
        assertNull(state.errorMessage)
        assertFalse(state.completed)
    }

    @Test
    fun `state can have error message`() {
        val state =
            ExtractState(
                inProgress = false,
                progress = 0.3f,
                errorMessage = "Extraction failed",
            )

        assertEquals("Extraction failed", state.errorMessage)
        assertFalse(state.inProgress)
    }

    @Test
    fun `state can be completed`() {
        val state =
            ExtractState(
                inProgress = false,
                progress = 1f,
                completed = true,
            )

        assertTrue(state.completed)
        assertEquals(1f, state.progress)
        assertFalse(state.inProgress)
    }

    @Test
    fun `copy preserves unchanged values`() {
        val original =
            ExtractState(
                inProgress = true,
                progress = 0.5f,
            )
        val modified = original.copy(progress = 0.8f)

        assertEquals(0.8f, modified.progress)
        assertTrue(modified.inProgress)
        assertNull(modified.errorMessage)
    }

    @Test
    fun `equals works correctly`() {
        val state1 = ExtractState(true, 0.5f, null, false)
        val state2 = ExtractState(true, 0.5f, null, false)
        val state3 = ExtractState(true, 0.6f, null, false)

        assertEquals(state1, state2)
        assertFalse(state1 == state3)
    }

    @Test
    fun `initial extraction state`() {
        val state = ExtractState(inProgress = false, progress = 0f)

        assertFalse(state.inProgress)
        assertEquals(0f, state.progress)
        assertFalse(state.completed)
    }
}
