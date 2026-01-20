package io.github.kdroidfilter.seforimapp.framework.session

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.SplitDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionModelsTest {
    @Test
    fun `SavedSessionV2 has correct default values`() {
        val session = SavedSessionV2()

        assertEquals(2, session.version)
        assertTrue(session.tabs.isEmpty())
        assertEquals(0, session.selectedIndex)
        assertTrue(session.tabStates.isEmpty())
    }

    @Test
    fun `TabPersistedState has correct default values`() {
        val state = TabPersistedState()

        assertEquals(BookContentPersistedState(), state.bookContent)
        assertNull(state.search)
    }

    @Test
    fun `BookContentPersistedState has correct navigation defaults`() {
        val state = BookContentPersistedState()

        assertEquals(-1L, state.selectedBookId)
        assertEquals(-1L, state.selectedCategoryId)
        assertTrue(state.expandedCategoryIds.isEmpty())
        assertEquals("", state.navigationSearchText)
        assertTrue(state.isBookTreeVisible)
        assertEquals(0, state.bookTreeScrollIndex)
        assertEquals(0, state.bookTreeScrollOffset)
    }

    @Test
    fun `BookContentPersistedState has correct TOC defaults`() {
        val state = BookContentPersistedState()

        assertFalse(state.isTocVisible)
        assertTrue(state.expandedTocEntryIds.isEmpty())
        assertEquals(-1L, state.selectedTocEntryId)
        assertEquals(0, state.tocScrollIndex)
        assertEquals(0, state.tocScrollOffset)
    }

    @Test
    fun `BookContentPersistedState has correct content defaults`() {
        val state = BookContentPersistedState()

        assertEquals(-1L, state.selectedLineId)
        assertFalse(state.showCommentaries)
        assertFalse(state.showTargum)
        assertFalse(state.showSources)
        assertEquals(0, state.paragraphScrollPosition)
        assertEquals(0, state.chapterScrollPosition)
        assertEquals(0, state.selectedChapter)
    }

    @Test
    fun `BookContentPersistedState has correct layout defaults`() {
        val state = BookContentPersistedState()

        assertEquals(SplitDefaults.MAIN, state.mainSplitPosition)
        assertEquals(SplitDefaults.TOC, state.tocSplitPosition)
        assertEquals(SplitDefaults.CONTENT, state.contentSplitPosition)
        assertEquals(0.8f, state.targumSplitPosition)
    }

    @Test
    fun `BookContentPersistedState has correct commentaries defaults`() {
        val state = BookContentPersistedState()

        assertEquals(0, state.commentariesSelectedTab)
        assertTrue(state.commentariesColumnScrollIndexByCommentator.isEmpty())
        assertTrue(state.selectedCommentatorsByLine.isEmpty())
        assertTrue(state.selectedCommentatorsByBook.isEmpty())
    }

    @Test
    fun `SearchPersistedState has correct default values`() {
        val state = SearchPersistedState()

        assertEquals("", state.query)
        assertFalse(state.globalExtended)
        assertEquals("global", state.datasetScope)
        assertEquals(0L, state.filterCategoryId)
        assertEquals(0L, state.filterBookId)
        assertEquals(0L, state.filterTocId)
        assertEquals(0, state.scrollIndex)
        assertEquals(0, state.scrollOffset)
        assertEquals(-1L, state.anchorId)
        assertNull(state.snapshot)
        assertTrue(state.breadcrumbs.isEmpty())
    }

    @Test
    fun `BookContentPersistedState copy works correctly`() {
        val original = BookContentPersistedState(selectedBookId = 100L)
        val modified = original.copy(selectedLineId = 200L)

        assertEquals(100L, modified.selectedBookId)
        assertEquals(200L, modified.selectedLineId)
    }

    @Test
    fun `SearchPersistedState copy works correctly`() {
        val original = SearchPersistedState(query = "test")
        val modified = original.copy(globalExtended = true)

        assertEquals("test", modified.query)
        assertTrue(modified.globalExtended)
    }

    @Test
    fun `SavedSessionV2 can store tab states`() {
        val tabState =
            TabPersistedState(
                bookContent = BookContentPersistedState(selectedBookId = 42L),
            )
        val session =
            SavedSessionV2(
                tabStates = mapOf("tab1" to tabState),
            )

        assertEquals(1, session.tabStates.size)
        assertEquals(42L, session.tabStates["tab1"]?.bookContent?.selectedBookId)
    }
}
