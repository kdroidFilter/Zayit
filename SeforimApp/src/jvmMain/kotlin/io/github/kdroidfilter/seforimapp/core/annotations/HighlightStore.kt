package io.github.kdroidfilter.seforimapp.core.annotations

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * A single position-based user highlight.
 *
 * @property id Database row id (0 before persistence).
 * @property lineId Id of the line carrying this highlight.
 * @property startOffset Inclusive start offset in the line's plain text.
 * @property endOffset Exclusive end offset in the line's plain text.
 * @property color Highlight color.
 */
@Stable
data class UserHighlight(
    val id: Long,
    val lineId: Long,
    val startOffset: Int,
    val endOffset: Int,
    val color: Color,
) {
    val textRange: TextRange get() = TextRange(startOffset, endOffset)
}

/** Palette offered to the user. [Transparent] acts as the "clear highlight" action. */
object HighlightColors {
    val Yellow = Color(0xFFFFEB3B)
    val Green = Color(0xFF4CAF50)
    val Blue = Color(0xFF2196F3)
    val Pink = Color(0xFFE91E63)
    val Orange = Color(0xFFFF9800)

    val all = listOf(Yellow, Green, Blue, Pink, Orange)
    val allWithClear = all + Color.Transparent
}

/**
 * Persists position-based highlights in the local user database and exposes them
 * as a per-book in-memory cache for the render hot path.
 *
 * Reads hit SQLite only once per book ([loadBook]); the rendering path reads the
 * cached [StateFlow] map (O(1) per line). Writes are rare, explicit user actions.
 */
class HighlightStore(
    database: UserSettingsDb,
) {
    private val queries = database.userHighlightsQueries

    private val _highlightsByBook = MutableStateFlow<Map<Long, List<UserHighlight>>>(emptyMap())
    val highlightsByBook: StateFlow<Map<Long, List<UserHighlight>>> = _highlightsByBook.asStateFlow()

    private val loadedBooks = mutableSetOf<Long>()

    /** Loads a book's highlights into the cache once. Subsequent calls are no-ops. */
    suspend fun loadBook(bookId: Long): Unit =
        withContext(Dispatchers.IO) {
            if (bookId in loadedBooks) return@withContext
            refreshBook(bookId)
            loadedBooks += bookId
        }

    /** Highlights of a single line; safe to call from composition (cache lookup). */
    fun highlightsForLine(
        bookId: Long,
        lineId: Long,
    ): List<UserHighlight> = _highlightsByBook.value[bookId].orEmpty().filter { it.lineId == lineId }

    /**
     * Adds a highlight, replacing any existing highlight overlapping the same range
     * on the same line. No-op for an empty/inverted range.
     */
    suspend fun addHighlight(
        bookId: Long,
        lineId: Long,
        startOffset: Int,
        endOffset: Int,
        color: Color,
        timestamp: Long,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (startOffset >= endOffset) return@withContext
            queries.transaction {
                queries.deleteOverlapping(
                    bookId = bookId,
                    lineId = lineId,
                    endOffset = endOffset.toLong(),
                    startOffset = startOffset.toLong(),
                )
                queries.insert(
                    bookId = bookId,
                    lineId = lineId,
                    startOffset = startOffset.toLong(),
                    endOffset = endOffset.toLong(),
                    colorArgb = color.toArgb().toLong(),
                    createdAt = timestamp,
                )
            }
            refreshBook(bookId)
        }

    /** Removes a previously persisted highlight. */
    suspend fun removeHighlight(
        bookId: Long,
        highlight: UserHighlight,
    ): Unit =
        withContext(Dispatchers.IO) {
            queries.deleteById(highlight.id)
            refreshBook(bookId)
        }

    /** Removes any highlight on [lineId] overlapping the given range (the "clear" action). */
    suspend fun removeOverlapping(
        bookId: Long,
        lineId: Long,
        startOffset: Int,
        endOffset: Int,
    ): Unit =
        withContext(Dispatchers.IO) {
            queries.deleteOverlapping(
                bookId = bookId,
                lineId = lineId,
                endOffset = endOffset.toLong(),
                startOffset = startOffset.toLong(),
            )
            refreshBook(bookId)
        }

    /** Re-reads the book's highlights from SQLite and replaces the cache entry. */
    private fun refreshBook(bookId: Long) {
        val list =
            queries.selectAllForBook(bookId).executeAsList().map { row ->
                UserHighlight(
                    id = row.id,
                    lineId = row.lineId,
                    startOffset = row.startOffset.toInt(),
                    endOffset = row.endOffset.toInt(),
                    color = Color(row.colorArgb.toInt()),
                )
            }
        _highlightsByBook.update { it + (bookId to list) }
    }
}
