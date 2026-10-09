package io.github.kdroidfilter.seforimapp.features.search.domain.reference

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/**
 * [ReferenceSource] over the seforim database. The TOCs of the last books looked up are kept,
 * since a reference is resolved again at each word typed.
 */
class RepositoryReferenceSource(
    private val repository: SeforimRepository,
) : ReferenceSource {
    private val tocs = LruCache<Long, List<TocNode>>(MAX_CACHED_BOOKS)
    private val altTocs = LruCache<Long, List<List<TocNode>>>(MAX_CACHED_BOOKS)

    override suspend fun booksNamed(normalizedName: String): List<Book> {
        // A LIKE pattern without wildcards: every book carrying exactly this title, whatever its source
        val byTitle = repository.findBooksByTitleLikeCore(normalizedName, limit = MAX_BOOKS)
        val byAcronym = repository.findBookIdsByAcronym(normalizedName).mapNotNull { repository.getBookCore(it) }
        return (byTitle + byAcronym).distinctBy { it.id }
    }

    override suspend fun toc(bookId: Long): List<TocNode> =
        tocs.getOrPut(bookId) {
            repository.getBookToc(bookId).map { TocNode(it.id, it.parentId, it.text, it.lineId) }
        }

    override suspend fun altTocs(bookId: Long): List<List<TocNode>> =
        altTocs.getOrPut(bookId) {
            repository.getAltTocStructuresForBook(bookId).map { structure ->
                repository.getAltTocEntriesForStructure(structure.id).map { TocNode(it.id, it.parentId, it.text, it.lineId) }
            }
        }

    override suspend fun linesOf(tocEntryId: Long): List<Line> =
        repository.getLinesByIds(repository.getLineIdsForTocEntry(tocEntryId)).sortedBy { it.lineIndex }

    // Thread-safe LRU; a value is loaded outside the lock, at worst twice by concurrent lookups
    private class LruCache<K, V>(
        private val maxSize: Int,
    ) {
        private val map =
            object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
            }

        suspend fun getOrPut(
            key: K,
            load: suspend () -> V,
        ): V {
            synchronized(map) { map[key] }?.let { return it }
            val value = load()
            synchronized(map) { map[key] = value }
            return value
        }
    }

    private companion object {
        const val MAX_BOOKS = 10
        const val MAX_CACHED_BOOKS = 12
    }
}
