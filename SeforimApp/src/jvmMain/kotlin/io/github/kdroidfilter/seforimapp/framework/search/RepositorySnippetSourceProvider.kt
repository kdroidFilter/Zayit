package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LineSnippetInfo
import io.github.kdroidfilter.seforimlibrary.search.SnippetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

// Must match the indexer constants
private const val SNIPPET_NEIGHBOR_WINDOW = 4
private const val SNIPPET_MIN_LENGTH = 280
private val WHITESPACE = Regex("\\s+")

/**
 * Implementation of [SnippetProvider] that fetches line content from the database
 * and reproduces the exact same snippet source logic as the indexer.
 *
 * This allows removing the text_raw field from the Lucene index to reduce index size,
 * while maintaining identical search behavior.
 */
class RepositorySnippetSourceProvider(
    private val repository: SeforimRepository,
) : SnippetProvider {
    override suspend fun getSnippetSources(lines: List<LineSnippetInfo>): Map<Long, String> {
        if (lines.isEmpty()) return emptyMap()

        return withContext(Dispatchers.IO) {
            // Group lines by bookId for efficient batch loading
            val byBook = lines.groupBy { it.bookId }
            val result = mutableMapOf<Long, String>()

            for ((bookId, bookLines) in byBook) {
                // Load only the window around each hit (overlapping windows merged): a book's hits can be thousands
                // of lines apart, and loading everything between them cost seconds on large books
                val contentByIndex = HashMap<Int, String>()
                for (range in neighborWindows(bookLines.map { it.lineIndex })) {
                    repository.getLines(bookId, range.first, range.last).forEach { contentByIndex[it.lineIndex] = it.content }
                }
                // Cleaned lazily: neighbors are only needed for short lines
                val plainByIndex = HashMap<Int, String>()

                fun plain(index: Int): String? =
                    plainByIndex[index] ?: contentByIndex[index]?.let { cleanHtml(it).also { clean -> plainByIndex[index] = clean } }

                // Build snippet source for each requested line
                for (info in bookLines) {
                    val basePlain = plain(info.lineIndex).orEmpty()
                    val snippetSource =
                        if (basePlain.length >= SNIPPET_MIN_LENGTH) {
                            basePlain
                        } else {
                            // Include neighboring lines to get enough context
                            val start = (info.lineIndex - SNIPPET_NEIGHBOR_WINDOW).coerceAtLeast(0)
                            val end = info.lineIndex + SNIPPET_NEIGHBOR_WINDOW
                            (start..end)
                                .mapNotNull { plain(it) }
                                .joinToString(" ")
                        }
                    result[info.lineId] = snippetSource
                }
            }

            result
        }
    }

    private fun neighborWindows(lineIndexes: List<Int>): List<IntRange> {
        val windows = ArrayList<IntRange>()
        for (index in lineIndexes.sorted()) {
            val start = (index - SNIPPET_NEIGHBOR_WINDOW).coerceAtLeast(0)
            val end = index + SNIPPET_NEIGHBOR_WINDOW
            val last = windows.lastOrNull()
            if (last != null && start <= last.last + 1) {
                windows[windows.lastIndex] = last.first..maxOf(last.last, end)
            } else {
                windows += start..end
            }
        }
        return windows
    }

    private fun cleanHtml(content: String): String =
        Jsoup
            .clean(content, Safelist.none())
            .replace(WHITESPACE, " ")
            .trim()
}
