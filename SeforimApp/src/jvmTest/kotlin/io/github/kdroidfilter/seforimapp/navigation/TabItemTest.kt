package io.github.kdroidfilter.seforimapp.navigation

import io.github.kdroidfilter.seforim.tabs.TabItem
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class TabItemTest {
    @Test
    fun `TabItem has correct id`() {
        val item = TabItem(id = 42)
        assertEquals(42, item.id)
    }

    @Test
    fun `TabItem default title is Default Tab`() {
        val item = TabItem(id = 1)
        assertEquals("Default Tab", item.title)
    }

    @Test
    fun `TabItem with custom title`() {
        val item = TabItem(id = 1, title = "Custom Title")
        assertEquals("Custom Title", item.title)
    }

    @Test
    fun `TabItem default destination is Home`() {
        val item = TabItem(id = 1)
        assertIs<TabsDestination.Home>(item.destination)
    }

    @Test
    fun `TabItem with custom destination`() {
        val destination = TabsDestination.Search(searchQuery = "test", tabId = "tab-1")
        val item = TabItem(id = 1, destination = destination)
        assertIs<TabsDestination.Search>(item.destination)
        assertEquals("test", (item.destination as TabsDestination.Search).searchQuery)
    }

    @Test
    fun `TabItem default tabType is SEARCH`() {
        val item = TabItem(id = 1)
        assertEquals(TabType.SEARCH, item.tabType)
    }

    @Test
    fun `TabItem with BOOK tabType`() {
        val item = TabItem(id = 1, tabType = TabType.BOOK)
        assertEquals(TabType.BOOK, item.tabType)
    }

    @Test
    fun `TabItem copy changes specified fields`() {
        val original = TabItem(
            id = 1,
            title = "Original",
            destination = TabsDestination.Home("tab-1"),
            tabType = TabType.SEARCH,
        )

        val copied = original.copy(title = "Copied", tabType = TabType.BOOK)

        assertEquals(1, copied.id)
        assertEquals("Copied", copied.title)
        assertEquals(TabType.BOOK, copied.tabType)
        assertEquals(original.destination, copied.destination)
    }

    @Test
    fun `TabItems with same values are equal`() {
        val dest = TabsDestination.Home("tab-1")
        val item1 = TabItem(id = 1, title = "Title", destination = dest, tabType = TabType.BOOK)
        val item2 = TabItem(id = 1, title = "Title", destination = dest, tabType = TabType.BOOK)
        assertEquals(item1, item2)
    }

    @Test
    fun `TabItems with different ids are not equal`() {
        val item1 = TabItem(id = 1)
        val item2 = TabItem(id = 2)
        assertNotEquals(item1, item2)
    }

    @Test
    fun `TabItem hashCode is consistent`() {
        val item1 = TabItem(id = 1, title = "Test")
        val item2 = TabItem(id = 1, title = "Test")
        assertEquals(item1.hashCode(), item2.hashCode())
    }

    @Test
    fun `TabItem toString contains relevant info`() {
        val item = TabItem(id = 42, title = "My Tab")
        val str = item.toString()
        assert(str.contains("42"))
        assert(str.contains("My Tab"))
    }

    @Test
    fun `TabItem handles Hebrew title`() {
        val item = TabItem(id = 1, title = "שלום עולם")
        assertEquals("שלום עולם", item.title)
    }

    @Test
    fun `TabItem with BookContent destination`() {
        val destination = TabsDestination.BookContent(bookId = 123L, tabId = "tab-1", lineId = 456L)
        val item = TabItem(id = 1, destination = destination)
        assertIs<TabsDestination.BookContent>(item.destination)
        assertEquals(123L, (item.destination as TabsDestination.BookContent).bookId)
    }
}
