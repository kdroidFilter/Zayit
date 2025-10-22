package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import com.dokar.sonner.Toaster
import com.dokar.sonner.ToastWidthPolicy
import com.dokar.sonner.rememberToasterState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EndVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.StartVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.BookContentPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc.BookTocPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.categorytree.CategoryTreePanel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.SplitDefaults
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.max_commentators_limit

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
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit
) {
    // Toaster for transient messages (e.g., selection limits)
    val toaster = rememberToasterState()
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

    // Render toaster overlay themed like Jewel
    Toaster(
        state = toaster,
        darkTheme = JewelTheme.isDark,
        alignment = Alignment.BottomEnd,
        expanded = true,
        showCloseButton = true,
        contentPadding = { PaddingValues(horizontal = 12.dp, vertical = 10.dp) },
        containerPadding = PaddingValues(all = 16.dp),
        widthPolicy = { ToastWidthPolicy(max = 420.dp) },
        elevation = 6.dp,
        shadowAmbientColor = Color.Black.copy(alpha = 0.18f),
        shadowSpotColor = Color.Black.copy(alpha = 0.24f),
        contentColor = { JewelTheme.globalColors.text.normal },
        border = { BorderStroke(1.dp, JewelTheme.globalColors.borders.disabled) },
        background = { SolidColor(JewelTheme.globalColors.panelBackground) },
        shape = { RectangleShape },
        offset = IntOffset(0, 0)
    )

    // React to state mutations to show a toast (no callbacks)
    val maxLimitMsg = stringResource(Res.string.max_commentators_limit)
    LaunchedEffect(uiState.content.maxCommentatorsLimitSignal) {
        if (uiState.content.maxCommentatorsLimitSignal > 0L) {
            toaster.show(
                message = maxLimitMsg,
                type = ToastType.Warning,

            )
        }
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
