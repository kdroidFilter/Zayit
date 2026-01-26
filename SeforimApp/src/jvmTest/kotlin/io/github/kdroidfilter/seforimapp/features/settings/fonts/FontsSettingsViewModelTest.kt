package io.github.kdroidfilter.seforimapp.features.settings.fonts

import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FontsSettingsViewModelTest {
    // FontsSettingsState tests
    @Test
    fun `FontsSettingsState has correct mainFontFamily`() {
        val state = FontsSettingsState(
            mainFontFamily = "David",
            mainFontScale = 1.0f,
            commentatorFontFamily = "Narkisim",
            commentatorFontScale = 0.9f,
        )
        assertEquals("David", state.mainFontFamily)
    }

    @Test
    fun `FontsSettingsState has correct mainFontScale`() {
        val state = FontsSettingsState(
            mainFontFamily = "David",
            mainFontScale = 1.2f,
            commentatorFontFamily = "Narkisim",
            commentatorFontScale = 0.9f,
        )
        assertEquals(1.2f, state.mainFontScale)
    }

    @Test
    fun `FontsSettingsState has correct commentatorFontFamily`() {
        val state = FontsSettingsState(
            mainFontFamily = "David",
            mainFontScale = 1.0f,
            commentatorFontFamily = "Narkisim",
            commentatorFontScale = 0.9f,
        )
        assertEquals("Narkisim", state.commentatorFontFamily)
    }

    @Test
    fun `FontsSettingsState has correct commentatorFontScale`() {
        val state = FontsSettingsState(
            mainFontFamily = "David",
            mainFontScale = 1.0f,
            commentatorFontFamily = "Narkisim",
            commentatorFontScale = 0.85f,
        )
        assertEquals(0.85f, state.commentatorFontScale)
    }

    @Test
    fun `FontsSettingsState copy changes mainFontFamily`() {
        val original = FontsSettingsState(
            mainFontFamily = "David",
            mainFontScale = 1.0f,
            commentatorFontFamily = "Narkisim",
            commentatorFontScale = 0.9f,
        )
        val copied = original.copy(mainFontFamily = "Times New Roman")
        assertEquals("Times New Roman", copied.mainFontFamily)
        assertEquals("Narkisim", copied.commentatorFontFamily)
    }

    @Test
    fun `FontsSettingsState copy changes mainFontScale`() {
        val original = FontsSettingsState(
            mainFontFamily = "David",
            mainFontScale = 1.0f,
            commentatorFontFamily = "Narkisim",
            commentatorFontScale = 0.9f,
        )
        val copied = original.copy(mainFontScale = 1.5f)
        assertEquals(1.5f, copied.mainFontScale)
    }

    @Test
    fun `FontsSettingsState equality`() {
        val state1 = FontsSettingsState("David", 1.0f, "Narkisim", 0.9f)
        val state2 = FontsSettingsState("David", 1.0f, "Narkisim", 0.9f)
        assertEquals(state1, state2)
    }

    @Test
    fun `FontsSettingsState inequality`() {
        val state1 = FontsSettingsState("David", 1.0f, "Narkisim", 0.9f)
        val state2 = FontsSettingsState("Arial", 1.0f, "Narkisim", 0.9f)
        assertNotEquals(state1, state2)
    }

    // FontsSettingsEvents tests
    @Test
    fun `SetMainFontFamily has correct value`() {
        val event = FontsSettingsEvents.SetMainFontFamily("Frank Ruhl Libre")
        assertEquals("Frank Ruhl Libre", event.family)
    }

    @Test
    fun `SetMainFontScale has correct value`() {
        val event = FontsSettingsEvents.SetMainFontScale(1.3f)
        assertEquals(1.3f, event.scale)
    }

    @Test
    fun `SetCommentatorFontFamily has correct value`() {
        val event = FontsSettingsEvents.SetCommentatorFontFamily("Taamey Frank")
        assertEquals("Taamey Frank", event.family)
    }

    @Test
    fun `SetCommentatorFontScale has correct value`() {
        val event = FontsSettingsEvents.SetCommentatorFontScale(0.8f)
        assertEquals(0.8f, event.scale)
    }

    @Test
    fun `events are sealed class subtypes`() {
        val mainFamily: FontsSettingsEvents = FontsSettingsEvents.SetMainFontFamily("David")
        val mainScale: FontsSettingsEvents = FontsSettingsEvents.SetMainFontScale(1.0f)
        val commentFamily: FontsSettingsEvents = FontsSettingsEvents.SetCommentatorFontFamily("Narkisim")
        val commentScale: FontsSettingsEvents = FontsSettingsEvents.SetCommentatorFontScale(0.9f)

        assertTrue(mainFamily is FontsSettingsEvents)
        assertTrue(mainScale is FontsSettingsEvents)
        assertTrue(commentFamily is FontsSettingsEvents)
        assertTrue(commentScale is FontsSettingsEvents)
    }

    @Test
    fun `when expression covers all event types`() {
        fun describe(event: FontsSettingsEvents): String = when (event) {
            is FontsSettingsEvents.SetMainFontFamily -> "mainFamily: ${event.family}"
            is FontsSettingsEvents.SetMainFontScale -> "mainScale: ${event.scale}"
            is FontsSettingsEvents.SetCommentatorFontFamily -> "commentFamily: ${event.family}"
            is FontsSettingsEvents.SetCommentatorFontScale -> "commentScale: ${event.scale}"
        }

        assertEquals("mainFamily: David", describe(FontsSettingsEvents.SetMainFontFamily("David")))
        assertEquals("mainScale: 1.0", describe(FontsSettingsEvents.SetMainFontScale(1.0f)))
        assertEquals("commentFamily: Narkisim", describe(FontsSettingsEvents.SetCommentatorFontFamily("Narkisim")))
        assertEquals("commentScale: 0.9", describe(FontsSettingsEvents.SetCommentatorFontScale(0.9f)))
    }

    @Test
    fun `SetMainFontFamily instances with same value are equal`() {
        val event1 = FontsSettingsEvents.SetMainFontFamily("David")
        val event2 = FontsSettingsEvents.SetMainFontFamily("David")
        assertEquals(event1, event2)
    }

    @Test
    fun `SetMainFontScale instances with same value are equal`() {
        val event1 = FontsSettingsEvents.SetMainFontScale(1.2f)
        val event2 = FontsSettingsEvents.SetMainFontScale(1.2f)
        assertEquals(event1, event2)
    }
}
