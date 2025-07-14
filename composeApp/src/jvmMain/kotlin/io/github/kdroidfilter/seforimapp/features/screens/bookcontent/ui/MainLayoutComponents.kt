package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.BookContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.*
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

@OptIn(ExperimentalSplitPaneApi::class, FlowPreview::class)
@Composable
fun MainBookContentLayout(
    uiState: BookContentUiState,
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

    DisposableEffect(Unit) {
        onDispose { onEvent(BookContentEvent.SaveState) }
    }

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
                            contentState = uiState.content,
                            contentSplitState = uiState.layout.contentSplitState,
                            onEvent = onEvent
                        )
                    }
                )
            }
        )

        EndVerticalBar(
            showCommentaries = uiState.content.showCommentaries,
            onToggleCommentaries = { onEvent(BookContentEvent.ToggleCommentaries) }
        )
    }
}

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
private fun ContentArea(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.navigation.isVisible -> {
            EnhancedHorizontalSplitPane(
                splitPaneState = uiState.layout.mainSplitState,
                modifier = modifier,
                firstContent = {
                    CategoryTreePanel(
                        navigationState = uiState.navigation,
                        onEvent = onEvent
                    )
                },
                secondContent = {
                    BookContentArea(uiState, onEvent)
                }
            )
        }
        else -> BookContentArea(uiState, onEvent, modifier)
    }
}

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
private fun BookContentArea(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.toc.isVisible -> {
            EnhancedHorizontalSplitPane(
                splitPaneState = uiState.layout.tocSplitState,
                modifier = modifier,
                firstContent = {
                    TocPanel(
                        selectedBook = uiState.navigation.selectedBook,
                        tocState = uiState.toc,
                        isLoading = uiState.isLoading,
                        onEvent = onEvent
                    )
                },
                secondContent = {
                    BookContentPanel(
                        selectedBook = uiState.navigation.selectedBook,
                        contentState = uiState.content,
                        contentSplitState = uiState.layout.contentSplitState,
                        onEvent = onEvent
                    )
                }
            )
        }
        else -> BookContentPanel(
            selectedBook = uiState.navigation.selectedBook,
            contentState = uiState.content,
            contentSplitState = uiState.layout.contentSplitState,
            onEvent = onEvent,
            modifier = modifier
        )
    }
}
