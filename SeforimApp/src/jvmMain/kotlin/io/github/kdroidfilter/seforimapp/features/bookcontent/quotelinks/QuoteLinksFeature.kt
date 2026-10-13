package io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks

import io.github.kdroidfilter.seforimlibrary.core.models.Line

/**
 * The verse quotes of the Talmud as links to their verses, added to the lines the reader loads until the DB carries
 * them: in Zayit's official builds only (open core), from the SeforimQuoteLinks package in addons/quotelinks/enabled; a
 * community build compiles addons/quotelinks/disabled, where [installedQuoteLinks] is null.
 */
interface QuoteLinksFeature {
    /** Whether [bookId] has quote links; the first call reads a small index. */
    fun covers(bookId: Long): Boolean

    /**
     * [lines] of [bookId] with their quote links; the lines themselves, for the cost of a set lookup, when the book has
     * none. Call it off the UI thread, where the lines were loaded (it parses the book's links the first time).
     * [linesByIds] reads the verses the links point to, once each, to drop a link whose verse is no longer the one it
     * was made for.
     */
    suspend fun decorate(
        bookId: Long,
        lines: List<Line>,
        linesByIds: suspend (Collection<Long>) -> List<Line>,
    ): List<Line>
}
