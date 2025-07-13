package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.seforimapp.core.presentation.components.*
import io.github.kdroidfilter.seforimapp.core.presentation.icons.*
import org.jetbrains.compose.resources.stringResource
import seforimapp.composeapp.generated.resources.*

@Composable
fun StartVerticalBar(
    showBookTree: Boolean,
    showToc: Boolean,
    onToggleBookTree: () -> Unit,
    onToggleToc: () -> Unit
) {
    VerticalLateralBar(
        position = VerticalLateralBarPosition.Start,
        topContentLabel = stringResource(Res.string.navigation),
        topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.book_list),
                onClick = onToggleBookTree,
                isSelected = showBookTree,
                icon = Library,
                iconDescription = stringResource(Res.string.books),
                label = stringResource(Res.string.books)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.book_content),
                onClick = onToggleToc,
                isSelected = showToc,
                icon = TableOfContents,
                iconDescription = stringResource(Res.string.table_of_contents),
                label = stringResource(Res.string.table_of_contents)
            )
        },
        bottomContentLabel = stringResource(Res.string.personal),
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
        }
    )
}

@Composable
fun EndVerticalBar(
    showCommentaries: Boolean,
    onToggleCommentaries: () -> Unit
) {
    VerticalLateralBar(
        position = VerticalLateralBarPosition.End,
        topContentLabel = stringResource(Res.string.tools),
        topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.zoom_in_tooltip),
                onClick = { },
                isSelected = false,
                icon = ZoomIn,
                iconDescription = stringResource(Res.string.zoom_in),
                label = stringResource(Res.string.zoom_in)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.zoom_out_tooltip),
                onClick = { },
                isSelected = false,
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
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.search_in_page_tooltip),
                onClick = { },
                isSelected = false,
                icon = Manage_search,
                iconDescription = stringResource(Res.string.search_in_page),
                label = stringResource(Res.string.search_in_page)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.print_tooltip),
                onClick = { },
                isSelected = false,
                icon = Print,
                iconDescription = stringResource(Res.string.print),
                label = stringResource(Res.string.print)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.report_tooltip),
                onClick = { },
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
                onClick = onToggleCommentaries,
                isSelected = showCommentaries,
                icon = ListColumnsReverse,
                iconDescription = stringResource(Res.string.show_commentaries),
                label = stringResource(Res.string.show_commentaries)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.columns_gap_tooltip),
                onClick = { },
                isSelected = false,
                icon = ColumnsGap,
                iconDescription = stringResource(Res.string.columns_gap),
                label = stringResource(Res.string.columns_gap)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.filter_commentators_tooltip),
                onClick = { },
                isSelected = false,
                icon = Filter,
                iconDescription = stringResource(Res.string.filter),
                label = stringResource(Res.string.filter)
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