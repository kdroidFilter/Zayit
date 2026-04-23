package io.github.kdroidfilter.seforimapp.core.selection

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionContextTest {
    private lateinit var context: SelectionContext

    @BeforeTest
    fun setup() {
        context = DefaultSelectionContext()
    }

    private fun book(
        id: Long,
        title: String = "ספר",
    ): Book =
        Book(
            id = id,
            categoryId = 1L,
            sourceId = 0L,
            title = title,
            heRef = title,
        )

    private fun line(
        bookId: Long,
        index: Int = 0,
    ): Line =
        Line(
            id = bookId * 1000 + index,
            bookId = bookId,
            lineIndex = index,
            content = "x",
            heRef = null,
        )

    // -------------------------------------------------------------------------------------------
    // selectedText
    // -------------------------------------------------------------------------------------------

    @Test
    fun `selectedText starts empty and round-trips updates`() {
        assertEquals("", context.selectedText.value)
        context.setSelectedText("שלום")
        assertEquals("שלום", context.selectedText.value)
        context.setSelectedText("")
        assertEquals("", context.selectedText.value)
    }

    @Test
    fun `clearSelectedText resets value`() {
        context.setSelectedText("hello")
        context.clearSelectedText()
        assertEquals("", context.selectedText.value)
    }

    // -------------------------------------------------------------------------------------------
    // activeBook
    // -------------------------------------------------------------------------------------------

    @Test
    fun `setActiveBook stamps the publishing tabId`() {
        context.setActiveBook(tabId = "tab-A", book = book(1L), rootTitle = "תנ\"ך")
        val active = context.activeBook.value
        assertEquals("tab-A", active?.tabId)
        assertEquals(1L, active?.book?.id)
    }

    @Test
    fun `setActiveBook with null book clears the slot`() {
        context.setActiveBook("tab-A", book(1L), null)
        context.setActiveBook("tab-A", null, null)
        assertNull(context.activeBook.value)
    }

    @Test
    fun `clearActiveBookIfOwnedBy only clears when tabIds match`() {
        context.setActiveBook("tab-A", book(42L), "תלמוד")
        context.clearActiveBookIfOwnedBy("tab-B")
        assertEquals("tab-A", context.activeBook.value?.tabId, "must not clear when tabId mismatches")
        context.clearActiveBookIfOwnedBy("tab-A")
        assertNull(context.activeBook.value)
    }

    @Test
    fun `same book opened in two tabs does not collide`() {
        // Tab A and Tab B both reference the same book id (legitimate when the user duplicates a tab).
        context.setActiveBook("tab-A", book(7L), "תנ\"ך")
        context.setActiveBook("tab-B", book(7L), "תנ\"ך")
        // Tab A disposes; the publish belongs to B now and must survive.
        context.clearActiveBookIfOwnedBy("tab-A")
        val active = context.activeBook.value
        assertEquals("tab-B", active?.tabId, "Tab A's dispose should not wipe Tab B's publish for the same book")
    }

    // -------------------------------------------------------------------------------------------
    // visibleLines
    // -------------------------------------------------------------------------------------------

    @Test
    fun `setVisibleLines stamps the publishing tabId`() {
        val lines = listOf(line(1, 0), line(1, 1))
        context.setVisibleLines("tab-A", lines)
        val snapshot = context.visibleLines.value
        assertEquals("tab-A", snapshot.tabId)
        assertEquals(2, snapshot.lines.size)
    }

    @Test
    fun `clearVisibleLinesIfOwnedBy only clears when tabIds match`() {
        context.setVisibleLines("tab-A", listOf(line(7L, 0), line(7L, 1)))
        context.clearVisibleLinesIfOwnedBy("tab-B")
        assertEquals("tab-A", context.visibleLines.value.tabId, "must not clear when tabId mismatches")
        context.clearVisibleLinesIfOwnedBy("tab-A")
        assertTrue(
            context.visibleLines.value.lines
                .isEmpty(),
        )
        assertNull(context.visibleLines.value.tabId)
    }

    @Test
    fun `clearVisibleLinesIfOwnedBy is a no-op when snapshot empty`() {
        context.clearVisibleLinesIfOwnedBy("tab-X")
        assertTrue(
            context.visibleLines.value.lines
                .isEmpty(),
        )
    }
}
