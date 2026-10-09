package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookTabUi
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.*
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.HomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes.NoteDraftAnchor
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeUiState
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator

/**
 * Whether the window's panes for a tab are in place (see `PaneSync`): a book's text first lays out
 * only then, so its initial scroll anchoring happens in its final frame. Always true outside a window.
 */
val LocalBookTextReady = staticCompositionLocalOf<(String) -> Boolean> { { true } }

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun BookContentPanel(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    diacritics: DiacriticsMode,
    modifier: Modifier = Modifier,
    isRestoringSession: Boolean = false,
    searchUi: SearchHomeUiState = SearchHomeUiState(),
    searchCallbacks: HomeSearchCallbacks =
        HomeSearchCallbacks(
            onReferenceQueryChanged = {},
            onTocQueryChanged = {},
            onGlobalExtendedChange = {},
            onSubmitTextSearch = {},
            onOpenReference = {},
            onPickCategory = {},
            onPickBook = {},
            onPickToc = {},
        ),
    isSelected: Boolean = true,
    bookCharCounts: IntArray? = null,
    noteDraft: NoteDraftAnchor? = null,
    tabUi: BookTabUi? = null,
) {
    val homeCardModifier =
        Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(JewelTheme.globalColors.panelBackground)
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
                        modifier = homeCardModifier,
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
                    diacritics = diacritics,
                    isSelected = isSelected,
                    bookCharCounts = bookCharCounts,
                    noteDraft = noteDraft,
                    tabUi = tabUi ?: viewModel { BookTabUi() },
                )
            }
        }
    }
}

