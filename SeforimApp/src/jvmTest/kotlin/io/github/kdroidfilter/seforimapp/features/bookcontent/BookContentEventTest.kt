package io.github.kdroidfilter.seforimapp.features.bookcontent

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BookContentEventTest {
    private val testCategory =
        Category(
            id = 1L,
            parentId = null,
            title = "Test Category",
            level = 0,
            order = 1,
        )

    private val testBook =
        Book(
            id = 1L,
            categoryId = 1L,
            sourceId = 1L,
            title = "Test Book",
        )

    private val testLine =
        Line(
            id = 1L,
            bookId = 1L,
            lineIndex = 1,
            content = "Test line content",
        )

    private val testTocEntry =
        TocEntry(
            id = 1L,
            bookId = 1L,
            parentId = null,
            text = "Chapter 1",
            level = 0,
            lineId = 1L,
            hasChildren = false,
        )

    @Test
    fun `SearchTextChanged stores text correctly`() {
        val event = BookContentEvent.SearchTextChanged("search query")
        assertEquals("search query", event.text)
        assertIs<BookContentEvent>(event)
    }

    @Test
    fun `CategorySelected stores category correctly`() {
        val event = BookContentEvent.CategorySelected(testCategory)
        assertEquals(testCategory, event.category)
        assertEquals(1L, event.category.id)
    }

    @Test
    fun `BookSelected stores book correctly`() {
        val event = BookContentEvent.BookSelected(testBook)
        assertEquals(testBook, event.book)
        assertEquals("Test Book", event.book.title)
    }

    @Test
    fun `LineSelected stores line correctly`() {
        val event = BookContentEvent.LineSelected(testLine)
        assertEquals(testLine, event.line)
        assertEquals(1L, event.line.id)
    }

    @Test
    fun `TocEntryExpanded stores entry correctly`() {
        val event = BookContentEvent.TocEntryExpanded(testTocEntry)
        assertEquals(testTocEntry, event.entry)
        assertEquals("Chapter 1", event.entry.text)
    }

    @Test
    fun `ContentScrolled stores all scroll parameters`() {
        val event =
            BookContentEvent.ContentScrolled(
                anchorId = 100L,
                anchorIndex = 5,
                scrollIndex = 10,
                scrollOffset = 50,
            )
        assertEquals(100L, event.anchorId)
        assertEquals(5, event.anchorIndex)
        assertEquals(10, event.scrollIndex)
        assertEquals(50, event.scrollOffset)
    }

    @Test
    fun `LoadAndSelectLine stores lineId correctly`() {
        val event = BookContentEvent.LoadAndSelectLine(lineId = 42L)
        assertEquals(42L, event.lineId)
    }

    @Test
    fun `OpenBookAtLine stores both bookId and lineId`() {
        val event = BookContentEvent.OpenBookAtLine(bookId = 10L, lineId = 20L)
        assertEquals(10L, event.bookId)
        assertEquals(20L, event.lineId)
    }

    @Test
    fun `SelectedCommentatorsChanged stores lineId and selectedIds`() {
        val selectedIds = setOf(1L, 2L, 3L)
        val event =
            BookContentEvent.SelectedCommentatorsChanged(
                lineId = 100L,
                selectedIds = selectedIds,
            )
        assertEquals(100L, event.lineId)
        assertEquals(selectedIds, event.selectedIds)
        assertTrue(event.selectedIds.contains(2L))
    }

    @Test
    fun `data objects are singletons`() {
        val toggle1 = BookContentEvent.ToggleBookTree
        val toggle2 = BookContentEvent.ToggleBookTree
        assertEquals(toggle1, toggle2)

        val toggleToc1 = BookContentEvent.ToggleToc
        val toggleToc2 = BookContentEvent.ToggleToc
        assertEquals(toggleToc1, toggleToc2)

        val toggleComm1 = BookContentEvent.ToggleCommentaries
        val toggleComm2 = BookContentEvent.ToggleCommentaries
        assertEquals(toggleComm1, toggleComm2)
    }

    @Test
    fun `BookTreeScrolled stores index and offset`() {
        val event = BookContentEvent.BookTreeScrolled(index = 15, offset = 100)
        assertEquals(15, event.index)
        assertEquals(100, event.offset)
    }

    @Test
    fun `CommentaryColumnScrolled stores commentatorId and scroll position`() {
        val event =
            BookContentEvent.CommentaryColumnScrolled(
                commentatorId = 5L,
                index = 10,
                offset = 25,
            )
        assertEquals(5L, event.commentatorId)
        assertEquals(10, event.index)
        assertEquals(25, event.offset)
    }

    @Test
    fun `all toggle events are BookContentEvent instances`() {
        val toggleEvents: List<BookContentEvent> =
            listOf(
                BookContentEvent.ToggleBookTree,
                BookContentEvent.ToggleToc,
                BookContentEvent.ToggleCommentaries,
                BookContentEvent.ToggleTargum,
                BookContentEvent.ToggleSources,
                BookContentEvent.ToggleDiacritics,
                BookContentEvent.SaveState,
                BookContentEvent.NavigateToPreviousLine,
                BookContentEvent.NavigateToNextLine,
            )

        assertEquals(9, toggleEvents.size)
        toggleEvents.forEach { event ->
            assertIs<BookContentEvent>(event)
        }
    }
}
