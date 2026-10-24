package io.github.kdroidfilter.seforimapp.features.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.annotations.BookNote
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import java.util.UUID

/** The user's notes, the most recently written first, with the titles of their books. */
internal data class UserNotes(
    val notes: List<BookNote> = emptyList(),
    val bookTitles: Map<Long, String> = emptyMap(),
) {
    /** The notes whose text, passage or book contains [query]; all of them for a blank one. */
    fun matching(query: String): List<BookNote> {
        val q = query.trim()
        if (q.isEmpty()) return notes
        return notes.filter { (bookId, note) ->
            note.note.contains(q, ignoreCase = true) ||
                note.quote.contains(q, ignoreCase = true) ||
                bookTitles[bookId].orEmpty().contains(q, ignoreCase = true)
        }
    }
}

/** All the user's notes, read again on every write. */
@Composable
internal fun rememberUserNotes(): UserNotes {
    val appGraph = LocalAppGraph.current
    val noteStore = appGraph.noteStore
    // The per-book cache changes on every write: the cue to read them all again
    val written by noteStore.notesByBook.collectAsState()
    var userNotes by remember { mutableStateOf(UserNotes()) }
    LaunchedEffect(written) {
        val notes = noteStore.all()
        userNotes =
            UserNotes(
                notes = notes,
                bookTitles =
                    notes.map { it.bookId }.distinct().associateWith {
                        appGraph.repository
                            .getBookCore(it)
                            ?.title
                            .orEmpty()
                    },
            )
    }
    return userNotes
}

/** A note's book, opened on its line with the notes pane showing it. */
fun noteDestination(bookNote: BookNote): TabsDestination =
    TabsDestination.BookContent(
        bookId = bookNote.bookId,
        tabId = UUID.randomUUID().toString(),
        lineId = bookNote.note.lineId,
        openNotes = true,
    )
