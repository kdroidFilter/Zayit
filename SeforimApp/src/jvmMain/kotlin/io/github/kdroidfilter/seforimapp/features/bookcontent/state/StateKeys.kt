package io.github.kdroidfilter.seforimapp.features.bookcontent.state

/**
 * Clés de sauvegarde d'état centralisées
 */
object StateKeys {
    // General
    const val TAB_ID = "tabId"
    const val BOOK_ID = "bookId"
    const val LINE_ID = "lineId"

    // Source hints for book opening (ephemeral)
    const val OPEN_SOURCE = "bookOpenSource"

    // Navigation
    const val SELECTED_BOOK = "selectedBook"
    const val SELECTED_CATEGORY = "selectedCategory"
    const val SEARCH_TEXT = "searchText"
    const val SHOW_BOOK_TREE = "showBookTree"
    const val EXPANDED_CATEGORIES = "expandedCategories"
    const val CATEGORY_CHILDREN = "categoryChildren"
    const val BOOKS_IN_CATEGORY = "booksInCategory"
    const val BOOK_TREE_SCROLL_INDEX = "bookTreeScrollIndex"
    const val BOOK_TREE_SCROLL_OFFSET = "bookTreeScrollOffset"

    // TOC
    const val SHOW_TOC = "showToc"
    const val EXPANDED_TOC_ENTRIES = "expandedTocEntries"
    const val TOC_CHILDREN = "tocChildren"
    const val TOC_SCROLL_INDEX = "tocScrollIndex"
    const val TOC_SCROLL_OFFSET = "tocScrollOffset"

    // Content
    const val SELECTED_LINE = "selectedLine"
    const val SELECTED_LINE_ID = "selectedLineId"
    const val SHOW_COMMENTARIES = "showCommentaries"
    const val SHOW_TARGUM = "showTargum"
    const val SHOW_SOURCES = "showSources"
    const val PARAGRAPH_SCROLL_POSITION = "paragraphScrollPosition"
    const val CHAPTER_SCROLL_POSITION = "chapterScrollPosition"
    const val SELECTED_CHAPTER = "selectedChapter"
    const val CONTENT_SCROLL_INDEX = "contentScrollIndex"
    const val CONTENT_SCROLL_OFFSET = "contentScrollOffset"
    const val CONTENT_ANCHOR_ID = "contentAnchorId"
    const val CONTENT_ANCHOR_INDEX = "contentAnchorIndex"

    // Commentaries
    const val COMMENTARIES_SELECTED_TAB = "commentariesSelectedTab"
    const val COMMENTARIES_SCROLL_INDEX = "commentariesScrollIndex"
    const val COMMENTARIES_SCROLL_OFFSET = "commentariesScrollOffset"
    const val COMMENTATORS_LIST_SCROLL_INDEX = "commentatorsListScrollIndex"
    const val COMMENTATORS_LIST_SCROLL_OFFSET = "commentatorsListScrollOffset"
    const val COMMENTARIES_COLUMN_SCROLL_INDEX_BY_COMMENTATOR = "commentariesColumnScrollIndexByCommentator"
    const val COMMENTARIES_COLUMN_SCROLL_OFFSET_BY_COMMENTATOR = "commentariesColumnScrollOffsetByCommentator"
    const val SELECTED_COMMENTATORS_BY_LINE = "selectedCommentatorsByLine"
    const val SELECTED_COMMENTATORS_BY_BOOK = "selectedCommentatorsByBook"
    const val SELECTED_TARGUM_SOURCES_BY_LINE = "selectedTargumSourcesByLine"
    const val SELECTED_TARGUM_SOURCES_BY_BOOK = "selectedTargumSourcesByBook"
    const val SELECTED_SOURCES_BY_LINE = "selectedSourcesByLine"
    const val SELECTED_SOURCES_BY_BOOK = "selectedSourcesByBook"

    // Layout - Split Panes
    const val SPLIT_PANE_POSITION = "splitPanePosition"
    const val TOC_SPLIT_PANE_POSITION = "tocSplitPanePosition"
    const val CONTENT_SPLIT_PANE_POSITION = "contentSplitPanePosition"
    const val TARGUM_SPLIT_PANE_POSITION = "targumSplitPanePosition"
    const val PREVIOUS_MAIN_SPLIT_POSITION = "previousMainSplitPosition"
    const val PREVIOUS_TOC_SPLIT_POSITION = "previousTocSplitPosition"
    const val PREVIOUS_CONTENT_SPLIT_POSITION = "previousContentSplitPosition"
    const val PREVIOUS_SOURCES_SPLIT_POSITION = "previousSourcesSplitPosition"
    const val PREVIOUS_TARGUM_SPLIT_POSITION = "previousTargumSplitPosition"
}
