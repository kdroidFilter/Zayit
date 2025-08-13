package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBarPosition
import io.github.kdroidfilter.seforimapp.core.presentation.icons.*
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentUiState
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.*

@Composable
fun StartVerticalBar(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit
) {
    VerticalLateralBar(
        position = VerticalLateralBarPosition.Start,
        topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.book_list),
                onClick = { onEvent(BookContentEvent.ToggleBookTree) },
                isSelected = uiState.navigation.isVisible,
                icon = Library,
                iconDescription = stringResource(Res.string.books),
                label = stringResource(Res.string.books)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.book_content),
                onClick = { onEvent(BookContentEvent.ToggleToc) },
                isSelected = uiState.toc.isVisible,
                icon = TableOfContents,
                iconDescription = stringResource(Res.string.table_of_contents),
                label = stringResource(Res.string.table_of_contents)
            )
        },
        bottomContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.my_bookmarks),
                onClick = { },
                isSelected = false,
                icon = JournalBookmark,
                iconDescription = stringResource(Res.string.bookmarks),
                label = stringResource(Res.string.bookmarks)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.my_commentaries),
                onClick = { },
                isSelected = false,
                icon = JournalText,
                iconDescription = stringResource(Res.string.my_commentaries_label),
                label = stringResource(Res.string.my_commentaries_label)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.write_note_tooltip),
                onClick = { },
                isSelected = false,
                icon = NotebookPen,
                iconDescription = stringResource(Res.string.write_note),
                label = stringResource(Res.string.write_note)
            )
        }
    )
}

@Composable
fun EndVerticalBar(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit
) {
    // Collect current text size from settings
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()

    // Determine if zoom buttons should be selected based on text size
    // Also check if we've reached min/max limits to disable buttons appropriately
    val canZoomIn = rawTextSize < AppSettings.MAX_TEXT_SIZE
    val canZoomOut = rawTextSize > AppSettings.MIN_TEXT_SIZE

    
    VerticalLateralBar(
        position = VerticalLateralBarPosition.End,
        topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = if (canZoomIn) 
                    stringResource(Res.string.zoom_in_tooltip) 
                else 
                    stringResource(Res.string.zoom_in_tooltip) + " (${AppSettings.MAX_TEXT_SIZE.toInt()}sp max)",
                onClick = { AppSettings.increaseTextSize() },
                isSelected = false,
                enabled = canZoomIn,
                icon = ZoomIn,
                iconDescription = stringResource(Res.string.zoom_in),
                label = stringResource(Res.string.zoom_in)
            )
            SelectableIconButtonWithToolip(
                toolTipText = if (canZoomOut) 
                    stringResource(Res.string.zoom_out_tooltip) 
                else 
                    stringResource(Res.string.zoom_out_tooltip) + " (${AppSettings.MIN_TEXT_SIZE.toInt()}sp min)",
                onClick = { AppSettings.decreaseTextSize() },
                isSelected = false,
                enabled = canZoomOut,
                icon = ZoomOut,
                iconDescription = stringResource(Res.string.zoom_out),
                label = stringResource(Res.string.zoom_out)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.add_bookmark_tooltip),
                onClick = { },
                isSelected = false,
                icon = Bookmark,
                iconDescription = stringResource(Res.string.add_bookmark),
                label = stringResource(Res.string.add_bookmark)
            )
        },
        bottomContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.show_targumim_tooltip),
                onClick = { onEvent(BookContentEvent.ToggleTargum) },
                isSelected = uiState.content.showTargum,
                icon = Align_horizontal_right,
                iconDescription = stringResource(Res.string.show_targumim),
                label = stringResource(Res.string.show_targumim)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.show_commentaries_tooltip),
                onClick = { onEvent(BookContentEvent.ToggleCommentaries) },
                isSelected = uiState.content.showCommentaries,
                icon = Align_end,
                iconDescription = stringResource(Res.string.show_commentaries),
                label = stringResource(Res.string.show_commentaries)
            )
            // Show Links button (UI-only for now)
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.show_links_tooltip),
                onClick = { },
                isSelected = false,
                icon = Library_books,
                iconDescription = stringResource(Res.string.show_links),
                label = stringResource(Res.string.show_links)
            )

        }
    )
}