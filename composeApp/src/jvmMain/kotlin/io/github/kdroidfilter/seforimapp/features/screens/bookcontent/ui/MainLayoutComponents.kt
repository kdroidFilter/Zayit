package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.BookContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.EndVerticalBar
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.StartVerticalBar
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.BookContentPanel
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.CategoryTreePanel
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.BookTocPanel
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

    LaunchedEffect(uiState.layout.targumSplitState, uiState.content.showTargum) {
        if (uiState.content.showTargum) {
            snapshotFlow { uiState.layout.targumSplitState.positionPercentage }
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
            uiState = uiState,
            onEvent = onEvent
        )

        // Always use the same structure - just hide panels by setting width to 0
        EnhancedHorizontalSplitPane(
            splitPaneState = uiState.layout.mainSplitState,
            modifier = Modifier.weight(1f),
            firstMinSize = if (uiState.navigation.isVisible) 200f else 0f,
            firstContent = {
                if (uiState.navigation.isVisible) {
                    CategoryTreePanel(
                        uiState = uiState,
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
                            BookTocPanel(
                                uiState = uiState,
                                onEvent = onEvent
                            )
                        }
                    },
                    secondContent = {
                        BookContentPanel(
                            uiState = uiState,
                            onEvent = onEvent
                        )
                    }
                )
            }
        )

        EndVerticalBar(
            uiState = uiState,
            onEvent = onEvent
        )
    }
}