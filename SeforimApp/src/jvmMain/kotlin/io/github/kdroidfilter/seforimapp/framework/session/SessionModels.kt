@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.framework.session

import io.github.kdroidfilter.seforim.desktop.VirtualDesktop
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforim.tabs.TabType
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
    // Appended last: the session is ProtoBuf, numbered by declaration order
    val siddur: SiddurPersistedState? = null,
)

/** A siddur tab's day, part and choices, and where it was read: restored with the session. */
@Serializable
data class SiddurPersistedState(
    val epochDay: Long? = null,
    val part: String? = null,
    val showAll: Boolean = false,
    val options: String = "",
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val tocVisible: Boolean = true,
    val showDiacritics: Boolean = true,
    /** The table of contents' open entries (a tefila's name, or name/heading/index). */
    val tocExpanded: String = "",
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
    val tocScrollIndex: Int = 0,
    val tocScrollOffset: Int = 0,
    // Notes pane
    val isNotesVisible: Boolean = false,
    val notesScrollIndex: Int = 0,
    val notesScrollOffset: Int = 0,
    // Content
    val selectedLineIds: Set<Long> = emptySet(),
    val primarySelectedLineId: Long = -1L,
    val isTocEntrySelection: Boolean = false,
    val showCommentaries: Boolean = false,
    val showTargum: Boolean = false,
    val showSources: Boolean = false,
    val shnayimMikra: Boolean = false,
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
    val commentariesPageIndex: Int = 0,
    val isCommentatorsListVisible: Boolean = true,
    val selectedCommentatorsByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedCommentatorsByBook: Map<Long, Set<Long>> = emptyMap(),
    val selectedTargumSourcesByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedTargumSourcesByBook: Map<Long, Set<Long>> = emptyMap(),
    val selectedSourcesByLine: Map<Long, Set<Long>> = emptyMap(),
    val selectedSourcesByBook: Map<Long, Set<Long>> = emptyMap(),
    // Layout
    val mainSplitPosition: Float = SplitDefaults.MAIN,
    val tocSplitPosition: Float = SplitDefaults.TOC,
    val notesSplitPosition: Float = SplitDefaults.NOTES,
    val contentSplitPosition: Float = SplitDefaults.CONTENT,
    val targumSplitPosition: Float = 0.8f,
    val previousMainSplitPosition: Float = SplitDefaults.MAIN,
    val previousTocSplitPosition: Float = SplitDefaults.TOC,
    val previousNotesSplitPosition: Float = SplitDefaults.NOTES,
    val previousContentSplitPosition: Float = SplitDefaults.CONTENT,
    val previousSourcesSplitPosition: Float = SplitDefaults.SOURCES,
    val previousTargumSplitPosition: Float = 0.8f,
)

// -- Virtual desktops persistence models --

/**
 * Persisted geometry of one OS window. Coordinates are logical (Dp) values.
 * [x]/[y] are [UNSPECIFIED] when the position was never absolute (e.g. centered).
 */
@Serializable
data class SavedGeometry(
    val x: Int = UNSPECIFIED,
    val y: Int = UNSPECIFIED,
    val width: Int = 1280,
    val height: Int = 800,
    val placement: String = "Maximized",
) {
    companion object {
        const val UNSPECIFIED: Int = Int.MIN_VALUE
    }
}

/** One OS window of a desktop: its tabs and its frame geometry. */
@Serializable
data class WindowSnapshot(
    val destinations: List<TabsDestination> = emptyList(),
    val selectedIndex: Int = 0,
    val titles: Map<String, SerializableTabTitle> = emptyMap(),
    val geometry: SavedGeometry? = null,
)

@Serializable
data class DesktopTabsSnapshot(
    // Legacy single-window fields (proto fields 1-3), still read for migration.
    val destinations: List<TabsDestination> = emptyList(),
    val selectedIndex: Int = 0,
    val titles: Map<String, SerializableTabTitle> = emptyMap(),
    val tabStates: Map<String, TabPersistedState> = emptyMap(),
    // New multi-window layout (appended field; absent in legacy files).
    val windows: List<WindowSnapshot> = emptyList(),
) {
    /** Multi-window view of the snapshot, wrapping legacy single-window data if needed. */
    fun effectiveWindows(): List<WindowSnapshot> =
        windows.ifEmpty {
            listOf(WindowSnapshot(destinations = destinations, selectedIndex = selectedIndex, titles = titles))
        }
}

@Serializable
data class SerializableTabTitle(
    val title: String,
    val tabType: TabType,
    // Appended last: the session is ProtoBuf, numbered by declaration order.
    val pinned: Boolean = false,
    val shortTitle: String = "",
)

@Serializable
data class DesktopsState(
    val desktops: List<VirtualDesktop> = emptyList(),
    val activeDesktopId: String = "",
    val snapshots: Map<String, DesktopTabsSnapshot> = emptyMap(),
    // Desktops that were open in windows when the session was saved (appended fields;
    // legacy files fall back to listOf(activeDesktopId)).
    val openDesktopIds: List<String> = emptyList(),
    val focusedDesktopId: String = "",
) {
    fun effectiveOpenDesktopIds(): List<String> =
        openDesktopIds
            .ifEmpty { listOf(activeDesktopId) }
            .filter { id -> desktops.any { it.id == id } }
            .distinct()
}

@Serializable
data class SearchPersistedState(
    val query: String = "",
    // What the bar holds, typed but not searched yet (restored in the bar; [query] is what is searched)
    val draftQuery: String = "",
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
) {
    /** The scope the search runs in: its fetch scope, else its view filter. */
    val scope: SearchScope
        get() {
            val categoryId = fetchCategoryId.takeIf { it > 0 } ?: filterCategoryId.takeIf { it > 0 }
            val bookId = fetchBookId.takeIf { it > 0 } ?: filterBookId.takeIf { it > 0 }
            val tocId = fetchTocId.takeIf { it > 0 } ?: filterTocId.takeIf { it > 0 }
            return when {
                tocId != null && bookId != null -> SearchScope.Toc(bookId = bookId, tocId = tocId)
                bookId != null -> SearchScope.Book(bookId)
                categoryId != null -> SearchScope.Category(categoryId)
                else -> SearchScope.Global
            }
        }

    /** Runs the search in [scope]: both its fetch scope and its view filter. */
    fun withScope(scope: SearchScope): SearchPersistedState {
        val categoryId = (scope as? SearchScope.Category)?.categoryId ?: 0L
        val bookId =
            when (scope) {
                is SearchScope.Book -> scope.bookId
                is SearchScope.Toc -> scope.bookId
                else -> 0L
            }
        val tocId = (scope as? SearchScope.Toc)?.tocId ?: 0L
        return copy(
            datasetScope =
                when (scope) {
                    SearchScope.Global -> "global"
                    is SearchScope.Category -> "category"
                    is SearchScope.Book -> "book"
                    is SearchScope.Toc -> "toc"
                },
            filterCategoryId = categoryId,
            filterBookId = bookId,
            filterTocId = tocId,
            fetchCategoryId = categoryId,
            fetchBookId = bookId,
            fetchTocId = tocId,
        )
    }
}
