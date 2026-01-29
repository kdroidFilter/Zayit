package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EnhancedVerticalSplitPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.asStable
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.*
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.HomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import kotlinx.coroutines.launch
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.no_sources_for_line
import seforimapp.seforimapp.generated.resources.select_line_for_sources
import seforimapp.seforimapp.generated.resources.sources

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun BookContentPanel(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    showDiacritics: Boolean,
    modifier: Modifier = Modifier,
    isRestoringSession: Boolean = false,
    searchUi: SearchHomeUiState = SearchHomeUiState(),
    searchCallbacks: HomeSearchCallbacks =
        HomeSearchCallbacks(
            onReferenceQueryChanged = {},
            onTocQueryChanged = {},
            onFilterChange = {},
            onGlobalExtendedChange = {},
            onSubmitTextSearch = {},
            onOpenReference = {},
            onPickCategory = {},
            onPickBook = {},
            onPickToc = {},
        ),
    isSelected: Boolean = true,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            // If no book is selected
            uiState.navigation.selectedBook == null -> {
                // If we're actively loading a book for this tab, avoid flashing the Home screen.
                // Show a minimal loader until the selected book is ready.
                if (uiState.isLoading || isRestoringSession) {
                    LoaderPanel()
                } else {
                    HomeView(
                        onEvent = onEvent,
                        searchUi = searchUi,
                        searchCallbacks = searchCallbacks,
                    )
                }
            }

            // Book is selected but providers are not ready yet (initialization in progress)
            // Show a centered loader to avoid flash of partial content.
            uiState.providers == null || uiState.isLoading -> {
                LoaderPanel()
            }

            // Main content when book and providers are ready
            else -> {
                BookContentPanelContent(
                    uiState = uiState,
                    onEvent = onEvent,
                    showDiacritics = showDiacritics,
                    isSelected = isSelected,
                )
            }
        }
    }
}

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
private fun BookContentPanelContent(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    showDiacritics: Boolean,
    isSelected: Boolean,
) {
    val providers = uiState.providers ?: return
    val selectedBook = uiState.navigation.selectedBook ?: return

    // Create LazyListState AFTER loading check, so anchorId is correctly set
    // When restoring with an anchor, use the computed anchorIndex which accounts for
    // lines near the beginning of the book (where target isn't at INITIAL_LOAD_SIZE/2)
    val bookListState =
        remember(selectedBook.id) {
            val hasAnchor = uiState.content.anchorId != -1L
            val initialIndex = if (hasAnchor) uiState.content.anchorIndex else uiState.content.scrollIndex
            LazyListState(
                firstVisibleItemIndex = initialIndex.coerceAtLeast(0),
                firstVisibleItemScrollOffset = uiState.content.scrollOffset.coerceAtLeast(0),
            )
        }

    val connectionsCache =
        remember(selectedBook.id) {
            mutableStateMapOf<Long, LineConnectionsSnapshot>()
        }
    val prefetchScope = rememberCoroutineScope()
    val prefetchConnections =
        remember(providers, connectionsCache) {
            { ids: List<Long> ->
                if (ids.isEmpty()) return@remember
                val missing = ids.filterNot { connectionsCache.containsKey(it) }.distinct()
                if (missing.isEmpty()) return@remember
                prefetchScope.launch {
                    runCatching { providers.loadLineConnections(missing) }
                        .onSuccess { connectionsCache.putAll(it) }
                }
            }
        }

    Column(modifier = Modifier.fillMaxSize()) {
        EnhancedVerticalSplitPane(
            splitPaneState = uiState.layout.contentSplitState.asStable(),
            modifier = Modifier.weight(1f),
            firstContent = {
                EnhancedHorizontalSplitPane(
                    splitPaneState = uiState.layout.targumSplitState.asStable(),
                    firstContent = {
                        BookContentView(
                            bookId = selectedBook.id,
                            linesPagingData = providers.linesPagingData,
                            selectedLineIds = uiState.content.selectedLineIds,
                            primarySelectedLineId = uiState.content.primarySelectedLineId,
                            onLineSelect = { line, isModifier ->
                                onEvent(BookContentEvent.LineSelected(line, isModifier))
                            },
                            onEvent = onEvent,
                            tabId = uiState.tabId,
                            showDiacritics = showDiacritics,
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
                                        scrollOffset = scrollOffset,
                                    ),
                                )
                            },
                            altHeadingsByLineId = uiState.altToc.lineHeadingsByLineId.asStableAltHeadings(),
                            lineConnections = connectionsCache,
                            onPrefetchLineConnections = prefetchConnections,
                            isSelected = isSelected,
                        )
                    },
                    secondContent =
                        if (uiState.content.showTargum) {
                            {
                                TargumPane(
                                    uiState = uiState,
                                    onEvent = onEvent,
                                    lineConnections = connectionsCache,
                                    showDiacritics = showDiacritics,
                                )
                            }
                        } else {
                            null
                        },
                )
            },
            secondContent =
                when {
                    uiState.content.showCommentaries -> {
                        {
                            CommentsPane(
                                uiState = uiState,
                                onEvent = onEvent,
                                lineConnections = connectionsCache,
                                showDiacritics = showDiacritics,
                            )
                        }
                    }

                    uiState.content.showSources -> {
                        {
                            SourcesPane(
                                uiState = uiState,
                                onEvent = onEvent,
                                lineConnections = connectionsCache,
                                showDiacritics = showDiacritics,
                            )
                        }
                    }

                    else -> null
                },
        )

        BreadcrumbSection(
            uiState = uiState,
            onEvent = onEvent,
            verticalPadding = 8.dp,
        )
    }
}

@Composable
private fun LoaderPanel(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center),
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CommentsPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    lineConnections: Map<Long, LineConnectionsSnapshot>,
    showDiacritics: Boolean,
) {
    LineCommentsView(
        uiState = uiState,
        onEvent = onEvent,
        lineConnections = lineConnections,
        showDiacritics = showDiacritics,
    )
}

@Composable
private fun SourcesPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    lineConnections: Map<Long, LineConnectionsSnapshot>,
    showDiacritics: Boolean,
) {
    LineTargumView(
        uiState = uiState,
        onEvent = onEvent,
        lineConnections = lineConnections,
        availabilityType = ConnectionType.SOURCE,
        showDiacritics = showDiacritics,
    )
}

@Composable
private fun TargumPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    lineConnections: Map<Long, LineConnectionsSnapshot>,
    showDiacritics: Boolean,
) {
    LineTargumView(
        uiState = uiState,
        onEvent = onEvent,
        lineConnections = lineConnections,
        showDiacritics = showDiacritics,
    )
}

@Composable
private fun BreadcrumbSection(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    verticalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().background(JewelTheme.globalColors.panelBackground),
    ) {
        HorizontalDivider()
        BreadcrumbView(
            uiState = uiState,
            onEvent = onEvent,
            modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding, horizontal = 16.dp),
        )
    }
}
