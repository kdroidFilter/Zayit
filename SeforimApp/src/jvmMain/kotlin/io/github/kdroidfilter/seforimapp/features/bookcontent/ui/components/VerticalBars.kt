package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBarPosition
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.isChumash
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.icons.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import seforimapp.seforimapp.generated.resources.*

@Composable
fun StartVerticalBar(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    // A search tab has no library tree: its results filter by category tabs
    showBookTree: Boolean = true,
) {
    VerticalLateralBar(
        position = VerticalLateralBarPosition.Start,
        topContent = {
            if (showBookTree) {
                SelectableIconButtonWithToolip(
                    toolTipText = stringResource(Res.string.book_list),
                    onClick = { onEvent(BookContentEvent.ToggleBookTree) },
                    isSelected = uiState.navigation.isVisible,
                    icon = Library,
                    iconDescription = stringResource(Res.string.books),
                    label = stringResource(Res.string.books),
                    shortcutHint = if (PlatformInfo.isMacOS) "B+⌘" else "B+Ctrl",
                )
            }
            if (uiState.navigation.selectedBook != null) {
                SelectableIconButtonWithToolip(
                    toolTipText = stringResource(Res.string.book_content),
                    onClick = { onEvent(BookContentEvent.ToggleToc) },
                    isSelected = uiState.toc.isVisible,
                    icon = TableOfContents,
                    iconDescription = stringResource(Res.string.table_of_contents),
                    label = stringResource(Res.string.table_of_contents),
                    shortcutHint = if (PlatformInfo.isMacOS) "B+⇧+⌘" else "B+Shift+Ctrl",
                )
            }
        },
        bottomContent = {
            if (uiState.navigation.selectedBook != null) {
                SelectableIconButtonWithToolip(
                    toolTipText = stringResource(Res.string.notes_pane_tooltip),
                    onClick = { onEvent(BookContentEvent.ToggleNotes) },
                    isSelected = uiState.notes.isVisible,
                    icon = NotebookPen,
                    iconDescription = stringResource(Res.string.notes_pane),
                    label = stringResource(Res.string.notes_pane),
                )
            }
        },
    )
}

@Composable
fun EndVerticalBar(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    diacritics: DiacriticsMode,
    // The tab's own toggles under the zoom (a search's book-details pane)
    extraTopContent: @Composable () -> Unit = {},
) {
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
            runSuspendCatching {
                providers.getAvailableLinksForLine(selectedLine.id)
            }.getOrNull()?.isNotEmpty()
        val commentariesAvailable =
            runSuspendCatching {
                providers.getAvailableCommentatorsForLine(selectedLine.id)
            }.getOrNull()?.isNotEmpty()
        val sourcesAvailable =
            runSuspendCatching {
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
            ZoomButtons()
            extraTopContent()

            // Diacritics toggle button - only when a book is selected and has nekudot/teamim
            if (!noBookSelected) {
                val bookHasDiacritics = selectedBook.hasNekudot || selectedBook.hasTeamim
                if (bookHasDiacritics) {
                    DiacriticsButton(
                        diacritics = diacritics,
                        hasTeamim = selectedBook.hasTeamim,
                        onClick = { onEvent(BookContentEvent.CycleDiacritics) },
                        shortcutHint = if (PlatformInfo.isMacOS) "J+⌘" else "J+Ctrl",
                    )
                }
                // Shnayim mikra: in the Chumash, each verse twice and then its targum
                if (selectedBook.isChumash) {
                    SelectableIconButtonWithToolip(
                        toolTipText = stringResource(Res.string.shnayim_mikra_mode_tooltip),
                        onClick = { onEvent(BookContentEvent.ToggleShnayimMikra) },
                        isSelected = uiState.content.shnayimMikra,
                        icon = JournalText,
                        iconDescription = stringResource(Res.string.shnayim_mikra_mode),
                        label = stringResource(Res.string.shnayim_mikra_mode),
                    )
                }
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

/** The text's zoom in and out, shared by the reading screens' end bars. */
@Composable
fun ZoomButtons() {
    val appSettings = LocalAppGraph.current.appSettings
    val rawTextSize by appSettings.textSizeFlow.collectAsState()
    val canZoomIn = rawTextSize < AppSettings.MAX_TEXT_SIZE
    val canZoomOut = rawTextSize > AppSettings.MIN_TEXT_SIZE
    SelectableIconButtonWithToolip(
        toolTipText =
            if (canZoomIn) {
                stringResource(Res.string.zoom_in_tooltip)
            } else {
                stringResource(Res.string.zoom_in_tooltip) + " (${AppSettings.MAX_TEXT_SIZE.toInt()}sp max)"
            },
        onClick = { appSettings.increaseTextSize() },
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
        onClick = { appSettings.decreaseTextSize() },
        isSelected = false,
        enabled = canZoomOut,
        icon = ZoomOut,
        iconDescription = stringResource(Res.string.zoom_out),
        label = stringResource(Res.string.zoom_out),
        shortcutHint = if (PlatformInfo.isMacOS) "-⌘" else "-Ctrl",
    )
}

/** Shows or hides the nikud and teamim. */
@Composable
fun DiacriticsButton(
    showDiacritics: Boolean,
    onClick: () -> Unit,
    shortcutHint: String? = null,
) {
    DiacriticsButton(
        diacritics = if (showDiacritics) DiacriticsMode.All else DiacriticsMode.None,
        hasTeamim = false,
        onClick = onClick,
        shortcutHint = shortcutHint,
    )
}

/** Cycles through the [DiacriticsMode]s: dots show how much is shown, the tooltip what the next click does. */
@Composable
fun DiacriticsButton(
    diacritics: DiacriticsMode,
    hasTeamim: Boolean,
    onClick: () -> Unit,
    shortcutHint: String? = null,
) {
    val tooltip =
        when (diacritics.next(hasTeamim)) {
            DiacriticsMode.All -> Res.string.show_diacritics_tooltip
            DiacriticsMode.NikudOnly -> Res.string.hide_teamim_tooltip
            DiacriticsMode.None ->
                if (diacritics == DiacriticsMode.NikudOnly) Res.string.hide_nikud_tooltip else Res.string.hide_diacritics_tooltip
        }
    SelectableIconButtonWithToolip(
        toolTipText = stringResource(tooltip),
        onClick = onClick,
        isSelected = diacritics != DiacriticsMode.None,
        icon = TextDiacritics,
        iconDescription = stringResource(Res.string.toggle_diacritics),
        label = stringResource(Res.string.toggle_diacritics),
        shortcutHint = shortcutHint,
        // With two states the selected background says it all; with three, dots tell how much is shown
        indicator = if (hasTeamim) ({ LevelIndicator(levels = 2, level = diacritics.shownLevel) }) else null,
    )
}

/** Diacritic layers shown: nikud and teamim, nikud only, none. */
private val DiacriticsMode.shownLevel: Int
    get() =
        when (this) {
            DiacriticsMode.All -> 2
            DiacriticsMode.NikudOnly -> 1
            DiacriticsMode.None -> 0
        }

/**
 * [levels] small dots, [level] of them lit. At level 0 the dots are invisible, as the unselected
 * button already says so, but keep their space so the icon does not move.
 */
@Composable
private fun LevelIndicator(
    levels: Int,
    level: Int,
) {
    val on = JewelTheme.globalColors.text.selected
    val off = if (level == 0) Color.Transparent else JewelTheme.globalColors.text.disabled
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(levels) {
            Box(Modifier.size(4.dp).background(if (it < level) on else off, CircleShape))
        }
    }
}
