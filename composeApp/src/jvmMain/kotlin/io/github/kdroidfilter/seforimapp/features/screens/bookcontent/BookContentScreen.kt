package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBarPosition
import io.github.kdroidfilter.seforimapp.core.presentation.icons.*
import io.github.kdroidfilter.seforimapp.core.presentation.utils.cursorForHorizontalResize
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.koin.compose.viewmodel.koinViewModel
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.add_bookmark
import seforimapp.composeapp.generated.resources.add_bookmark_tooltip
import seforimapp.composeapp.generated.resources.book_content
import seforimapp.composeapp.generated.resources.book_list
import seforimapp.composeapp.generated.resources.bookmarks
import seforimapp.composeapp.generated.resources.books
import seforimapp.composeapp.generated.resources.chapter
import seforimapp.composeapp.generated.resources.columns_gap
import seforimapp.composeapp.generated.resources.columns_gap_tooltip
import seforimapp.composeapp.generated.resources.commentaries
import seforimapp.composeapp.generated.resources.filter
import seforimapp.composeapp.generated.resources.filter_commentators_tooltip
import seforimapp.composeapp.generated.resources.my_bookmarks
import seforimapp.composeapp.generated.resources.my_commentaries
import seforimapp.composeapp.generated.resources.my_commentaries_label
import seforimapp.composeapp.generated.resources.navigation
import seforimapp.composeapp.generated.resources.paragraph
import seforimapp.composeapp.generated.resources.personal
import seforimapp.composeapp.generated.resources.print
import seforimapp.composeapp.generated.resources.print_tooltip
import seforimapp.composeapp.generated.resources.report
import seforimapp.composeapp.generated.resources.report_tooltip
import seforimapp.composeapp.generated.resources.search_in_page
import seforimapp.composeapp.generated.resources.search_in_page_tooltip
import seforimapp.composeapp.generated.resources.search_placeholder
import seforimapp.composeapp.generated.resources.show_commentaries
import seforimapp.composeapp.generated.resources.show_commentaries_tooltip
import seforimapp.composeapp.generated.resources.table_of_contents
import seforimapp.composeapp.generated.resources.tools
import seforimapp.composeapp.generated.resources.write_note
import seforimapp.composeapp.generated.resources.write_note_tooltip
import seforimapp.composeapp.generated.resources.zoom_in
import seforimapp.composeapp.generated.resources.zoom_in_tooltip
import seforimapp.composeapp.generated.resources.zoom_out
import seforimapp.composeapp.generated.resources.zoom_out_tooltip

@Composable
fun BookContentScreen() {
    val viewModel: BookContentViewModel = koinViewModel()
    val state = rememberBookContentState(viewModel)
    BookContentView(state, viewModel::onEvent)

}

@OptIn(ExperimentalSplitPaneApi::class, ExperimentalFoundationApi::class)
@Composable
fun BookContentView(state: BookContentState, onEvents: (BookContentEvents) -> Unit) {
    // Local scroll state
    val paragraphScrollState = rememberScrollState(state.paragraphScrollPosition)

    val chapterScrollState = rememberScrollState(state.chapterScrollPosition)

    // Save scroll position when it changes
    LaunchedEffect(paragraphScrollState) {
        snapshotFlow { paragraphScrollState.value }.collect { position ->
            onEvents(BookContentEvents.OnUpdateParagraphScrollPosition(position))
        }
    }

    LaunchedEffect(chapterScrollState) {
        snapshotFlow { chapterScrollState.value }.collect { position ->
            onEvents(BookContentEvents.OnUpdateChapterScrollPosition(position))
        }
    }

    // Save split pane position when it changes
    LaunchedEffect(state.splitPaneState.positionPercentage) {
        snapshotFlow { state.splitPaneState.positionPercentage }.collect {
            onEvents(BookContentEvents.SaveAllStates)
        }
    }

    // Save all states when the screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            onEvents(BookContentEvents.SaveAllStates)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        StartVerticalBar()
        EnhancedSplitLayouts(
            modifier = Modifier.weight(1f),
            splitPaneState = state.splitPaneState,
            searchText = state.searchText,
            onSearchTextChange = { onEvents(BookContentEvents.OnSearchTextChange(it)) },
            selectedChapter = state.selectedChapter,
            onChapterSelected = { onEvents(BookContentEvents.OnChapterSelected(it)) },
            paragraphScrollState = paragraphScrollState,
            chapterScrollState = chapterScrollState
        )
        EndVerticalBar()
    }
}


@Composable
fun StartVerticalBar() {
    VerticalLateralBar(
        position = VerticalLateralBarPosition.Start,
        topContentLabel = stringResource(Res.string.navigation),
        topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.book_list),
                onClick = {

                },
                isSelected = false,
                icon = Library,
                iconDescription = stringResource(Res.string.books),
                label = stringResource(Res.string.books)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.book_content),
                onClick = {

                },
                isSelected = false,
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

                },
                isSelected = false,
                icon = JournalBookmark,
                iconDescription = stringResource(Res.string.bookmarks),
                label = stringResource(Res.string.bookmarks)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.my_commentaries),
                onClick = {

                },
                isSelected = false,
                icon = JournalText,
                iconDescription = stringResource(Res.string.my_commentaries_label),
                label = stringResource(Res.string.my_commentaries_label)
            )
        })

}

