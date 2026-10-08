package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforim.htmlparser.plainTextFromHtml
import io.github.kdroidfilter.seforimapp.core.presentation.text.findAllMatchesNormalized
import io.github.kdroidfilter.seforimapp.core.presentation.text.normalizeQueryForHebrew
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The matches of the find-in-page query in a book, in reading order. Only each matching line's
 * index and id, and a prefix sum of its match count, are kept: a query matching every line of a
 * large book costs three arrays, never the lines themselves.
 */
@Immutable
class BookFindMatches internal constructor(
    private val lineIndices: IntArray,
    internal val lineIds: LongArray,
    // firstOccurrence[i] = matches before the line i; one more entry holding the total
    private val firstOccurrence: IntArray,
) {
    /** Matches in the whole book, several in a line counting as several. */
    val occurrences: Int get() = firstOccurrence.last()

    data class Match(
        val lineIndex: Int,
        val lineId: Long,
        /** Rank of the match within its line, from 0. */
        val ordinal: Int,
        /** Rank of the match within the book, from 1, as shown to the user. */
        val position: Int,
    )

    /** The match after [current], or before it when not [forward], wrapping around the book. */
    fun next(
        current: Match,
        forward: Boolean,
    ): Match? {
        val i = lineIndices.binarySearch(current.lineIndex)
        if (i < 0) return nextAfterLine(current.lineIndex, forward)
        val ordinal = current.ordinal + if (forward) 1 else -1
        return when {
            ordinal in 0 until countIn(i) -> matchAt(i, ordinal)
            forward -> matchAt(if (i + 1 < lineIndices.size) i + 1 else 0, 0)
            else -> (if (i > 0) i - 1 else lineIndices.lastIndex).let { matchAt(it, countIn(it) - 1) }
        }
    }

    /**
     * The first match of the first line after [fromLineIndex], or when not [forward] the last
     * match of the last line before it, wrapping around the book.
     */
    fun nextAfterLine(
        fromLineIndex: Int,
        forward: Boolean,
    ): Match? {
        if (lineIndices.isEmpty()) return null
        val pos = lineIndices.binarySearch(fromLineIndex)
        return if (forward) {
            matchAt((if (pos >= 0) pos + 1 else -pos - 1).takeIf { it < lineIndices.size } ?: 0, 0)
        } else {
            val i = (if (pos >= 0) pos - 1 else -pos - 2).takeIf { it >= 0 } ?: lineIndices.lastIndex
            matchAt(i, countIn(i) - 1)
        }
    }

    /** Whether [match] is one of these matches: a match kept from older results may no longer be. */
    operator fun contains(match: Match): Boolean {
        val i = lineIndices.binarySearch(match.lineIndex)
        return i >= 0 &&
            lineIds[i] == match.lineId &&
            match.ordinal in 0 until countIn(i) &&
            match.position == firstOccurrence[i] + match.ordinal + 1
    }

    private fun countIn(i: Int) = firstOccurrence[i + 1] - firstOccurrence[i]

    private fun matchAt(
        i: Int,
        ordinal: Int,
    ) = Match(lineIndices[i], lineIds[i], ordinal, firstOccurrence[i] + ordinal + 1)

    companion object {
        val Empty = BookFindMatches(IntArray(0), LongArray(0), IntArray(1))

        /** Whole-line matches (the smart mode's), one occurrence each. */
        fun ofLines(lines: List<Line>): BookFindMatches {
            val sorted = lines.sortedBy { it.lineIndex }
            return BookFindMatches(
                lineIndices = IntArray(sorted.size) { sorted[it].lineIndex },
                lineIds = LongArray(sorted.size) { sorted[it].id },
                firstOccurrence = IntArray(sorted.size + 1) { it },
            )
        }
    }
}

/**
 * Finds [query] in the whole book [bookId], loaded lines or not. Lucene narrows the book down to
 * its candidate lines, read from the DB [FIND_CHUNK] at a time and matched exactly as the
 * highlight does (diacritic-insensitive, on the displayed text); only the matches are kept.
 *
 * @param narrowing matches of a query [query] contains: only their lines can hold [query], which
 *        spares rescanning a large book at each letter typed
 * @param loadRange the lines of the book with an index in the range, to scan it all when the
 *        index can't answer (book not indexed, no index)
 */
suspend fun findInBook(
    searchEngine: SearchEngine,
    loadLines: suspend (List<Long>) -> List<Line>,
    loadRange: suspend (start: Int, endInclusive: Int) -> List<Line>,
    bookId: Long,
    query: String,
    narrowing: BookFindMatches? = null,
): BookFindMatches =
    withContext(Dispatchers.Default) {
        val candidates =
            narrowing?.lineIds
                ?: try {
                    searchEngine.findInBookCandidates(query, bookId)
                } catch (e: IOException) {
                    debugln { "find-in-book: index unavailable, scanning the book: $e" }
                    null
                } catch (e: RuntimeException) {
                    // e.g. too many clauses for a long pasted passage: the scan still answers
                    debugln { "find-in-book: index query failed, scanning the book: $e" }
                    null
                }
        val normalizedQuery = normalizeQueryForHebrew(query)
        // (lineIndex, lineId, count) of each matching line, sorted once complete
        val found = ArrayList<Triple<Int, Long, Int>>()

        fun confirm(lines: List<Line>) {
            for (line in lines) {
                ensureActive()
                // The displayed text, without building its styles
                val count = findAllMatchesNormalized(plainTextFromHtml(line.content), normalizedQuery).size
                if (count > 0) found += Triple(line.lineIndex, line.id, count)
            }
        }
        if (candidates != null) {
            for (chunk in candidates.asList().chunked(FIND_CHUNK)) confirm(loadLines(chunk))
        } else {
            // Line indices run from 0 without gaps: stop at the first short page
            var start = 0
            do {
                val lines = loadRange(start, start + FIND_CHUNK - 1)
                confirm(lines)
                start += FIND_CHUNK
            } while (lines.size == FIND_CHUNK)
        }
        found.sortBy { it.first }
        val firstOccurrence = IntArray(found.size + 1)
        found.forEachIndexed { i, (_, _, count) -> firstOccurrence[i + 1] = firstOccurrence[i] + count }
        BookFindMatches(
            lineIndices = IntArray(found.size) { found[it].first },
            lineIds = LongArray(found.size) { found[it].second },
            firstOccurrence = firstOccurrence,
        )
    }

/** Whether every line holding [query] also holds [previous], so the matches of [previous] can narrow its search. */
fun queryNarrows(
    query: String,
    previous: String,
): Boolean {
    val prev = normalizeQueryForHebrew(previous)
    return prev.length >= 2 && normalizeQueryForHebrew(query).contains(prev)
}

// Lines read per DB query: bounds memory, and stays under SQLite's bound-parameter limit
private const val FIND_CHUNK = 500
