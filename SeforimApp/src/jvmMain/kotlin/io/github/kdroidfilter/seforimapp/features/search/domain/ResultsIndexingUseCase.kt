package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Arrays

private const val PARALLEL_FILTER_THRESHOLD = 2_000

/**
 * Index structure for fast filtering of search results.
 *
 * @property identity Identity hash code of the results list this index was built from
 * @property bookToIndices Map from book ID to sorted array of result indices
 * @property lineIdToIndex Map from line ID to result index
 */
data class ResultsIndex(
    val identity: Int,
    val bookToIndices: Map<Long, IntArray>,
    val lineIdToIndex: Map<Long, Int>,
)

/**
 * Use case for indexing and fast filtering of search results.
 *
 * This class provides efficient filtering operations on search results by building
 * an index that maps book IDs and line IDs to result indices. It supports:
 * - Fast filtering by book IDs
 * - Fast filtering by TOC subtree (via line IDs)
 * - Union of multiple filters
 * - Parallel processing for large result sets
 *
 * The use case is stateful - it maintains a cached index that is invalidated when
 * the results list changes (detected by identity hash code).
 */
class ResultsIndexingUseCase {
    private val indexMutex = Mutex()

    @Volatile
    private var cachedIndex: ResultsIndex? = null

    // TOC indices cache - invalidated when results change
    private var tocIndicesIdentity: Int = -1
    private val tocIndicesCache: MutableMap<Long, IntArray> = mutableMapOf()

    /**
     * Ensures the results index is built for the given results list.
     * If the index is already built for this exact results list (by identity), no work is done.
     *
     * @param results The search results to index
     * @return The built index
     */
    suspend fun ensureIndex(results: List<SearchResult>): ResultsIndex {
        val identity = System.identityHashCode(results)
        val current = cachedIndex
        if (current != null && current.identity == identity) return current

        indexMutex.withLock {
            // Double-check after acquiring lock
            val cur2 = cachedIndex
            if (cur2 != null && cur2.identity == identity) return cur2

            val bookMap = HashMap<Long, MutableList<Int>>()
            val lineMap = HashMap<Long, Int>(results.size * 2)

            for ((i, r) in results.withIndex()) {
                bookMap.getOrPut(r.bookId) { ArrayList() }.add(i)
                lineMap[r.lineId] = i
            }

            val finalBook = HashMap<Long, IntArray>(bookMap.size)
            for ((k, v) in bookMap) {
                val arr = IntArray(v.size)
                var idx = 0
                for (n in v) {
                    arr[idx++] = n
                }
                finalBook[k] = arr
            }

            val index =
                ResultsIndex(
                    identity = identity,
                    bookToIndices = finalBook,
                    lineIdToIndex = lineMap,
                )
            cachedIndex = index

            // Invalidate TOC indices cache when results change
            tocIndicesIdentity = identity
            tocIndicesCache.clear()

            return index
        }
    }

    /**
     * Gets the current cached index, if available.
     */
    fun getCachedIndex(): ResultsIndex? = cachedIndex

    /**
     * Computes indices for a TOC subtree given the line IDs in that subtree.
     * Results are cached per (results identity, tocId).
     *
     * @param tocId TOC entry ID (used as cache key)
     * @param lineIds Line IDs belonging to the TOC subtree
     * @param index The results index
     * @return Sorted array of result indices
     */
    fun indicesForTocSubtree(
        tocId: Long,
        lineIds: LongArray,
        index: ResultsIndex,
    ): IntArray {
        // Invalidate cache if results changed
        if (tocIndicesIdentity != index.identity) {
            tocIndicesIdentity = index.identity
            tocIndicesCache.clear()
        }

        tocIndicesCache[tocId]?.let { return it }

        if (lineIds.isEmpty()) return IntArray(0)

        var count = 0
        val tmp = IntArray(lineIds.size)
        for (lid in lineIds) {
            val idx = index.lineIdToIndex[lid]
            if (idx != null) tmp[count++] = idx
        }

        if (count == 0) return IntArray(0)

        val arr = if (count == tmp.size) tmp else tmp.copyOf(count)
        Arrays.parallelSort(arr)
        tocIndicesCache[tocId] = arr
        return arr
    }

