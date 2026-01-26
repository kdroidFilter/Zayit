package io.github.kdroidfilter.seforimapp.navigation

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TabsDestinationTest {
    private val json = Json { ignoreUnknownKeys = true }

    // Home destination tests
    @Test
    fun `Home destination has correct tabId`() {
        val dest = TabsDestination.Home(tabId = "test-tab")
        assertEquals("test-tab", dest.tabId)
    }

    @Test
    fun `Home destination default version is 0`() {
        val dest = TabsDestination.Home(tabId = "test-tab")
        assertEquals(0L, dest.version)
    }

    @Test
    fun `Home destination with custom version`() {
        val dest = TabsDestination.Home(tabId = "test-tab", version = 12345L)
        assertEquals(12345L, dest.version)
    }

    @Test
    fun `Home destinations with same values are equal`() {
        val dest1 = TabsDestination.Home(tabId = "tab", version = 1L)
        val dest2 = TabsDestination.Home(tabId = "tab", version = 1L)
        assertEquals(dest1, dest2)
    }

    @Test
    fun `Home destination is serializable`() {
        val original = TabsDestination.Home(tabId = "test-tab", version = 123L)
        val encoded = json.encodeToString<TabsDestination>(original)
        val decoded = json.decodeFromString<TabsDestination>(encoded)
        assertEquals(original, decoded)
    }

    // Search destination tests
    @Test
    fun `Search destination has correct properties`() {
        val dest = TabsDestination.Search(searchQuery = "test query", tabId = "search-tab")
        assertEquals("test query", dest.searchQuery)
        assertEquals("search-tab", dest.tabId)
    }

    @Test
    fun `Search destinations with same values are equal`() {
        val dest1 = TabsDestination.Search(searchQuery = "query", tabId = "tab")
        val dest2 = TabsDestination.Search(searchQuery = "query", tabId = "tab")
        assertEquals(dest1, dest2)
    }

    @Test
    fun `Search destinations with different queries are not equal`() {
        val dest1 = TabsDestination.Search(searchQuery = "query1", tabId = "tab")
        val dest2 = TabsDestination.Search(searchQuery = "query2", tabId = "tab")
        assertNotEquals(dest1, dest2)
    }

    @Test
    fun `Search destination is serializable`() {
        val original = TabsDestination.Search(searchQuery = "test", tabId = "search-tab")
        val encoded = json.encodeToString<TabsDestination>(original)
        val decoded = json.decodeFromString<TabsDestination>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Search destination handles Hebrew query`() {
        val dest = TabsDestination.Search(searchQuery = "שלום עולם", tabId = "tab")
        assertEquals("שלום עולם", dest.searchQuery)
    }

    // BookContent destination tests
    @Test
    fun `BookContent destination has correct properties`() {
        val dest = TabsDestination.BookContent(bookId = 123L, tabId = "book-tab")
        assertEquals(123L, dest.bookId)
        assertEquals("book-tab", dest.tabId)
        assertNull(dest.lineId)
    }

    @Test
    fun `BookContent destination with lineId`() {
        val dest = TabsDestination.BookContent(bookId = 123L, tabId = "book-tab", lineId = 456L)
        assertEquals(123L, dest.bookId)
        assertEquals(456L, dest.lineId)
    }

    @Test
    fun `BookContent destinations with same values are equal`() {
        val dest1 = TabsDestination.BookContent(bookId = 1, tabId = "tab", lineId = 2)
        val dest2 = TabsDestination.BookContent(bookId = 1, tabId = "tab", lineId = 2)
        assertEquals(dest1, dest2)
    }

    @Test
    fun `BookContent destination is serializable`() {
        val original = TabsDestination.BookContent(bookId = 123L, tabId = "tab", lineId = 456L)
        val encoded = json.encodeToString<TabsDestination>(original)
        val decoded = json.decodeFromString<TabsDestination>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `BookContent destination with null lineId is serializable`() {
        val original = TabsDestination.BookContent(bookId = 123L, tabId = "tab")
        val encoded = json.encodeToString<TabsDestination>(original)
        val decoded = json.decodeFromString<TabsDestination>(encoded)
        assertEquals(original, decoded)
    }

    // Polymorphic tests
    @Test
    fun `destinations are TabsDestination subtypes`() {
        val home: TabsDestination = TabsDestination.Home("tab")
        val search: TabsDestination = TabsDestination.Search("query", "tab")
        val book: TabsDestination = TabsDestination.BookContent(1L, "tab")

        assertIs<TabsDestination>(home)
        assertIs<TabsDestination>(search)
        assertIs<TabsDestination>(book)
    }

    @Test
    fun `tabId is accessible from sealed interface`() {
        val destinations: List<TabsDestination> = listOf(
            TabsDestination.Home("tab1"),
            TabsDestination.Search("query", "tab2"),
            TabsDestination.BookContent(1L, "tab3"),
        )

        assertEquals("tab1", destinations[0].tabId)
        assertEquals("tab2", destinations[1].tabId)
        assertEquals("tab3", destinations[2].tabId)
    }
}
