package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.features.search.SearchHomeNavigationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SearchHomeNavigationEventTest {
    // NavigateToSearch tests
    @Test
    fun `NavigateToSearch has correct query`() {
        val event = SearchHomeNavigationEvent.NavigateToSearch(query = "test query", tabId = "tab-1")
        assertEquals("test query", event.query)
    }

    @Test
    fun `NavigateToSearch has correct tabId`() {
        val event = SearchHomeNavigationEvent.NavigateToSearch(query = "test", tabId = "tab-123")
        assertEquals("tab-123", event.tabId)
    }

    @Test
    fun `NavigateToSearch handles Hebrew query`() {
        val event = SearchHomeNavigationEvent.NavigateToSearch(query = "שלום עולם", tabId = "tab-1")
        assertEquals("שלום עולם", event.query)
    }

    @Test
    fun `NavigateToSearch instances with same values are equal`() {
        val event1 = SearchHomeNavigationEvent.NavigateToSearch("query", "tab-1")
        val event2 = SearchHomeNavigationEvent.NavigateToSearch("query", "tab-1")
        assertEquals(event1, event2)
    }

    @Test
    fun `NavigateToSearch instances with different queries are not equal`() {
        val event1 = SearchHomeNavigationEvent.NavigateToSearch("query1", "tab-1")
        val event2 = SearchHomeNavigationEvent.NavigateToSearch("query2", "tab-1")
        assertNotEquals(event1, event2)
    }

    // NavigateToBookContent tests
    @Test
    fun `NavigateToBookContent has correct bookId`() {
        val event = SearchHomeNavigationEvent.NavigateToBookContent(bookId = 123L, tabId = "tab-1", lineId = null)
        assertEquals(123L, event.bookId)
    }

    @Test
    fun `NavigateToBookContent has correct tabId`() {
        val event = SearchHomeNavigationEvent.NavigateToBookContent(bookId = 1L, tabId = "tab-123", lineId = null)
        assertEquals("tab-123", event.tabId)
    }

    @Test
    fun `NavigateToBookContent with null lineId`() {
        val event = SearchHomeNavigationEvent.NavigateToBookContent(bookId = 1L, tabId = "tab-1", lineId = null)
        assertNull(event.lineId)
    }

    @Test
    fun `NavigateToBookContent with lineId`() {
        val event = SearchHomeNavigationEvent.NavigateToBookContent(bookId = 1L, tabId = "tab-1", lineId = 456L)
        assertEquals(456L, event.lineId)
    }

    @Test
    fun `NavigateToBookContent instances with same values are equal`() {
        val event1 = SearchHomeNavigationEvent.NavigateToBookContent(1L, "tab-1", 100L)
        val event2 = SearchHomeNavigationEvent.NavigateToBookContent(1L, "tab-1", 100L)
        assertEquals(event1, event2)
    }

    @Test
    fun `NavigateToBookContent instances with different bookId are not equal`() {
        val event1 = SearchHomeNavigationEvent.NavigateToBookContent(1L, "tab-1", null)
        val event2 = SearchHomeNavigationEvent.NavigateToBookContent(2L, "tab-1", null)
        assertNotEquals(event1, event2)
    }

    // Sealed class tests
    @Test
    fun `events are SearchHomeNavigationEvent subtypes`() {
        val navigateToSearch: SearchHomeNavigationEvent = SearchHomeNavigationEvent.NavigateToSearch("q", "t")
        val navigateToBook: SearchHomeNavigationEvent = SearchHomeNavigationEvent.NavigateToBookContent(1L, "t", null)

        assertIs<SearchHomeNavigationEvent>(navigateToSearch)
        assertIs<SearchHomeNavigationEvent>(navigateToBook)
    }

    @Test
    fun `when expression covers all event types`() {
        fun describe(event: SearchHomeNavigationEvent): String = when (event) {
            is SearchHomeNavigationEvent.NavigateToSearch -> "search: ${event.query}"
            is SearchHomeNavigationEvent.NavigateToBookContent -> "book: ${event.bookId}"
        }

        assertEquals("search: test", describe(SearchHomeNavigationEvent.NavigateToSearch("test", "tab")))
        assertEquals("book: 42", describe(SearchHomeNavigationEvent.NavigateToBookContent(42L, "tab", null)))
    }
}
