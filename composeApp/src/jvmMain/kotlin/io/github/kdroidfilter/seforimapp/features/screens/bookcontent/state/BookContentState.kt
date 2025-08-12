package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry

/**
 * État centralisé pour l'écran de contenu du livre
 */
data class BookContentState(
    // Navigation
    val navigation: NavigationState = NavigationState(),

    // Table of Contents
    val toc: TocState = TocState(),

    // Content
    val content: ContentState = ContentState(),

    // Layout
    val layout: LayoutState = LayoutState(),

    // Loading
    val isLoading: Boolean = false
)

data class NavigationState(
    val rootCategories: List<Category> = emptyList(),
    val expandedCategories: Set<Long> = emptySet(),
    val categoryChildren: Map<Long, List<Category>> = emptyMap(),
    val booksInCategory: Set<Book> = emptySet(),
    val selectedCategory: Category? = null,
    val selectedBook: Book? = null,
    val searchText: String = "",
    val isVisible: Boolean = true,
    val scrollPosition: ScrollPosition = ScrollPosition()
)

data class TocState(
    val entries: List<TocEntry> = emptyList(),
    val expandedEntries: Set<Long> = emptySet(),
    val children: Map<Long, List<TocEntry>> = emptyMap(),
    val isVisible: Boolean = true,
    val scrollPosition: ScrollPosition = ScrollPosition()
)

data class ContentState(
    val selectedLine: Line? = null,
    val showCommentaries: Boolean = false,
    val showLinks: Boolean = false,
    val scrollPosition: ScrollPosition = ScrollPosition(),
    val anchorId: Long = -1L,
    val anchorIndex: Int = 0,
    val paragraphScrollPosition: Int = 0,
    val chapterScrollPosition: Int = 0,
    val selectedChapter: Int = 0,
    val commentariesState: CommentariesState = CommentariesState(),
    val scrollToLineTimestamp: Long = 0L
)

data class CommentariesState(
    val selectedTab: Int = 0,
    val scrollPosition: ScrollPosition = ScrollPosition(),
    val selectedCommentatorsByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedCommentatorsByBook: Map<Long, Set<Long>> = emptyMap(),
    val selectedLinkSourcesByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedLinkSourcesByBook: Map<Long, Set<Long>> = emptyMap()
)

data class LayoutState(
    val mainSplitPosition: Float = 0.3f,
    val tocSplitPosition: Float = 0.3f,
    val contentSplitPosition: Float = 0.7f,
    val linksSplitPosition: Float = 0.8f,
    val previousPositions: PreviousPositions = PreviousPositions()
)

data class PreviousPositions(
    val main: Float = 0.3f,
    val toc: Float = 0.3f,
    val content: Float = 0.7f,
    val links: Float = 0.8f
)

data class ScrollPosition(
    val index: Int = 0,
    val offset: Int = 0
)