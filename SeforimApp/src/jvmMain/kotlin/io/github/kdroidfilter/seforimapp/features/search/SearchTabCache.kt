package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.serialization.Serializable

/** Serializable search-result snapshots persisted with the session (no in-memory state). */
object SearchTabCache {
    @Serializable
    data class CategoryAggSnapshot(
        val categoryCounts: Map<Long, Int>,
        val bookCounts: Map<Long, Int>,
        val booksForCategory: Map<Long, List<Book>> = emptyMap(),
    )

    @Serializable
    data class SearchTreeBookSnapshot(
        val book: Book,
        val count: Int,
    )

    @Serializable
    data class SearchTreeCategorySnapshot(
        val category: io.github.kdroidfilter.seforimlibrary.core.models.Category,
        val count: Int,
        val children: List<SearchTreeCategorySnapshot>,
        val books: List<SearchTreeBookSnapshot>,
    )

    @Serializable
    data class TocTreeSnapshot(
        val rootEntries: List<TocEntry>,
        val children: Map<Long, List<TocEntry>>,
    )

    @Serializable
    data class Snapshot(
        val results: List<SearchResult>,
        val categoryAgg: CategoryAggSnapshot,
        val tocCounts: Map<Long, Int>,
        val tocTree: TocTreeSnapshot?,
        // Optional precomputed search tree to accelerate restore
        val searchTree: List<SearchTreeCategorySnapshot>? = null,
        // Total hits for lazy loading continuation
        val totalHits: Long = 0L,
        val hasMore: Boolean = false,
    )
}
