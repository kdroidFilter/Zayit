package io.github.kdroidfilter.seforimapp.features.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforimapp.core.annotations.BookNote
import io.github.kdroidfilter.seforimapp.core.presentation.components.CardSurface
import io.github.kdroidfilter.seforimapp.core.presentation.components.EmptyState
import io.github.kdroidfilter.seforimapp.core.presentation.components.ListPageContainer
import io.github.kdroidfilter.seforimapp.core.presentation.components.ListPageLazyColumn
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

    val userNotes = rememberUserNotes()
    val bookTitles = userNotes.bookTitles
    var query by remember { mutableStateOf("") }
    val shown = remember(userNotes, query) { userNotes.matching(query) }

    fun open(bookNote: BookNote) {
        tabsViewModel.replaceCurrentTabWithNewTabId(noteDestination(bookNote))
    }

    ListPageContainer {
        PageHeader(title = notesTitle)
        PageSearchField(query = query, placeholder = Res.string.notes_search_placeholder, onQueryChange = { query = it })

        if (shown.isEmpty()) {
            EmptyState(iconKey = AllIconsKeys.Actions.Edit, message = stringResource(Res.string.notes_empty))
        } else {
            // Books in the order of their latest note (the notes come newest first)
            val byBook = remember(shown) { shown.groupBy { it.bookId }.toList() }
            ListPageLazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(byBook, key = { (bookId, _) -> bookId }) { (bookId, bookNotes) ->
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
    // Saveable: the list disposes the cards scrolled out of view
    var expanded by rememberSaveable { mutableStateOf(true) }
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
