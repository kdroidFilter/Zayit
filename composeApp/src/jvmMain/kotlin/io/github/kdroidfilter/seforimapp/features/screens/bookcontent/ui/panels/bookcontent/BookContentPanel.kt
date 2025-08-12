package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent

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
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.BookContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.*
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent.views.BookContentView
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent.views.BreadcrumbView
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent.views.LineCommentsView
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent.views.LineTargumView
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.select_book


@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun BookContentPanel(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {

    // Preserve LazyListState across recompositions
    val bookListState = remember(uiState.navigation.selectedBook?.id) { LazyListState() }

    if (uiState.navigation.selectedBook == null) {
        SelectBookPane(modifier = modifier)
        return
    }

    // Use providers from uiState for paging data and builder functions
    val providers = uiState.providers ?: // Providers not ready yet; avoid accessing ViewModel directly per requirements
    return

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
                            modifier = Modifier.padding(16.dp),
                            preservedListState = bookListState,
                            scrollIndex = uiState.content.scrollIndex,
                            scrollOffset = uiState.content.scrollOffset,
                            scrollToLineTimestamp = uiState.content.scrollToLineTimestamp,
                            anchorId = uiState.content.anchorId,
                            anchorIndex = uiState.content.anchorIndex,
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
private fun SelectBookPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(16.dp).fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Text(stringResource(Res.string.select_book))
    }
}


@Composable
private fun CommentsPane(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
) {
    LineCommentsView(
        uiState = uiState,
        onEvent = onEvent
    )
}

@Composable
private fun TargumPane(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
) {
    LineTargumView(
        uiState = uiState,
        onEvent = onEvent
    )
}

@Composable
private fun BreadcrumbSection(
    uiState: BookContentUiState,
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