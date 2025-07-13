package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvents
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

/**
 * Main layout component for the book content screen.
 * Handles the overall layout and state management.
 */
@OptIn(ExperimentalSplitPaneApi::class, FlowPreview::class)
@Composable
fun MainBookContentLayout(
    state: BookContentState,
    onEvents: (BookContentEvents) -> Unit
) {
    // Local scroll states
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
        ContentLayout(
            modifier = Modifier.weight(1f),
            state = state,
            onEvents = onEvents,
            paragraphScrollState = paragraphScrollState,
            chapterScrollState = chapterScrollState
        )
        EndVerticalBar(state, onEvents)
    }
}

/**
 * Content layout component that handles the main content area.
 * Adapts the layout based on the state of the book tree and TOC visibility.
 */
@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun ContentLayout(
    modifier: Modifier = Modifier,
    state: BookContentState,
    onEvents: (BookContentEvents) -> Unit,
    paragraphScrollState: ScrollState,
    chapterScrollState: ScrollState
) {
    Column(modifier = modifier) {
        if (state.showBookTree) {
            // First split pane: Category tree | (TOC + Book content)
            EnhancedHorizontalSplitPane(
                splitPaneState = state.splitPaneState,
                modifier = Modifier.fillMaxSize(),
                firstContent = {
                    // Navigation panel (Category tree)
                    CategoryTreePanel(
                        state = state,
                        onEvents = onEvents
                    )
                },
                secondContent = {
                    // Content area (TOC + Book content or just Book content)
                    if (state.showToc) {
                        // When TOC is visible, use HorizontalSplitPane
                        EnhancedHorizontalSplitPane(
                            splitPaneState = state.tocSplitPaneState,
                            firstContent = {
                                // TOC panel
                                TocPanel(
                                    state = state,
                                    onEvents = onEvents
                                )
                            },
                            secondContent = {
                                // Book content panel
                                BookContentPanel(
                                    state = state,
                                    onEvents = onEvents
                                )
                            }
                        )
                    } else {
                        // When TOC is hidden, show only the book content
                        BookContentPanel(
                            state = state,
                            onEvents = onEvents
                        )
                    }
                }
            )
        } else {
            // When book tree is hidden, show only the content without SplitPane
            if (state.showToc) {
                // When TOC is visible, use HorizontalSplitPane
                EnhancedHorizontalSplitPane(
                    splitPaneState = state.tocSplitPaneState,
                    firstContent = {
                        // TOC panel
                        TocPanel(
                            state = state,
                            onEvents = onEvents
                        )
                    },
                    secondContent = {
                        // Book content panel
                        BookContentPanel(
                            state = state,
                            onEvents = onEvents
                        )
                    }
                )
            } else {
                // When TOC is hidden, show only the book content
                BookContentPanel(
                    state = state,
                    onEvents = onEvents
                )
            }
        }
    }
}