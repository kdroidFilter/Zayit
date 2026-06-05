package io.github.kdroidfilter.seforimapp.core.annotations

import androidx.compose.runtime.Stable
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * A single position-based user note, anchored to a character range on a line. A line may carry
 * several notes (a word, a phrase…), possibly overlapping.
 *
 * @property id Database row id.
 * @property lineId Id of the annotated line.
 * @property startOffset Inclusive start offset in the line's plain text.
 * @property endOffset Exclusive end offset in the line's plain text.
 * @property note Free-text note body.
 * @property quote The anchored passage (selected text) shown in the notes pane.
 */
@Stable
data class UserNote(
    val id: Long,
    val lineId: Long,
    val startOffset: Int,
    val endOffset: Int,
    val note: String,
    val quote: String = "",
)

/**
 * Persists position-based notes in the local user database and exposes them as a per-book
 * in-memory cache for the render hot path. Mirrors [HighlightStore]: reads hit SQLite once per
 * book ([loadBook]); the rendering path reads the cached [StateFlow] map.
 */
class NoteStore(
    database: UserSettingsDb,
) {
    private val queries = database.userNotesQueries

    private val _notesByBook = MutableStateFlow<Map<Long, List<UserNote>>>(emptyMap())
    val notesByBook: StateFlow<Map<Long, List<UserNote>>> = _notesByBook.asStateFlow()

    private val loadedBooks = mutableSetOf<Long>()

    /** Loads a book's notes into the cache once. Subsequent calls are no-ops. */
    suspend fun loadBook(bookId: Long): Unit =
        withContext(Dispatchers.IO) {
            if (bookId in loadedBooks) return@withContext
            refreshBook(bookId)
            loadedBooks += bookId
        }

    /** Notes of a single line; safe to call from composition (cache lookup). */
    fun notesForLine(
        bookId: Long,
        lineId: Long,
    ): List<UserNote> = _notesByBook.value[bookId].orEmpty().filter { it.lineId == lineId }

    /**
     * Adds a note anchored to [startOffset, endOffset) and returns its new row id, or `null` for an
     * empty/inverted range or a blank body (the insert is then skipped).
     */
    suspend fun addNote(
        bookId: Long,
        lineId: Long,
        startOffset: Int,
        endOffset: Int,
        note: String,
        timestamp: Long,
        quote: String = "",
    ): Long? =
        withContext(Dispatchers.IO) {
            if (startOffset >= endOffset || note.isBlank()) return@withContext null
            val id =
                queries.transactionWithResult {
                    queries.insert(
                        bookId = bookId,
                        lineId = lineId,
                        startOffset = startOffset.toLong(),
                        endOffset = endOffset.toLong(),
                        note = note,
                        quote = quote,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    )
                    queries.lastInsertRowId().executeAsOne()
                }
            refreshBook(bookId)
            id
        }

    /** Updates a note's text; a blank text deletes it instead. */
    suspend fun updateNote(
        bookId: Long,
        noteId: Long,
        note: String,
        timestamp: Long,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (note.isBlank()) {
                queries.deleteById(noteId)
            } else {
                queries.updateNote(note = note, updatedAt = timestamp, id = noteId)
            }
            refreshBook(bookId)
        }

    /** Removes a note. */
    suspend fun removeNote(
        bookId: Long,
        noteId: Long,
    ): Unit =
        withContext(Dispatchers.IO) {
            queries.deleteById(noteId)
            refreshBook(bookId)
        }

    /** Re-reads the book's notes from SQLite and replaces the cache entry. */
    private fun refreshBook(bookId: Long) {
        val list =
            queries.selectAllForBook(bookId).executeAsList().map { row ->
                UserNote(
                    id = row.id,
                    lineId = row.lineId,
                    startOffset = row.startOffset.toInt(),
                    endOffset = row.endOffset.toInt(),
                    note = row.note,
                    quote = row.quote,
                )
            }
        _notesByBook.update { it + (bookId to list) }
    }
}
