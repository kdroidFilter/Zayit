package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBarPosition
import io.github.kdroidfilter.seforimapp.core.presentation.icons.*
import io.github.kdroidfilter.seforimapp.core.presentation.utils.cursorForHorizontalResize
import io.github.kdroidfilter.seforimapp.core.presentation.utils.cursorForVerticalResize
import kotlinx.coroutines.FlowPreview
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.compose.splitpane.VerticalSplitPane
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.koin.compose.viewmodel.koinViewModel
import seforimapp.composeapp.generated.resources.*

@Composable
fun BookContentScreen() {
    val viewModel: BookContentViewModel = koinViewModel()
    val state = rememberBookContentState(viewModel)
    BookContentView(state, viewModel::onEvent)

}

@OptIn(ExperimentalSplitPaneApi::class, ExperimentalFoundationApi::class, FlowPreview::class)
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

    // Save split pane position when it changes, with debounce to improve performance
    LaunchedEffect(state.splitPaneState) {
        snapshotFlow { state.splitPaneState.positionPercentage }
            .debounce(300) // 300ms debounce to reduce frequency of state saving
            .collect {
                onEvents(BookContentEvents.SaveAllStates)
            }
    }

    // Save TOC split pane position when it changes, with debounce to improve performance
    LaunchedEffect(state.tocSplitPaneState) {
        snapshotFlow { state.tocSplitPaneState.positionPercentage }
            .debounce(300) // 300ms debounce to reduce frequency of state saving
            .collect {
                onEvents(BookContentEvents.SaveAllStates)
            }
    }

    // Save content split pane position when it changes, with debounce to improve performance
    LaunchedEffect(state.contentSplitPaneState) {
        snapshotFlow { state.contentSplitPaneState.positionPercentage }
            .debounce(300) // 300ms debounce to reduce frequency of state saving
            .collect {
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
        StartVerticalBar(state, onEvents)
        EnhancedSplitLayouts(
            modifier = Modifier.weight(1f),
            splitPaneState = state.splitPaneState,
            searchText = state.searchText,
            onSearchTextChange = { onEvents(BookContentEvents.OnSearchTextChange(it)) },
            selectedChapter = state.selectedChapter,
            onChapterSelected = { onEvents(BookContentEvents.OnChapterSelected(it)) },
            paragraphScrollState = paragraphScrollState,
            chapterScrollState = chapterScrollState,
            state = state,
            onEvents = onEvents
        )
        EndVerticalBar(state, onEvents)
    }
}


