package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.EndVerticalBar
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.StartVerticalBar
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.bookcontent.BookContentPanel
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.booktoc.BookTocPanel
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.categorytree.CategoryTreePanel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.SplitDefaults
import kotlin.math.roundToInt

/**
 * Composable function to display the book content screen.
 *
 * This screen observes the `uiState` from the `BookContentViewModel` and passes
 * it to the `BookContentView` composable for rendering. It also provides the
 * `onEvent` lambda from the ViewModel to handle user interactions within the
 * `BookContentView`.
 */
@Composable
fun BookContentScreen(viewModel: BookContentViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    BookContentView(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

/**
 * Displays the content view of a book with multiple panels configured within split panes.
 *
 * @param uiState The complete UI state used for rendering the book content screen, capturing navigation, TOC, content display, layout management, and more.
 * @param onEvent Function that handles various user-driven events or state updates within the book content view.
 */
@OptIn(ExperimentalSplitPaneApi::class, FlowPreview::class)
@Composable
fun BookContentView(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit
) {
    // Configuration of split panes to monitor
    val splitPaneConfigs = listOf(
        SplitPaneConfig(
            splitState = uiState.layout.mainSplitState,
            isVisible = uiState.navigation.isVisible,
            positionFilter = { it > 0 }
        ),
        SplitPaneConfig(
            splitState = uiState.layout.tocSplitState,
            isVisible = uiState.toc.isVisible,
            positionFilter = { it > 0 }
        ),
        SplitPaneConfig(
            splitState = uiState.layout.contentSplitState,
            isVisible = uiState.content.showCommentaries,
            positionFilter = { it > 0 && it < 1 }
        ),
        SplitPaneConfig(
            splitState = uiState.layout.targumSplitState,
            isVisible = uiState.content.showTargum,
            positionFilter = { it > 0 && it < 1 }
        )
    )

    // Monitor all split panes with the same logic
    splitPaneConfigs.forEach { config ->
        LaunchedEffect(config.splitState, config.isVisible) {
            if (config.isVisible) {
                snapshotFlow { config.splitState.positionPercentage }
                    .map { ((it * 100).roundToInt() / 100f) }
                    .distinctUntilChanged()
                    .debounce(300)
                    .filter(config.positionFilter)
                    .collect { onEvent(BookContentEvent.SaveState) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onEvent(BookContentEvent.SaveState) }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        StartVerticalBar(uiState = uiState, onEvent = onEvent)

        EnhancedHorizontalSplitPane(
            splitPaneState = uiState.layout.mainSplitState,
            modifier = Modifier.weight(1f),
            firstMinSize = if (uiState.navigation.isVisible) SplitDefaults.MIN_MAIN else 0f,
            firstContent = {
                if (uiState.navigation.isVisible) {
                    CategoryTreePanel(uiState = uiState, onEvent = onEvent)
                }
            },
            secondContent = {
                EnhancedHorizontalSplitPane(
                    splitPaneState = uiState.layout.tocSplitState,
                    firstMinSize = if (uiState.toc.isVisible) SplitDefaults.MIN_TOC else 0f,
                    firstContent = {
                        if (uiState.toc.isVisible) {
                            BookTocPanel(uiState = uiState, onEvent = onEvent)
                        }
                    },
                    secondContent = {
                        BookContentPanel(uiState = uiState, onEvent = onEvent)
                    },
                    showSplitter = uiState.toc.isVisible
                )
            },
            showSplitter = uiState.navigation.isVisible
        )

        EndVerticalBar(uiState = uiState, onEvent = onEvent)
    }
}

/**
 * Represents the configuration used to manage the state and behavior of a split-pane component.
 *
 * @property splitState The state object representing the current split position and related properties.
 * @property isVisible Indicates whether the split-pane is visible or not.
 * @property positionFilter A filter function applied to the split position value to determine its validity.
 */
private data class SplitPaneConfig @OptIn(ExperimentalSplitPaneApi::class) constructor(
    val splitState: SplitPaneState,
    val isVisible: Boolean,
    val positionFilter: (Float) -> Boolean
)
