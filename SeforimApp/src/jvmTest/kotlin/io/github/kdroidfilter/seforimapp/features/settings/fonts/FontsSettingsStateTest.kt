package io.github.kdroidfilter.seforimapp.features.settings.fonts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FontsSettingsStateTest {
    @Test
    fun `default state has correct font codes`() {
        val state = FontsSettingsState()

        assertEquals("notoserifhebrew", state.bookFontCode)
        assertEquals("frankruhllibre", state.commentaryFontCode)
        assertEquals("taameyashkenaz", state.targumFontCode)
        assertEquals("frankruhllibre", state.sourceFontCode)
    }

    @Test
    fun `state can be created with custom font codes`() {
        val state =
            FontsSettingsState(
                bookFontCode = "custom_book",
                commentaryFontCode = "custom_commentary",
                targumFontCode = "custom_targum",
                sourceFontCode = "custom_source",
            )

        assertEquals("custom_book", state.bookFontCode)
        assertEquals("custom_commentary", state.commentaryFontCode)
        assertEquals("custom_targum", state.targumFontCode)
        assertEquals("custom_source", state.sourceFontCode)
    }

    @Test
    fun `copy preserves unchanged values`() {
        val original = FontsSettingsState()
        val modified = original.copy(bookFontCode = "new_font")

        assertEquals("new_font", modified.bookFontCode)
        assertEquals(original.commentaryFontCode, modified.commentaryFontCode)
        assertEquals(original.targumFontCode, modified.targumFontCode)
        assertEquals(original.sourceFontCode, modified.sourceFontCode)
    }

    @Test
    fun `preview companion object is available`() {
        val preview = FontsSettingsState.preview
        assertNotNull(preview)
        assertEquals("notoserifhebrew", preview.bookFontCode)
        assertEquals("frankruhllibre", preview.commentaryFontCode)
        assertEquals("taameyashkenaz", preview.targumFontCode)
        assertEquals("frankruhllibre", preview.sourceFontCode)
    }

    @Test
    fun `equals works correctly`() {
        val state1 = FontsSettingsState(bookFontCode = "font1")
        val state2 = FontsSettingsState(bookFontCode = "font1")
        val state3 = FontsSettingsState(bookFontCode = "font2")

        assertEquals(state1, state2)
        assert(state1 != state3)
    }

    @Test
    fun `default fonts are non-empty strings`() {
        val state = FontsSettingsState()

        assert(state.bookFontCode.isNotEmpty())
        assert(state.commentaryFontCode.isNotEmpty())
        assert(state.targumFontCode.isNotEmpty())
        assert(state.sourceFontCode.isNotEmpty())
    }
}
