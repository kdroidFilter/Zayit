package io.github.kdroidfilter.seforimapp.features.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchFilterTest {
    @Test
    fun `SearchFilter enum has exactly 2 values`() {
        assertEquals(2, SearchFilter.entries.size)
    }

    @Test
    fun `SearchFilter contains REFERENCE`() {
        assertTrue(SearchFilter.entries.contains(SearchFilter.REFERENCE))
    }

    @Test
    fun `SearchFilter contains TEXT`() {
        assertTrue(SearchFilter.entries.contains(SearchFilter.TEXT))
    }

    @Test
    fun `SearchFilter values have correct ordinals`() {
        assertEquals(0, SearchFilter.REFERENCE.ordinal)
        assertEquals(1, SearchFilter.TEXT.ordinal)
    }

    @Test
    fun `SearchFilter valueOf works correctly`() {
        assertEquals(SearchFilter.REFERENCE, SearchFilter.valueOf("REFERENCE"))
        assertEquals(SearchFilter.TEXT, SearchFilter.valueOf("TEXT"))
    }
}
