package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import io.github.kdroidfilter.seforimlibrary.dao.repository.BookEdition
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/** A part of a book and the line it starts at. */
data class BookPart(
    val title: String,
    val lineId: Long,
)

/** What a search names, shown in a panel beside its results, as Google's knowledge panel. */
sealed interface SearchEntity {
    data class BookEntity(
        val book: Book,
        // Its categories, from the root (הלכה › שולחן ערוך)
        val categories: List<String>,
        // Its authors, with their era and years
        val authors: List<AuthorDetails>,
        // Its main parts (הלכות לשון הרע, הלכות רכילות; פרק א for a tractate), to open directly
        val parts: List<BookPart>,
        // Its editions, with their source and license
        val editions: List<BookEdition>,
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
    private val books: BookDetailsLoader,
) {
    suspend fun find(query: String): SearchEntity? {
        val key = query.searchKey()
        if (key.isEmpty()) return null
        val hits = lookup.suggestBooks(query, BOOK_CANDIDATES)
        hits.firstOrNull { it.title.searchKey() == key }?.let { return books.load(it.id) }
        authorNamed(query, key)?.let { return SearchEntity.AuthorEntity(it) }
        return hits.firstOrNull { it.exactAcronym }?.let { books.load(it.id) }
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

    private companion object {
        const val BOOK_CANDIDATES = 5
        const val AUTHOR_CANDIDATES = 3
    }
}
