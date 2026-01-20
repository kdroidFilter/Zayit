package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.serialization.Serializable

object SearchTabCache {
    private const val MAX_TABS = 8

    @Serializable
    data class CategoryAggSnapshot(
        val categoryCounts: Map<Long, Int>,
        val bookCounts: Map<Long, Int>,
        val booksForCategory: Map<Long, List<Book>>,
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
    )

    private val cache =
        object : LinkedHashMap<String, Snapshot>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Snapshot>?): Boolean = size > MAX_TABS
        }

    fun put(
        tabId: String,
        snapshot: Snapshot,
    ) {
        cache[tabId] = snapshot
    }

    fun get(tabId: String): Snapshot? = cache[tabId]

    fun clear(tabId: String) {
        cache.remove(tabId)
    }
}
