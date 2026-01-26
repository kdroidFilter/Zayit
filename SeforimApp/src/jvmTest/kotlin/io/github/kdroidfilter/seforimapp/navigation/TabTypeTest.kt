package io.github.kdroidfilter.seforimapp.navigation

import io.github.kdroidfilter.seforim.tabs.TabType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TabTypeTest {
    @Test
    fun `TabType has BOOK value`() {
        val type = TabType.BOOK
        assertEquals(TabType.BOOK, type)
    }

    @Test
    fun `TabType has SEARCH value`() {
        val type = TabType.SEARCH
        assertEquals(TabType.SEARCH, type)
    }

    @Test
    fun `TabType values are distinct`() {
        assertNotEquals(TabType.BOOK, TabType.SEARCH)
    }

    @Test
    fun `TabType values contains all types`() {
        val values = TabType.entries
        assertEquals(2, values.size)
        assertTrue(values.contains(TabType.BOOK))
        assertTrue(values.contains(TabType.SEARCH))
    }

    @Test
    fun `TabType name returns correct string`() {
        assertEquals("BOOK", TabType.BOOK.name)
        assertEquals("SEARCH", TabType.SEARCH.name)
    }

    @Test
    fun `TabType ordinal is consistent`() {
        assertEquals(0, TabType.BOOK.ordinal)
        assertEquals(1, TabType.SEARCH.ordinal)
    }

    @Test
    fun `TabType valueOf returns correct type`() {
        assertEquals(TabType.BOOK, TabType.valueOf("BOOK"))
        assertEquals(TabType.SEARCH, TabType.valueOf("SEARCH"))
    }

    @Test
    fun `TabType can be used in when expression`() {
        fun describe(type: TabType): String = when (type) {
            TabType.BOOK -> "A book tab"
            TabType.SEARCH -> "A search tab"
        }

        assertEquals("A book tab", describe(TabType.BOOK))
        assertEquals("A search tab", describe(TabType.SEARCH))
    }
}
