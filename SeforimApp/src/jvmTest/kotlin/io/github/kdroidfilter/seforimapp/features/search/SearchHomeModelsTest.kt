package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchHomeModelsTest {
    private val testCategory = Category(id = 1L, title = "Test Category", parentId = null)
    private val testBook = Book(id = 1L, categoryId = 1L, sourceId = 1L, title = "Test Book")
    private val testTocEntry = TocEntry(id = 1L, bookId = 1L, text = "Chapter 1", level = 1)

    // SearchHomeNavigationEvent tests
    @Test
    fun `NavigateToSearch stores query and tabId`() {
        val event = SearchHomeNavigationEvent.NavigateToSearch(query = "test query", tabId = "tab-123")
        assertEquals("test query", event.query)
        assertEquals("tab-123", event.tabId)
        assertIs<SearchHomeNavigationEvent>(event)
    }

    @Test
    fun `NavigateToBookContent stores all values`() {
        val event =
            SearchHomeNavigationEvent.NavigateToBookContent(
                bookId = 42L,
                tabId = "tab-456",
                lineId = 100L,
            )
        assertEquals(42L, event.bookId)
        assertEquals("tab-456", event.tabId)
        assertEquals(100L, event.lineId)
        assertIs<SearchHomeNavigationEvent>(event)
    }

    @Test
    fun `NavigateToBookContent lineId can be null`() {
        val event =
            SearchHomeNavigationEvent.NavigateToBookContent(
                bookId = 42L,
                tabId = "tab-456",
                lineId = null,
            )
        assertNull(event.lineId)
    }

    @Test
    fun `NavigateToSearch equals works correctly`() {
        val event1 = SearchHomeNavigationEvent.NavigateToSearch("a", "b")
        val event2 = SearchHomeNavigationEvent.NavigateToSearch("a", "b")
        val event3 = SearchHomeNavigationEvent.NavigateToSearch("x", "y")
        assertEquals(event1, event2)
        assertTrue(event1 != event3)
    }

    // CategorySuggestionDto tests
    @Test
    fun `CategorySuggestionDto stores category and path`() {
        val path = listOf("Root", "Parent")
        val dto = CategorySuggestionDto(category = testCategory, path = path)
        assertEquals(testCategory, dto.category)
        assertEquals(2, dto.path.size)
    }

    @Test
    fun `CategorySuggestionDto path can be empty`() {
        val dto = CategorySuggestionDto(category = testCategory, path = emptyList())
        assertTrue(dto.path.isEmpty())
    }

    // BookSuggestionDto tests
    @Test
    fun `BookSuggestionDto stores book and path`() {
        val path = listOf("Category", "SubCategory")
        val dto = BookSuggestionDto(book = testBook, path = path)
        assertEquals(testBook, dto.book)
        assertEquals(2, dto.path.size)
    }

    // TocSuggestionDto tests
    @Test
    fun `TocSuggestionDto stores toc and path`() {
        val path = listOf("Book", "Section")
        val dto = TocSuggestionDto(toc = testTocEntry, path = path)
        assertEquals(testTocEntry, dto.toc)
        assertEquals(2, dto.path.size)
    }

    // SearchHomeUiState tests
    @Test
    fun `SearchHomeUiState has correct defaults`() {
        val state = SearchHomeUiState()
        assertEquals(SearchFilter.TEXT, state.selectedFilter)
        assertFalse(state.globalExtended)
        assertFalse(state.suggestionsVisible)
        assertFalse(state.isReferenceLoading)
        assertTrue(state.categorySuggestions.isEmpty())
        assertTrue(state.bookSuggestions.isEmpty())
        assertFalse(state.tocSuggestionsVisible)
        assertFalse(state.isTocLoading)
        assertTrue(state.tocSuggestions.isEmpty())
        assertNull(state.selectedScopeCategory)
        assertNull(state.selectedScopeBook)
        assertNull(state.selectedScopeToc)
        assertEquals("", state.userDisplayName)
        assertNull(state.userCommunityCode)
        assertTrue(state.tocPreviewHints.isEmpty())
        assertTrue(state.pairedReferenceHints.isEmpty())
    }

    @Test
    fun `SearchHomeUiState can be created with custom values`() {
        val state =
            SearchHomeUiState(
                selectedFilter = SearchFilter.REFERENCE,
                globalExtended = true,
                suggestionsVisible = true,
                isReferenceLoading = true,
                userDisplayName = "John Doe",
                userCommunityCode = "SEPHARADE",
            )

        assertEquals(SearchFilter.REFERENCE, state.selectedFilter)
        assertTrue(state.globalExtended)
        assertTrue(state.suggestionsVisible)
        assertTrue(state.isReferenceLoading)
        assertEquals("John Doe", state.userDisplayName)
        assertEquals("SEPHARADE", state.userCommunityCode)
    }

    @Test
    fun `SearchHomeUiState copy preserves unchanged values`() {
        val original =
            SearchHomeUiState(
                selectedFilter = SearchFilter.REFERENCE,
                userDisplayName = "Test",
            )
        val modified = original.copy(globalExtended = true)

        assertEquals(SearchFilter.REFERENCE, modified.selectedFilter)
        assertEquals("Test", modified.userDisplayName)
        assertTrue(modified.globalExtended)
    }

    @Test
    fun `SearchHomeUiState equals works correctly`() {
        val state1 = SearchHomeUiState(userDisplayName = "A")
        val state2 = SearchHomeUiState(userDisplayName = "A")
        val state3 = SearchHomeUiState(userDisplayName = "B")

        assertEquals(state1, state2)
        assertTrue(state1 != state3)
    }
}
