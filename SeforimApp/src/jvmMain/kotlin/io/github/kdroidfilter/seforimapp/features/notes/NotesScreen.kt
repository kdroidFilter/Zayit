package io.github.kdroidfilter.seforimapp.features.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.annotations.BookNote
import io.github.kdroidfilter.seforimapp.core.presentation.components.CardSurface
import io.github.kdroidfilter.seforimapp.core.presentation.components.EmptyState
import io.github.kdroidfilter.seforimapp.core.presentation.components.ListPageContainer
import io.github.kdroidfilter.seforimapp.core.presentation.components.ListRow
import io.github.kdroidfilter.seforimapp.core.presentation.components.PageHeader
import io.github.kdroidfilter.seforimapp.core.presentation.components.PageSearchField
import io.github.kdroidfilter.seforimapp.core.presentation.components.SectionHeader
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.NotebookPen
import io.github.kdroidfilter.seforimapp.icons.bookOpenTabs
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.notes_empty
import seforimapp.seforimapp.generated.resources.notes_search_placeholder
import seforimapp.seforimapp.generated.resources.notes_title
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * The user's notes, of every book: grouped by book, the most recently written first, searchable by their text, their
 * passage or their book. Opening one opens its book on its line with the notes pane showing it.
 */
@Composable
fun NotesTabContent(tabId: String) {
    val appGraph = LocalAppGraph.current
    val noteStore = appGraph.noteStore
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel
    val scope = rememberCoroutineScope()

    val notesTitle = stringResource(Res.string.notes_title)
    LaunchedEffect(tabId, notesTitle) {
        appGraph.tabTitleUpdateManager.updateTabTitle(tabId, notesTitle, TabType.NOTES)
    }

    // The per-book cache changes on every write: the cue to read them all again
    val written by noteStore.notesByBook.collectAsState()
    var notes by remember { mutableStateOf<List<BookNote>>(emptyList()) }
    var bookTitles by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(written) {
        notes = noteStore.all()
        bookTitles =
            notes.map { it.bookId }.distinct().associateWith {
                appGraph.repository
                    .getBookCore(it)
                    ?.title
                    .orEmpty()
            }
    }

    var query by remember { mutableStateOf("") }
    val shown =
        remember(notes, bookTitles, query) {
            val q = query.trim()
            if (q.isEmpty()) {
                notes
            } else {
                notes.filter { (bookId, note) ->
                    note.note.contains(q, ignoreCase = true) ||
                        note.quote.contains(q, ignoreCase = true) ||
                        bookTitles[bookId].orEmpty().contains(q, ignoreCase = true)
                }
            }
        }

    fun open(bookNote: BookNote) {
        tabsViewModel.replaceCurrentTabWithNewTabId(
            TabsDestination.BookContent(
                bookId = bookNote.bookId,
                tabId = UUID.randomUUID().toString(),
                lineId = bookNote.note.lineId,
                openNotes = true,
            ),
        )
    }

    ListPageContainer {
        PageHeader(title = notesTitle)
        PageSearchField(query = query, placeholder = Res.string.notes_search_placeholder, onQueryChange = { query = it })

        if (shown.isEmpty()) {
            EmptyState(iconKey = AllIconsKeys.Actions.Edit, message = stringResource(Res.string.notes_empty))
        } else {
            // Books in the order of their latest note (the notes come newest first)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shown.groupBy { it.bookId }.forEach { (bookId, bookNotes) ->
                    BookNotesCard(
                        title = bookTitles[bookId].orEmpty(),
                        notes = bookNotes,
                        onOpen = ::open,
                        onDelete = { scope.launch { noteStore.removeNote(it.bookId, it.note.id) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookNotesCard(
    title: String,
    notes: List<BookNote>,
    onOpen: (BookNote) -> Unit,
    onDelete: (BookNote) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    CardSurface {
        Column {
            SectionHeader(
                title = title,
                count = notes.size,
                expanded = expanded,
                onToggleExpand = { expanded = !expanded },
                leadingIconVector = bookOpenTabs(JewelTheme.globalColors.text.normal),
            )
            if (expanded) {
                Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp)) {
                    notes.forEachIndexed { index, bookNote ->
                        val note = bookNote.note
                        ListRow(
                            title = note.note,
                            subtitle =
                                listOf(
                                    note.quote,
                                    NOTE_DATE.format(Instant.ofEpochMilli(note.updatedAt)),
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                            onOpen = { onOpen(bookNote) },
                            onDelete = { onDelete(bookNote) },
                            leadingIconVector = NotebookPen,
                            showDivider = index < notes.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

private val NOTE_DATE = DateTimeFormatter.ofPattern("d.M.yyyy").withZone(ZoneId.systemDefault())