@Composable
fun EndVerticalBar() {
    VerticalLateralBar(
        position = VerticalLateralBarPosition.End,
        topContentLabel = stringResource(Res.string.tools),
        topContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.zoom_in_tooltip),
                onClick = {

                },
                isSelected = false,
                icon = ZoomIn,
                iconDescription = stringResource(Res.string.zoom_in),
                label = stringResource(Res.string.zoom_in)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.zoom_out_tooltip),
                onClick = {

                },
                isSelected = false,
                icon = ZoomOut,
                iconDescription = stringResource(Res.string.zoom_out),
                label = stringResource(Res.string.zoom_out)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.add_bookmark_tooltip),
                onClick = {

                },
                isSelected = false,
                icon = Bookmark,
                iconDescription = stringResource(Res.string.add_bookmark),
                label = stringResource(Res.string.add_bookmark)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.search_in_page_tooltip),
                onClick = {

                },
                isSelected = false,
                icon = Manage_search,
                iconDescription = stringResource(Res.string.search_in_page),
                label = stringResource(Res.string.search_in_page)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.print_tooltip),
                onClick = {

                },
                isSelected = false,
                icon = Print,
                iconDescription = stringResource(Res.string.print),
                label = stringResource(Res.string.print)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.report_tooltip),
                onClick = {

                },
                isSelected = false,
                icon = FileWarning,
                iconDescription = stringResource(Res.string.report),
                label = stringResource(Res.string.report)
            )
        }, bottomContentLabel = stringResource(Res.string.commentaries),
        bottomContent = {
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.show_commentaries_tooltip),
                onClick = {

                },
                isSelected = false,
                icon = ListColumnsReverse,
                iconDescription = stringResource(Res.string.show_commentaries),
                label = stringResource(Res.string.show_commentaries)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.columns_gap_tooltip),
                onClick = {

                },
                isSelected = false,
                icon = ColumnsGap,
                iconDescription = stringResource(Res.string.columns_gap),
                label = stringResource(Res.string.columns_gap)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.filter_commentators_tooltip),
                onClick = {

                },
                isSelected = false,
                icon = Filter,
                iconDescription = stringResource(Res.string.filter),
                label = stringResource(Res.string.filter)
            )
            SelectableIconButtonWithToolip(
                toolTipText = stringResource(Res.string.write_note_tooltip),
                onClick = {

                },
                isSelected = false, icon = NotebookPen,
                iconDescription = stringResource(Res.string.write_note),
                label = stringResource(Res.string.write_note)
            )
        })
}

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun EnhancedSplitLayouts(
    modifier: Modifier = Modifier,
    splitPaneState: SplitPaneState,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    selectedChapter: Int,
    onChapterSelected: (Int) -> Unit,
    paragraphScrollState: ScrollState,
    chapterScrollState: ScrollState
) {
    Column(modifier = modifier) {
        HorizontalSplitPane(
            splitPaneState = splitPaneState
        ) {
            first(200.dp) {
                // Navigation panel
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    // Search field
                    val searchFieldState = rememberTextFieldState(searchText)

                    // Synchronize state with the viewmodel
                    LaunchedEffect(searchText) {
                        if (searchFieldState.text.toString() != searchText) {
                            searchFieldState.edit {
                                replace(0, length, searchText)
                            }
                        }
                    }

                    LaunchedEffect(searchFieldState.text) {
                        onSearchTextChange(searchFieldState.text.toString())
                    }

                    TextField(
                        state = searchFieldState,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(Res.string.search_placeholder)) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Chapter list
                    Column(
                        modifier = Modifier
                            .verticalScroll(chapterScrollState)
                    ) {
                        repeat(20) { index ->
                            ChapterItem(
                                chapter = index,
                                isSelected = selectedChapter == index,
                                onClick = { onChapterSelected(index) })
                        }
                    }
                }
            }
            second(200.dp) {

                // Main content
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(paragraphScrollState)
                ) {

                    Text("${stringResource(Res.string.chapter)} ${selectedChapter + 1}")
                    Spacer(modifier = Modifier.height(16.dp))

                    // Simulated content
                    repeat(100) { index ->
                        Text(
                            stringResource(Res.string.paragraph, index + 1, selectedChapter + 1),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
            splitter {
                visiblePart {
                    Divider(
                        Orientation.Vertical,
                        Modifier.fillMaxHeight().width(1.dp),
                        color = JewelTheme.globalColors.borders.disabled
                    )
                }
                handle {
                    Box(
                        Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .markAsHandle()
                            .cursorForHorizontalResize(),
                        contentAlignment = Alignment.Center
                    ) {

                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: Int, isSelected: Boolean, onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }.background(
            if (isSelected) Color.Blue.copy(alpha = 0.2f)
            else Color.Transparent
        ).padding(8.dp)
    ) {
        Text("${stringResource(Res.string.chapter)} ${chapter + 1}")
    }
}
