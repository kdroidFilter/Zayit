package io.github.kdroidfilter.seforimapp.core.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TocQuickLinkTest {
    @Test
    fun `TocQuickLink stores all values`() {
        val link =
            TocQuickLink(
                label = "Chapter 1",
                tocId = 42L,
                firstLineId = 100L,
            )

        assertEquals("Chapter 1", link.label)
        assertEquals(42L, link.tocId)
        assertEquals(100L, link.firstLineId)
    }

    @Test
    fun `TocQuickLink firstLineId can be null`() {
        val link =
            TocQuickLink(
                label = "Section",
                tocId = 10L,
                firstLineId = null,
            )

        assertNull(link.firstLineId)
    }

    @Test
    fun `copy preserves unchanged values`() {
        val original =
            TocQuickLink(
                label = "Original",
                tocId = 1L,
                firstLineId = 50L,
            )
        val modified = original.copy(label = "Modified")

        assertEquals("Modified", modified.label)
        assertEquals(1L, modified.tocId)
        assertEquals(50L, modified.firstLineId)
    }

    @Test
    fun `equals works correctly`() {
        val link1 = TocQuickLink("A", 1L, 10L)
        val link2 = TocQuickLink("A", 1L, 10L)
        val link3 = TocQuickLink("B", 1L, 10L)

        assertEquals(link1, link2)
        assertTrue(link1 != link3)
    }

    @Test
    fun `TocQuickLink with empty label`() {
        val link = TocQuickLink(label = "", tocId = 1L, firstLineId = null)
        assertEquals("", link.label)
    }
}
