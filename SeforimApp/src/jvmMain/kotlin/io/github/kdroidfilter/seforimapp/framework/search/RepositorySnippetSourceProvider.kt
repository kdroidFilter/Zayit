package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LineSnippetInfo
import io.github.kdroidfilter.seforimlibrary.search.SnippetProvider
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/**
 * Implementation of [SnippetProvider] that fetches line content from the database
 * and reproduces the exact same snippet source logic as the indexer.
 *
 * This allows removing the text_raw field from the Lucene index to reduce index size,
 * while maintaining identical search behavior.
 */
class RepositorySnippetSourceProvider(
    private val repository: SeforimRepository
) : SnippetProvider {

    companion object {
        // Must match the indexer constants
        private const val SNIPPET_NEIGHBOR_WINDOW = 4
        private const val SNIPPET_MIN_LENGTH = 280
    }

    override fun getSnippetSources(lines: List<LineSnippetInfo>): Map<Long, String> {
        if (lines.isEmpty()) return emptyMap()

        return runBlocking {
            // Group lines by bookId for efficient batch loading
            val byBook = lines.groupBy { it.bookId }
            val result = mutableMapOf<Long, String>()

            for ((bookId, bookLines) in byBook) {
                // Determine the range of line indices we need to load (including neighbors)
                val minIdx = bookLines.minOf { it.lineIndex }
                val maxIdx = bookLines.maxOf { it.lineIndex }
                val rangeStart = (minIdx - SNIPPET_NEIGHBOR_WINDOW).coerceAtLeast(0)
                val rangeEnd = maxIdx + SNIPPET_NEIGHBOR_WINDOW

                // Load all lines in the extended range
                val allLines = repository.getLines(bookId, rangeStart, rangeEnd)

                // Create a map of lineIndex -> cleaned plain text
                val plainByIndex = allLines.associate { line ->
                    line.lineIndex to cleanHtml(line.content)
                }

                // Build snippet source for each requested line
                for (info in bookLines) {
                    val basePlain = plainByIndex[info.lineIndex].orEmpty()
                    val snippetSource = if (basePlain.length >= SNIPPET_MIN_LENGTH) {
                        basePlain
                    } else {
                        // Include neighboring lines to get enough context
                        val start = (info.lineIndex - SNIPPET_NEIGHBOR_WINDOW).coerceAtLeast(0)
                        val end = info.lineIndex + SNIPPET_NEIGHBOR_WINDOW
                        (start..end)
                            .mapNotNull { plainByIndex[it] }
                            .joinToString(" ")
                    }
                    result[info.lineId] = snippetSource
                }
            }

            result
        }
    }

    private fun cleanHtml(content: String): String {
        return Jsoup.clean(content, Safelist.none())
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
