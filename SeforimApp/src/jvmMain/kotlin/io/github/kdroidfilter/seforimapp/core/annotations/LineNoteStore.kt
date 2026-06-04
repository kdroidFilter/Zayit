package io.github.kdroidfilter.seforimapp.core.annotations

import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Persists one free-text note per line in the local user database.
 *
 * Only the set of annotated line ids per book is cached ([linesWithNote]) to feed
 * the margin indicator cheaply; the note text itself is fetched on demand
 * ([getNote]) when the user opens the editor.
 */
class LineNoteStore(
    database: UserSettingsDb,
) {
    private val queries = database.lineNotesQueries

    private val _linesWithNote = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())
    val linesWithNote: StateFlow<Map<Long, Set<Long>>> = _linesWithNote.asStateFlow()

    private val loadedBooks = mutableSetOf<Long>()

    /** Loads the set of annotated line ids for a book once. Subsequent calls are no-ops. */
    suspend fun loadBook(bookId: Long): Unit =
        withContext(Dispatchers.IO) {
            if (bookId in loadedBooks) return@withContext
            val lineIds = queries.selectLineIdsForBook(bookId).executeAsList().toSet()
            _linesWithNote.update { it + (bookId to lineIds) }
            loadedBooks += bookId
        }

    /** Whether a line currently has a note (cache lookup; safe from composition). */
    fun hasNote(
        bookId: Long,
        lineId: Long,
    ): Boolean = _linesWithNote.value[bookId]?.contains(lineId) == true

    /** Fetches a line's note text, or null if none. */
    suspend fun getNote(
        bookId: Long,
        lineId: Long,
    ): String? =
        withContext(Dispatchers.IO) {
            queries.selectForLine(bookId, lineId).executeAsOneOrNull()
        }

    /** Upserts a note; a blank text deletes the note instead. */
    suspend fun setNote(
        bookId: Long,
        lineId: Long,
        note: String,
        timestamp: Long,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (note.isBlank()) {
                deleteNoteInternal(bookId, lineId)
                return@withContext
            }
            queries.upsert(bookId = bookId, lineId = lineId, note = note, updatedAt = timestamp)
            updateCache(bookId) { it + lineId }
        }

    /** Removes a line's note. */
    suspend fun deleteNote(
        bookId: Long,
        lineId: Long,
    ): Unit =
        withContext(Dispatchers.IO) {
            deleteNoteInternal(bookId, lineId)
        }

    private fun deleteNoteInternal(
        bookId: Long,
        lineId: Long,
    ) {
        queries.delete(bookId, lineId)
        updateCache(bookId) { it - lineId }
    }

    private fun updateCache(
        bookId: Long,
        transform: (Set<Long>) -> Set<Long>,
    ) {
        _linesWithNote.update { current ->
            current + (bookId to transform(current[bookId].orEmpty()))
        }
    }
}
