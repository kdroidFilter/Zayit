package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EnhancedVerticalSplitPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.*
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator


@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun BookContentPanel(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    isRestoringSession: Boolean = false
) {

    // Preserve LazyListState across recompositions
    val bookListState = remember(uiState.navigation.selectedBook?.id) { LazyListState() }

    if (uiState.navigation.selectedBook == null) {
        // If we're actively loading a book for this tab, avoid flashing the Home screen.
        // Show a minimal loader until the selected book is ready.
        if (uiState.isLoading || isRestoringSession) {
            LoaderPanel(modifier = modifier)
        } else {
            HomeView(
                uiState = uiState,
                onEvent = onEvent,
                modifier = modifier
            )
        }
        return
    }

    // Use providers from uiState for paging data and builder functions
    val providers = uiState.providers
    if (providers == null || uiState.isLoading) {
        // Book is selected but providers are not ready yet (initialization in progress)
        // Show a centered loader to avoid flash of partial content.
        LoaderPanel(modifier = modifier)
        return
    }

    Column(modifier = modifier.fillMaxSize()) {

        EnhancedVerticalSplitPane(
            splitPaneState = uiState.layout.contentSplitState,
            modifier = Modifier.weight(1f),
            firstContent = {
                EnhancedHorizontalSplitPane(
                    uiState.layout.targumSplitState, firstContent = {
                        BookContentView(
                            book = uiState.navigation.selectedBook,
                            linesPagingData = providers.linesPagingData,
                            selectedLine = uiState.content.selectedLine,
                            onLineSelected = { line ->
                                onEvent(BookContentEvent.LineSelected(line))
                            },
                            onEvent = onEvent,
                            modifier = Modifier,
                            preservedListState = bookListState,
                            scrollIndex = uiState.content.scrollIndex,
                            scrollOffset = uiState.content.scrollOffset,
                            scrollToLineTimestamp = uiState.content.scrollToLineTimestamp,
                            anchorId = uiState.content.anchorId,
                            anchorIndex = uiState.content.anchorIndex,
                            topAnchorLineId = uiState.content.topAnchorLineId,
                            topAnchorTimestamp = uiState.content.topAnchorRequestTimestamp,
                            onScroll = { anchorId, anchorIndex, scrollIndex, scrollOffset ->
                                onEvent(
                                    BookContentEvent.ContentScrolled(
                                        anchorId = anchorId,
                                        anchorIndex = anchorIndex,
                                        scrollIndex = scrollIndex,
                                        scrollOffset = scrollOffset
                                    )
                                )
                            }
                        )
                }, secondContent = if (uiState.content.showTargum) {
                    {
                        TargumPane(
                            uiState = uiState,
                            onEvent = onEvent
                        )
                    }
                } else null)
            },
            secondContent = if (uiState.content.showCommentaries) {
                {
                    CommentsPane(
                        uiState = uiState,
                        onEvent = onEvent
                    )
                }
            } else null)

        BreadcrumbSection(
            uiState = uiState,
            onEvent = onEvent,
            verticalPadding = 8.dp
        )
    }
}

@Composable
private fun LoaderPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CommentsPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
) {
    LineCommentsView(
        uiState = uiState,
        onEvent = onEvent
    )
}

@Composable
private fun TargumPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
) {
    LineTargumView(
        uiState = uiState,
        onEvent = onEvent
    )
}

@Composable
private fun BreadcrumbSection(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    verticalPadding: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().background(JewelTheme.globalColors.panelBackground)
    ) {
        HorizontalDivider()
        BreadcrumbView(
            uiState = uiState,
            onEvent = onEvent,
            modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding, horizontal = 16.dp)
        )
    }
}
