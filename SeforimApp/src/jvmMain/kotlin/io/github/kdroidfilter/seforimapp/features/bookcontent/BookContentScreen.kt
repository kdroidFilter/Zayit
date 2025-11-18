package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.dokar.sonner.ToastType
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
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.max_commentators_limit
import io.github.kdroidfilter.seforimapp.core.presentation.components.AppToaster
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.HomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.launch

/**
 * Composable function to display the book content screen.
 *
 * This screen observes the `uiState` from the `BookContentViewModel` and passes
 * it to the `BookContentView` composable for rendering. It also provides the
 * `onEvent` lambda from the ViewModel to handle user interactions within the
 * `BookContentView`.
 */
@Composable
fun BookContentScreen(
    viewModel: BookContentViewModel,
    isRestoringSession: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()

    // Bridge SearchHomeViewModel (global) into BookContent so that
    // HomeView only consumes state + callbacks, not the ViewModel.
    val appGraph = LocalAppGraph.current
    val searchHomeViewModel = appGraph.searchHomeViewModel
    val searchUi: SearchHomeUiState by remember(searchHomeViewModel) { searchHomeViewModel.uiState }.collectAsState()
    val scope = rememberCoroutineScope()
    val homeSearchCallbacks = remember(searchHomeViewModel, scope) {
        HomeSearchCallbacks(
            onReferenceQueryChanged = searchHomeViewModel::onReferenceQueryChanged,
            onTocQueryChanged = searchHomeViewModel::onTocQueryChanged,
            onFilterChange = searchHomeViewModel::onFilterChange,
            onLevelIndexChange = searchHomeViewModel::onLevelIndexChange,
            onGlobalExtendedChange = searchHomeViewModel::onGlobalExtendedChange,
            onSubmitTextSearch = { query ->
                scope.launch { searchHomeViewModel.submitSearch(query) }
            },
            onOpenReference = {
                scope.launch { searchHomeViewModel.openSelectedReferenceInCurrentTab() }
            },
            onPickCategory = searchHomeViewModel::onPickCategory,
            onPickBook = searchHomeViewModel::onPickBook,
            onPickToc = searchHomeViewModel::onPickToc
        )
    }

    BookContentView(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        isRestoringSession = isRestoringSession,
        searchUi = searchUi,
        searchCallbacks = homeSearchCallbacks
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
    onEvent: (BookContentEvent) -> Unit,
    isRestoringSession: Boolean = false,
    searchUi: SearchHomeUiState,
    searchCallbacks: HomeSearchCallbacks
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                    when {
                        isCtrlOrCmd && keyEvent.key == Key.B -> {
                            if (keyEvent.isShiftPressed) {
                                onEvent(BookContentEvent.ToggleToc)
                            } else {
                                onEvent(BookContentEvent.ToggleBookTree)
                            }
                            true
                        }
                        isCtrlOrCmd && keyEvent.key == Key.K -> {
                            if (keyEvent.isShiftPressed) {
                                onEvent(BookContentEvent.ToggleTargum)
                            } else {
                                onEvent(BookContentEvent.ToggleCommentaries)
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
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
                        BookContentPanel(
                            uiState = uiState,
                            onEvent = onEvent,
                            isRestoringSession = isRestoringSession,
                            searchUi = searchUi,
                            searchCallbacks = searchCallbacks
                        )
                    },
                    showSplitter = uiState.toc.isVisible
                )
            },
            showSplitter = uiState.navigation.isVisible
        )

        EndVerticalBar(uiState = uiState, onEvent = onEvent)
    }

    // Render toaster overlay themed like Jewel (reusable)
    AppToaster(state = toaster)

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
