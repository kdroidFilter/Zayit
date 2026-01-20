package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateKeysTest {
    @Test
    fun `general keys are correctly defined`() {
        assertEquals("tabId", StateKeys.TAB_ID)
        assertEquals("bookId", StateKeys.BOOK_ID)
        assertEquals("lineId", StateKeys.LINE_ID)
        assertEquals("bookOpenSource", StateKeys.OPEN_SOURCE)
    }

    @Test
    fun `navigation keys are correctly defined`() {
        assertEquals("selectedBook", StateKeys.SELECTED_BOOK)
        assertEquals("selectedCategory", StateKeys.SELECTED_CATEGORY)
        assertEquals("searchText", StateKeys.SEARCH_TEXT)
        assertEquals("showBookTree", StateKeys.SHOW_BOOK_TREE)
        assertEquals("expandedCategories", StateKeys.EXPANDED_CATEGORIES)
        assertEquals("categoryChildren", StateKeys.CATEGORY_CHILDREN)
        assertEquals("booksInCategory", StateKeys.BOOKS_IN_CATEGORY)
        assertEquals("bookTreeScrollIndex", StateKeys.BOOK_TREE_SCROLL_INDEX)
        assertEquals("bookTreeScrollOffset", StateKeys.BOOK_TREE_SCROLL_OFFSET)
    }

    @Test
    fun `TOC keys are correctly defined`() {
        assertEquals("showToc", StateKeys.SHOW_TOC)
        assertEquals("expandedTocEntries", StateKeys.EXPANDED_TOC_ENTRIES)
        assertEquals("tocChildren", StateKeys.TOC_CHILDREN)
        assertEquals("tocScrollIndex", StateKeys.TOC_SCROLL_INDEX)
        assertEquals("tocScrollOffset", StateKeys.TOC_SCROLL_OFFSET)
    }

    @Test
    fun `content keys are correctly defined`() {
        assertEquals("selectedLine", StateKeys.SELECTED_LINE)
        assertEquals("selectedLineId", StateKeys.SELECTED_LINE_ID)
        assertEquals("showCommentaries", StateKeys.SHOW_COMMENTARIES)
        assertEquals("showTargum", StateKeys.SHOW_TARGUM)
        assertEquals("showSources", StateKeys.SHOW_SOURCES)
        assertEquals("paragraphScrollPosition", StateKeys.PARAGRAPH_SCROLL_POSITION)
        assertEquals("chapterScrollPosition", StateKeys.CHAPTER_SCROLL_POSITION)
        assertEquals("selectedChapter", StateKeys.SELECTED_CHAPTER)
        assertEquals("contentScrollIndex", StateKeys.CONTENT_SCROLL_INDEX)
        assertEquals("contentScrollOffset", StateKeys.CONTENT_SCROLL_OFFSET)
        assertEquals("contentAnchorId", StateKeys.CONTENT_ANCHOR_ID)
        assertEquals("contentAnchorIndex", StateKeys.CONTENT_ANCHOR_INDEX)
    }

    @Test
    fun `commentaries keys are correctly defined`() {
        assertEquals("commentariesSelectedTab", StateKeys.COMMENTARIES_SELECTED_TAB)
        assertEquals("commentariesScrollIndex", StateKeys.COMMENTARIES_SCROLL_INDEX)
        assertEquals("commentariesScrollOffset", StateKeys.COMMENTARIES_SCROLL_OFFSET)
        assertEquals("commentatorsListScrollIndex", StateKeys.COMMENTATORS_LIST_SCROLL_INDEX)
        assertEquals("commentatorsListScrollOffset", StateKeys.COMMENTATORS_LIST_SCROLL_OFFSET)
    }

    @Test
    fun `layout keys are correctly defined`() {
        assertEquals("splitPanePosition", StateKeys.SPLIT_PANE_POSITION)
        assertEquals("tocSplitPanePosition", StateKeys.TOC_SPLIT_PANE_POSITION)
        assertEquals("contentSplitPanePosition", StateKeys.CONTENT_SPLIT_PANE_POSITION)
        assertEquals("targumSplitPanePosition", StateKeys.TARGUM_SPLIT_PANE_POSITION)
    }

    @Test
    fun `all keys are unique`() {
        val allKeys =
            listOf(
                StateKeys.TAB_ID,
                StateKeys.BOOK_ID,
                StateKeys.LINE_ID,
                StateKeys.OPEN_SOURCE,
                StateKeys.SELECTED_BOOK,
                StateKeys.SELECTED_CATEGORY,
                StateKeys.SEARCH_TEXT,
                StateKeys.SHOW_BOOK_TREE,
                StateKeys.EXPANDED_CATEGORIES,
                StateKeys.CATEGORY_CHILDREN,
                StateKeys.BOOKS_IN_CATEGORY,
                StateKeys.BOOK_TREE_SCROLL_INDEX,
                StateKeys.BOOK_TREE_SCROLL_OFFSET,
                StateKeys.SHOW_TOC,
                StateKeys.EXPANDED_TOC_ENTRIES,
                StateKeys.TOC_CHILDREN,
                StateKeys.TOC_SCROLL_INDEX,
                StateKeys.TOC_SCROLL_OFFSET,
                StateKeys.SELECTED_LINE,
                StateKeys.SELECTED_LINE_ID,
                StateKeys.SHOW_COMMENTARIES,
                StateKeys.SHOW_TARGUM,
                StateKeys.SHOW_SOURCES,
                StateKeys.PARAGRAPH_SCROLL_POSITION,
                StateKeys.CHAPTER_SCROLL_POSITION,
                StateKeys.SELECTED_CHAPTER,
                StateKeys.CONTENT_SCROLL_INDEX,
                StateKeys.CONTENT_SCROLL_OFFSET,
                StateKeys.CONTENT_ANCHOR_ID,
                StateKeys.CONTENT_ANCHOR_INDEX,
            )
        val uniqueKeys = allKeys.toSet()
        assertEquals(allKeys.size, uniqueKeys.size, "All keys should be unique")
    }

    @Test
    fun `keys are non-empty strings`() {
        assertTrue(StateKeys.TAB_ID.isNotEmpty())
        assertTrue(StateKeys.BOOK_ID.isNotEmpty())
        assertTrue(StateKeys.SELECTED_BOOK.isNotEmpty())
        assertTrue(StateKeys.SHOW_TOC.isNotEmpty())
    }
}
