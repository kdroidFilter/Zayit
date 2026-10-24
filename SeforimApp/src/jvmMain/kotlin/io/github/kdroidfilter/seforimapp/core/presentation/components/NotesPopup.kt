package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforimapp.features.notes.noteDestination
import io.github.kdroidfilter.seforimapp.features.notes.openNotesTab
import io.github.kdroidfilter.seforimapp.features.notes.rememberUserNotes
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.PathIconKey
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.notes_empty
import seforimapp.seforimapp.generated.resources.notes_search_placeholder
import seforimapp.seforimapp.generated.resources.notes_show_all
import seforimapp.seforimapp.generated.resources.notes_title

// Lucide notebook-pen, the notes glyph elsewhere in the app, drawn in the flat title-bar palette.
private object NotesIconAnchor

private val NotesIcon = PathIconKey("icons/notes.svg", NotesIconAnchor::class.java)

/**
 * The notes counterpart of [FavoritesMenuButton] for Linux/Windows: a title-bar button opening a popup that lists the
 * notes by book with a search field and a footer leading to the full notes page. macOS uses the native Notes menu.
 */
@Composable
fun NotesMenuButton() {
    var visible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
        TitleBarActionButton(
            key = NotesIcon,
            contentDescription = stringResource(Res.string.notes_title),
            onClick = { visible = !visible },
            tooltipText = stringResource(Res.string.notes_title),
        )
        if (visible) {
            Popup(
                onDismissRequest = { visible = false },
                popupPositionProvider = BelowAnchorEndPositionProvider,
                properties = PopupProperties(focusable = true),
            ) {
                NotesPopupContent(onDismiss = { visible = false })
            }
        }
    }
}

@Composable
private fun NotesPopupContent(onDismiss: () -> Unit) {
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel
    val userNotes = rememberUserNotes()
    var query by remember { mutableStateOf("") }
    // Books in the order of their latest note (the notes come newest first)
    val byBook = remember(userNotes, query) { userNotes.matching(query).groupBy { it.bookId }.toList() }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier =
            Modifier
                .width(340.dp)
                .background(JewelTheme.globalColors.panelBackground, PopupShape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, PopupShape)
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PopupSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(Res.string.notes_search_placeholder),
            focusRequester = focusRequester,
        )

        LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
            byBook.forEach { (bookId, bookNotes) ->
                item(key = "book-$bookId") { PopupSectionHeader(userNotes.bookTitles[bookId].orEmpty()) }
                items(bookNotes, key = { "note-" + it.note.id }) { bookNote ->
                    PopupRow(
                        label = bookNote.note.note,
                        tabType = TabType.NOTES,
                        onClick = {
                            tabsViewModel.openTab(noteDestination(bookNote))
                            onDismiss()
                        },
                        onClose = null,
                    )
                }
            }
            if (byBook.isEmpty()) {
                item(key = "empty") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.notes_empty),
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.info,
                        )
                    }
                }
            }
        }

        PopupFooterRow(
            label = stringResource(Res.string.notes_show_all),
            shortcutHint = null,
            icon = NotesIcon,
            onClick = {
                openNotesTab(tabsViewModel)
                onDismiss()
            },
        )
    }
}
