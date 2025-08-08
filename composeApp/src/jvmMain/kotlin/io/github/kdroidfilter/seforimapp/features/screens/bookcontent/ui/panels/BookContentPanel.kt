package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paging.PagingData
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.core.presentation.navigation.Navigator
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.ContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.NavigationUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.TocUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.BookContentView
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.BreadcrumbView
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.EnhancedVerticalSplitPane
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.LineCommentsPagedView
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.koin.compose.koinInject
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.select_book
import java.util.*


@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun BookContentPanel(
    selectedBook: Book?,
    linesPagingData: Flow<PagingData<Line>>, // Paging data flow for lines
    commentsPagingData: Flow<PagingData<io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText>>, // Paging data flow for comments
    contentState: ContentUiState,
    tocState: TocUiState,
    navigationState: NavigationUiState,
    contentSplitState: SplitPaneState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    // Get the Navigator from Koin
    val navigator = koinInject<Navigator>()
    val scope = rememberCoroutineScope()

    // SplitPaneState for the commentaries horizontal split (commentators list and commentaries)
    val commentariesSplitState = rememberSplitPaneState(0.10f) // 10% for commentators list

    // Preserve LazyListState across recompositions
    val bookListState = remember(selectedBook?.id) { LazyListState() }

    when {
        selectedBook == null -> {
            Box(
                modifier = modifier.padding(16.dp).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(Res.string.select_book))
            }
        }
        contentState.showCommentaries -> {
            Column(modifier = modifier.fillMaxSize()) {
                // Main content with commentaries
                EnhancedVerticalSplitPane(
                    splitPaneState = contentSplitState,
                    modifier = Modifier.weight(1f),
                    firstContent = {
                        BookContentView(
                            book = selectedBook,
                            linesPagingData = linesPagingData, // Pass paging data
                            selectedLine = contentState.selectedLine,
                            shouldScrollToLine = contentState.shouldScrollToLine,
                            tocEntries = tocState.entries,
                            tocChildren = tocState.children,
                            rootCategories = navigationState.rootCategories,
                            categoryChildren = navigationState.categoryChildren,
                            onLineSelected = { line ->
                                onEvent(BookContentEvent.LineSelected(line))
                            },
                            onTocEntryClick = { entry ->
                                entry.lineId?.let { lineId ->
                                    onEvent(BookContentEvent.LoadAndSelectLine(lineId))
                                }
                            },
                            onCategoryClick = { category ->
                                onEvent(BookContentEvent.CategorySelected(category))
                            },
                            onEvent = onEvent,
                            modifier = Modifier.padding(16.dp),
                            preservedListState = bookListState,
                            scrollIndex = contentState.scrollIndex,
                            scrollOffset = contentState.scrollOffset,
                            scrollToLineTimestamp = contentState.scrollToLineTimestamp,
                            anchorId = contentState.anchorId,
                            anchorIndex = contentState.anchorIndex,
                            onScroll = { anchorId, anchorIndex, scrollIndex, scrollOffset ->
                                onEvent(BookContentEvent.ContentScrolled(
                                    anchorId = anchorId,
                                    anchorIndex = anchorIndex,
                                    scrollIndex = scrollIndex,
                                    scrollOffset = scrollOffset
                                ))
                            }
                        )
                    },
                    secondContent = {
                        LineCommentsPagedView(
                            selectedLine = contentState.selectedLine,
                            commentsPagingData = commentsPagingData,
                            commentariesScrollIndex = contentState.commentariesScrollIndex,
                            commentariesScrollOffset = contentState.commentariesScrollOffset,
                            onCommentClick = { commentary ->
                                scope.launch {
                                    navigator.navigate(
                                        TabsDestination.BookContent(
                                            bookId = commentary.link.targetBookId,
                                            tabId = UUID.randomUUID().toString(),
                                            lineId = commentary.link.targetLineId
                                        )
                                    )
                                }
                            },
                            onScroll = { index, offset ->
                                onEvent(BookContentEvent.CommentariesScrolled(index, offset))
                            }
                        )
                    }
                )

                // Breadcrumb at the bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JewelTheme.globalColors.panelBackground)
                ) {
                    HorizontalDivider()
                    BreadcrumbView(
                        book = selectedBook,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 0.dp, horizontal = 16.dp)
                    )
                }
            }
        }
        else -> {
            Column(modifier = modifier.fillMaxSize()) {
                // Main content
                BookContentView(
                    book = selectedBook,
                    linesPagingData = linesPagingData, // Pass paging data
                    selectedLine = contentState.selectedLine,
                    shouldScrollToLine = contentState.shouldScrollToLine,
                    tocEntries = tocState.entries,
                    tocChildren = tocState.children,
                    rootCategories = navigationState.rootCategories,
                    categoryChildren = navigationState.categoryChildren,
                    onLineSelected = { line ->
                        onEvent(BookContentEvent.LineSelected(line))
                    },
                    onTocEntryClick = { entry ->
                        entry.lineId?.let { lineId ->
                            onEvent(BookContentEvent.LoadAndSelectLine(lineId))
                        }
                    },
                    onCategoryClick = { category ->
                        onEvent(BookContentEvent.CategorySelected(category))
                    },
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f).padding(16.dp),
                    preservedListState = bookListState,
                    scrollIndex = contentState.scrollIndex,
                    scrollOffset = contentState.scrollOffset,
                    scrollToLineTimestamp = contentState.scrollToLineTimestamp,
                    anchorId = contentState.anchorId,
                    anchorIndex = contentState.anchorIndex,
                    onScroll = { anchorId, anchorIndex, scrollIndex, scrollOffset ->
                        onEvent(BookContentEvent.ContentScrolled(
                            anchorId = anchorId,
                            anchorIndex = anchorIndex,
                            scrollIndex = scrollIndex,
                            scrollOffset = scrollOffset
                        ))
                    }
                )

                // Breadcrumb at the bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JewelTheme.globalColors.panelBackground)
                ) {
                    HorizontalDivider()
                    BreadcrumbView(
                        book = selectedBook,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    )
                }
            }
        }
    }
}