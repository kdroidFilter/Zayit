package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult

data class SearchParamsKey(
    val query: String,
    val near: Int,
    val filterCategoryId: Long?,
    val filterBookId: Long?,
    val filterTocId: Long?
)

/**
 * Lightweight in-memory LRU cache for search results.
 * Avoids re-querying the DB when re-displaying a Search tab with same params.
 */
data class SearchCacheEntry(
    val results: List<SearchResult>,
    val nextOffset: Int = 0,
    val allowedBooks: List<Long> = emptyList(),
    val perBookOffset: Map<Long, Int> = emptyMap(),
    val hasMore: Boolean = false
)

class SearchResultsCache(private val maxSize: Int = 64) {
    private val map = object : LinkedHashMap<SearchParamsKey, SearchCacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SearchParamsKey, SearchCacheEntry>?): Boolean {
            return size > maxSize
        }
    }

    fun get(key: SearchParamsKey): SearchCacheEntry? = synchronized(map) { map[key] }

    fun put(key: SearchParamsKey, entry: SearchCacheEntry) {
        synchronized(map) { map[key] = entry }
    }

    fun clear() = synchronized(map) { map.clear() }
}
