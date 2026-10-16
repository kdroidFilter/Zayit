@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.framework.session

import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.features.search.SearchTabCache
import kotlinx.serialization.Serializable

/** What a search tab keeps across sessions: the desktop session saves it with its tab, a mobile app as it likes. */
@Serializable
data class SearchPersistedState(
    val query: String = "",
    val globalExtended: Boolean = false,
    val datasetScope: String = "global",
    // Unused (a search is no longer scoped to a category), kept for the ProtoBuf field numbers
    val filterCategoryId: Long = 0L,
    val filterBookId: Long = 0L,
    val filterTocId: Long = 0L,
    // Unused, as filterCategoryId
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
    // Appended last: the session is ProtoBuf, numbered by declaration order.
    // What the bar holds, typed but not searched yet (restored in the bar; [query] is what is searched)
    val draftQuery: String = "",
    // The author whose books are searched (none with a book or TOC scope)
    val fetchAuthorId: Long = 0L,
) {
    /** The scope the search runs in: its fetch scope, else its view filter. */
    val scope: SearchScope
        get() {
            val bookId = fetchBookId.takeIf { it > 0 } ?: filterBookId.takeIf { it > 0 }
            val tocId = fetchTocId.takeIf { it > 0 } ?: filterTocId.takeIf { it > 0 }
            return when {
                tocId != null && bookId != null -> SearchScope.Toc(bookId = bookId, tocId = tocId)
                bookId != null -> SearchScope.Book(bookId)
                fetchAuthorId > 0 -> SearchScope.Author(fetchAuthorId)
                else -> SearchScope.Global
            }
        }

    /** Runs the search in [scope]: both its fetch scope and its view filter (a legacy category: everywhere). */
    fun withScope(scope: SearchScope): SearchPersistedState {
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
                    SearchScope.Global, is SearchScope.Category -> "global"
                    is SearchScope.Book -> "book"
                    is SearchScope.Toc -> "toc"
                    is SearchScope.Author -> "author"
                },
            filterCategoryId = 0L,
            filterBookId = bookId,
            filterTocId = tocId,
            fetchCategoryId = 0L,
            fetchBookId = bookId,
            fetchTocId = tocId,
            fetchAuthorId = (scope as? SearchScope.Author)?.authorId ?: 0L,
        )
    }
}
