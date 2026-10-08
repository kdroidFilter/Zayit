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
    // Warms the first page of the given line's open commentators (used to warm a background tab ahead of display).
    val prefetchCommentaries: suspend (Long, Set<Long>) -> Unit,
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
    // Char-count vectors used by the commentary scrollbar to derive visual-line metrics.
    // Mirror the pager ordering above; failures fold to an empty list.
    val getCommentaryCharCountsForLine: suspend (Long, Long) -> List<Int>,
    val getCommentaryCharCountsForLines: suspend (List<Long>, Long) -> List<Int>,
    // Char-count vectors for sources/targum scrollbar, one section at a time
    // (filtered by target-book id + connection type). Mirror the pager ordering.
    val getLinkCharCountsForLine: suspend (Long, Long, ConnectionType) -> List<Int>,
    val getLinkCharCountsForLines: suspend (List<Long>, Long, ConnectionType) -> List<Int>,
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
    // The book the structures belong to: kept until the next book's are loaded
    val bookId: Long? = null,
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
        val notes: NotesState = NotesState(),
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
    // Search bar under the header (not persisted): null while hidden
    val search: PaneSearchState<BookFilterResult>? = null,
)

/** The book tree narrowed to the books suggested for the query, with their categories. */
@Immutable
data class BookFilterResult(
    val roots: List<Category> = emptyList(),
    val children: Map<Long, List<Category>> = emptyMap(),
    val books: Set<Book> = emptySet(),
    /** Every category of the filtered tree, all shown expanded. */
    val categoryIds: Set<Long> = emptySet(),
    override val matchIds: List<Long> = emptyList(),
    override val bestMatchId: Long? = null,
) : PaneSearchResult

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
    // Search bar under the header (not persisted): null while hidden
    val search: PaneSearchState<TocFilterResult>? = null,
)

/** What a pane search found: [matchIds] in display order, [bestMatchId] the one Enter opens. */
interface PaneSearchResult {
    val matchIds: List<Long>
    val bestMatchId: Long?
}

/**
 * The search bar of a side pane: the typed [query] and the last computed [result], which
 * answers [resultQuery] and may lag behind [query] while the next one is computed.
 */
@Immutable
data class PaneSearchState<T : PaneSearchResult>(
    val query: String = "",
    val result: T? = null,
    val resultQuery: String? = null,
)

/**
 * The TOC narrowed to the entries whose title matches the query, plus their ancestors; and
 * likewise the shown alt TOC ([altRoots]), whose entries carry their negated id so the two
 * trees share [children], [matchIds] and [highlights].
 */
@Immutable
data class TocFilterResult(
    val roots: List<TocEntry> = emptyList(),
    val altRoots: List<TocEntry> = emptyList(),
    val children: Map<Long, List<TocEntry>> = emptyMap(),
    override val matchIds: List<Long> = emptyList(),
    /** Ranges of the title to highlight, per matching entry. */
    val highlights: Map<Long, List<IntRange>> = emptyMap(),
    override val bestMatchId: Long? = null,
) : PaneSearchResult

@Immutable
data class NotesState(
    // UI only: the notes themselves live in NoteStore's per-book cache.
    val isVisible: Boolean = false,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

/** Lines [first] to [last] of the book [bookId], by their index: a passage marked to be read. */
@Immutable
data class MarkedRange(
    val bookId: Long,
    val first: Int,
    val last: Int,
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
    // Each verse twice, then its targum
    val shnayimMikra: Boolean = false,
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
    // Current page of the commentaries vertical pager (restored on tab re-entry)
    val commentariesPageIndex: Int = 0,
    // Visibility of the commentators selection sidebar inside the commentaries pane
    val isCommentatorsListVisible: Boolean = true,
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
    // A passage marked in the text, as the day's limud opened from the Home: not a selection, its commentaries unloaded
    val markedRange: MarkedRange? = null,
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
        val notesSplitState: SplitPaneState = SplitPaneState(initialPositionPercentage = SplitDefaults.NOTES, moveEnabled = true),
        val contentSplitState: SplitPaneState = SplitPaneState(initialPositionPercentage = SplitDefaults.CONTENT, moveEnabled = true),
        val targumSplitState: SplitPaneState = SplitPaneState(initialPositionPercentage = 0.8f, moveEnabled = true),
        val previousPositions: PreviousPositions = PreviousPositions(),
    )

@Immutable
data class PreviousPositions(
    val main: Float = SplitDefaults.MAIN,
    val toc: Float = SplitDefaults.TOC,
    val notes: Float = SplitDefaults.NOTES,
    val content: Float = SplitDefaults.CONTENT,
    val sources: Float = SplitDefaults.SOURCES,
    val links: Float = 0.8f,
)
