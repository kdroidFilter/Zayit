package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/** What a search names, shown in a panel beside its results, as Google's knowledge panel. */
sealed interface SearchEntity {
    data class BookEntity(
        val book: Book,
        // Its categories, from the root (הלכה › שולחן ערוך)
        val categories: List<String>,
    ) : SearchEntity

    data class AuthorEntity(
        val details: AuthorDetails,
    ) : SearchEntity
}

/**
 * Finds the book or author a query names exactly: a book's title first (חפץ חיים is a book before
 * its author's alias), then an author's name or alias (רמב"ם, חפץ חיים), then a book's whole
 * acronym (שוע). Null when the query names nothing, the usual case for a text search.
 */
class SearchEntityFinder(
    private val lookup: LuceneLookupSearchService,
    private val repository: SeforimRepository,
) {
    suspend fun find(query: String): SearchEntity? {
        val key = query.searchKey()
        if (key.isEmpty()) return null
        val books = lookup.suggestBooks(query, BOOK_CANDIDATES)
        books.firstOrNull { it.title.searchKey() == key }?.let { return bookEntity(it.id) }
        authorNamed(query, key)?.let { return SearchEntity.AuthorEntity(it) }
        return books.firstOrNull { it.exactAcronym }?.let { bookEntity(it.id) }
    }

    private suspend fun authorNamed(
        query: String,
        key: String,
    ): AuthorDetails? {
        for (hit in lookup.suggestAuthors(query, AUTHOR_CANDIDATES)) {
            val details = repository.getAuthorDetails(hit.id) ?: continue
            val names = listOf(details.name, AuthorNames.display(details.name)) + details.aliases
            if (names.any { it.searchKey() == key }) return details
        }
        return null
    }

    private suspend fun bookEntity(bookId: Long): SearchEntity.BookEntity? {
        val book = repository.getBook(bookId) ?: return null
        val categories = ArrayList<String>()
        var categoryId: Long? = book.categoryId
        while (categoryId != null && categories.size < MAX_DEPTH) {
            val category = repository.getCategory(categoryId) ?: break
            categories.add(0, category.title)
            categoryId = category.parentId
        }
        return SearchEntity.BookEntity(book, categories)
    }

    private companion object {
        const val BOOK_CANDIDATES = 5
        const val AUTHOR_CANDIDATES = 3
        const val MAX_DEPTH = 12
    }
}