@Composable
fun StartVerticalBar(state: BookContentState, onEvents: (BookContentEvents) -> Unit) {
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
    chapterScrollState: ScrollState,
    // Database-related parameters
    state: BookContentState,
    onEvents: (BookContentEvents) -> Unit
) {
    Column(modifier = modifier) {
        if (state.showBookTree) {
            // First split pane: Category tree | (TOC + Book content)
            HorizontalSplitPane(
                splitPaneState = splitPaneState
            ) {
                first(200.dp) {
                    // Navigation panel (Category tree)
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

                        // Show category tree
                        CategoryBookTree(
                            rootCategories = state.rootCategories,
                            expandedCategories = state.expandedCategories,
                            categoryChildren = state.categoryChildren,
                            booksInCategory = state.booksInCategory,
                            selectedCategory = state.selectedCategory,
                            selectedBook = state.selectedBook,
                            onCategoryClick = { category -> 
                                onEvents(BookContentEvents.OnCategorySelected(category))
                            },
                            onBookClick = { book ->
                                onEvents(BookContentEvents.OnBookSelected(book))
                            },
                            modifier = Modifier
                        )
                    }
                }
                second(200.dp) {
                    // Second split pane: TOC | Book content
                    HorizontalSplitPane(
                        splitPaneState = state.tocSplitPaneState
                    ) {
                        first(200.dp) {
                            // TOC panel
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    text = stringResource(Res.string.table_of_contents),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (state.selectedBook != null) {
                                    // Show TOC for selected book
                                    // Check if we have root entries stored with the special key (-1L)
                                    val rootEntries = state.tocChildren[-1L] ?: emptyList()
                                    val displayTocEntries = rootEntries.ifEmpty {
                                        // Otherwise, use the original tocEntries
                                        state.tocEntries
                                    }

                                    Box(modifier = Modifier.fillMaxHeight()) {
                                        TocView(
                                            tocEntries = displayTocEntries,
                                            expandedEntries = state.expandedTocEntries,
                                            tocChildren = state.tocChildren,
                                            onEntryClick = { tocEntry ->
                                                // Handle TOC entry click
                                                tocEntry.lineId?.let { lineId ->
                                                    // Check if the line is already loaded
                                                    val existingLine = state.bookLines.find { it.id == lineId }
                                                    if (existingLine != null) {
                                                        // If the line is already loaded, just select it
                                                        onEvents(BookContentEvents.OnLineSelected(existingLine))
                                                    } else {
                                                        // If the line is not loaded, we need to load it first
                                                        // This will be handled by the ViewModel through a new event
                                                        onEvents(BookContentEvents.OnLoadAndSelectLine(lineId))
                                                    }
                                                }
                                            },
                                            onEntryExpand = { tocEntry ->
                                                onEvents(BookContentEvents.OnTocEntryExpanded(tocEntry))
                                            },
                                            modifier = Modifier.fillMaxHeight()
                                        )
                                    }
                                } else {
                                    // Show placeholder when no book is selected
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(Res.string.select_book_for_toc))
                                    }
                                }
                            }
                        }
                        second(200.dp) {
                            // Book content panel with commentaries
                            if (state.selectedBook != null) {
                                if (state.showCommentaries) {
                                    // Show book content with commentaries in a vertical split pane
                                    VerticalSplitPane(
                                        splitPaneState = state.contentSplitPaneState
                                    ) {
                                        first(200.dp) {
                                            // Book content
                                            BookContentView(
                                                book = state.selectedBook,
                                                lines = state.bookLines,
                                                selectedLine = state.selectedLine,
                                                onLineSelected = { line ->
                                                    onEvents(BookContentEvents.OnLineSelected(line))
                                                },
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                        second(200.dp) {
                                            // Commentaries panel
                                            LineCommentsView(
                                                selectedLine = state.selectedLine,
                                                commentaries = state.commentaries,
                                                onCommentClick = { /* Handle comment click if needed */ }
                                            )
                                        }
                                        splitter {
                                            visiblePart {
                                                Divider(
                                                    Orientation.Horizontal,
                                                    Modifier.fillMaxWidth().height(1.dp),
                                                    color = JewelTheme.globalColors.borders.disabled
                                                )
                                            }
                                            handle {
                                                Box(
                                                    Modifier
                                                        .height(5.dp)
                                                        .fillMaxWidth()
                                                        .markAsHandle()
                                                        .cursorForVerticalResize(),
                                                    contentAlignment = Alignment.Center
                                                ) {

                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Show only book content
                                    BookContentView(
                                        book = state.selectedBook,
                                        lines = state.bookLines,
                                        selectedLine = state.selectedLine,
                                        onLineSelected = { line ->
                                            onEvents(BookContentEvents.OnLineSelected(line))
                                        },
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                // Show placeholder content when no book is selected
                                Box(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(stringResource(Res.string.select_book))
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
        } else {
            // When book tree is hidden, show only the content without SplitPane
            // Second split pane: TOC | Book content
            HorizontalSplitPane(
                splitPaneState = state.tocSplitPaneState
            ) {
                first(200.dp) {
                    // TOC panel
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = stringResource(Res.string.table_of_contents),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (state.selectedBook != null) {
                            // Show TOC for selected book
                            // Check if we have root entries stored with the special key (-1L)
                            val rootEntries = state.tocChildren[-1L] ?: emptyList()
                            val displayTocEntries = if (rootEntries.isNotEmpty()) {
                                // If we have root entries, use them instead of state.tocEntries
                                // This ensures we're displaying the correct entries
                                rootEntries
                            } else {
                                // Otherwise, use the original tocEntries
                                state.tocEntries
                            }

                            Box(modifier = Modifier.fillMaxHeight()) {
                                TocView(
                                    tocEntries = displayTocEntries,
                                    expandedEntries = state.expandedTocEntries,
                                    tocChildren = state.tocChildren,
                                    onEntryClick = { tocEntry ->
                                        // Handle TOC entry click
                                        tocEntry.lineId?.let { lineId ->
                                            // Check if the line is already loaded
                                            val existingLine = state.bookLines.find { it.id == lineId }
                                            if (existingLine != null) {
                                                // If the line is already loaded, just select it
                                                onEvents(BookContentEvents.OnLineSelected(existingLine))
                                            } else {
                                                // If the line is not loaded, we need to load it first
                                                // This will be handled by the ViewModel through a new event
                                                onEvents(BookContentEvents.OnLoadAndSelectLine(lineId))
                                            }
                                        }
                                    },
                                    onEntryExpand = { tocEntry ->
                                        onEvents(BookContentEvents.OnTocEntryExpanded(tocEntry))
                                    },
                                    modifier = Modifier.fillMaxHeight()
                                )

                                // Show loading indicator when loading TOC entries
                                if (state.isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0x80FFFFFF)), // Semi-transparent white background
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        } else {
                            // Show placeholder when no book is selected
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(Res.string.select_book_for_toc))
                            }
                        }
                    }
                }
                second(200.dp) {
                    // Book content panel with commentaries
                    if (state.selectedBook != null) {
                        if (state.showCommentaries) {
                            // Show book content with commentaries in a vertical split pane
                            VerticalSplitPane(
                                splitPaneState = state.contentSplitPaneState
                            ) {
                                first(200.dp) {
                                    // Book content
                                    BookContentView(
                                        book = state.selectedBook,
                                        lines = state.bookLines,
                                        selectedLine = state.selectedLine,
                                        onLineSelected = { line ->
                                            onEvents(BookContentEvents.OnLineSelected(line))
                                        },
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                                second(200.dp) {
                                    // Commentaries panel
                                    LineCommentsView(
                                        selectedLine = state.selectedLine,
                                        commentaries = state.commentaries,
                                        onCommentClick = { /* Handle comment click if needed */ }
                                    )
                                }
                                splitter {
                                    visiblePart {
                                        Divider(
                                            Orientation.Horizontal,
                                            Modifier.fillMaxWidth().height(1.dp),
                                            color = JewelTheme.globalColors.borders.disabled
                                        )
                                    }
                                    handle {
                                        Box(
                                            Modifier
                                                .height(5.dp)
                                                .fillMaxWidth()
                                                .markAsHandle()
                                                .cursorForVerticalResize(),
                                            contentAlignment = Alignment.Center
                                        ) {

                                        }
                                    }
                                }
                            }
                        } else {
                            // Show only book content
                            BookContentView(
                                book = state.selectedBook,
                                lines = state.bookLines,
                                selectedLine = state.selectedLine,
                                onLineSelected = { line ->
                                    onEvents(BookContentEvents.OnLineSelected(line))
                                },
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        // Show placeholder content when no book is selected
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(Res.string.select_book))
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
}
