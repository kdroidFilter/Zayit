@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.framework.session

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.SplitDefaults
import io.github.kdroidfilter.seforimapp.features.search.SearchTabCache
import kotlinx.serialization.Serializable

/**
 * Serializable session snapshot persisted on disk at app close.
 *
 * Notes:
 * - This intentionally stores only IDs and lightweight UI primitives for BookContent.
 * - Search can be large by design (full results + aggregates) to restore "identically".
 */
@Serializable
data class SavedSessionV2(
    val version: Int = 2,
    val tabs: List<TabsDestination> = emptyList(),
    val selectedIndex: Int = 0,
    val tabStates: Map<String, TabPersistedState> = emptyMap(),
)

@Serializable
data class TabPersistedState(
    val bookContent: BookContentPersistedState = BookContentPersistedState(),
    val search: SearchPersistedState? = null,
)

@Serializable
data class BookContentPersistedState(
    // Navigation (IDs only; catalog/DB will repopulate objects)
    val selectedBookId: Long = -1L,
    val selectedCategoryId: Long = -1L,
    val expandedCategoryIds: Set<Long> = emptySet(),
    val navigationSearchText: String = "",
    val isBookTreeVisible: Boolean = true,
    val bookTreeScrollIndex: Int = 0,
    val bookTreeScrollOffset: Int = 0,
    // TOC
    val isTocVisible: Boolean = false,
    val expandedTocEntryIds: Set<Long> = emptySet(),
    val selectedTocEntryId: Long = -1L,
    val selectedTocEntryIds: Set<Long> = emptySet(),
    val tocScrollIndex: Int = 0,
    val tocScrollOffset: Int = 0,
    // Content
    val selectedLineId: Long = -1L,
    val selectedLineIds: Set<Long> = emptySet(),
    val isTocBasedSelection: Boolean = false,
    val showCommentaries: Boolean = false,
    val showTargum: Boolean = false,
    val showSources: Boolean = false,
    val paragraphScrollPosition: Int = 0,
    val chapterScrollPosition: Int = 0,
    val selectedChapter: Int = 0,
    val contentScrollIndex: Int = 0,
    val contentScrollOffset: Int = 0,
    val contentAnchorLineId: Long = -1L,
    val contentAnchorIndex: Int = 0,
    // Commentaries
    val commentariesSelectedTab: Int = 0,
    val commentariesScrollIndex: Int = 0,
    val commentariesScrollOffset: Int = 0,
    val commentatorsListScrollIndex: Int = 0,
    val commentatorsListScrollOffset: Int = 0,
    val commentariesColumnScrollIndexByCommentator: Map<Long, Int> = emptyMap(),
    val commentariesColumnScrollOffsetByCommentator: Map<Long, Int> = emptyMap(),
    val selectedCommentatorsByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedCommentatorsByBook: Map<Long, Set<Long>> = emptyMap(),
    val selectedTargumSourcesByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedTargumSourcesByBook: Map<Long, Set<Long>> = emptyMap(),
    val selectedSourcesByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedSourcesByBook: Map<Long, Set<Long>> = emptyMap(),
    // Layout
    val mainSplitPosition: Float = SplitDefaults.MAIN,
    val tocSplitPosition: Float = SplitDefaults.TOC,
    val contentSplitPosition: Float = SplitDefaults.CONTENT,
    val targumSplitPosition: Float = 0.8f,
    val previousMainSplitPosition: Float = SplitDefaults.MAIN,
    val previousTocSplitPosition: Float = SplitDefaults.TOC,
    val previousContentSplitPosition: Float = SplitDefaults.CONTENT,
    val previousSourcesSplitPosition: Float = SplitDefaults.SOURCES,
    val previousTargumSplitPosition: Float = 0.8f,
)

@Serializable
data class SearchPersistedState(
    val query: String = "",
    val globalExtended: Boolean = false,
    val datasetScope: String = "global",
    val filterCategoryId: Long = 0L,
    val filterBookId: Long = 0L,
    val filterTocId: Long = 0L,
    val fetchCategoryId: Long = 0L,
    val fetchBookId: Long = 0L,
    val fetchTocId: Long = 0L,
    // View filters (multi-select)
    val selectedCategoryIds: Set<Long> = emptySet(),
    val selectedBookIds: Set<Long> = emptySet(),
    val selectedTocIds: Set<Long> = emptySet(),
    // Scroll/anchor persistence
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val anchorId: Long = -1L,
    val anchorIndex: Int = 0,
    // Full search snapshot (results + aggregates) for identical restore
    val snapshot: SearchTabCache.Snapshot? = null,
    val breadcrumbs: Map<Long, List<String>> = emptyMap(),
)
