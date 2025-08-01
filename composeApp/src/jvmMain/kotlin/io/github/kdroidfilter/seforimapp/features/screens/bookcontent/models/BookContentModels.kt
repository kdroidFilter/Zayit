package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.github.kdroidfilter.seforimlibrary.core.models.*
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState

/**
 * UI state for navigation panel (categories and books)
 */
@Immutable
data class NavigationUiState(
    val rootCategories: List<Category> = emptyList(),
    val expandedCategories: Set<Long> = emptySet(),
    val categoryChildren: Map<Long, List<Category>> = emptyMap(),
    val booksInCategory: Set<Book> = emptySet(),
    val selectedCategory: Category? = null,
    val selectedBook: Book? = null,
    val searchText: String = "",
    val isVisible: Boolean = true,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0
)

/**
 * UI state for table of contents
 */
@Immutable
data class TocUiState(
    val entries: List<TocEntry> = emptyList(),
    val expandedEntries: Set<Long> = emptySet(),
    val children: Map<Long, List<TocEntry>> = emptyMap(),
    val isVisible: Boolean = true,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0
)

/**
 * UI state for book content
 */
@Immutable
data class ContentUiState(
    val lines: List<Line> = emptyList(),
    val selectedLine: Line? = null,
    val commentaries: List<CommentaryWithText> = emptyList(),
    val showCommentaries: Boolean = false,
    val paragraphScrollPosition: Int = 0,
    val chapterScrollPosition: Int = 0,
    val selectedChapter: Int = 0,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0
)

/**
 * UI state for layout configuration
 */
@Stable
data class LayoutUiState @OptIn(ExperimentalSplitPaneApi::class) constructor(
    val mainSplitState: SplitPaneState,
    val tocSplitState: SplitPaneState,
    val contentSplitState: SplitPaneState
)

/**
 * Complete UI state for book content screen
 */
@Stable
data class BookContentUiState(
    val navigation: NavigationUiState = NavigationUiState(),
    val toc: TocUiState = TocUiState(),
    val content: ContentUiState = ContentUiState(),
    val layout: LayoutUiState,
    val isLoading: Boolean = false
)

/**
 * Represents a visible TOC entry in the flattened list
 */
@Immutable
data class VisibleTocEntry(
    val entry: TocEntry,
    val level: Int,
    val isExpanded: Boolean,
    val hasChildren: Boolean
)