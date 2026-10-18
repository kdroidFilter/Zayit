package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookOpenSourceTest {
    @Test
    fun `BookOpenSource enum has exactly 4 values`() {
        assertEquals(4, BookOpenSource.entries.size)
    }

    @Test
    fun `BookOpenSource contains all expected values`() {
        val entries = BookOpenSource.entries
        assertTrue(entries.contains(BookOpenSource.HOME_REFERENCE))
        assertTrue(entries.contains(BookOpenSource.CATEGORY_TREE_NEW_TAB))
        assertTrue(entries.contains(BookOpenSource.SEARCH_RESULT))
        assertTrue(entries.contains(BookOpenSource.COMMENTARY_OR_TARGUM))
    }

    @Test
    fun `BookOpenSource values have correct ordinals`() {
        assertEquals(0, BookOpenSource.HOME_REFERENCE.ordinal)
        assertEquals(1, BookOpenSource.CATEGORY_TREE_NEW_TAB.ordinal)
        assertEquals(2, BookOpenSource.SEARCH_RESULT.ordinal)
        assertEquals(3, BookOpenSource.COMMENTARY_OR_TARGUM.ordinal)
    }

    @Test
    fun `BookOpenSource valueOf works correctly`() {
        assertEquals(BookOpenSource.HOME_REFERENCE, BookOpenSource.valueOf("HOME_REFERENCE"))
        assertEquals(BookOpenSource.CATEGORY_TREE_NEW_TAB, BookOpenSource.valueOf("CATEGORY_TREE_NEW_TAB"))
        assertEquals(BookOpenSource.SEARCH_RESULT, BookOpenSource.valueOf("SEARCH_RESULT"))
        assertEquals(BookOpenSource.COMMENTARY_OR_TARGUM, BookOpenSource.valueOf("COMMENTARY_OR_TARGUM"))
    }
}
