package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry

/**
 * Lightweight in-memory cache to fully restore a Search tab without re-running the query.
 * Keeps results and precomputed aggregates to avoid expensive recomputation on restore.
 */
object SearchTabCache {
    private const val MAX_TABS = 8
    data class CategoryAggSnapshot(
        val categoryCounts: Map<Long, Int>,
        val bookCounts: Map<Long, Int>,
        val booksForCategory: Map<Long, List<Book>>
    )

    data class TocTreeSnapshot(
        val rootEntries: List<TocEntry>,
        val children: Map<Long, List<TocEntry>>
    )

    data class Snapshot(
        val results: List<SearchResult>,
        val categoryAgg: CategoryAggSnapshot,
        val tocCounts: Map<Long, Int>,
        val tocTree: TocTreeSnapshot?
    )

    private val cache = object : LinkedHashMap<String, Snapshot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Snapshot>?): Boolean {
            return size > MAX_TABS
        }
    }

    fun put(tabId: String, snapshot: Snapshot) {
        cache[tabId] = snapshot
    }

    fun get(tabId: String): Snapshot? = cache[tabId]

    fun clear(tabId: String) {
        cache.remove(tabId)
    }
}
