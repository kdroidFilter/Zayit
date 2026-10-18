package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookContentStateTest {
    @Test
    fun `BookContentState has correct default values`() {
        val state = BookContentState()

        assertEquals("", state.tabId)
        assertFalse(state.isLoading)
        assertNull(state.providers)
    }

    @Test
    fun `NavigationState has correct default values`() {
        val state = NavigationState()

        assertTrue(state.rootCategories.isEmpty())
        assertTrue(state.expandedCategories.isEmpty())
        assertTrue(state.categoryChildren.isEmpty())
        assertTrue(state.booksInCategory.isEmpty())
        assertNull(state.selectedCategory)
        assertNull(state.selectedBook)
        assertEquals("", state.searchText)
        assertTrue(state.isVisible)
        assertEquals(0, state.scrollIndex)
        assertEquals(0, state.scrollOffset)
    }

    @Test
    fun `TocState has correct default values`() {
        val state = TocState()

        assertTrue(state.entries.isEmpty())
        assertTrue(state.expandedEntries.isEmpty())
        assertTrue(state.children.isEmpty())
        assertNull(state.selectedEntryId)
        assertTrue(state.breadcrumbPath.isEmpty())
        assertFalse(state.isVisible)
        assertEquals(0, state.scrollIndex)
        assertEquals(0, state.scrollOffset)
    }

    @Test
    fun `ContentState has correct default values`() {
        val state = ContentState()

        assertTrue(state.lines.isEmpty())
        assertTrue(state.selectedLines.isEmpty())
        assertNull(state.primaryLine)
        assertTrue(state.commentaries.isEmpty())
        assertFalse(state.showCommentaries)
        assertFalse(state.showTargum)
        assertFalse(state.showSources)
        assertEquals(0, state.paragraphScrollPosition)
        assertEquals(0, state.chapterScrollPosition)
        assertEquals(0, state.selectedChapter)
        assertEquals(-1L, state.anchorId)
        assertEquals(0, state.anchorIndex)
    }

    @Test
    fun `AltTocState has correct default values`() {
        val state = AltTocState()

        assertTrue(state.structures.isEmpty())
        assertNull(state.selectedStructureId)
        assertTrue(state.entries.isEmpty())
        assertTrue(state.expandedEntries.isEmpty())
        assertTrue(state.children.isEmpty())
        assertNull(state.selectedEntryId)
        assertEquals(0, state.scrollIndex)
        assertEquals(0, state.scrollOffset)
    }

    @Test
    fun `PreviousPositions has correct default values`() {
        val positions = PreviousPositions()

        assertEquals(SplitDefaults.MAIN, positions.main)
        assertEquals(SplitDefaults.TOC, positions.toc)
        assertEquals(SplitDefaults.CONTENT, positions.content)
        assertEquals(SplitDefaults.SOURCES, positions.sources)
        assertEquals(0.8f, positions.links)
    }

    @Test
    fun `VisibleTocEntry can be created with valid data`() {
        val mockEntry =
            io.github.kdroidfilter.seforimlibrary.core.models.TocEntry(
                id = 1L,
                bookId = 100L,
                parentId = null,
                text = "Chapter 1",
                level = 0,
                lineId = 50L,
                hasChildren = true,
            )

        val visibleEntry =
            VisibleTocEntry(
                entry = mockEntry,
                level = 0,
                isExpanded = true,
                hasChildren = true,
                isLastChild = false,
            )

        assertEquals(mockEntry, visibleEntry.entry)
        assertEquals(0, visibleEntry.level)
        assertTrue(visibleEntry.isExpanded)
        assertTrue(visibleEntry.hasChildren)
        assertFalse(visibleEntry.isLastChild)
    }

    @Test
    fun `NavigationState copy works correctly`() {
        val original = NavigationState(searchText = "test", isVisible = true)
        val modified = original.copy(searchText = "modified", isVisible = false)

        assertEquals("test", original.searchText)
        assertTrue(original.isVisible)
        assertEquals("modified", modified.searchText)
        assertFalse(modified.isVisible)
    }

    @Test
    fun `ContentState copy preserves unchanged values`() {
        val original =
            ContentState(
                showCommentaries = true,
                scrollIndex = 5,
                anchorId = 100L,
            )
        val modified = original.copy(showCommentaries = false)

        assertFalse(modified.showCommentaries)
        assertEquals(5, modified.scrollIndex)
        assertEquals(100L, modified.anchorId)
    }
}
