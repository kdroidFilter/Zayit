package io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks

import io.github.kdroidfilter.seforim.quotelinks.BookQuoteLinks
import io.github.kdroidfilter.seforim.quotelinks.QuoteLinkInjector
import io.github.kdroidfilter.seforim.quotelinks.QuoteLinks
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import java.util.concurrent.ConcurrentHashMap

/** An official build: the quote links of the SeforimQuoteLinks package. */
val installedQuoteLinks: QuoteLinksFeature? = TalmudQuoteLinks

internal object TalmudQuoteLinks : QuoteLinksFeature {
    private const val MAX_BOOKS = 4

    // The last books read, parsed from the package once each
    private val books =
        object : LinkedHashMap<Long, BookQuoteLinks>(MAX_BOOKS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, BookQuoteLinks>?): Boolean = size > MAX_BOOKS
        }

    // Verse line id → whether it still is the verse the links were made for; read once per session
    private val validTargets = ConcurrentHashMap<Long, Boolean>()

    override fun covers(bookId: Long): Boolean = QuoteLinks.covers(bookId)

    override suspend fun decorate(
        bookId: Long,
        lines: List<Line>,
        linesByIds: suspend (Collection<Long>) -> List<Line>,
    ): List<Line> {
        // Any other book: a set lookup
        val book = book(bookId) ?: return lines
        val unchecked =
            lines
                .asSequence()
                .mapNotNull { book.of(it.heRef) }
                .flatMap { it.links }
                .filter { !validTargets.containsKey(it.targetId) }
                .associateBy { it.targetId }
        if (unchecked.isNotEmpty()) {
            val heRefs = linesByIds(unchecked.keys).associate { it.id to it.heRef }
            unchecked.forEach { (id, link) -> validTargets[id] = heRefs[id] == link.targetHeRef }
        }
        return lines.map { line ->
            val quotes = book.of(line.heRef) ?: return@map line
            val html = QuoteLinkInjector.decorate(line.content, quotes) { validTargets[it.targetId] == true }
            if (html === line.content) line else line.copy(content = html)
        }
    }

    private fun book(bookId: Long): BookQuoteLinks? =
        synchronized(books) { books[bookId] }
            ?: QuoteLinks.book(bookId)?.also { synchronized(books) { books[bookId] = it } }
}
