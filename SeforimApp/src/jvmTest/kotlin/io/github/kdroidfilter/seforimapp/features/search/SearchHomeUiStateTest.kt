package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.features.search.BookSuggestionDto
import io.github.kdroidfilter.seforimapp.features.search.CategorySuggestionDto
import io.github.kdroidfilter.seforimapp.features.search.SearchFilter
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState
import io.github.kdroidfilter.seforimapp.features.search.TocSuggestionDto
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchHomeUiStateTest {
    // Default state tests
    @Test
    fun `default state has TEXT filter selected`() {
        val state = SearchHomeUiState()
        assertEquals(SearchFilter.TEXT, state.selectedFilter)
    }

    @Test
    fun `default state has globalExtended false`() {
        val state = SearchHomeUiState()
        assertFalse(state.globalExtended)
    }

    @Test
    fun `default state has suggestionsVisible false`() {
        val state = SearchHomeUiState()
        assertFalse(state.suggestionsVisible)
    }

    @Test
    fun `default state has isReferenceLoading false`() {
        val state = SearchHomeUiState()
        assertFalse(state.isReferenceLoading)
    }

    @Test
    fun `default state has empty categorySuggestions`() {
        val state = SearchHomeUiState()
        assertTrue(state.categorySuggestions.isEmpty())
    }

    @Test
    fun `default state has empty bookSuggestions`() {
        val state = SearchHomeUiState()
        assertTrue(state.bookSuggestions.isEmpty())
    }

    @Test
    fun `default state has tocSuggestionsVisible false`() {
        val state = SearchHomeUiState()
        assertFalse(state.tocSuggestionsVisible)
    }

    @Test
    fun `default state has isTocLoading false`() {
        val state = SearchHomeUiState()
        assertFalse(state.isTocLoading)
    }

    @Test
    fun `default state has empty tocSuggestions`() {
        val state = SearchHomeUiState()
        assertTrue(state.tocSuggestions.isEmpty())
    }

    @Test
    fun `default state has null selectedScopeCategory`() {
        val state = SearchHomeUiState()
        assertNull(state.selectedScopeCategory)
    }

    @Test
    fun `default state has null selectedScopeBook`() {
        val state = SearchHomeUiState()
        assertNull(state.selectedScopeBook)
    }

    @Test
    fun `default state has null selectedScopeToc`() {
        val state = SearchHomeUiState()
        assertNull(state.selectedScopeToc)
    }

    @Test
    fun `default state has empty userDisplayName`() {
        val state = SearchHomeUiState()
        assertEquals("", state.userDisplayName)
    }

    @Test
    fun `default state has null userCommunityCode`() {
        val state = SearchHomeUiState()
        assertNull(state.userCommunityCode)
    }

    @Test
    fun `default state has empty tocPreviewHints`() {
        val state = SearchHomeUiState()
        assertTrue(state.tocPreviewHints.isEmpty())
    }

    @Test
    fun `default state has empty pairedReferenceHints`() {
        val state = SearchHomeUiState()
        assertTrue(state.pairedReferenceHints.isEmpty())
    }

    // Copy tests
    @Test
    fun `copy with selectedFilter changes filter`() {
        val state = SearchHomeUiState()
        val updated = state.copy(selectedFilter = SearchFilter.REFERENCE)
        assertEquals(SearchFilter.REFERENCE, updated.selectedFilter)
    }

    @Test
    fun `copy with globalExtended changes value`() {
        val state = SearchHomeUiState()
        val updated = state.copy(globalExtended = true)
        assertTrue(updated.globalExtended)
    }

    @Test
    fun `copy with suggestionsVisible changes value`() {
        val state = SearchHomeUiState()
        val updated = state.copy(suggestionsVisible = true)
        assertTrue(updated.suggestionsVisible)
    }

    @Test
    fun `copy with userDisplayName changes value`() {
        val state = SearchHomeUiState()
        val updated = state.copy(userDisplayName = "John Doe")
        assertEquals("John Doe", updated.userDisplayName)
    }

    @Test
    fun `copy preserves unchanged values`() {
        val state = SearchHomeUiState(
            selectedFilter = SearchFilter.TEXT,
            globalExtended = true,
            userDisplayName = "Test User",
        )
        val updated = state.copy(suggestionsVisible = true)

        assertEquals(SearchFilter.TEXT, updated.selectedFilter)
        assertTrue(updated.globalExtended)
        assertEquals("Test User", updated.userDisplayName)
        assertTrue(updated.suggestionsVisible)
    }

    // Equality tests
    @Test
    fun `states with same values are equal`() {
        val state1 = SearchHomeUiState(selectedFilter = SearchFilter.TEXT, globalExtended = true)
        val state2 = SearchHomeUiState(selectedFilter = SearchFilter.TEXT, globalExtended = true)
        assertEquals(state1, state2)
    }

    @Test
    fun `states with different values are not equal`() {
        val state1 = SearchHomeUiState(selectedFilter = SearchFilter.TEXT)
        val state2 = SearchHomeUiState(selectedFilter = SearchFilter.REFERENCE)
        assertNotEquals(state1, state2)
    }
}

