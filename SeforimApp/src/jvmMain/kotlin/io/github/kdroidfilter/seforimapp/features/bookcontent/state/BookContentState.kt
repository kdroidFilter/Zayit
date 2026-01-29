package io.github.kdroidfilter.seforimapp.features.bookcontent.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.paging.PagingData
import io.github.kdroidfilter.seforimlibrary.core.models.*
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState

/**
 * Auxiliary models that are not part of the persistent state
 */
@Immutable
data class Providers(
    val linesPagingData: Flow<PagingData<Line>>,
    // Single-line pagers
    val buildCommentariesPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    val getAvailableCommentatorsForLine: suspend (Long) -> Map<String, Long>,
    val getCommentatorGroupsForLine: suspend (Long) -> List<CommentatorGroup>,
    val loadLineConnections: suspend (List<Long>) -> Map<Long, LineConnectionsSnapshot>,
    val buildLinksPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    val getAvailableLinksForLine: suspend (Long) -> Map<String, Long>,
    val buildSourcesPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    val getAvailableSourcesForLine: suspend (Long) -> Map<String, Long>,
    // Multi-line pagers (for multi-selection)
    val buildCommentariesPagerForLines: (List<Long>, Long?) -> Flow<PagingData<CommentaryWithText>>,
    val getCommentatorGroupsForLines: suspend (List<Long>) -> List<CommentatorGroup>,
    val buildLinksPagerForLines: (List<Long>, Long?) -> Flow<PagingData<CommentaryWithText>>,
    val getAvailableLinksForLines: suspend (List<Long>) -> Map<String, Long>,
    val buildSourcesPagerForLines: (List<Long>, Long?) -> Flow<PagingData<CommentaryWithText>>,
    val getAvailableSourcesForLines: suspend (List<Long>) -> Map<String, Long>,
)

/**
 * Represents a visible TOC entry in the flattened list
 */
@Immutable
data class VisibleTocEntry(
    val entry: TocEntry,
    val level: Int,
    val isExpanded: Boolean,
    val hasChildren: Boolean,
    val isLastChild: Boolean,
)

@Immutable
data class AltTocState(
    val structures: List<AltTocStructure> = emptyList(),
    val selectedStructureId: Long? = null,
    val entries: List<AltTocEntry> = emptyList(),
    val expandedEntries: Set<Long> = emptySet(),
    val children: Map<Long, List<AltTocEntry>> = emptyMap(),
    val selectedEntryId: Long? = null,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val lineHeadingsByLineId: Map<Long, List<AltTocEntry>> = emptyMap(),
    val entriesById: Map<Long, AltTocEntry> = emptyMap(),
)

/**
 * Unified state for BookContent (UI + Business)
 */
@Stable
data class BookContentState
    @OptIn(ExperimentalSplitPaneApi::class)
    constructor(
        val tabId: String = "",
        val navigation: NavigationState = NavigationState(),
        val toc: TocState = TocState(),
        val altToc: AltTocState = AltTocState(),
        val content: ContentState = ContentState(),
        val layout: LayoutState = LayoutState(),
        val isLoading: Boolean = false,
        val providers: Providers? = null,
    )

