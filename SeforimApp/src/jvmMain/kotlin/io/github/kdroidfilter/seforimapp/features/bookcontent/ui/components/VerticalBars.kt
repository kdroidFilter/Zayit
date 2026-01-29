package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBarPosition
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.icons.*
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.*

@Composable
fun StartVerticalBar(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
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
                shortcutHint = if (PlatformInfo.isMacOS) "B+⌘" else "B+Ctrl",
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.book_content),
                onClick = { onEvent(BookContentEvent.ToggleToc) },
                isSelected = uiState.toc.isVisible,
                icon = TableOfContents,
                iconDescription = stringResource(Res.string.table_of_contents),
                label = stringResource(Res.string.table_of_contents),
                shortcutHint = if (PlatformInfo.isMacOS) "B+⇧+⌘" else "B+Shift+Ctrl",
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
        },
    )
}

@Composable
fun EndVerticalBar(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    showDiacritics: Boolean,
) {
    // Collect current text size from settings
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()

    // Determine if zoom buttons should be selected based on text size
    // Also check if we've reached min/max limits to disable buttons appropriately
    val canZoomIn = rawTextSize < AppSettings.MAX_TEXT_SIZE
    val canZoomOut = rawTextSize > AppSettings.MIN_TEXT_SIZE

    val selectedBook = uiState.navigation.selectedBook
    val noBookSelected = selectedBook == null
    val selectedLine = uiState.content.primaryLine
    val providers = uiState.providers

    val lineAvailability by produceState(
        initialValue = LineResourceAvailability(),
        key1 = selectedLine?.id,
        key2 = providers,
    ) {
        if (selectedLine == null || providers == null) {
            value = LineResourceAvailability()
            return@produceState
        }

        val targumAvailable =
            runCatching {
                providers.getAvailableLinksForLine(selectedLine.id)
            }.getOrNull()?.isNotEmpty()
        val commentariesAvailable =
            runCatching {
                providers.getAvailableCommentatorsForLine(selectedLine.id)
            }.getOrNull()?.isNotEmpty()
        val sourcesAvailable =
            runCatching {
                providers.getAvailableSourcesForLine(selectedLine.id)
            }.getOrNull()?.isNotEmpty()

        value =
            LineResourceAvailability(
                targumAvailable = targumAvailable,
                commentariesAvailable = commentariesAvailable,
                sourcesAvailable = sourcesAvailable,
            )
    }

    VerticalLateralBar(
        position = VerticalLateralBarPosition.End,
        topContent = {
            // Platform-specific shortcut hint for Zoom In
            SelectableIconButtonWithToolip(
                toolTipText =
                    if (canZoomIn) {
                        stringResource(Res.string.zoom_in_tooltip)
                    } else {
                        stringResource(Res.string.zoom_in_tooltip) + " (${AppSettings.MAX_TEXT_SIZE.toInt()}sp max)"
                    },
                onClick = { AppSettings.increaseTextSize() },
                isSelected = false,
                enabled = canZoomIn,
                icon = ZoomIn,
                iconDescription = stringResource(Res.string.zoom_in),
                label = stringResource(Res.string.zoom_in),
                shortcutHint = if (PlatformInfo.isMacOS) "+⌘" else "+Ctrl",
            )
            SelectableIconButtonWithToolip(
                toolTipText =
                    if (canZoomOut) {
                        stringResource(Res.string.zoom_out_tooltip)
                    } else {
                        stringResource(Res.string.zoom_out_tooltip) + " (${AppSettings.MIN_TEXT_SIZE.toInt()}sp min)"
                    },
                onClick = { AppSettings.decreaseTextSize() },
                isSelected = false,
                enabled = canZoomOut,
                icon = ZoomOut,
                iconDescription = stringResource(Res.string.zoom_out),
                label = stringResource(Res.string.zoom_out),
                shortcutHint = if (PlatformInfo.isMacOS) "-⌘" else "-Ctrl",
            )

            // Diacritics toggle button - only show when book has nekudot or teamim
            val bookHasDiacritics = selectedBook?.hasNekudot == true || selectedBook?.hasTeamim == true
            if (bookHasDiacritics) {
                SelectableIconButtonWithToolip(
                    toolTipText =
                        stringResource(
                            if (showDiacritics) {
                                Res.string.hide_diacritics_tooltip
                            } else {
                                Res.string.show_diacritics_tooltip
                            },
                        ),
                    onClick = { onEvent(BookContentEvent.ToggleDiacritics) },
                    isSelected = showDiacritics,
                    icon = TextDiacritics,
                    iconDescription = stringResource(Res.string.toggle_diacritics),
                    label = stringResource(Res.string.toggle_diacritics),
                    shortcutHint = if (PlatformInfo.isMacOS) "J+⌘" else "J+Ctrl",
                )
            }
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
            val sourcesEnabled = selectedBook?.hasSourceConnection == true
            val linksEnabled = (selectedBook?.hasReferenceConnection == true) || (selectedBook?.hasOtherConnection == true)

            // Hide both buttons on Home (no book selected)
            if (!noBookSelected) {
                val targumDisabledForLine =
                    selectedLine != null &&
                        lineAvailability.targumAvailable == false &&
                        !uiState.content.showTargum
                val commentaryDisabledForLine =
                    selectedLine != null &&
                        lineAvailability.commentariesAvailable == false &&
                        !uiState.content.showCommentaries
                val sourcesDisabledForLine =
                    selectedLine != null &&
                        lineAvailability.sourcesAvailable == false &&
                        !uiState.content.showSources

                val targumTooltip =
                    when {
                        targumDisabledForLine -> stringResource(Res.string.no_links_for_line)
                        selectedLine == null -> stringResource(Res.string.select_line_for_links)
                        else -> stringResource(Res.string.show_targumim_tooltip)
                    }
                val commentaryTooltip =
                    when {
                        commentaryDisabledForLine -> stringResource(Res.string.no_commentaries_for_line)
                        selectedLine == null -> stringResource(Res.string.select_line_for_commentaries)
                        else -> stringResource(Res.string.show_commentaries_tooltip)
                    }
                val sourcesTooltip =
                    when {
                        sourcesDisabledForLine -> stringResource(Res.string.no_sources_for_line)
                        selectedLine == null -> stringResource(Res.string.select_line_for_sources)
                        else -> stringResource(Res.string.show_sources_tooltip)
                    }

                // Show Targum only when available for the book
                if (targumEnabled) {
                    SelectableIconButtonWithToolip(
                        toolTipText = targumTooltip,
                        onClick = { onEvent(BookContentEvent.ToggleTargum) },
                        isSelected = uiState.content.showTargum,
                        icon = Align_horizontal_right,
                        iconDescription = stringResource(Res.string.show_targumim),
                        label = stringResource(Res.string.show_targumim),
                        enabled = !targumDisabledForLine,
                        shortcutHint = if (PlatformInfo.isMacOS) "K+⇧+⌘" else "K+Shift+Ctrl",
                    )
                }

                if (sourcesEnabled) {
                    SelectableIconButtonWithToolip(
                        toolTipText = sourcesTooltip,
                        onClick = { onEvent(BookContentEvent.ToggleSources) },
                        isSelected = uiState.content.showSources,
                        icon = References,
                        iconDescription = stringResource(Res.string.show_sources),
                        label = stringResource(Res.string.show_sources),
                        enabled = !sourcesDisabledForLine,
                        shortcutHint = if (PlatformInfo.isMacOS) "K+⌥+⌘" else "K+Alt+Ctrl",
                    )
                }

                // Show Commentaries only when available for the book
                if (commentaryEnabled) {
                    SelectableIconButtonWithToolip(
                        toolTipText = commentaryTooltip,
                        onClick = { onEvent(BookContentEvent.ToggleCommentaries) },
                        isSelected = uiState.content.showCommentaries,
                        icon = Align_end,
                        iconDescription = stringResource(Res.string.show_commentaries),
                        label = stringResource(Res.string.show_commentaries),
                        enabled = !commentaryDisabledForLine,
                        shortcutHint = if (PlatformInfo.isMacOS) "K+⌘" else "K+Ctrl",
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
        },
    )
}

private data class LineResourceAvailability(
    val targumAvailable: Boolean? = null,
    val commentariesAvailable: Boolean? = null,
    val sourcesAvailable: Boolean? = null,
)
