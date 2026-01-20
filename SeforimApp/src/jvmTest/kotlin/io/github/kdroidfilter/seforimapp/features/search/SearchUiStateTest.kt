package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchUiStateTest {
    @Test
    fun `default state has correct values`() {
        val state = SearchUiState()

        assertEquals("", state.query)
        assertFalse(state.globalExtended)
        assertFalse(state.isLoading)
        assertTrue(state.results.isEmpty())
        assertTrue(state.scopeCategoryPath.isEmpty())
        assertNull(state.scopeBook)
        assertNull(state.scopeTocId)
        assertEquals(0, state.scrollIndex)
        assertEquals(0, state.scrollOffset)
        assertEquals(-1L, state.anchorId)
        assertEquals(0, state.anchorIndex)
        assertEquals(0L, state.scrollToAnchorTimestamp)
        assertEquals(AppSettings.DEFAULT_TEXT_SIZE, state.textSize)
        assertFalse(state.hasMore)
        assertFalse(state.isLoadingMore)
        assertEquals(0, state.progressCurrent)
        assertNull(state.progressTotal)
    }

    @Test
    fun `state can be created with custom query`() {
        val state = SearchUiState(query = "search term")
        assertEquals("search term", state.query)
    }

    @Test
    fun `state can be created with loading true`() {
        val state = SearchUiState(isLoading = true)
        assertTrue(state.isLoading)
    }

    @Test
    fun `state can be created with global extended`() {
        val state = SearchUiState(globalExtended = true)
        assertTrue(state.globalExtended)
    }

    @Test
    fun `state can be created with scope book`() {
        val book = Book(id = 1L, categoryId = 1L, sourceId = 1L, title = "Test Book")
        val state = SearchUiState(scopeBook = book)
        assertEquals(book, state.scopeBook)
    }

    @Test
    fun `state can be created with scope category path`() {
        val categories =
            listOf(
                Category(id = 1L, title = "Root", parentId = null),
                Category(id = 2L, title = "Child", parentId = 1L),
            )
        val state = SearchUiState(scopeCategoryPath = categories)
        assertEquals(2, state.scopeCategoryPath.size)
    }

    @Test
    fun `state can be created with pagination values`() {
        val state =
            SearchUiState(
                hasMore = true,
                isLoadingMore = true,
                progressCurrent = 50,
                progressTotal = 100L,
            )
        assertTrue(state.hasMore)
        assertTrue(state.isLoadingMore)
        assertEquals(50, state.progressCurrent)
        assertEquals(100L, state.progressTotal)
    }

    @Test
    fun `state can be created with scroll values`() {
        val state =
            SearchUiState(
                scrollIndex = 10,
                scrollOffset = 50,
                anchorId = 123L,
                anchorIndex = 5,
                scrollToAnchorTimestamp = 1000L,
            )
        assertEquals(10, state.scrollIndex)
        assertEquals(50, state.scrollOffset)
        assertEquals(123L, state.anchorId)
        assertEquals(5, state.anchorIndex)
        assertEquals(1000L, state.scrollToAnchorTimestamp)
    }

    @Test
    fun `state can be created with custom text size`() {
        val state = SearchUiState(textSize = 20f)
        assertEquals(20f, state.textSize)
    }

    @Test
    fun `copy preserves unchanged values`() {
        val original =
            SearchUiState(
                query = "original query",
                isLoading = true,
            )
        val modified = original.copy(isLoading = false)

        assertEquals("original query", modified.query)
        assertFalse(modified.isLoading)
    }

    @Test
    fun `equals works correctly`() {
        val state1 = SearchUiState(query = "test")
        val state2 = SearchUiState(query = "test")
        val state3 = SearchUiState(query = "different")

        assertEquals(state1, state2)
        assertTrue(state1 != state3)
    }
}
