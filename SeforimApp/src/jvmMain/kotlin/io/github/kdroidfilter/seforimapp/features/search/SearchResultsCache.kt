package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult

data class SearchParamsKey(
    val query: String,
    val near: Int,
    val filterCategoryId: Long?,
    val filterBookId: Long?
)

/**
 * Lightweight in-memory LRU cache for search results.
 * Avoids re-querying the DB when re-displaying a Search tab with same params.
 */
class SearchResultsCache(private val maxSize: Int = 64) {
    private val map = object : LinkedHashMap<SearchParamsKey, List<SearchResult>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SearchParamsKey, List<SearchResult>>?): Boolean {
            return size > maxSize
        }
    }

    fun get(key: SearchParamsKey): List<SearchResult>? = synchronized(map) { map[key] }

    fun put(key: SearchParamsKey, value: List<SearchResult>) {
        synchronized(map) { map[key] = value }
    }

    fun clear() = synchronized(map) { map.clear() }
}

