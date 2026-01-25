package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import java.util.Arrays

/**
 * Use case for TOC-related operations in search.
 *
 * This class provides functionality to:
 * - Build TOC tree structures for books
 * - Create and cache line-to-TOC mappings
 * - Collect line IDs under TOC subtrees
 * - Compute hit counts per TOC entry
 *
 * @property repository The Seforim database repository
 */
class SearchTocUseCase(
    private val repository: SeforimRepository,
) {
    // Cache for TOC line indexes by book ID
    private val tocLineIndexCache: MutableMap<Long, TocLineIndex> = mutableMapOf()

    /**
     * Builds a TOC tree for a given book.
     *
     * @param bookId The book ID
     * @return TocTree with root entries and children map
     */
    suspend fun buildTocTreeForBook(bookId: Long): TocTree {
        val all = runCatching { repository.getBookToc(bookId) }.getOrElse { emptyList() }
        val byParent = all.groupBy { it.parentId ?: -1L }
        val roots = byParent[-1L] ?: all.filter { it.parentId == null }
        val children = all.filter { it.parentId != null }.groupBy { it.parentId!! }
        return TocTree(roots, children)
    }

    /**
     * Ensures a TOC line index exists for the given book and returns it.
     * Uses caching to avoid repeated lookups.
     *
     * @param bookId The book ID
     * @param existingTree Optional existing TocTree to use for building the index
     * @return TocLineIndex for the book
     */
    suspend fun ensureTocLineIndex(
        bookId: Long,
        existingTree: TocTree? = null,
    ): TocLineIndex {
        tocLineIndexCache[bookId]?.let { return it }

        val mappings = runCatching { repository.getLineTocMappingsForBook(bookId) }.getOrElse { emptyList() }
        val grouped =
            mappings.groupBy { it.tocEntryId }.mapValues { entry ->
                entry.value
                    .map { it.lineId }
                    .sorted()
                    .toLongArray()
            }

        val tocEntries =
            existingTree?.let { it.rootEntries + it.children.values.flatten() }
                ?: runCatching { repository.getBookToc(bookId) }.getOrElse { emptyList() }

        val children = mutableMapOf<Long, MutableList<Long>>()
        val parent = mutableMapOf<Long, Long?>()
        for (t in tocEntries) {
            parent[t.id] = t.parentId
            if (t.parentId != null) {
                children.getOrPut(t.parentId!!) { mutableListOf() }.add(t.id)
            }
        }

        val index =
            TocLineIndex(
                tocToLines = grouped,
                children = children.mapValues { it.value.toList() },
                parent = parent,
            )
        tocLineIndexCache[bookId] = index
        return index
    }

    /**
     * Collects all line IDs under a TOC subtree.
     *
     * @param tocId The TOC entry ID (root of subtree)
     * @param bookId The book ID
     * @return Set of line IDs under the TOC subtree
     */
    suspend fun collectLineIdsForTocSubtree(
        tocId: Long,
        bookId: Long,
    ): Set<Long> {
        val index = ensureTocLineIndex(bookId)
        return index.subtreeLineIds(tocId).toSet()
    }

    /**
     * Gets the line IDs for a TOC subtree as a LongArray (for use with ResultsIndexingUseCase).
     *
     * @param tocId The TOC entry ID
     * @param bookId The book ID
     * @return LongArray of line IDs
     */
    suspend fun getSubtreeLineIds(
        tocId: Long,
        bookId: Long,
    ): LongArray {
        val index = ensureTocLineIndex(bookId)
        return index.subtreeLineIds(tocId)
    }

    /**
     * Computes TOC counts for a list of line IDs.
     * Returns a map of TOC entry ID to hit count, including ancestor propagation.
     *
     * @param lineIds The line IDs to count
     * @param index The TocLineIndex to use
     * @return Map of TOC ID to count
     */
    fun computeTocCountsForLines(
        lineIds: List<Long>,
        index: TocLineIndex,
    ): Map<Long, Int> {
        val counts = mutableMapOf<Long, Int>()
        for (lineId in lineIds) {
            val tocId = index.lineIdToTocId[lineId] ?: continue
            var current: Long? = tocId
            var guard = 0
            while (current != null && guard++ < 500) {
                counts[current] = (counts[current] ?: 0) + 1
                current = index.parent[current]
            }
        }
        return counts
    }

    /**
     * Clears all caches. Call this when data may have changed.
     */
    fun clearCache() {
        tocLineIndexCache.clear()
    }

    /**
     * Gets a cached TocLineIndex if available.
     *
     * @param bookId The book ID
     * @return The cached index or null
     */
    fun getCachedIndex(bookId: Long): TocLineIndex? = tocLineIndexCache[bookId]
}

/**
 * Represents a TOC tree structure for a book.
 *
 * @property rootEntries Top-level TOC entries
 * @property children Map of parent ID to list of child entries
 */
data class TocTree(
    val rootEntries: List<TocEntry>,
    val children: Map<Long, List<TocEntry>>,
)

/**
 * Index structure for fast TOC line lookups.
 *
 * @property tocToLines Map of TOC entry ID to array of line IDs
 * @property children Map of TOC ID to list of child TOC IDs
 * @property parent Map of TOC ID to parent TOC ID
 */
data class TocLineIndex(
    val tocToLines: Map<Long, LongArray>,
    val children: Map<Long, List<Long>>,
    val parent: Map<Long, Long?>,
    private val subtreeCache: MutableMap<Long, LongArray> = mutableMapOf(),
) {
    /**
     * Map of line ID to its containing TOC entry ID.
     */
    val lineIdToTocId: Map<Long, Long> =
        tocToLines
            .flatMap { (k, v) -> v.map { it to k } }
            .toMap()

    /**
     * Gets all line IDs under a TOC subtree (including descendants).
     *
     * @param tocId The TOC entry ID
     * @return LongArray of line IDs
     */
    fun subtreeLineIds(tocId: Long): LongArray {
        subtreeCache[tocId]?.let { return it }
        val self = tocToLines[tocId] ?: LongArray(0)
        val childIds = children[tocId].orEmpty()
        if (childIds.isEmpty()) {
            subtreeCache[tocId] = self
            return self
        }
        val arrays = ArrayList<LongArray>(childIds.size + 1)
        if (self.isNotEmpty()) arrays.add(self)
        for (child in childIds) {
            val arr = subtreeLineIds(child)
            if (arr.isNotEmpty()) arrays.add(arr)
        }
        val merged = mergeLongArrays(arrays)
        subtreeCache[tocId] = merged
        return merged
    }

    companion object {
        /**
         * Merges multiple sorted LongArrays into a single sorted array without duplicates.
         */
        fun mergeLongArrays(arrays: List<LongArray>): LongArray {
            if (arrays.isEmpty()) return LongArray(0)
            if (arrays.size == 1) return arrays[0]
            var total = 0
            for (a in arrays) total += a.size
            if (total == 0) return LongArray(0)
            val out = LongArray(total)
            var pos = 0
            for (a in arrays) {
                System.arraycopy(a, 0, out, pos, a.size)
                pos += a.size
            }
            Arrays.parallelSort(out)
            var write = 0
            var i = 0
            while (i < out.size) {
                val v = out[i]
                if (write == 0 || out[write - 1] != v) {
                    out[write++] = v
                }
                i++
            }
            return if (write == out.size) out else out.copyOf(write)
        }
    }
}