@Composable
private fun BookContentPanelContent(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    diacritics: DiacriticsMode,
    isSelected: Boolean,
    bookCharCounts: IntArray?,
    noteDraft: NoteDraftAnchor?,
    tabUi: BookTabUi,
) {
    val providers = uiState.providers ?: return
    val selectedBook = uiState.navigation.selectedBook ?: return
    // Latched: once laid out, the text stays composed whatever the panes do next.
    var laidOut by remember(selectedBook.id) { mutableStateOf(false) }
    if (!laidOut && LocalBookTextReady.current(uiState.tabId)) laidOut = true
    if (!laidOut) {
        LoaderPanel()
        return
    }
    var isBookContentZoomInProgress by remember { mutableStateOf(false) }

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

    // Shared with the links / commentaries / sources panes of the window showing this tab.
    val connectionsCache = tabUi.connections(selectedBook.id)
    val prefetchConnections =
        remember(tabUi, selectedBook.id, providers) {
            { ids: List<Long> -> tabUi.prefetch(selectedBook.id, providers, ids) }
        }

    // Warm the text of the commentators that were open for the selected line, so that a background
    // tab (composed but not displayed) has its open commentaries ready and the on-demand pager load
    // is instant once the tab is shown. Gated on the pane actually being open.
    val openCommentatorIds = uiState.content.selectedCommentatorIds
    LaunchedEffect(uiState.content.primarySelectedLineId, openCommentatorIds, uiState.content.showCommentaries) {
        val lineId = uiState.content.primarySelectedLineId ?: return@LaunchedEffect
        if (!uiState.content.showCommentaries || openCommentatorIds.isEmpty()) return@LaunchedEffect
        providers.prefetchCommentaries(lineId, openCommentatorIds)
    }

    val hasBottomPane = uiState.content.showCommentaries || uiState.content.showSources
    val panelBackground = JewelTheme.globalColors.panelBackground
    val paneCardModifier =
        remember(hasBottomPane, panelBackground) {
            islandsCardModifier(panelBackground, bottom = if (hasBottomPane) 3.dp else 6.dp)
        }

    // Collect paging data here to keep BookContentView skippable
    val lazyPagingItems = providers.linesPagingData.collectAsLazyPagingItems()

    CompositionLocalProvider(LocalBookContentZoomInProgress provides isBookContentZoomInProgress) {
        // The links / commentaries / sources panes dock around this text and the breadcrumb sits
        // under them (see the window body), as the split panes had it.
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                BookContentView(
                    bookId = selectedBook.id,
                    lazyPagingItems = lazyPagingItems,
                    selectedLineIds = uiState.content.selectedLineIds,
                    primarySelectedLineId = uiState.content.primarySelectedLineId,
                    isTocEntrySelection = uiState.content.isTocEntrySelection,
                    markedLines =
                        uiState.content.markedRange
                            ?.takeIf { it.bookId == selectedBook.id }
                            ?.let { it.first..it.last },
                    // The Chumash's only: another book opened in the tab, by any way, reads as usual
                    shnayimMikra = uiState.content.shnayimMikra && selectedBook.isChumash,
                    onLineSelect = { line, isModifier ->
                        onEvent(BookContentEvent.LineSelected(line, isModifier))
                    },
                    onEvent = onEvent,
                    tabId = uiState.tabId,
                    diacritics = diacritics,
                    draftNote = noteDraft,
                    modifier = paneCardModifier,
                    preservedListState = bookListState,
                    scrollIndex = uiState.content.scrollIndex,
                    scrollOffset = uiState.content.scrollOffset,
                    scrollToLineTimestamp = uiState.content.scrollToLineTimestamp,
                    anchorId = uiState.content.anchorId,
                    anchorIndex = uiState.content.anchorIndex,
                    topAnchorLineId = uiState.content.topAnchorLineId,
                    topAnchorTimestamp = uiState.content.topAnchorRequestTimestamp,
                    onScroll = { bookId, anchorId, anchorIndex, scrollIndex, scrollOffset ->
                        onEvent(
                            BookContentEvent.ContentScrolled(
                                bookId = bookId,
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
                    bookCharCounts = bookCharCounts,
                    onPointerZoomInProgressChange = { isBookContentZoomInProgress = it },
                    focusRequest = uiState.content.focusTextRequest,
                )
            }
        }
    }
}

/** The breadcrumb under a book's text and its line panes; drawn by the window under its inner dock. */
@Composable
fun BookBreadcrumb(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
) {
    BreadcrumbSection(
        uiState = uiState,
        onEvent = onEvent,
        verticalPadding = 8.dp,
    )
}

/** Whether [uiState]'s tab shows a book's text (not Home, not a loader): its breadcrumb and line panes follow. */
fun isBookTextShown(uiState: BookContentState): Boolean =
    uiState.navigation.selectedBook != null && uiState.providers != null && !uiState.isLoading

@Composable
private fun LoaderPanel(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun CommentsPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    lineConnections: Map<Long, LineConnectionsSnapshot>,
    diacritics: DiacriticsMode,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LineCommentsView(
            uiState = uiState,
            onEvent = onEvent,
            lineConnections = lineConnections,
            diacritics = diacritics,
        )
    }
}

@Composable
fun SourcesPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    lineConnections: Map<Long, LineConnectionsSnapshot>,
    diacritics: DiacriticsMode,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LineTargumView(
            uiState = uiState,
            onEvent = onEvent,
            lineConnections = lineConnections,
            availabilityType = ConnectionType.SOURCE,
            diacritics = diacritics,
        )
    }
}

@Composable
fun TargumPane(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    lineConnections: Map<Long, LineConnectionsSnapshot>,
    diacritics: DiacriticsMode,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LineTargumView(
            uiState = uiState,
            onEvent = onEvent,
            lineConnections = lineConnections,
            diacritics = diacritics,
        )
    }
}

@Composable
private fun BreadcrumbSection(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    verticalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val sectionModifier =
        modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp, start = 4.dp, end = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(JewelTheme.globalColors.panelBackground)
    Column(modifier = sectionModifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BreadcrumbView(
                uiState = uiState,
                onEvent = onEvent,
                modifier = Modifier.weight(1f),
            )
            BreadcrumbActionsView(uiState = uiState)
        }
    }
}

private fun islandsCardModifier(
    panelBackground: androidx.compose.ui.graphics.Color,
    top: Dp = 6.dp,
    bottom: Dp = 6.dp,
): Modifier =
    Modifier
        .fillMaxSize()
        .padding(top = top, bottom = bottom, start = 4.dp, end = 4.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(panelBackground)
