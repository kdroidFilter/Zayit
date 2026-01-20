package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitDefaultsTest {
    @Test
    fun `MAIN default is valid percentage`() {
        assertTrue(SplitDefaults.MAIN >= 0f && SplitDefaults.MAIN <= 1f)
        assertEquals(0.05f, SplitDefaults.MAIN)
    }

    @Test
    fun `TOC default is valid percentage`() {
        assertTrue(SplitDefaults.TOC >= 0f && SplitDefaults.TOC <= 1f)
        assertEquals(0.05f, SplitDefaults.TOC)
    }

    @Test
    fun `CONTENT default is valid percentage`() {
        assertTrue(SplitDefaults.CONTENT >= 0f && SplitDefaults.CONTENT <= 1f)
        assertEquals(0.7f, SplitDefaults.CONTENT)
    }

    @Test
    fun `SOURCES default is valid percentage`() {
        assertTrue(SplitDefaults.SOURCES >= 0f && SplitDefaults.SOURCES <= 1f)
        assertEquals(0.85f, SplitDefaults.SOURCES)
    }

    @Test
    fun `MIN_MAIN is positive value`() {
        assertTrue(SplitDefaults.MIN_MAIN > 0f)
        assertEquals(150f, SplitDefaults.MIN_MAIN)
    }

    @Test
    fun `MIN_TOC is positive value`() {
        assertTrue(SplitDefaults.MIN_TOC > 0f)
        assertEquals(120f, SplitDefaults.MIN_TOC)
    }

    @Test
    fun `CONTENT is larger than MAIN and TOC`() {
        assertTrue(SplitDefaults.CONTENT > SplitDefaults.MAIN)
        assertTrue(SplitDefaults.CONTENT > SplitDefaults.TOC)
    }

    @Test
    fun `SOURCES is larger than CONTENT`() {
        assertTrue(SplitDefaults.SOURCES > SplitDefaults.CONTENT)
    }
}