    /**
     * Merges multiple sorted index arrays into a single sorted array with duplicates removed.
     * Uses parallel divide-and-conquer for large inputs.
     *
     * @param arrays List of sorted index arrays to merge
     * @return Single sorted array with unique indices
     */
    suspend fun mergeSortedIndicesParallel(arrays: List<IntArray>): IntArray {
        if (arrays.isEmpty()) return IntArray(0)
        if (arrays.size == 1) return arrays[0]

        return coroutineScope {
            suspend fun mergeRange(
                start: Int,
                end: Int,
            ): IntArray {
                val len = end - start
                return when {
                    len <= 0 -> IntArray(0)
                    len == 1 -> arrays[start]
                    len == 2 -> mergeTwo(arrays[start], arrays[start + 1])
                    else -> {
                        val mid = start + len / 2
                        val left = async(Dispatchers.Default) { mergeRange(start, mid) }
                        val right = async(Dispatchers.Default) { mergeRange(mid, end) }
                        mergeTwo(left.await(), right.await())
                    }
                }
            }
            mergeRange(0, arrays.size)
        }
    }

    /**
     * Filters results by book IDs using parallel processing for large datasets.
     *
     * @param results The search results to filter
     * @param allowedBookIds Set of allowed book IDs
     * @return Filtered results containing only matching books
     */
    fun parallelFilterByBook(
        results: List<SearchResult>,
        allowedBookIds: Set<Long>,
    ): List<SearchResult> {
        val total = results.size
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        if (total < PARALLEL_FILTER_THRESHOLD || cores == 1) {
            return fastFilterByBookSequential(results, allowedBookIds)
        }

        val chunk = (total + cores - 1) / cores
        return runBlocking(Dispatchers.Default) {
            val tasks = ArrayList<Deferred<List<SearchResult>>>(cores)
            var start = 0
            while (start < total) {
                val s = start
                val e = minOf(total, start + chunk)
                tasks +=
                    async {
                        val sub = ArrayList<SearchResult>(e - s)
                        var i = s
                        while (i < e) {
                            val v = results[i]
                            if (v.bookId in allowedBookIds) sub.add(v)
                            i++
                        }
                        sub
                    }
                start = e
            }
            tasks.awaitAll().flatten()
        }
    }

    /**
     * Filters results by book IDs sequentially.
     *
     * @param results The search results to filter
     * @param allowedBookIds Set of allowed book IDs
     * @return Filtered results containing only matching books
     */
    fun fastFilterByBookSequential(
        results: List<SearchResult>,
        allowedBookIds: Set<Long>,
    ): List<SearchResult> {
        val out = ArrayList<SearchResult>(results.size)
        for (r in results) {
            if (r.bookId in allowedBookIds) out.add(r)
        }
        return out
    }

    /**
     * Extracts results at the given indices from the results list.
     *
     * @param results The full results list
     * @param indices Sorted array of indices to extract
     * @return List of results at the specified indices
     */
    fun extractResultsAtIndices(
        results: List<SearchResult>,
        indices: IntArray,
    ): List<SearchResult> =
        ArrayList<SearchResult>(indices.size).apply {
            var i = 0
            while (i < indices.size) {
                add(results[indices[i]])
                i++
            }
        }

    companion object {
        /**
         * Merges two sorted arrays into one sorted array with duplicates removed.
         */
        fun mergeTwo(
            a: IntArray,
            b: IntArray,
        ): IntArray {
            val out = IntArray(a.size + b.size)
            var i = 0
            var j = 0
            var k = 0
            var hasLast = false
            var last = 0

            while (i < a.size && j < b.size) {
                val av = a[i]
                val bv = b[j]
                val v: Int
                if (av <= bv) {
                    v = av
                    i++
                } else {
                    v = bv
                    j++
                }
                if (!hasLast || v != last) {
                    out[k++] = v
                    last = v
                    hasLast = true
                }
            }

            while (i < a.size) {
                val v = a[i++]
                if (!hasLast || v != last) {
                    out[k++] = v
                    last = v
                    hasLast = true
                }
            }

            while (j < b.size) {
                val v = b[j++]
                if (!hasLast || v != last) {
                    out[k++] = v
                    last = v
                    hasLast = true
                }
            }

            return if (k == out.size) out else out.copyOf(k)
        }
    }
}
