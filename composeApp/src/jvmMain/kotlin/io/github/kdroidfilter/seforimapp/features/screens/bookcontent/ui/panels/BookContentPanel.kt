package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimapp.core.presentation.navigation.Navigator
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.ContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.jewel.ui.component.Text
import org.koin.compose.koinInject
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.select_book
import java.util.UUID


@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun BookContentPanel(
    selectedBook: Book?,
    contentState: ContentUiState,
    contentSplitState: SplitPaneState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    // Get the Navigator from Koin
    val navigator = koinInject<Navigator>()
    val scope = rememberCoroutineScope()

    // Preserve LazyListState across recompositions
    val bookListState = rememberLazyListState()
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
            EnhancedVerticalSplitPane(
                splitPaneState = contentSplitState,
                modifier = modifier,
                firstContent = {
                    BookContentView(
                        book = selectedBook,
                        lines = contentState.lines,
                        selectedLine = contentState.selectedLine,
                        onLineSelected = { line ->
                            onEvent(BookContentEvent.LineSelected(line))
                        },
                        modifier = Modifier.padding(16.dp),
                        preservedListState = bookListState,
                        scrollIndex = contentState.scrollIndex,
                        scrollOffset = contentState.scrollOffset,
                        onScroll = { index, offset ->
                            onEvent(BookContentEvent.ContentScrolled(index, offset))
                        },
                        onLoadMore = {
                            onEvent(BookContentEvent.LoadMoreLines)
                        }
                    )
                },
                secondContent = {
                    LineCommentsView(
                        selectedLine = contentState.selectedLine,
                        commentaries = contentState.commentaries,
                        selectedTabIndex = contentState.commentariesSelectedTab,
                        commentariesScrollIndex = contentState.commentariesScrollIndex,
                        commentariesScrollOffset = contentState.commentariesScrollOffset,
                        onCommentClick = { commentary ->
                            // When a commentary is clicked, open a new tab with the book and line of the commentary
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
                        onTabSelected = { index ->
                            onEvent(BookContentEvent.CommentariesTabSelected(index))
                        },
                        onScroll = { index, offset ->
                            onEvent(BookContentEvent.CommentariesScrolled(index, offset))
                        }
                    )
                }
            )
        }
        else -> {
            BookContentView(
                book = selectedBook,
                lines = contentState.lines,
                selectedLine = contentState.selectedLine,
                onLineSelected = { line ->
                    onEvent(BookContentEvent.LineSelected(line))
                },
                modifier = modifier.padding(16.dp),
                preservedListState = bookListState,
                scrollIndex = contentState.scrollIndex,
                scrollOffset = contentState.scrollOffset,
                onScroll = { index, offset ->
                    onEvent(BookContentEvent.ContentScrolled(index, offset))
                },
                onLoadMore = {
                    onEvent(BookContentEvent.LoadMoreLines)
                }
            )
        }
    }
}
