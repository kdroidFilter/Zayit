package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * A book's details as the book-details pane shows them (categories, authors with their years, main
 * parts), for the search's panel and the reader's pane alike.
 */
class BookDetailsLoader(
    private val repository: SeforimRepository,
) {
    private val cache = ConcurrentHashMap<Long, SearchEntity.BookEntity>()

    /** A book with its categories, authors and main parts, once per book. */
    suspend fun load(bookId: Long): SearchEntity.BookEntity? = cache[bookId] ?: describe(bookId)?.also { cache[bookId] = it }

    private suspend fun describe(bookId: Long): SearchEntity.BookEntity? {
        val book = repository.getBook(bookId) ?: return null
        val categories = ArrayList<String>()
        var categoryId: Long? = book.categoryId
        while (categoryId != null && categories.size < MAX_DEPTH) {
            val category = repository.getCategory(categoryId) ?: break
            categories.add(0, category.title)
            categoryId = category.parentId
        }
        val authors = book.authors.mapNotNull { repository.getAuthorDetails(it.id) }
        return SearchEntity.BookEntity(book, categories, authors, mainParts(book))
    }

    /**
     * The book's main parts: its top TOC entries below a root that only repeats the title, when few
     * enough to mean something (הלכות לשון הרע…), else the first alternative TOC that has few (a
     * tractate's chapters rather than its folios); none when every list is long (סימן א, ב, ג…).
     */
    private suspend fun mainParts(book: Book): List<BookPart> {
        var level = repository.getBookRootToc(book.id)
        while (level.size == 1 && level[0].hasChildren) level = repository.getTocChildren(level[0].id)
        if (level.size <= MAX_PARTS) return level.mapNotNull { e -> e.lineId?.let { BookPart(e.text, it) } }
        for (structure in repository.getAltTocStructuresForBook(book.id)) {
            var alt = repository.getAltRootToc(structure.id)
            while (alt.size == 1 && alt[0].hasChildren) alt = repository.getAltTocChildren(alt[0].id)
            if (alt.size in 2..MAX_ALT_PARTS) return alt.mapNotNull { e -> e.lineId?.let { BookPart(e.text, it) } }
        }
        return emptyList()
    }

    private companion object {
        const val MAX_DEPTH = 12
        const val MAX_PARTS = 12
        const val MAX_ALT_PARTS = 20
    }
}
