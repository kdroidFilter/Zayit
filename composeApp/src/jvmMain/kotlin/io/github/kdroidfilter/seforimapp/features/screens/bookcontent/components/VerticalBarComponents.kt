package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.components

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBarPosition
import io.github.kdroidfilter.seforimapp.core.presentation.icons.*
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvents
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentState
import org.jetbrains.compose.resources.stringResource
import seforimapp.composeapp.generated.resources.*

/**
 * Left vertical bar with navigation options.
 */
@Composable
fun StartVerticalBar(
    state: BookContentState, 
    onEvents: (BookContentEvents) -> Unit
) {
    VerticalLateralBar(
        position = VerticalLateralBarPosition.Start,
        topContentLabel = stringResource(Res.string.navigation),
        topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.book_list),
                onClick = {
                    onEvents(BookContentEvents.OnToggleBookTree)
                },
                isSelected = state.showBookTree,
                icon = Library,
                iconDescription = stringResource(Res.string.books),
                label = stringResource(Res.string.books)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.book_content),
                onClick = {
                    onEvents(BookContentEvents.OnToggleToc)
                },
                isSelected = state.showToc,
                icon = TableOfContents,
                iconDescription = stringResource(Res.string.table_of_contents),
                label = stringResource(Res.string.table_of_contents)
            )
        },
        bottomContentLabel = stringResource(Res.string.personal),
        bottomContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.my_bookmarks),
                onClick = {
                    // Placeholder for future functionality
                },
                isSelected = false,
                icon = JournalBookmark,
                iconDescription = stringResource(Res.string.bookmarks),
                label = stringResource(Res.string.bookmarks)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.my_commentaries),
                onClick = {
                    // Placeholder for future functionality
                },
                isSelected = false,
                icon = JournalText,
                iconDescription = stringResource(Res.string.my_commentaries_label),
                label = stringResource(Res.string.my_commentaries_label)
            )
        }
    )
}

/**
 * Right vertical bar with tools and commentary options.
 */
@Composable
fun EndVerticalBar(
    state: BookContentState,
    onEvents: (BookContentEvents) -> Unit
) {
    VerticalLateralBar(
        position = VerticalLateralBarPosition.End,
        topContentLabel = stringResource(Res.string.tools),
        topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.zoom_in_tooltip),
                onClick = {
                    // Placeholder for future functionality
                },
                isSelected = false,
                icon = ZoomIn,
                iconDescription = stringResource(Res.string.zoom_in),
                label = stringResource(Res.string.zoom_in)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.zoom_out_tooltip),
                onClick = {
                    // Placeholder for future functionality
                },
                isSelected = false,
                icon = ZoomOut,
                iconDescription = stringResource(Res.string.zoom_out),
                label = stringResource(Res.string.zoom_out)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.add_bookmark_tooltip),
                onClick = {
                    // Placeholder for future functionality
                },
                isSelected = false,
                icon = Bookmark,
                iconDescription = stringResource(Res.string.add_bookmark),
                label = stringResource(Res.string.add_bookmark)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.search_in_page_tooltip),
                onClick = {
                    // Placeholder for future functionality
                },
                isSelected = false,
                icon = Manage_search,
                iconDescription = stringResource(Res.string.search_in_page),
                label = stringResource(Res.string.search_in_page)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.print_tooltip),
                onClick = {
                    // Placeholder for future functionality
                },
                isSelected = false,
                icon = Print,
                iconDescription = stringResource(Res.string.print),
                label = stringResource(Res.string.print)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.report_tooltip),
                onClick = {
                    // Placeholder for future functionality
                },
                isSelected = false,
                icon = FileWarning,
                iconDescription = stringResource(Res.string.report),
                label = stringResource(Res.string.report)
            )
        }, 
        bottomContentLabel = stringResource(Res.string.commentaries),
        bottomContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.show_commentaries_tooltip),
                onClick = {
                    onEvents(BookContentEvents.OnToggleCommentaries)
                },
                isSelected = state.showCommentaries,
                icon = ListColumnsReverse,
                iconDescription = stringResource(Res.string.show_commentaries),
                label = stringResource(Res.string.show_commentaries)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.columns_gap_tooltip),
                onClick = {
                    // Placeholder for future functionality
                },
                isSelected = false,
                icon = ColumnsGap,
                iconDescription = stringResource(Res.string.columns_gap),
                label = stringResource(Res.string.columns_gap)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.filter_commentators_tooltip),
                onClick = {
                    // Placeholder for future functionality
                },
                isSelected = false,
                icon = Filter,
                iconDescription = stringResource(Res.string.filter),
                label = stringResource(Res.string.filter)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.write_note_tooltip),
                onClick = {
                    // Placeholder for future functionality
                },
                isSelected = false, 
                icon = NotebookPen,
                iconDescription = stringResource(Res.string.write_note),
                label = stringResource(Res.string.write_note)
            )
        }
    )
}