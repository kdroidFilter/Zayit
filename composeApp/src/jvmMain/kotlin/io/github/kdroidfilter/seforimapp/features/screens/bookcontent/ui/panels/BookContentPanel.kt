package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paging.PagingData
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.ContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.NavigationUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.TocUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.BookContentView
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.BreadcrumbView
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.EnhancedVerticalSplitPane
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.LineCommentsView
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.LineTargumView
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.select_book


@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun BookContentPanel(
    selectedBook: Book?,
    linesPagingData: Flow<PagingData<Line>>, // Paging data flow for lines
    buildCommentariesPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    getAvailableCommentatorsForLine: suspend (Long) -> Map<String, Long>,
    buildLinksPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    getAvailableLinksForLine: suspend (Long) -> Map<String, Long>,
    contentState: ContentUiState,
    tocState: TocUiState,
    navigationState: NavigationUiState,
    verticalContentSplitState: SplitPaneState,
    horizontalTargumSplitState: SplitPaneState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {

    // Preserve LazyListState across recompositions
    val bookListState = remember(selectedBook?.id) { LazyListState() }

    if (selectedBook == null) {
        SelectBookPane(modifier = modifier)
        return
    }

    Column(modifier = modifier.fillMaxSize()) {

        EnhancedVerticalSplitPane(
            splitPaneState = verticalContentSplitState,
            modifier = Modifier.weight(1f),
            firstContent = {
                EnhancedHorizontalSplitPane(
                    horizontalTargumSplitState, firstContent = {
                    BookContentPane(
                        book = selectedBook,
                        linesPagingData = linesPagingData,
                        contentState = contentState,
                        onEvent = onEvent,
                        preservedListState = bookListState,
                        modifier = Modifier.padding(16.dp)
                    )
                }, secondContent = if (contentState.showTargum) {
                    {
                        TargumPane(
                            contentState = contentState,
                            buildLinksPagerFor = buildLinksPagerFor,
                            getAvailableLinksForLine = getAvailableLinksForLine,
                            onEvent = onEvent
                        )
                    }
                } else null)
            },
            secondContent = if (contentState.showCommentaries) {
                {
                    CommentsPane(
                        contentState = contentState,
                        buildCommentariesPagerFor = buildCommentariesPagerFor,
                        getAvailableCommentatorsForLine = getAvailableCommentatorsForLine,
                        onEvent = onEvent
                    )
                }
            } else null)

        BreadcrumbSection(
            book = selectedBook,
            contentState = contentState,
            tocState = tocState,
            navigationState = navigationState,
            onEvent = onEvent,
            verticalPadding = 8.dp
        )
    }
}

@Composable
private fun SelectBookPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(16.dp).fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Text(stringResource(Res.string.select_book))
    }
}

@Composable
private fun BookContentPane(
    book: Book,
    linesPagingData: Flow<PagingData<Line>>,
    contentState: ContentUiState,
    onEvent: (BookContentEvent) -> Unit,
    preservedListState: LazyListState,
    modifier: Modifier = Modifier
) {
    BookContentView(
        book = book,
        linesPagingData = linesPagingData,
        selectedLine = contentState.selectedLine,
        onLineSelected = { line ->
            onEvent(BookContentEvent.LineSelected(line))
        },
        onEvent = onEvent,
        modifier = modifier,
        preservedListState = preservedListState,
        scrollIndex = contentState.scrollIndex,
        scrollOffset = contentState.scrollOffset,
        scrollToLineTimestamp = contentState.scrollToLineTimestamp,
        anchorId = contentState.anchorId,
        anchorIndex = contentState.anchorIndex,
        onScroll = { anchorId, anchorIndex, scrollIndex, scrollOffset ->
            onEvent(
                BookContentEvent.ContentScrolled(
                    anchorId = anchorId,
                    anchorIndex = anchorIndex,
                    scrollIndex = scrollIndex,
                    scrollOffset = scrollOffset
                )
            )
        })
}

@Composable
private fun CommentsPane(
    contentState: ContentUiState,
    buildCommentariesPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    getAvailableCommentatorsForLine: suspend (Long) -> Map<String, Long>,
    onEvent: (BookContentEvent) -> Unit,
) {
    LineCommentsView(
        selectedLine = contentState.selectedLine,
        buildCommentariesPagerFor = buildCommentariesPagerFor,
        getAvailableCommentatorsForLine = getAvailableCommentatorsForLine,
        commentariesScrollIndex = contentState.commentariesScrollIndex,
        commentariesScrollOffset = contentState.commentariesScrollOffset,
        initiallySelectedCommentatorIds = contentState.selectedCommentatorIds,
        onSelectedCommentatorsChange = { ids ->
            contentState.selectedLine?.let { line ->
                onEvent(BookContentEvent.SelectedCommentatorsChanged(line.id, ids))
            }
        },
        onCommentClick = { commentary ->
            onEvent(
                BookContentEvent.OpenCommentaryTarget(
                    bookId = commentary.link.targetBookId, lineId = commentary.link.targetLineId
                )
            )
        },
        onScroll = { index, offset ->
            onEvent(BookContentEvent.CommentariesScrolled(index, offset))
        })
}

@Composable
private fun TargumPane(
    contentState: ContentUiState,
    buildLinksPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    getAvailableLinksForLine: suspend (Long) -> Map<String, Long>,
    onEvent: (BookContentEvent) -> Unit,
) {
    LineTargumView(
        selectedLine = contentState.selectedLine,
        buildLinksPagerFor = buildLinksPagerFor,
        getAvailableLinksForLine = getAvailableLinksForLine,
        commentariesScrollIndex = contentState.commentariesScrollIndex,
        commentariesScrollOffset = contentState.commentariesScrollOffset,
        initiallySelectedSourceIds = contentState.selectedTargumSourceIds,
        onSelectedSourcesChange = { ids ->
            contentState.selectedLine?.let { line ->
                onEvent(BookContentEvent.SelectedTargumSourcesChanged(line.id, ids))
            }
        },
        onLinkClick = { commentary ->
            onEvent(
                BookContentEvent.OpenCommentaryTarget(
                    bookId = commentary.link.targetBookId, lineId = commentary.link.targetLineId
                )
            )
        },
        onScroll = { index, offset ->
            onEvent(BookContentEvent.CommentariesScrolled(index, offset))
        })
}

@Composable
private fun BreadcrumbSection(
    book: Book,
    contentState: ContentUiState,
    tocState: TocUiState,
    navigationState: NavigationUiState,
    onEvent: (BookContentEvent) -> Unit,
    verticalPadding: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().background(JewelTheme.globalColors.panelBackground)
    ) {
        HorizontalDivider()
        BreadcrumbView(
            book = book,
            selectedLine = contentState.selectedLine,
            tocEntries = tocState.entries,
            tocChildren = tocState.children,
            rootCategories = navigationState.rootCategories,
            categoryChildren = navigationState.categoryChildren,
            onTocEntryClick = { entry ->
                entry.lineId?.let { lineId ->
                    onEvent(BookContentEvent.LoadAndSelectLine(lineId))
                }
            },
            onCategoryClick = { category ->
                onEvent(BookContentEvent.CategorySelected(category))
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding, horizontal = 16.dp)
        )
    }
}