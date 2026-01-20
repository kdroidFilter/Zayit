package io.github.kdroidfilter.seforimapp.features.onboarding.userprofile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommunityTest {
    @Test
    fun `Community enum has exactly 3 values`() {
        assertEquals(3, Community.entries.size)
    }

    @Test
    fun `Community contains SEPHARADE`() {
        assertTrue(Community.entries.contains(Community.SEPHARADE))
    }

    @Test
    fun `Community contains ASHKENAZE`() {
        assertTrue(Community.entries.contains(Community.ASHKENAZE))
    }

    @Test
    fun `Community contains SEFARD`() {
        assertTrue(Community.entries.contains(Community.SEFARD))
    }

    @Test
    fun `Community values have correct ordinal`() {
        assertEquals(0, Community.SEPHARADE.ordinal)
        assertEquals(1, Community.ASHKENAZE.ordinal)
        assertEquals(2, Community.SEFARD.ordinal)
    }

    @Test
    fun `Community valueOf works correctly`() {
        assertEquals(Community.SEPHARADE, Community.valueOf("SEPHARADE"))
        assertEquals(Community.ASHKENAZE, Community.valueOf("ASHKENAZE"))
        assertEquals(Community.SEFARD, Community.valueOf("SEFARD"))
    }

    @Test
    fun `Community name returns correct string`() {
        assertEquals("SEPHARADE", Community.SEPHARADE.name)
        assertEquals("ASHKENAZE", Community.ASHKENAZE.name)
        assertEquals("SEFARD", Community.SEFARD.name)
    }
}