@Immutable
data class NavigationState(
    // Business
    val rootCategories: List<Category> = emptyList(),
    val expandedCategories: Set<Long> = emptySet(),
    val categoryChildren: Map<Long, List<Category>> = emptyMap(),
    val booksInCategory: Set<Book> = emptySet(),
    val selectedCategory: Category? = null,
    val selectedBook: Book? = null,
    val searchText: String = "",
    // UI
    val isVisible: Boolean = true,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

@Immutable
data class TocState(
    // Business
    val entries: List<TocEntry> = emptyList(),
    val expandedEntries: Set<Long> = emptySet(),
    val children: Map<Long, List<TocEntry>> = emptyMap(),
    val selectedEntryId: Long? = null,
    val breadcrumbPath: List<TocEntry> = emptyList(),
    // UI
    val isVisible: Boolean = false,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

@Immutable
data class ContentState(
    // Data
    val lines: List<Line> = emptyList(),
    val selectedLines: Set<Line> = emptySet(),
    val primarySelectedLineId: Long? = null,
    // True si la multi-sélection vient d'un clic sur un TOC entry (pas Ctrl+click)
    val isTocEntrySelection: Boolean = false,
    val commentaries: List<CommentaryWithText> = emptyList(),
    // Visibility
    val showCommentaries: Boolean = false,
    val showTargum: Boolean = false,
    val showSources: Boolean = false,
    // Scroll positions
    val paragraphScrollPosition: Int = 0,
    val chapterScrollPosition: Int = 0,
    val selectedChapter: Int = 0,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    // Anchoring
    val anchorId: Long = -1L,
    val anchorIndex: Int = 0,
    // Commentaries UI state
    val commentariesSelectedTab: Int = 0,
    val commentariesScrollIndex: Int = 0,
    val commentariesScrollOffset: Int = 0,
    val commentatorsListScrollIndex: Int = 0,
    val commentatorsListScrollOffset: Int = 0,
    // Per-column (per commentator) scroll positions
    val commentariesColumnScrollIndexByCommentator: Map<Long, Int> = emptyMap(),
    val commentariesColumnScrollOffsetByCommentator: Map<Long, Int> = emptyMap(),
    // Filters selected in UI (for current line)
    val selectedCommentatorIds: Set<Long> = emptySet(),
    val selectedTargumSourceIds: Set<Long> = emptySet(),
    val selectedSourceIds: Set<Long> = emptySet(),
    // Business selections by line/book (kept for use cases)
    val selectedCommentatorsByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedCommentatorsByBook: Map<Long, Set<Long>> = emptyMap(),
    val selectedLinkSourcesByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedLinkSourcesByBook: Map<Long, Set<Long>> = emptyMap(),
    val selectedSourcesByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedSourcesByBook: Map<Long, Set<Long>> = emptyMap(),
    // Scrolling behavior control
    val shouldScrollToLine: Boolean = false,
    val scrollToLineTimestamp: Long = 0L,
    // One-shot request to top-anchor a specific line (e.g., from TOC)
    val topAnchorLineId: Long = -1L,
    val topAnchorRequestTimestamp: Long = 0L,
    // Transient UI signals (not persisted)
    val maxCommentatorsLimitSignal: Long = 0L,
) {
    /** The primary selected line (for TOC highlight, breadcrumb, etc.) */
    val primaryLine: Line?
        get() =
            selectedLines.firstOrNull { it.id == primarySelectedLineId }
                ?: selectedLines.firstOrNull()

    /** Set of selected line IDs for efficient lookup */
    val selectedLineIds: Set<Long>
        get() = selectedLines.mapTo(mutableSetOf()) { it.id }
}

/**
 * Layout state uses SplitPaneState to directly bind with UI panes
 */
@Stable
data class LayoutState
    @OptIn(ExperimentalSplitPaneApi::class)
    constructor(
        val mainSplitState: SplitPaneState = SplitPaneState(initialPositionPercentage = SplitDefaults.MAIN, moveEnabled = true),
        val tocSplitState: SplitPaneState = SplitPaneState(initialPositionPercentage = SplitDefaults.TOC, moveEnabled = true),
        val contentSplitState: SplitPaneState = SplitPaneState(initialPositionPercentage = SplitDefaults.CONTENT, moveEnabled = true),
        val targumSplitState: SplitPaneState = SplitPaneState(initialPositionPercentage = 0.8f, moveEnabled = true),
        val previousPositions: PreviousPositions = PreviousPositions(),
    )

@Immutable
data class PreviousPositions(
    val main: Float = SplitDefaults.MAIN,
    val toc: Float = SplitDefaults.TOC,
    val content: Float = SplitDefaults.CONTENT,
    val sources: Float = SplitDefaults.SOURCES,
    val links: Float = 0.8f,
)
