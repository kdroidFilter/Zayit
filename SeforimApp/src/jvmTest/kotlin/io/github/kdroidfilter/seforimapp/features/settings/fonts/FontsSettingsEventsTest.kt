package io.github.kdroidfilter.seforimapp.features.settings.fonts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class FontsSettingsEventsTest {
    @Test
    fun `SetBookFont stores code`() {
        val event = FontsSettingsEvents.SetBookFont("frank-ruhl")
        assertEquals("frank-ruhl", event.code)
    }

    @Test
    fun `SetBookFont implements FontsSettingsEvents`() {
        val event: FontsSettingsEvents = FontsSettingsEvents.SetBookFont("test")
        assertIs<FontsSettingsEvents.SetBookFont>(event)
    }

    @Test
    fun `SetCommentaryFont stores code`() {
        val event = FontsSettingsEvents.SetCommentaryFont("david")
        assertEquals("david", event.code)
    }

    @Test
    fun `SetCommentaryFont implements FontsSettingsEvents`() {
        val event: FontsSettingsEvents = FontsSettingsEvents.SetCommentaryFont("test")
        assertIs<FontsSettingsEvents.SetCommentaryFont>(event)
    }

    @Test
    fun `SetTargumFont stores code`() {
        val event = FontsSettingsEvents.SetTargumFont("arial")
        assertEquals("arial", event.code)
    }

    @Test
    fun `SetTargumFont implements FontsSettingsEvents`() {
        val event: FontsSettingsEvents = FontsSettingsEvents.SetTargumFont("test")
        assertIs<FontsSettingsEvents.SetTargumFont>(event)
    }

    @Test
    fun `SetSourceFont stores code`() {
        val event = FontsSettingsEvents.SetSourceFont("miriam")
        assertEquals("miriam", event.code)
    }

    @Test
    fun `SetSourceFont implements FontsSettingsEvents`() {
        val event: FontsSettingsEvents = FontsSettingsEvents.SetSourceFont("test")
        assertIs<FontsSettingsEvents.SetSourceFont>(event)
    }

    @Test
    fun `ResetToDefaults is singleton`() {
        val event1 = FontsSettingsEvents.ResetToDefaults
        val event2 = FontsSettingsEvents.ResetToDefaults
        assertSame(event1, event2)
    }

    @Test
    fun `ResetToDefaults implements FontsSettingsEvents`() {
        val event: FontsSettingsEvents = FontsSettingsEvents.ResetToDefaults
        assertIs<FontsSettingsEvents.ResetToDefaults>(event)
    }

    @Test
    fun `all event types are exhaustive in when expression`() {
        val events: List<FontsSettingsEvents> =
            listOf(
                FontsSettingsEvents.SetBookFont("a"),
                FontsSettingsEvents.SetCommentaryFont("b"),
                FontsSettingsEvents.SetTargumFont("c"),
                FontsSettingsEvents.SetSourceFont("d"),
                FontsSettingsEvents.ResetToDefaults,
            )

        events.forEach { event ->
            when (event) {
                is FontsSettingsEvents.SetBookFont -> assertEquals("a", event.code)
                is FontsSettingsEvents.SetCommentaryFont -> assertEquals("b", event.code)
                is FontsSettingsEvents.SetTargumFont -> assertEquals("c", event.code)
                is FontsSettingsEvents.SetSourceFont -> assertEquals("d", event.code)
                FontsSettingsEvents.ResetToDefaults -> { /* ok */ }
            }
        }
    }
}
