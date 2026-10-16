package io.github.kdroidfilter.seforimapp.framework.search

import kotlin.math.round

/**
 * What the app's search answers on real data, as text: the lookup (books, authors, tables of contents) and the search
 * engine (hybrid in the official builds: hits, snippets, highlights by meaning, smart find in a book). The same data
 * gives the same dump on the desktop and the mobile apps, up to Lucene's score noise (see SeforimLibrary's SearchDump).
 */
object SearchKitDump {
    private val BOOK_QUERIES = listOf("שוע", "רמבם", "בראשית", "חפץ חיים", "משנה ברורה", "גמ")
    private val SEARCH_QUERIES = listOf("מה מברכים על אוכל", "ואהבת לרעך כמוך", "\"קריאת שמע\"", "הלכות שבת")

    suspend fun dump(services: SearchServices): String =
        buildString {
            val lookup = services.lookup
            for (q in BOOK_QUERIES) appendLine("books $q -> ${lookup.suggestBooks(q, 8).map { "${it.id}:${it.title}" }}")
            appendLine("authors רמב -> ${lookup.suggestAuthors("רמב", 5).map { it.name }}")
            appendLine("toc הלכות שבת -> ${lookup.searchTocPrefix("הלכות שבת", 5).map { "${it.bookId}:${it.text}" }}")

            val engine = services.engine
            appendLine("dense ${engine.denseReady()}")
            for (q in SEARCH_QUERIES) {
                appendLine("## $q (by meaning: ${engine.highlightsByMeaning(q)})")
                val page = engine.openSession(q, baseBookOnly = false)?.use { it.nextPage(10) }
                appendLine("total ${page?.totalHits}")
                val hits = page?.hits.orEmpty()
                for (hit in hits) appendLine("  ${hit.lineId} ${hit.bookTitle} ${(round(hit.score * 1e5) / 1e5)} | ${hit.snippet}")
                val texts = hits.take(3).map { it.rawText.substring(it.lineStart, it.lineEnd) }
                val highlights = engine.highlights(texts, q)
                highlights.forEachIndexed {
                    i,
                    h,
                    ->
                    appendLine("  highlight ${hits[i].lineId} byMeaning=${h.byMeaning} ${h.ranges.map { texts[i].substring(it) }}")
                }
                hits.firstOrNull()?.let { appendLine("  find in ${it.bookId}: ${engine.semanticFind(q, it.bookId, 5)}") }
            }
        }
}
