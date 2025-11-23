package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBarPosition
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.icons.Kamatz
import io.github.kdroidfilter.seforimapp.icons.*
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.*

@Composable
fun StartVerticalBar(
    uiState: BookContentState,
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
                label = stringResource(Res.string.books),
                shortcutHint = if (getOperatingSystem() == OperatingSystem.MACOS) "B+⌘" else "B+Ctrl"
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.book_content),
                onClick = { onEvent(BookContentEvent.ToggleToc) },
                isSelected = uiState.toc.isVisible,
                icon = TableOfContents,
                iconDescription = stringResource(Res.string.table_of_contents),
                label = stringResource(Res.string.table_of_contents),
                shortcutHint = if (getOperatingSystem() == OperatingSystem.MACOS) "B+⇧+⌘" else "B+Shift+Ctrl"

            )
        },
        bottomContent = {
//            SelectableIconButtonWithToolip(
//                toolTipText = stringResource(Res.string.my_bookmarks),
//                onClick = { },
//                isSelected = false,
//                icon = JournalBookmark,
//                iconDescription = stringResource(Res.string.bookmarks),
//                label = stringResource(Res.string.bookmarks)
//            )
//            SelectableIconButtonWithToolip(
//                toolTipText = stringResource(Res.string.my_commentaries),
//                onClick = { },
//                isSelected = false,
//                icon = JournalText,
//                iconDescription = stringResource(Res.string.my_commentaries_label),
//                label = stringResource(Res.string.my_commentaries_label)
//            )
//            SelectableIconButtonWithToolip(
//                toolTipText = stringResource(Res.string.write_note_tooltip),
//                onClick = { },
//                isSelected = false,
//                icon = NotebookPen,
//                iconDescription = stringResource(Res.string.write_note),
//                label = stringResource(Res.string.write_note)
//            )
        }
    )
}

@Composable
fun EndVerticalBar(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit
) {
    // Collect current text size from settings
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()

    // Determine if zoom buttons should be selected based on text size
    // Also check if we've reached min/max limits to disable buttons appropriately
    val canZoomIn = rawTextSize < AppSettings.MAX_TEXT_SIZE
    val canZoomOut = rawTextSize > AppSettings.MIN_TEXT_SIZE

    val selectedBook = uiState.navigation.selectedBook
    val noBookSelected = selectedBook == null

    VerticalLateralBar(
        position = VerticalLateralBarPosition.End,
        topContent = {
            // Platform-specific shortcut hint for Zoom In
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
                label = stringResource(Res.string.zoom_in),
                shortcutHint = if (getOperatingSystem() == OperatingSystem.MACOS) "+⌘" else "+Ctrl"
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
                label = stringResource(Res.string.zoom_out),
                shortcutHint = if (getOperatingSystem() == OperatingSystem.MACOS) "-⌘" else "-Ctrl"
            )
//            SelectableIconButtonWithToolip(
//                toolTipText = stringResource(
//                    if (noBookSelected) Res.string.please_select_a_book else Res.string.add_bookmark_tooltip
//                ),
//                onClick = { },
//                isSelected = false,
//                icon = Bookmark,
//                iconDescription = stringResource(Res.string.add_bookmark),
//                label = stringResource(Res.string.add_bookmark),
//                enabled = !noBookSelected
//            )
        },
        bottomContent = {
            val targumEnabled = selectedBook?.hasTargumConnection == true
            val commentaryEnabled = selectedBook?.hasCommentaryConnection == true
            val linksEnabled = (selectedBook?.hasReferenceConnection == true) || (selectedBook?.hasOtherConnection == true)

            // Hide both buttons on Home (no book selected)
            if (!noBookSelected) {
                if (uiState.content.hasNikud) {
                    SelectableIconButtonWithToolip(
                        toolTipText = stringResource(Res.string.toggle_nikud_tooltip),
                        onClick = { onEvent(BookContentEvent.ToggleNikud) },
                        isSelected = uiState.content.showNikud,
                        icon = Kamatz,
                        iconDescription = stringResource(Res.string.toggle_nikud),
                        label = stringResource(Res.string.nikud),
                        shortcutHint = if (getOperatingSystem() == OperatingSystem.MACOS) "J+⌘" else "J+Ctrl"
                    )
                }

                // Show Targum only when available for the book
                if (targumEnabled) {
                    SelectableIconButtonWithToolip(
                        toolTipText = stringResource(Res.string.show_targumim_tooltip),
                        onClick = { onEvent(BookContentEvent.ToggleTargum) },
                        isSelected = uiState.content.showTargum,
                        icon = Align_horizontal_right,
                        iconDescription = stringResource(Res.string.show_targumim),
                        label = stringResource(Res.string.show_targumim),
                        enabled = true,
                        shortcutHint = if (getOperatingSystem() == OperatingSystem.MACOS) "K+⇧+⌘" else "K+Shift+Ctrl"
                    )
                }
                // Show Commentaries only when available for the book
                if (commentaryEnabled) {
                    SelectableIconButtonWithToolip(
                        toolTipText = stringResource(Res.string.show_commentaries_tooltip),
                        onClick = { onEvent(BookContentEvent.ToggleCommentaries) },
                        isSelected = uiState.content.showCommentaries,
                        icon = Align_end,
                        iconDescription = stringResource(Res.string.show_commentaries),
                        label = stringResource(Res.string.show_commentaries),
                        enabled = true,
                        shortcutHint = if (getOperatingSystem() == OperatingSystem.MACOS) "K+⌘" else "K+Ctrl"
                    )
                }
            }
            // Show Links button (UI-only for now)
//            SelectableIconButtonWithToolip(
//                toolTipText = when {
//                    noBookSelected -> stringResource(Res.string.please_select_a_book)
//                    linksEnabled -> stringResource(Res.string.show_links_tooltip)
//                    else -> stringResource(Res.string.links_not_available_in_book)
//                },
//                onClick = { },
//                isSelected = false,
//                icon = Library_books,
//                iconDescription = stringResource(Res.string.show_links),
//                label = stringResource(Res.string.show_links),
//                enabled = linksEnabled
//            )

        }
    )
}