class CategorySuggestionDtoTest {
    private val sampleCategory = Category(
        id = 1L,
        parentId = null,
        title = "Test Category",
        order = 1.0f,
    )

    @Test
    fun `CategorySuggestionDto has correct category`() {
        val dto = CategorySuggestionDto(category = sampleCategory, path = listOf("Root", "Test Category"))
        assertEquals(sampleCategory, dto.category)
    }

    @Test
    fun `CategorySuggestionDto has correct path`() {
        val path = listOf("Root", "Sub", "Test")
        val dto = CategorySuggestionDto(category = sampleCategory, path = path)
        assertEquals(path, dto.path)
    }

    @Test
    fun `CategorySuggestionDto equality`() {
        val dto1 = CategorySuggestionDto(sampleCategory, listOf("A", "B"))
        val dto2 = CategorySuggestionDto(sampleCategory, listOf("A", "B"))
        assertEquals(dto1, dto2)
    }
}

class BookSuggestionDtoTest {
    private val sampleBook = Book(
        id = 1L,
        categoryId = 10L,
        sourceId = 0,
        title = "Test Book",
        order = 1.0f,
        isBaseBook = true,
    )

    @Test
    fun `BookSuggestionDto has correct book`() {
        val dto = BookSuggestionDto(book = sampleBook, path = listOf("Category", "Test Book"))
        assertEquals(sampleBook, dto.book)
    }

    @Test
    fun `BookSuggestionDto has correct path`() {
        val path = listOf("Torah", "Chumash", "Test Book")
        val dto = BookSuggestionDto(book = sampleBook, path = path)
        assertEquals(path, dto.path)
    }

    @Test
    fun `BookSuggestionDto equality`() {
        val dto1 = BookSuggestionDto(sampleBook, listOf("A", "B"))
        val dto2 = BookSuggestionDto(sampleBook, listOf("A", "B"))
        assertEquals(dto1, dto2)
    }
}

class TocSuggestionDtoTest {
    private val sampleToc = TocEntry(
        id = 1L,
        bookId = 10L,
        parentId = null,
        lineId = 100L,
        text = "Chapter 1",
        level = 1,
        order = 1.0f,
    )

    @Test
    fun `TocSuggestionDto has correct toc`() {
        val dto = TocSuggestionDto(toc = sampleToc, path = listOf("Book", "Chapter 1"))
        assertEquals(sampleToc, dto.toc)
    }

    @Test
    fun `TocSuggestionDto has correct path`() {
        val path = listOf("Genesis", "Chapter 1")
        val dto = TocSuggestionDto(toc = sampleToc, path = path)
        assertEquals(path, dto.path)
    }

    @Test
    fun `TocSuggestionDto equality`() {
        val dto1 = TocSuggestionDto(sampleToc, listOf("A", "B"))
        val dto2 = TocSuggestionDto(sampleToc, listOf("A", "B"))
        assertEquals(dto1, dto2)
    }
}
