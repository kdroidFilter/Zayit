package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.presentation.components.CountBadge
import io.github.kdroidfilter.seforimapp.core.presentation.components.FindInPageBar
import io.github.kdroidfilter.seforimapp.core.presentation.text.findAllMatchesOriginal
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimlibrary.core.models.AltTocEntry
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text

@OptIn(FlowPreview::class)
@Composable
fun BookContentView(
    bookId: Long,
    linesPagingData: Flow<PagingData<Line>>,
    selectedLineId: Long?,
    onLineSelect: (Line) -> Unit,
    onEvent: (BookContentEvent) -> Unit,
    tabId: String,
    showDiacritics: Boolean,
    modifier: Modifier = Modifier,
    preservedListState: LazyListState? = null,
    scrollIndex: Int = 0,
    scrollOffset: Int = 0,
    scrollToLineTimestamp: Long = 0,
    anchorId: Long = -1L,
    anchorIndex: Int = 0,
    topAnchorLineId: Long = -1L,
    topAnchorTimestamp: Long = 0L,
    onScroll: (Long, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    altHeadingsByLineId: StableAltHeadings = StableAltHeadings.Empty,
    lineConnections: Map<Long, LineConnectionsSnapshot> = emptyMap(),
    onPrefetchLineConnections: (List<Long>) -> Unit = {},
    isSelected: Boolean = true,
) {
    // Collect paging data
    val lazyPagingItems: LazyPagingItems<Line> = linesPagingData.collectAsLazyPagingItems()

    // Don't use the saved scroll position initially if we have an anchor
    // The restoration will be handled after pagination loads
    val listState =
        preservedListState ?: rememberLazyListState(
            initialFirstVisibleItemIndex = if (anchorId != -1L) 0 else scrollIndex,
            initialFirstVisibleItemScrollOffset = if (anchorId != -1L) 0 else scrollOffset,
        )

    // Collect text size from settings
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()

    // Animate text size changes for smoother transitions
    val textSize by animateFloatAsState(
        targetValue = rawTextSize,
        animationSpec = tween(durationMillis = 300),
        label = "textSizeAnimation",
    )

    // Collect line height from settings
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()

    // Animate line height changes for smoother transitions
    val lineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = tween(durationMillis = 300),
        label = "lineHeightAnimation",
    )

    // Selected font for main book content
    val bookFontCode by AppSettings.bookFontCodeFlow.collectAsState()
    val hebrewFontFamily = FontCatalog.familyFor(bookFontCode)
    // macOS fallback: some Hebrew fonts have no Bold face; slightly scale bold text for visibility
    val boldScaleForPlatform =
        remember(bookFontCode) {
            val lacksBold = bookFontCode in setOf("notoserifhebrew", "notorashihebrew", "frankruhllibre")
            if (PlatformInfo.isMacOS && lacksBold) 1.08f else 1.0f
        }

    // Track restoration state per book
    var hasRestored by remember(bookId) { mutableStateOf(false) }

    // Track the restored anchor to avoid re-restoration
    var restoredAnchorId by remember(bookId) { mutableStateOf(-1L) }

    // Track if this is the initial book open (vs changing TOC within same book)
    var isInitialBookOpen by remember(bookId) { mutableStateOf(true) }

    // Hide content until initial scroll is complete to prevent visual glitch
    // Only apply on initial book open, not when changing TOC entries
    val needsInitialPositioning = isInitialBookOpen && topAnchorLineId != -1L && !hasRestored
    val contentAlpha by animateFloatAsState(
        targetValue = if (needsInitialPositioning) 0f else 1f,
        animationSpec = tween(durationMillis = if (needsInitialPositioning) 0 else 50),
        label = "contentAlpha",
    )

    // selectedLineId is now passed as a parameter for stability

    // Prefetch connection data for visible lines to avoid per-line DB calls
    LaunchedEffect(listState, lazyPagingItems, onPrefetchLineConnections) {
        snapshotFlow {
            if (lazyPagingItems.itemCount == 0) {
                emptyList()
            } else {
                listState.layoutInfo.visibleItemsInfo
                    .mapNotNull { info ->
                        if (info.index < lazyPagingItems.itemCount) {
                            lazyPagingItems.peek(info.index)?.id
                        } else {
                            null
                        }
                    }.distinct()
            }
        }.map { ids -> ids.distinct() }
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .debounce(150)
            .collect { ids -> onPrefetchLineConnections(ids) }
    }

    // Ensure the selected line is prefetched even if it is not visible yet
    LaunchedEffect(selectedLineId, lineConnections) {
        val id = selectedLineId ?: return@LaunchedEffect
        if (lineConnections[id] == null) {
            onPrefetchLineConnections(listOf(id))
        }
    }

    // Ensure the selected line is visible when explicitly requested (keyboard/nav)
    // without forcing it to the very top of the viewport.
    LaunchedEffect(scrollToLineTimestamp, selectedLineId, topAnchorTimestamp, topAnchorLineId) {
        if (scrollToLineTimestamp == 0L || selectedLineId == null) return@LaunchedEffect

        // Skip minimal bring-into-view when a top-anchoring request is active for this selection
        val isTopAnchorRequest = (topAnchorTimestamp == scrollToLineTimestamp && topAnchorLineId == selectedLineId)
        if (isTopAnchorRequest) return@LaunchedEffect

        while (lazyPagingItems.loadState.refresh is LoadState.Loading) {
            delay(16)
        }

        val snapshot = lazyPagingItems.itemSnapshotList
        val index = snapshot.indices.firstOrNull { snapshot[it]?.id == selectedLineId }
        if (index != null) {
            val first = listState.firstVisibleItemIndex
            val last =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: first
            if (index < first || index > last) {
                // Scroll just enough so the item is not glued to the top
                val targetOffsetPx = 32 // small top padding in px; minimal jump
                listState.scrollToItem(index, targetOffsetPx)
            }
        }
    }

    // Robust top-anchored restoration for TOC-driven selection.
    // Trigger on every new anchorId. This ensures repeated TOC clicks always re-align at the top.
    LaunchedEffect(topAnchorTimestamp, topAnchorLineId) {
        if (topAnchorTimestamp == 0L || topAnchorLineId == -1L) return@LaunchedEffect

        // Reset restoration guard for this top-anchor event
        hasRestored = false

        // Wait for any ongoing refresh to complete
        while (lazyPagingItems.loadState.refresh is LoadState.Loading) {
            delay(16)
        }

        // Helper to locate the target index in the current snapshot
        fun currentTargetIndex(): Int? {
            val snapshot = lazyPagingItems.itemSnapshotList
            return snapshot.indices.firstOrNull { snapshot[it]?.id == topAnchorLineId }
        }

        var targetIndex = currentTargetIndex()
        if (targetIndex == null) {
            debugln { "Top-anchor target $topAnchorLineId not yet in snapshot; waiting" }
            withTimeoutOrNull(1500L) {
                snapshotFlow { lazyPagingItems.itemSnapshotList.items }
                    .map { items -> items.indices.firstOrNull { items[it].id == topAnchorLineId } }
                    .filterNotNull()
                    .first()
                    .also { idx -> targetIndex = idx }
            }
        }

        targetIndex?.let { idx ->
            debugln { "Top-anchoring to index $idx for line $topAnchorLineId" }
            listState.scrollToItem(idx, 0)
            restoredAnchorId = topAnchorLineId
            hasRestored = true
            // After first restoration, disable alpha effect for subsequent TOC navigations
            isInitialBookOpen = false
        }
    }

    // Initial restoration from saved state (TabSystem): prefer saved anchor, otherwise saved index/offset.
    // Runs once per book unless a top-anchor request has been issued (which handles itself).
    LaunchedEffect(bookId, topAnchorTimestamp, anchorId, scrollIndex, scrollOffset) {
        if (topAnchorTimestamp != 0L) return@LaunchedEffect
        if (hasRestored) return@LaunchedEffect

        // Wait for initial page load to complete
        while (lazyPagingItems.loadState.refresh is LoadState.Loading) {
            delay(16)
        }

        if (lazyPagingItems.itemCount <= 0) return@LaunchedEffect

        // Try saved anchor if available
        if (anchorId != -1L) {
            fun currentAnchorIndex(): Int? {
                val snapshot = lazyPagingItems.itemSnapshotList
                return snapshot.indices.firstOrNull { snapshot[it]?.id == anchorId }
            }

            var idx = currentAnchorIndex()
            if (idx == null) {
                debugln { "Saved anchor $anchorId not yet in snapshot; waiting" }
                withTimeoutOrNull(1500L) {
                    snapshotFlow { lazyPagingItems.itemSnapshotList }
                        .map { snapshot -> snapshot.indices.firstOrNull { snapshot[it]?.id == anchorId } }
                        .filterNotNull()
                        .first()
                        .also { resolved -> idx = resolved }
                }
            }

            idx?.let { resolved ->
                debugln { "Restoring by saved anchor: idx=$resolved, offset=$scrollOffset" }
                listState.scrollToItem(resolved, scrollOffset.coerceAtLeast(0))
                hasRestored = true
                restoredAnchorId = anchorId
                return@LaunchedEffect
            }
        }

        // Fallback to index/offset when no anchor or anchor not in snapshot
        if (scrollIndex > 0 || scrollOffset > 0) {
            val itemCount = lazyPagingItems.itemCount
            val targetIndex = scrollIndex.coerceIn(0, maxOf(0, itemCount - 1))
            val targetOffset = scrollOffset.coerceAtLeast(0)
            debugln { "Restoring by index/offset: index=$targetIndex, offset=$targetOffset" }
            listState.scrollToItem(targetIndex, targetOffset)
            hasRestored = true
        }
    }

    // Save scroll position with anchor information - optimized with derivedStateOf
    val scrollData =
        remember(listState, lazyPagingItems) {
            derivedStateOf {
                val firstVisibleInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                val firstVisibleIndex = firstVisibleInfo?.index ?: listState.firstVisibleItemIndex
                val itemCount = lazyPagingItems.itemCount
                val safeIndex = firstVisibleIndex.coerceIn(0, maxOf(0, itemCount - 1))

                // Prefer the LazyColumn item key to avoid relying on paging snapshot access.
                val currentAnchorId: Long =
                    when (val key = firstVisibleInfo?.key) {
                        is Long -> key
                        is Int -> key.toLong()
                        else -> if (safeIndex in 0 until itemCount) lazyPagingItems[safeIndex]?.id ?: -1L else -1L
                    }

                val scrollOff = listState.firstVisibleItemScrollOffset.coerceAtLeast(0)

                AnchorData(
                    anchorId = currentAnchorId,
                    anchorIndex = safeIndex,
                    scrollIndex = firstVisibleIndex,
                    scrollOffset = scrollOff,
                )
            }
        }

    val onScrollUpdated by rememberUpdatedState(onScroll)
    val hasRestoredUpdated by rememberUpdatedState(hasRestored)
    val savedScrollIndexUpdated by rememberUpdatedState(scrollIndex)
    val savedScrollOffsetUpdated by rememberUpdatedState(scrollOffset)
    val savedAnchorIdUpdated by rememberUpdatedState(anchorId)
    val savedAnchorIndexUpdated by rememberUpdatedState(anchorIndex)

    LaunchedEffect(listState, lazyPagingItems) {
        fun maybeSave(data: AnchorData) {
            // Guard: on cold-boot restore, don't overwrite the persisted position with an initial transient emission
            // before the restoration effect has applied the saved anchor/offset.
            val hasPersistedPosition =
                savedAnchorIdUpdated > 0 || savedScrollIndexUpdated > 0 || savedScrollOffsetUpdated > 0
            if (!hasRestoredUpdated && hasPersistedPosition) {
                return
            }

            // Avoid wiping a previously known anchor when the list hasn't resolved item keys yet (e.g., while loading).
            val stableAnchorId = data.anchorId.takeIf { it > 0 } ?: savedAnchorIdUpdated
            val stableAnchorIndex = if (data.anchorId > 0) data.anchorIndex else savedAnchorIndexUpdated

            debugln {
                "Saving scroll: anchor=$stableAnchorId, index=${data.scrollIndex}, offset=${data.scrollOffset}"
            }
            onScrollUpdated(stableAnchorId, stableAnchorIndex, data.scrollIndex, data.scrollOffset)
        }

        // While scrolling, sample periodically so a close during an active scroll still restores closely.
        launch {
            snapshotFlow { scrollData.value }
                .distinctUntilChanged()
                .sample(200)
                .collect { data -> maybeSave(data) }
        }

        // When scrolling stops, immediately flush the latest value (avoids being a few lines behind).
        launch {
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .filter { inProgress -> !inProgress }
                .collect {
                    // Wait one frame so layoutInfo/visibleItemsInfo reflect the final settled position.
                    withFrameNanos { }
                    maybeSave(scrollData.value)
                }
        }
    }

    // Find-in-page UI state (scoped per tab)
    val showFind by AppSettings.findBarOpenFlow(tabId).collectAsState()
    val persistedFindQuery by AppSettings.findQueryFlow(tabId).collectAsState("")
    val smartModeEnabled by AppSettings.findSmartModeFlow(tabId).collectAsState()
    val findState = remember(tabId) { TextFieldState() }
    LaunchedEffect(persistedFindQuery) {
        val current = findState.text.toString()
        if (current != persistedFindQuery) {
            findState.edit { replace(0, length, persistedFindQuery) }
        }
    }

    // Smart mode: get highlight terms from search engine with dictionary expansion
    val appGraph = io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph.current
    val smartHighlightTerms by remember(smartModeEnabled, findState) {
        derivedStateOf {
            if (smartModeEnabled) {
                val query = findState.text.toString()
                if (query.length >= 2) {
                    appGraph.searchEngine.buildHighlightTerms(query)
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }

    var currentHitLineIndex by remember { mutableIntStateOf(-1) }
    var currentMatchLineId by remember { mutableStateOf<Long?>(null) }
    var currentMatchStart by remember { mutableIntStateOf(-1) }
    val plainTextCache = remember(bookId) { mutableStateMapOf<Long, String>() }
    val stableAnnotatedCache =
        remember(bookId, textSize, boldScaleForPlatform, showDiacritics) {
            StableAnnotatedCache(mutableStateMapOf())
        }

    // Navigate to next/previous line containing the query (wrap-around)
    val scope = rememberCoroutineScope()

    fun navigateToMatch(next: Boolean) {
        val query = findState.text.toString()
        if (query.length < 2) return
        val snapshot = lazyPagingItems.itemSnapshotList
        if (snapshot.isEmpty()) return
        val startIndex =
            if (currentHitLineIndex in snapshot.indices) currentHitLineIndex else listState.firstVisibleItemIndex
        val step = if (next) 1 else -1
        var i = startIndex
        var guard = 0
        val size = snapshot.size
        while (guard++ < size) {
            i = (i + step + size) % size
            val line = snapshot[i] ?: continue
            val text =
                plainTextCache.getOrPut(line.id) {
                    buildAnnotatedFromHtml(line.content, textSize).text
                }
            val start = findAllMatchesOriginal(text, query).firstOrNull()?.first ?: -1
            if (start >= 0) {
                currentHitLineIndex = i
                currentMatchLineId = line.id
                currentMatchStart = start
                // Bring line into view (slight offset)
                scope.launch {
                    listState.scrollToItem(i, 32)
                }
                break
            }
        }
    }

    // Global preview handler: handle basic navigation keys regardless of inner focus
    val previewKeyHandler =
        remember(onEvent) {
            { keyEvent: KeyEvent ->
                // Ctrl/Cmd+F handled globally at window level; do not intercept here
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            onEvent(BookContentEvent.NavigateToPreviousLine)
                            true
                        }

                        Key.DirectionDown -> {
                            onEvent(BookContentEvent.NavigateToNextLine)
                            true
                        }

                        Key.Escape -> {
                            if (showFind) {
                                AppSettings.closeFindBar(tabId)
                                true
                            } else {
                                false
                            }
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
        }

    // Request initial focus so arrow keys work as soon as the view appears
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(bookId) { focusRequester.requestFocus() }
    LaunchedEffect(showFind) {
        if (!showFind) {
            focusRequester.requestFocus()
        }
    }
    // Request focus when tab becomes selected for immediate keyboard navigation
    LaunchedEffect(isSelected) {
        if (isSelected) {
            focusRequester.requestFocus()
        }
    }

    // Workaround for Compose selection crash when extending selection with Shift+Click across
    // multiple selectables in a virtualized list. We intercept Shift+primary mouse presses at
    // the container level to avoid the extend-selection path, while preserving normal drag
    // selection and regular clicks.
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha } // Hide until positioned to prevent glitch
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent(previewKeyHandler)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val isShiftPrimaryPress = event.buttons.isPrimaryPressed && event.keyboardModifiers.isShiftPressed
                        if (isShiftPrimaryPress) {
                            // Consume to prevent SelectionContainer from handling extend-selection
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
    ) {
        SelectionContainer {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
                // Content list. Avoid a single SelectionContainer around the entire
                // paged list to prevent cross-item selection spanning unloaded pages,
                // which can crash when paging composes/uncomposes items.
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = 16.dp),
                ) {
                    items(
                        count = lazyPagingItems.itemCount,
                        key = lazyPagingItems.itemKey { it.id },
                        contentType = { "line" }, // Optimization: specify content type
                    ) { index ->
                        val line = lazyPagingItems[index]

                        if (line != null) {
                            val altHeadings = altHeadingsByLineId[line.id]
                            Column {
                                altHeadings.forEach { entry ->
                                    AltHeadingItem(
                                        entryId = entry.id,
                                        level = entry.level,
                                        text = entry.text,
                                        onClick = { onLineSelect(line) },
                                    )
                                }
                                LineItem(
                                    lineId = line.id,
                                    lineContent = line.content,
                                    isSelected = selectedLineId == line.id,
                                    fontFamily = hebrewFontFamily,
                                    onClick = { onLineSelect(line) },
                                    scrollToLineTimestamp = scrollToLineTimestamp,
                                    baseTextSize = textSize,
                                    lineHeight = lineHeight,
                                    boldScale = boldScaleForPlatform,
                                    highlightQuery = findState.text.toString().takeIf { showFind && !smartModeEnabled },
                                    highlightTerms = smartHighlightTerms.takeIf { showFind && smartModeEnabled },
                                    currentMatchStart = if (showFind && currentMatchLineId == line.id) currentMatchStart else null,
                                    annotatedCache = stableAnnotatedCache,
                                    showDiacritics = showDiacritics,
                                )
                            }
                        } else {
                            // Placeholder while loading
                            LoadingPlaceholder()
                        }
                    }

                    // Show loading indicators
                    lazyPagingItems.apply {
                        when {
                            // Avoid flicker: only show full loader on refresh if we have no items yet
                            loadState.refresh is LoadState.Loading && itemCount == 0 -> {
                                item(contentType = "loading") {
                                    LoadingIndicator()
                                }
                            }
                            // Keep small loader for pagination append
                            loadState.append is LoadState.Loading -> {
                                item(contentType = "loading") {
                                    LoadingIndicator(isSmall = true)
                                }
                            }

                            loadState.refresh is LoadState.Error -> {
                                val error = (loadState.refresh as LoadState.Error).error
                                item(contentType = "error") {
                                    ErrorIndicator(message = "Error: ${error.message}")
                                }
                            }

                            loadState.append is LoadState.Error -> {
                                val error = (loadState.append as LoadState.Error).error
                                item(contentType = "error") {
                                    ErrorIndicator(message = "Error loading more: ${error.message}")
                                }
                            }
                        }
                    }
                }
            }

            // Find-in-page bar overlay with result count badge (uniform style)
            if (showFind) {
                // Compute total matches across currently loaded snapshot (approximate)
                val queryText = findState.text.toString()
                val snapshotItems = lazyPagingItems.itemSnapshotList.items
                val matchCount =
                    remember(queryText, snapshotItems) {
                        if (queryText.length < 2) {
                            0
                        } else {
                            var total = 0
                            // Count non-overlapping occurrences per visible/loaded line
                            for (ln in snapshotItems) {
                                val text =
                                    try {
                                        buildAnnotatedFromHtml(ln.content, textSize).text
                                    } catch (_: Throwable) {
                                        ln.content
                                    }
                                total += findAllMatchesOriginal(text, queryText).size
                            }
                            total
                        }
                    }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .zIndex(2f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (queryText.length >= 2) {
                        // Wrap badge in a small panel background to improve border contrast,
                        // keeping the badge's own border color (disabled) identical to the tree.
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(JewelTheme.globalColors.panelBackground)
                                    .padding(2.dp),
                        ) {
                            CountBadge(count = matchCount)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    FindInPageBar(
                        state = findState,
                        onEnterNext = { navigateToMatch(true) },
                        onEnterPrev = { navigateToMatch(false) },
                        onClose = { AppSettings.closeFindBar(tabId) },
                        smartModeEnabled = smartModeEnabled,
                        onToggleSmartMode = { AppSettings.toggleFindSmartMode(tabId) },
                    )
                    LaunchedEffect(findState.text, showFind) {
                        val q = findState.text.toString()
                        AppSettings.setFindQuery(tabId, if (q.length >= 2) q else "")
                    }
                }
            }
        }
    }
}

// Data class for anchor information
private data class AnchorData(
    val anchorId: Long,
    val anchorIndex: Int,
    val scrollIndex: Int,
    val scrollOffset: Int,
)

/**
 * Stable wrapper for alt headings map to avoid unnecessary recompositions.
 * The map content is considered stable once created.
 */
@Stable
class StableAltHeadings(
    val map: Map<Long, List<AltTocEntry>>,
) {
    operator fun get(lineId: Long): List<AltTocEntry> = map[lineId].orEmpty()

    companion object {
        val Empty = StableAltHeadings(emptyMap())
    }
}

fun Map<Long, List<AltTocEntry>>.asStableAltHeadings(): StableAltHeadings = StableAltHeadings(this)

/**
 * Stable wrapper for annotated string cache to avoid unnecessary recompositions.
 */
@Stable
class StableAnnotatedCache(
    val cache: MutableMap<Long, AnnotatedString>,
) {
    fun getOrPut(
        lineId: Long,
        defaultValue: () -> AnnotatedString,
    ): AnnotatedString = cache.getOrPut(lineId, defaultValue)
}

@Composable
private fun AltHeadingItem(
    entryId: Long,
    level: Int,
    text: String,
    onClick: () -> Unit,
) {
    val fontSize =
        when (level) {
            0 -> 20.sp
            1 -> 18.sp
            else -> 16.sp
        }
    val paddingTop = if (level == 0) 12.dp else 8.dp
    val paddingBottom = 4.dp

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = paddingTop, bottom = paddingBottom)
                .pointerInput(entryId) {
                    detectTapGestures(onTap = { onClick() })
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight(),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LineItem(
    lineId: Long,
    lineContent: String,
    isSelected: Boolean,
    fontFamily: FontFamily,
    onClick: () -> Unit,
    scrollToLineTimestamp: Long,
    baseTextSize: Float = 16f,
    lineHeight: Float = 1.5f,
    boldScale: Float = 1.0f,
    highlightQuery: String? = null,
    highlightTerms: List<String>? = null,
    currentMatchStart: Int? = null,
    annotatedCache: StableAnnotatedCache? = null,
    showDiacritics: Boolean = true,
) {
    // Process content: remove diacritics if setting is disabled
    val processedContent =
        remember(lineContent, showDiacritics) {
            if (showDiacritics) {
                lineContent
            } else {
                HebrewTextUtils.removeAllDiacritics(lineContent)
            }
        }

    // Footnote marker color from theme
    val footnoteMarkerColor = JewelTheme.globalColors.outlines.focused

    // Memoize the annotated string with proper keys
    val annotated =
        remember(lineId, processedContent, baseTextSize, boldScale, annotatedCache, showDiacritics, footnoteMarkerColor) {
            annotatedCache?.getOrPut(lineId) {
                buildAnnotatedFromHtml(
                    processedContent,
                    baseTextSize,
                    boldScale = if (boldScale < 1f) 1f else boldScale,
                    footnoteMarkerColor = footnoteMarkerColor,
                )
            } ?: buildAnnotatedFromHtml(
                processedContent,
                baseTextSize,
                boldScale = if (boldScale < 1f) 1f else boldScale,
                footnoteMarkerColor = footnoteMarkerColor,
            )
        }

    // Build highlighted text when a query is active (>= 2 chars)
    val baseHl =
        JewelTheme.globalColors.outlines.focused
            .copy(alpha = 0.22f)
    val currentHl =
        JewelTheme.globalColors.outlines.focused
            .copy(alpha = 0.42f)
    val displayText: AnnotatedString =
        remember(annotated, highlightQuery, highlightTerms, currentMatchStart, baseHl, currentHl) {
            if (!highlightTerms.isNullOrEmpty()) {
                // Smart mode: highlight multiple terms from dictionary expansion
                io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotatedWithTerms(
                    annotated = annotated,
                    terms = highlightTerms,
                    currentStart = currentMatchStart?.takeIf { it >= 0 },
                    baseColor = baseHl,
                    currentColor = currentHl,
                )
            } else {
                // Normal mode: highlight single query
                io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotatedWithCurrent(
                    annotated = annotated,
                    query = highlightQuery,
                    currentStart = currentMatchStart?.takeIf { it >= 0 },
                    currentLength = highlightQuery?.length,
                    baseColor = baseHl,
                    currentColor = currentHl,
                )
            }
        }

    // Get theme color in composable context
    val borderColor = if (isSelected) JewelTheme.globalColors.outlines.focused else Color.Transparent

    val bringRequester = remember { BringIntoViewRequester() }

    // On navigation/explicit request, bring the selected line minimally into view
    LaunchedEffect(isSelected, scrollToLineTimestamp) {
        if (isSelected && scrollToLineTimestamp != 0L) {
            try {
                bringRequester.bringIntoView()
            } catch (_: Throwable) {
                // no-op: layout might not be ready yet
            }
        }
    }

    val textModifier =
        remember {
            Modifier.fillMaxWidth()
        }.bringIntoViewRequester(bringRequester)
            .pointerInput(lineId) {
                detectTapGestures(onTap = { onClick() })
            }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(borderColor)
                        .zIndex(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = displayText,
                textAlign = TextAlign.Justify,
                fontFamily = fontFamily,
                lineHeight = (baseTextSize * lineHeight).sp,
                modifier = textModifier,
            )
        }
    }
}

// Extract reusable components to avoid inline composition
@Composable
private fun LoadingPlaceholder() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LoadingIndicator(isSmall: Boolean = false) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = if (isSmall) Modifier.size(24.dp) else Modifier,
        )
    }
}

@Composable
private fun ErrorIndicator(message: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Color.Red,
        )
    }
}
