package io.github.kdroidfilter.seforimapp.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppSettingsConstantsTest {
    @Test
    fun `DEFAULT_TEXT_SIZE is 16`() {
        assertEquals(16f, AppSettings.DEFAULT_TEXT_SIZE)
    }

    @Test
    fun `MIN_TEXT_SIZE is 14`() {
        assertEquals(14f, AppSettings.MIN_TEXT_SIZE)
    }

    @Test
    fun `MAX_TEXT_SIZE is 32`() {
        assertEquals(32f, AppSettings.MAX_TEXT_SIZE)
    }

    @Test
    fun `TEXT_SIZE_INCREMENT is 2`() {
        assertEquals(2f, AppSettings.TEXT_SIZE_INCREMENT)
    }

    @Test
    fun `DEFAULT_LINE_HEIGHT is 1_5`() {
        assertEquals(1.5f, AppSettings.DEFAULT_LINE_HEIGHT)
    }

    @Test
    fun `MIN_LINE_HEIGHT is 1_0`() {
        assertEquals(1.0f, AppSettings.MIN_LINE_HEIGHT)
    }

    @Test
    fun `MAX_LINE_HEIGHT is 2_5`() {
        assertEquals(2.5f, AppSettings.MAX_LINE_HEIGHT)
    }

    @Test
    fun `LINE_HEIGHT_INCREMENT is 0_1`() {
        assertEquals(0.1f, AppSettings.LINE_HEIGHT_INCREMENT)
    }

    @Test
    fun `DEFAULT_BOOK_FONT is notoserifhebrew`() {
        assertEquals("notoserifhebrew", AppSettings.DEFAULT_BOOK_FONT)
    }

    @Test
    fun `DEFAULT_COMMENTARY_FONT is frankruhllibre`() {
        assertEquals("frankruhllibre", AppSettings.DEFAULT_COMMENTARY_FONT)
    }

    @Test
    fun `DEFAULT_TARGUM_FONT is taameyashkenaz`() {
        assertEquals("taameyashkenaz", AppSettings.DEFAULT_TARGUM_FONT)
    }

    @Test
    fun `DEFAULT_SOURCE_FONT is tinos`() {
        assertEquals("tinos", AppSettings.DEFAULT_SOURCE_FONT)
    }

    @Test
    fun `MAX_TAB_TITLE_LENGTH is 20`() {
        assertEquals(20, AppSettings.MAX_TAB_TITLE_LENGTH)
    }

    @Test
    fun `TAB_FIXED_WIDTH_DP is 180`() {
        assertEquals(180, AppSettings.TAB_FIXED_WIDTH_DP)
    }

    @Test
    fun `text size range is valid`() {
        assertTrue(AppSettings.MIN_TEXT_SIZE < AppSettings.MAX_TEXT_SIZE)
        assertTrue(AppSettings.DEFAULT_TEXT_SIZE >= AppSettings.MIN_TEXT_SIZE)
        assertTrue(AppSettings.DEFAULT_TEXT_SIZE <= AppSettings.MAX_TEXT_SIZE)
    }

    @Test
    fun `line height range is valid`() {
        assertTrue(AppSettings.MIN_LINE_HEIGHT < AppSettings.MAX_LINE_HEIGHT)
        assertTrue(AppSettings.DEFAULT_LINE_HEIGHT >= AppSettings.MIN_LINE_HEIGHT)
        assertTrue(AppSettings.DEFAULT_LINE_HEIGHT <= AppSettings.MAX_LINE_HEIGHT)
    }
}
