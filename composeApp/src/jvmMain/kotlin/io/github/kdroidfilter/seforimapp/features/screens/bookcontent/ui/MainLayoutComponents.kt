package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import app.cash.paging.PagingData
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.BookContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.EndVerticalBar
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.StartVerticalBar
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.BookContentPanel
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.CategoryTreePanel
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.TocPanel
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

@OptIn(ExperimentalSplitPaneApi::class, FlowPreview::class)
@Composable
fun MainBookContentLayout(
    uiState: BookContentUiState,
    linesPagingData: Flow<PagingData<Line>>, // Paging data flow for lines
    buildCommentariesPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    getAvailableCommentatorsForLine: suspend (Long) -> Map<String, Long>,
    buildLinksPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    getAvailableLinksForLine: suspend (Long) -> Map<String, Long>,
    onEvent: (BookContentEvent) -> Unit
) {
    // Save split pane positions with debounce - only when panels are visible
    LaunchedEffect(uiState.layout.mainSplitState, uiState.navigation.isVisible) {
        if (uiState.navigation.isVisible) {
            snapshotFlow { uiState.layout.mainSplitState.positionPercentage }
                .debounce(300)
                .filter { it > 0 } // Only save non-zero positions
                .collect { onEvent(BookContentEvent.SaveState) }
        }
    }

    LaunchedEffect(uiState.layout.tocSplitState, uiState.toc.isVisible) {
        if (uiState.toc.isVisible) {
            snapshotFlow { uiState.layout.tocSplitState.positionPercentage }
                .debounce(300)
                .filter { it > 0 } // Only save non-zero positions
                .collect { onEvent(BookContentEvent.SaveState) }
        }
    }

    LaunchedEffect(uiState.layout.contentSplitState, uiState.content.showCommentaries) {
        if (uiState.content.showCommentaries) {
            snapshotFlow { uiState.layout.contentSplitState.positionPercentage }
                .debounce(300)
                .filter { it > 0 && it < 1 } // Only save non-zero and non-one positions
                .collect { onEvent(BookContentEvent.SaveState) }
        }
    }

    LaunchedEffect(uiState.layout.linksSplitState, uiState.content.showLinks) {
        if (uiState.content.showLinks) {
            snapshotFlow { uiState.layout.linksSplitState.positionPercentage }
                .debounce(300)
                .filter { it > 0 && it < 1 }
                .collect { onEvent(BookContentEvent.SaveState) }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onEvent(BookContentEvent.SaveState) }
    }

    // Main content area with panels - now takes full height
    Row(modifier = Modifier.fillMaxSize()) {
        StartVerticalBar(
            showBookTree = uiState.navigation.isVisible,
            showToc = uiState.toc.isVisible,
            onToggleBookTree = { onEvent(BookContentEvent.ToggleBookTree) },
            onToggleToc = { onEvent(BookContentEvent.ToggleToc) }
        )

        // Always use the same structure - just hide panels by setting width to 0
        EnhancedHorizontalSplitPane(
            splitPaneState = uiState.layout.mainSplitState,
            modifier = Modifier.weight(1f),
            firstMinSize = if (uiState.navigation.isVisible) 200f else 0f,
            firstContent = {
                if (uiState.navigation.isVisible) {
                    CategoryTreePanel(
                        navigationState = uiState.navigation,
                        onEvent = onEvent
                    )
                }
            },
            secondContent = {
                EnhancedHorizontalSplitPane(
                    splitPaneState = uiState.layout.tocSplitState,
                    firstMinSize = if (uiState.toc.isVisible) 200f else 0f,
                    firstContent = {
                        if (uiState.toc.isVisible) {
                            TocPanel(
                                selectedBook = uiState.navigation.selectedBook,
                                tocState = uiState.toc,
                                isLoading = uiState.isLoading,
                                onEvent = onEvent
                            )
                        }
                    },
                    secondContent = {
                        BookContentPanel(
                            selectedBook = uiState.navigation.selectedBook,
                            linesPagingData = linesPagingData, // Pass paging data
                            buildCommentariesPagerFor = buildCommentariesPagerFor,
                            getAvailableCommentatorsForLine = getAvailableCommentatorsForLine,
                            buildLinksPagerFor = buildLinksPagerFor,
                            getAvailableLinksForLine = getAvailableLinksForLine,
                            contentState = uiState.content,
                            tocState = uiState.toc,
                            navigationState = uiState.navigation,
                            verticalContentSplitState = uiState.layout.contentSplitState,
                            horizontalLinksSplitState = uiState.layout.linksSplitState,
                            onEvent = onEvent
                        )
                    }
                )
            }
        )

        EndVerticalBar(
            showCommentaries = uiState.content.showCommentaries,
            onToggleCommentaries = { onEvent(BookContentEvent.ToggleCommentaries) },
            showLinks = uiState.content.showLinks,
            onToggleLinks = { onEvent(BookContentEvent.ToggleLinks) }
        )
    }
}