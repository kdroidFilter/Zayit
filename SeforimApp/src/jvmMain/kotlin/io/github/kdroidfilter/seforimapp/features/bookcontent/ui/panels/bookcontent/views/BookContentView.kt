package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import io.github.kdroidfilter.seforim.htmlparser.SkiaHtmlImageBuilder
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.annotations.UserHighlight
import io.github.kdroidfilter.seforimapp.core.annotations.UserNote
import io.github.kdroidfilter.seforimapp.core.coroutines.EfficiencyCoreDispatcher
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.e2e.E2e
import io.github.kdroidfilter.seforimapp.core.e2e.E2eReader
import io.github.kdroidfilter.seforimapp.core.presentation.components.FindInPageBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.rememberAppTextZoom
import io.github.kdroidfilter.seforimapp.core.presentation.components.syncFindField
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.presentation.text.CurrentFindMatchColor
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.core.presentation.text.applyUserHighlights
import io.github.kdroidfilter.seforimapp.core.presentation.text.drawNoteUnderlines
import io.github.kdroidfilter.seforimapp.core.presentation.text.findAllMatchesOriginal
import io.github.kdroidfilter.seforimapp.core.presentation.text.normalizeQueryForHebrew
import io.github.kdroidfilter.seforimapp.core.presentation.text.noteDisplayRanges
import io.github.kdroidfilter.seforimapp.core.presentation.text.withBackground
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.SafeSelectionContainer
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes.NoteDraftAnchor
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.BookFindMatches
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.findInBook
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.queryNarrows
import io.github.kdroidfilter.seforimapp.features.search.semantic.installedSemanticSearch
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimlibrary.core.models.AltTocEntry
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class, ExperimentalComposeUiApi::class)
@Suppress(
    "ComposeUnstableCollections",
    "ParamsComparedByRef",
) // LazyPagingItems is inherently mutable; recomposition on paging changes is expected
@Composable
fun BookContentView(
    bookId: Long,
    lazyPagingItems: LazyPagingItems<Line>,
    selectedLineIds: Set<Long>,
    primarySelectedLineId: Long?,
    onLineSelect: (Line, Boolean) -> Unit,
    onEvent: (BookContentEvent) -> Unit,
    tabId: String,
    diacritics: DiacriticsMode,
    modifier: Modifier = Modifier,
    draftNote: NoteDraftAnchor? = null,
    isTocEntrySelection: Boolean = false,
    // Lines by index, tinted, with an end mark after the last: a passage to read, such as the day's limud
    markedLines: IntRange? = null,
    // Each verse twice, then its targum: shnayim mikra
    shnayimMikra: Boolean = false,
    preservedListState: LazyListState? = null,
    scrollIndex: Int = 0,
    scrollOffset: Int = 0,
    scrollToLineTimestamp: Long = 0,
    anchorId: Long = -1L,
    anchorIndex: Int = 0,
    topAnchorLineId: Long = -1L,
    topAnchorTimestamp: Long = 0L,
    onScroll: (Long, Long, Int, Int, Int) -> Unit = { _, _, _, _, _ -> },
    altHeadingsByLineId: StableAltHeadings = StableAltHeadings.Empty,
    lineConnections: Map<Long, LineConnectionsSnapshot> = emptyMap(),
    onPrefetchLineConnections: (List<Long>) -> Unit = {},
    // A line under the pointer, likely to be selected next: its commentaries can be loaded ahead
    onLineHover: (Long) -> Unit = {},
    isSelected: Boolean = true,
    bookCharCounts: IntArray? = null,
    onPointerZoomInProgressChange: (Boolean) -> Unit = {},
    // Bumped to take the keyboard focus back (a breadcrumb popup closed)
    focusRequest: Int = 0,
) {
    val appSettings = LocalAppGraph.current.appSettings
    // Don't use the saved scroll position initially if we have an anchor
    // The restoration will be handled after pagination loads
    val listState =
        preservedListState ?: rememberLazyListState(
            initialFirstVisibleItemIndex = if (anchorId != -1L) 0 else scrollIndex,
            initialFirstVisibleItemScrollOffset = if (anchorId != -1L) 0 else scrollOffset,
        )

    // Layout-width of the first rendered line's Text, captured once via `onTextLayout`
    // from `result.layoutInput.constraints.maxWidth`. Includes all the nested padding
    // already subtracted by Compose's layout pass, so we don't have to replicate the
    // padding chain (outer Box .padding(bottom 8.dp) → LazyColumn .padding(end 16.dp) →
    // Row .padding(horizontal 8.dp) → SelectionBar width + Spacer 8.dp).
    var textLayoutWidthPx by remember(bookId) { mutableIntStateOf(0) }

    // Collect text size from settings
    val isTabSelected = LocalTabSelected.current
    // Ctrl/Cmd + wheel, a trackpad pinch or two fingers zoom the text
    val zoom = rememberAppTextZoom(onPointerZoomInProgressChange)
    val textSize = zoom.textSize

    // Collect line height from settings
    val rawLineHeight by appSettings.lineHeightFlow.collectAsState()

    // Animate line height changes only for the active tab
    val lineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = if (isTabSelected) tween(durationMillis = 300) else snap(),
        label = "lineHeightAnimation",
    )

    // Selected font for main book content
    val bookFontCode by appSettings.bookFontCodeFlow.collectAsState()
    val hebrewFontFamily = FontCatalog.familyFor(bookFontCode)
    val targumFontCode by appSettings.targumFontCodeFlow.collectAsState()
    val targumFontFamily = FontCatalog.familyFor(targumFontCode)
    val shnayimMikraTargum = rememberShnayimMikraTargum(bookId, shnayimMikra)
    // macOS fallback: some Hebrew fonts have no Bold face; slightly scale bold text for visibility
    val boldScaleForPlatform = FontCatalog.boldScaleFor(bookFontCode)

    // Track restoration state per book. Plain remember: a tab is kept alive across switches and is
    // never rebuilt on a switch, so this only resets when the composition is actually torn down
    // (the tab navigates to a different book/destination, or is closed). In that case we WANT the
    // masking below to replay, because the paged list reloads from empty and would otherwise show
    // the top then jump to the saved position.
    var hasRestored by remember(bookId) { mutableStateOf(false) }

    // Track the restored anchor to avoid re-restoration
    var restoredAnchorId by remember(bookId) { mutableStateOf(-1L) }

    // Track if this is the initial book open (vs changing TOC within same book)
    var isInitialBookOpen by remember(bookId) { mutableStateOf(true) }

    // Hide content until initial scroll is complete to prevent a visual glitch (showing the top
    // then jumping to the saved position). The reveal is instant (no fade). It only runs on a cold
    // open / session restore; a tab switch keeps the list alive and positioned, so nothing masks.
    val hasSavedInitialPosition = anchorId != -1L || scrollIndex > 0 || scrollOffset > 0
    val hasTopAnchorRequest = topAnchorTimestamp != 0L && topAnchorLineId != -1L
    val needsInitialPositioning =
        isInitialBookOpen &&
            !hasRestored &&
            (hasTopAnchorRequest || (topAnchorTimestamp == 0L && hasSavedInitialPosition))
    val contentAlpha = if (needsInitialPositioning) 0f else 1f
    if (E2e.enabled) SideEffect { E2eReader.bookShown(bookId, listState, visible = contentAlpha == 1f) }

    // selectedLineId is now passed as a parameter for stability

    // Publish the currently materialized lines to the SelectionContext so context-menu actions
    // (e.g. "copy with source") and the AWT keyboard dispatcher can map a free-form text
    // selection back to its source lines. Only the active tab publishes; lifecycle clears are
    // tabId-scoped so a backgrounded tab cannot wipe a sibling tab's snapshot — even when both
    // tabs reference the same book.
    val selectionContext = LocalAppGraph.current.selectionContext
    LaunchedEffect(lazyPagingItems, isTabSelected, tabId, selectionContext) {
        if (!isTabSelected) {
            selectionContext.clearVisibleLinesIfOwnedBy(tabId)
            return@LaunchedEffect
        }
        snapshotFlow { lazyPagingItems.itemSnapshotList.items }
            .distinctUntilChanged()
            .catch { e -> debugln { "visible-lines flow failed: $e" } }
            .collect { items -> selectionContext.setVisibleLines(tabId, items) }
    }
    DisposableEffect(tabId, selectionContext) {
        onDispose { selectionContext.clearVisibleLinesIfOwnedBy(tabId) }
    }

    // Persisted user highlights for this book: loaded once into the store's per-book cache,
    // then read from memory on the render hot path (no per-line DB access). Grouped by line so
    // a single highlight change only recomposes the affected line — unhighlighted lines receive
    // the stable emptyList() singleton and are skipped by strong skipping.
    val highlightStore = LocalAppGraph.current.highlightStore
    LaunchedEffect(bookId, highlightStore) { highlightStore.loadBook(bookId) }
    val highlightsByBook by highlightStore.highlightsByBook.collectAsState()
    val highlightsByLine =
        remember(highlightsByBook, bookId) {
            highlightsByBook[bookId].orEmpty().groupBy { it.lineId }
        }

    // Persisted user notes for this book: same per-book cache + group-by-line strategy as
    // highlights. Noted ranges are underlined on the line; the notes pane shows the bodies.
    val noteStore = LocalAppGraph.current.noteStore
    LaunchedEffect(bookId, noteStore) { noteStore.loadBook(bookId) }
    val notesByBook by noteStore.notesByBook.collectAsState()
    val notesByLine =
        remember(notesByBook, bookId, draftNote) {
            val persisted = notesByBook[bookId].orEmpty().groupBy { it.lineId }
            // Underline the pending draft range immediately, before it is saved.
            val d = draftNote ?: return@remember persisted
            val transient = UserNote(id = -1L, lineId = d.lineId, startOffset = d.startOffset, endOffset = d.endOffset, note = "")
            persisted + (d.lineId to (persisted[d.lineId].orEmpty() + transient))
        }

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
            .debounce(150.milliseconds)
            .catch { e -> debugln { "prefetch-connections flow failed: $e" } }
            .collect { ids ->
                onPrefetchLineConnections(ids)
            }
    }

    // Ensure the selected line is prefetched even if it is not visible yet
    LaunchedEffect(primarySelectedLineId, lineConnections) {
        val id = primarySelectedLineId ?: return@LaunchedEffect
        val alreadyCached = lineConnections[id] != null
        if (!alreadyCached) {
            onPrefetchLineConnections(listOf(id))
        }
    }

    // Ensure the selected line is visible when selection changes (keyboard nav or explicit request).
    // Only scrolls if the line is outside the visible viewport.
    LaunchedEffect(scrollToLineTimestamp, primarySelectedLineId, topAnchorTimestamp, topAnchorLineId) {
        if (primarySelectedLineId == null) return@LaunchedEffect
        // Skip while this tab isn't the selected one (composed but not displayed): the initial
        // bring-into-view must not override the anchor restoration. isTabSelected is intentionally
        // NOT a key here, so becoming selected doesn't re-trigger a jump — real selection events
        // (new timestamp) still run.
        if (!isTabSelected) return@LaunchedEffect

        // Skip minimal bring-into-view when a top-anchoring request is active for this selection
        val isTopAnchorRequest =
            scrollToLineTimestamp != 0L && topAnchorTimestamp == scrollToLineTimestamp && topAnchorLineId == primarySelectedLineId
        if (isTopAnchorRequest) return@LaunchedEffect

        while (lazyPagingItems.loadState.refresh is LoadState.Loading) {
            delay(16.milliseconds)
        }

        val snapshot = lazyPagingItems.itemSnapshotList
        val index = snapshot.indices.firstOrNull { snapshot[it]?.id == primarySelectedLineId }
        if (index != null) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val viewportEnd = layoutInfo.viewportEndOffset
            val first = listState.firstVisibleItemIndex
            val itemInfo = visibleItems.firstOrNull { it.index == index }
            val isFullyVisible =
                isLineFullyVisible(itemInfo?.offset, itemInfo?.size, viewportEnd)
            if (!isFullyVisible) {
                if (index <= first) {
                    listState.scrollToItem(index, 0)
                } else {
                    // Place the item so its bottom aligns with the viewport bottom
                    listState.scrollToItem(index, 0)
                }
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
            delay(16.milliseconds)
        }

        // Helper to locate the target index in the current snapshot
        fun currentTargetIndex(): Int? {
            val snapshot = lazyPagingItems.itemSnapshotList
            return snapshot.indices.firstOrNull { snapshot[it]?.id == topAnchorLineId }
        }

        var targetIndex = currentTargetIndex()
        if (targetIndex == null) {
            withTimeoutOrNull(1500L.milliseconds) {
                snapshotFlow { lazyPagingItems.itemSnapshotList.items }
                    .mapNotNull { items ->
                        items.indices.firstOrNull {
                            items[it].id ==
                                topAnchorLineId
                        }
                    }.first()
                    .also { idx -> targetIndex = idx }
            }
        }

        targetIndex?.let { idx ->
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
        // On a cold open / session restore this positions the list and the contentAlpha reveal
        // plays once. A tab switch no longer re-runs this — the tab stays composed and the list
        // keeps its position.

        // Wait for initial page load to complete
        while (lazyPagingItems.loadState.refresh is LoadState.Loading) {
            delay(16.milliseconds)
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
                withTimeoutOrNull(1500L.milliseconds) {
                    snapshotFlow { lazyPagingItems.itemSnapshotList }
                        .mapNotNull { snapshot ->
                            snapshot.indices.firstOrNull {
                                snapshot[it]?.id ==
                                    anchorId
                            }
                        }.first()
                        .also { resolved -> idx = resolved }
                }
            }

            idx?.let { resolved ->
                listState.scrollToItem(resolved, scrollOffset.coerceAtLeast(0))
                hasRestored = true
                isInitialBookOpen = false
                restoredAnchorId = anchorId
                return@LaunchedEffect
            }
        }

        // Fallback to index/offset when no anchor or anchor not in snapshot
        if (scrollIndex > 0 || scrollOffset > 0) {
            val itemCount = lazyPagingItems.itemCount
            val targetIndex = scrollIndex.coerceIn(0, maxOf(0, itemCount - 1))
            val targetOffset = scrollOffset.coerceAtLeast(0)
            listState.scrollToItem(targetIndex, targetOffset)
            hasRestored = true
            isInitialBookOpen = false
            return@LaunchedEffect
        }

        hasRestored = true
        isInitialBookOpen = false
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

        // Tag the save with the book this list belongs to. `bookId` is captured from the
        // composition that created this list: the teardown save of an outgoing book (fired
        // while a new book is loading) carries the OLD id and is rejected by the ViewModel,
        // so it can never overwrite the new book's freshly-reset anchor.
        onScrollUpdated(bookId, stableAnchorId, stableAnchorIndex, data.scrollIndex, data.scrollOffset)
    }

    DisposableEffect(listState, lazyPagingItems) {
        onDispose { maybeSave(scrollData.value) }
    }

    LaunchedEffect(listState, lazyPagingItems) {
        // While scrolling, sample periodically so a close during an active scroll still restores closely.
        // Gated by isScrollInProgress so the `sample` ticker only runs during an active scroll —
        // otherwise its fixedPeriodTicker keeps the FlushCoroutineDispatcher waking up at 5 Hz forever
        // and the Compose scene re-renders every tick even at idle.
        launch {
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .flatMapLatest { inProgress ->
                    if (inProgress) {
                        snapshotFlow { scrollData.value }
                            .distinctUntilChanged()
                            .sample(200.milliseconds)
                    } else {
                        emptyFlow()
                    }
                }.catch { e -> debugln { "scroll-save flow failed: $e" } }
                .collect { data -> maybeSave(data) }
        }

        // When scrolling stops, immediately flush the latest value (avoids being a few lines behind).
        launch {
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .filter { inProgress -> !inProgress }
                .catch { e -> debugln { "scroll-stop flush flow failed: $e" } }
                .collect {
                    // Wait one frame so layoutInfo/visibleItemsInfo reflect the final settled position.
                    withFrameNanos { }
                    maybeSave(scrollData.value)
                }
        }
    }

    // Find-in-page UI state (scoped per tab)
    val showFind by appSettings.findBarOpenFlow(tabId).collectAsState()
    val persistedFindQuery by appSettings.findQueryFlow(tabId).collectAsState("")
    val smartModeEnabled by appSettings.findSmartModeFlow(tabId).collectAsState()
    val findFocusRequest by appSettings.findFocusRequestFlow(tabId).collectAsState()
    val findState = remember(tabId) { TextFieldState() }
    LaunchedEffect(persistedFindQuery) { syncFindField(findState, persistedFindQuery) }

    // Smart find = embedding-based (vs simple mode which matches literal words): for the typed
    // query we fetch the lines of THIS book closest in meaning (dense KNN over the index) and the
    // passage to highlight in each. Per-line highlight reads from this map; navigation jumps
    // between its lines. Computed off-main; empty when dense search is unavailable.
    val appGraph = LocalAppGraph.current
    // Tagged with its query: until the new query's lines arrive, the previous ones must not pass for them
    val semanticFind by androidx.compose.runtime.produceState(
        SemanticFind("", false, -1, emptySet()),
        smartModeEnabled,
        persistedFindQuery,
        bookId,
    ) {
        val query = persistedFindQuery
        val ids =
            if (!smartModeEnabled || query.length < 2) {
                emptySet()
            } else {
                withContext(kotlinx.coroutines.Dispatchers.Default) {
                    try {
                        appGraph.searchEngine.semanticFind(query, bookId, SMART_FIND_LIMIT).toSet()
                    } catch (c: kotlinx.coroutines.CancellationException) {
                        throw c // composition left / keys changed — not a real failure
                    } catch (_: Throwable) {
                        emptySet()
                    }
                }
            }
        value = SemanticFind(query, smartModeEnabled, bookId, ids)
    }
    val semanticFindIds = semanticFind.takeIf { it.isFor(persistedFindQuery, bookId) }?.ids ?: emptySet()

    // Matches across the whole book, not only its loaded pages (#451): Lucene candidates
    // confirmed off-main; smart mode wraps its semantic lines. Navigation and count read from it.
    val findResults by androidx.compose.runtime.produceState(
        FindResults.None,
        showFind,
        smartModeEnabled,
        persistedFindQuery,
        semanticFind,
        bookId,
    ) {
        val query = persistedFindQuery
        val smart = smartModeEnabled
        if (query.length < 2) {
            // Untagged: nothing to show nor to narrow a next query with
            value = FindResults.None
            return@produceState
        }
        // Kept while hidden: reopening the bar on the same query reuses them
        if (!showFind) return@produceState
        // Smart mode waits for this query's semantic lines; the previous results stay untagged for it
        val semanticIds = if (smart) semanticFind.takeIf { it.isFor(query, bookId) }?.ids ?: return@produceState else null
        val previous = value.takeIf { !smart && !it.smart && it.bookId == bookId }
        // Same query once normalized (a trailing space, nikud): same matches
        if (previous != null && normalizeQueryForHebrew(previous.query) == normalizeQueryForHebrew(query)) {
            value = FindResults(query, false, bookId, previous.matches)
            return@produceState
        }
        if (!smart) delay(FIND_DEBOUNCE) // let the typing settle before querying the index
        // Typing on: only the lines that held the previous query can hold this one
        val narrowing = previous?.takeIf { queryNarrows(query, it.query) }?.matches
        runSuspendCatching {
            if (semanticIds != null) {
                BookFindMatches.ofLines(appGraph.repository.getLinesByIds(semanticIds))
            } else {
                findInBook(
                    searchEngine = appGraph.searchEngine,
                    loadLines = appGraph.repository::getLinesByIds,
                    loadRange = { start, end -> appGraph.repository.getLines(bookId, start, end) },
                    bookId = bookId,
                    query = query,
                    narrowing = narrowing,
                )
            }
        }.onSuccess { value = FindResults(query, smart, bookId, it) }
            .onFailure { e -> debugln { "find-in-book failed: $e" } }
    }

    var currentMatch by remember(bookId) { mutableStateOf<BookFindMatches.Match?>(null) }
    // The current match while it belongs to the results shown: one kept from older results isn't
    val liveMatch = currentMatch?.takeIf { it in findResults.matches }
    val findMatchLocator = remember { FindMatchLocator() }
    val findNavigation = remember { FindNavigation() }
    // A new query or mode starts over from the viewport, and so does a session reopened
    LaunchedEffect(persistedFindQuery, smartModeEnabled) { currentMatch = null }
    LaunchedEffect(showFind) { if (!showFind) currentMatch = null }

    // Theme-derived inputs to the HTML annotation, hoisted here so the off-screen prefetcher
    // can build keys/annotations identical to those produced inside LineItem.
    val isDarkTheme = JewelTheme.isDark
    val footnoteMarkerColor = JewelTheme.globalColors.outlines.focused
    val prefetchImageBuilder =
        remember(isDarkTheme) {
            SkiaHtmlImageBuilder.build { if (isDarkTheme) SkiaHtmlImageBuilder.InvertColorFilter else null }
        }

    // Backed by a ConcurrentHashMap: the prefetcher writes from EfficiencyCoreDispatcher while
    // LineItem reads during composition on the main thread.
    val stableAnnotatedCache =
        remember(bookId, textSize, boldScaleForPlatform, diacritics) {
            StableAnnotatedCache()
        }

    // Warm the annotation cache for lines just outside the viewport so they render straight from
    // cache (no async placeholder flash, no height reflow) by the time they scroll into view.
    // conflate() (not collectLatest) keeps the latest viewport range without cancelling an
    // in-flight build batch — so fast scrolling still makes forward progress on parsing.
    LaunchedEffect(
        listState,
        lazyPagingItems,
        stableAnnotatedCache,
        prefetchImageBuilder,
        textSize,
        boldScaleForPlatform,
        diacritics,
        footnoteMarkerColor,
        isDarkTheme,
    ) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                IntRange.EMPTY
            } else {
                (visible.first().index - HTML_PREFETCH_BEHIND)..(visible.last().index + HTML_PREFETCH_AHEAD)
            }
        }.distinctUntilChanged()
            .conflate()
            .catch { e -> debugln { "annotation-prefetch flow failed: $e" } }
            .collect { range ->
                if (range.isEmpty()) return@collect
                val count = lazyPagingItems.itemCount
                // Snapshot candidate lines on the main thread; peek() never triggers a page load.
                val lines = range.mapNotNull { i -> if (i in 0 until count) lazyPagingItems.peek(i) else null }
                if (lines.isEmpty()) return@collect
                // Off-screen warm-up: run on efficiency cores so it never competes with the UI
                // or scroll on performance cores. The user isn't waiting on this work.
                withContext(EfficiencyCoreDispatcher) {
                    for (line in lines) {
                        ensureActive()
                        val processed =
                            diacritics.apply(line.content)
                        val key =
                            htmlAnnotationCacheKey(
                                lineId = line.id,
                                processedContent = processed,
                                baseTextSize = textSize,
                                boldScale = boldScaleForPlatform,
                                footnoteMarkerColor = footnoteMarkerColor,
                                invertImages = isDarkTheme,
                            )
                        if (stableAnnotatedCache.get(key) != null) continue
                        stableAnnotatedCache.put(
                            key,
                            buildLineAnnotation(
                                html = processed,
                                baseTextSize = textSize,
                                boldScale = boldScaleForPlatform,
                                footnoteMarkerColor = footnoteMarkerColor,
                                imageContentBuilder = prefetchImageBuilder,
                            ),
                        )
                    }
                }
            }
    }

    // Navigate to next/previous line containing the query (wrap-around)
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current

    /**
     * Centers the current match in the viewport, as browsers do: the match itself, not its line,
     * which may be taller than the screen. With [onlyIfHidden], leaves a match already readable
     * where it is. Returns false while its line isn't laid out.
     */
    suspend fun revealFindMatch(
        lineId: Long,
        onlyIfHidden: Boolean,
    ): Boolean {
        val top = findMatchLocator.matchTop(lineId) ?: return false
        val viewport = listState.layoutInfo.viewportSize.height
        val readable = with(density) { FIND_BAR_RESERVE.toPx() }..(viewport - with(density) { FIND_BOTTOM_RESERVE.toPx() })
        if (onlyIfHidden && top in readable) return true
        if (onlyIfHidden && top !in -viewport.toFloat()..viewport * 2f) return false // far off: jump instead
        listState.scrollBy(top - viewport / 2f)
        return true
    }

    /** The list index of the book line [lineIndex], rebuilding the pager around it when it isn't loaded. */
    suspend fun loadLine(lineIndex: Int): Int? {
        // Loaded lines are sorted by lineIndex: binary search, as the scrollbar does
        fun loadedIndex() = lazyPagingItems.itemSnapshotList.items.binarySearchBy(lineIndex) { it.lineIndex }
        return loadedIndex().takeIf { it >= 0 }
            ?: run {
                onEvent(BookContentEvent.ContentScrollToLineIndex(lineIndex))
                withTimeoutOrNull(FIND_JUMP_TIMEOUT) { snapshotFlow { loadedIndex() }.first { it >= 0 } }
            }
    }

    fun navigateToMatch(
        next: Boolean,
        @StructuredScope scope: CoroutineScope,
    ) {
        // One navigation at a time: a held Enter must not pile up scrolls and pager rebuilds
        findNavigation.job?.cancel()
        findNavigation.job =
            scope.launch {
                // The matches of the query as typed: Enter or Ctrl+G may come before they are ready
                val matches =
                    withTimeoutOrNull(FIND_RESULTS_TIMEOUT) {
                        snapshotFlow {
                            findResults
                                .takeIf {
                                    showFind &&
                                        it.query == persistedFindQuery &&
                                        (it.query.isEmpty() || (it.smart == smartModeEnabled && it.bookId == bookId))
                                }?.matches
                        }.filterNotNull().first()
                    } ?: return@launch
                // Match by match from the current one, else from the viewport top (included when going forward)
                val match =
                    currentMatch?.takeIf { it in matches }?.let { matches.next(it, forward = next) }
                        ?: listState.firstVisibleItemIndex
                            .takeIf { it < lazyPagingItems.itemCount }
                            ?.let { lazyPagingItems.peek(it)?.lineIndex }
                            ?.let { matches.nextAfterLine(if (next) it - 1 else it, forward = next) }
                        ?: return@launch
                currentMatch = match

                // A match already on screen moves only if hidden by the find bar or at the very bottom
                awaitNextLayout() // let its line recompose and publish where the match sits
                if (revealFindMatch(match.lineId, onlyIfHidden = true)) return@launch
                listState.scrollToItem(loadLine(match.lineIndex) ?: return@launch)
                // The line's annotation may still be building off-main: retry until it's laid out
                withTimeoutOrNull(FIND_JUMP_TIMEOUT) {
                    do awaitNextLayout() while (!revealFindMatch(match.lineId, onlyIfHidden = false))
                }
            }
    }

    // Ctrl+Enter, as in Chromium: ends the find session acting on the current match, here by
    // selecting its line as a click would
    fun activateCurrentMatch() {
        appSettings.closeFindBar(tabId)
        val match = currentMatch ?: return
        lazyPagingItems.itemSnapshotList.items
            .firstOrNull { it.id == match.lineId }
            ?.let { onLineSelect(it, false) }
    }

    // Ctrl/Cmd+G and F3 come from the window's shortcuts
    val navigateToMatchLatest by rememberUpdatedState<(Boolean) -> Unit> { navigateToMatch(it, scope) }
    LaunchedEffect(tabId) {
        appSettings.findStepRequests(tabId).collect { forward -> navigateToMatchLatest(forward) }
    }

    // Ctrl+Home / Ctrl+End: first or last line of the whole book, loaded or not. The line count
    // arrives after the first composition, which the remembered key handler would otherwise keep.
    val bookLineCount by rememberUpdatedState(bookCharCounts?.size)

    fun scrollToBookEdge(
        end: Boolean,
        @StructuredScope scope: CoroutineScope,
    ) {
        val lineIndex = if (end) (bookLineCount ?: return) - 1 else 0
        scope.launch {
            val index = loadLine(lineIndex) ?: return@launch
            listState.scrollToItem(index)
            // The last line may be taller than the viewport: down to its very end
            if (end) {
                val info = listState.layoutInfo
                info.visibleItemsInfo.firstOrNull { it.index == index }?.let {
                    val overflow = it.offset + it.size - info.viewportEndOffset
                    if (overflow > 0) listState.scrollBy(overflow.toFloat())
                }
            }
        }
    }

    fun scrollByPage(
        forward: Boolean,
        @StructuredScope scope: CoroutineScope,
    ) {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return
        val targetIndex =
            computePageScrollTargetIndex(
                forward = forward,
                visibleItemIndices = visibleItems.map { it.index },
                visibleItemEndOffsets = visibleItems.associate { it.index to (it.offset + it.size) },
                viewportEnd = listState.layoutInfo.viewportEndOffset,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
            ) ?: return
        scope.launch { listState.animateScrollToItem(targetIndex, 0) }
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

                        Key.PageUp -> {
                            scrollByPage(forward = false, scope)
                            true
                        }

                        Key.PageDown -> {
                            scrollByPage(forward = true, scope)
                            true
                        }

                        // Also from the find field, which Chromium forwards them from
                        Key.MoveHome, Key.MoveEnd -> {
                            if (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) {
                                scrollToBookEdge(end = keyEvent.key == Key.MoveEnd, scope)
                                true
                            } else {
                                false
                            }
                        }

                        Key.Escape -> {
                            if (showFind) {
                                appSettings.closeFindBar(tabId)
                                true
                            } else {
                                false
                            }
                        }

                        else -> {
                            false
                        }
                    }
                } else {
                    false
                }
            }
        }

    // Request initial focus so arrow keys work as soon as the view appears
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(bookId) { focusRequester.requestFocus() }
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) focusRequester.requestFocus()
    }
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

    SafeSelectionContainer(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha } // Hide until positioned to prevent glitch
                .then(zoom.modifier)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent(previewKeyHandler)
                .focusable(),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(end = 16.dp)
                        .onGloballyPositioned { findMatchLocator.list = it },
            ) {
                items(
                    count = lazyPagingItems.itemCount,
                    key = lazyPagingItems.itemKey { it.id },
                    contentType = { "line" }, // Optimization: specify content type
                ) { index ->
                    val line = lazyPagingItems[index]

                    if (line != null) {
                        // Smart find: the line is highlighted as a search result is (see LineItem)
                        val isSmartMatch = showFind && smartModeEnabled && line.id in semanticFindIds
                        val altHeadings = altHeadingsByLineId[line.id]
                        val isCurrentSelected = line.id in selectedLineIds
                        val useThickBar = shouldUseThickBar(line.id, primarySelectedLineId, isTocEntrySelection)
                        val nextLineId = if (index < lazyPagingItems.itemCount - 1) lazyPagingItems.peek(index + 1)?.id else null
                        val nextUseThickBar = shouldUseThickBar(nextLineId ?: -1, primarySelectedLineId, isTocEntrySelection)
                        val isNextSelected =
                            shouldExtendToNext(isCurrentSelected, nextLineId, selectedLineIds, useThickBar, nextUseThickBar)

                        val prevLineId =
                            if (index > 0) lazyPagingItems.peek(index - 1)?.id else null
                        val isPrevSelected =
                            prevLineId != null && prevLineId in selectedLineIds
                        // Alt headings go inside the selection bar only when
                        // the previous line is also selected (consecutive selection).
                        val altHeadingsInsideBar =
                            shouldPlaceAltHeadingsInsideBar(isCurrentSelected, isPrevSelected, altHeadings.isNotEmpty())

                        val borderColor =
                            if (isCurrentSelected) {
                                if (useThickBar) {
                                    JewelTheme.globalColors.outlines.focused
                                } else {
                                    JewelTheme.globalColors.borders.normal
                                }
                            } else {
                                Color.Transparent
                            }

                        // Alt headings outside the selection bar when line is
                        // selected alone or is the first in a consecutive selection
                        if (altHeadings.isNotEmpty() && !altHeadingsInsideBar) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp).padding(start = 12.dp),
                            ) {
                                altHeadings.forEach { entry ->
                                    AltHeadingItem(
                                        entryId = entry.id,
                                        level = entry.level,
                                        text = entry.text,
                                        onClick = { onLineSelect(line, false) },
                                        baseTextSize = textSize,
                                    )
                                }
                            }
                        }

                        val isMarked = markedLines?.contains(line.lineIndex) == true
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(if (isMarked) Modifier.background(markedTint()) else Modifier)
                                    .padding(horizontal = 8.dp)
                                    .selectionBar(
                                        isSelected = isCurrentSelected,
                                        isNextSelected = isNextSelected,
                                        color = borderColor,
                                        isPrimary = useThickBar,
                                    ),
                        ) {
                            Spacer(modifier = Modifier.width(SelectionBarWidth + 8.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                if (altHeadingsInsideBar) {
                                    Column {
                                        altHeadings.forEach { entry ->
                                            AltHeadingItem(
                                                entryId = entry.id,
                                                level = entry.level,
                                                text = entry.text,
                                                onClick = { onLineSelect(line, false) },
                                                baseTextSize = textSize,
                                            )
                                        }
                                    }
                                }
                                Box(modifier = Modifier.padding(vertical = LineItemVerticalPaddingPerSide)) {
                                    // Stable per-line list: unhighlighted lines get the emptyList()
                                    // singleton, so only lines whose highlights changed recompose.
                                    val lineHighlights = highlightsByLine[line.id] ?: emptyList()
                                    val lineNotes = notesByLine[line.id] ?: emptyList()
                                    LineItem(
                                        lineId = line.id,
                                        lineContent = line.content,
                                        userHighlights = lineHighlights,
                                        userNotes = lineNotes,
                                        fontFamily = hebrewFontFamily,
                                        onClick = { isModifier -> onLineSelect(line, isModifier) },
                                        onHover = { onLineHover(line.id) },
                                        isSelected = isCurrentSelected,
                                        isPrimary = useThickBar,
                                        baseTextSize = textSize,
                                        lineHeight = lineHeight,
                                        boldScale = boldScaleForPlatform,
                                        // Simple mode: the literal query; smart mode: the search's highlighting
                                        highlightQuery = findState.text.toString().takeIf { showFind && !smartModeEnabled },
                                        smartQuery = persistedFindQuery.takeIf { isSmartMatch && it.length >= 2 },
                                        currentMatchOrdinal =
                                            liveMatch?.takeIf { showFind && it.lineId == line.id }?.ordinal,
                                        findMatchLocator =
                                            findMatchLocator.takeIf { showFind && liveMatch?.lineId == line.id },
                                        annotatedCache = stableAnnotatedCache,
                                        diacritics = diacritics,
                                        onLayoutWidthMeasure = { width ->
                                            if (textLayoutWidthPx == 0 && width > 0) {
                                                textLayoutWidthPx = width
                                            }
                                        },
                                        onContextClick = {
                                            selectionContext.setCurrentLineId(line.id)
                                            // Drop any stale comments-pane anchor so a main-pane
                                            // highlight never lands on a commentary line.
                                            selectionContext.setActiveCommentaryColumn(emptyList())
                                        },
                                    )
                                }
                                // The verse read again, then its targum (headings have no reference)
                                if (shnayimMikra && !line.heRef.isNullOrBlank()) {
                                    Box(modifier = Modifier.padding(vertical = LineItemVerticalPaddingPerSide)) {
                                        LineItem(
                                            lineId = line.id,
                                            lineContent = line.content,
                                            userHighlights = highlightsByLine[line.id] ?: emptyList(),
                                            userNotes = notesByLine[line.id] ?: emptyList(),
                                            fontFamily = hebrewFontFamily,
                                            onClick = { isModifier -> onLineSelect(line, isModifier) },
                                            onHover = { onLineHover(line.id) },
                                            isSelected = isCurrentSelected,
                                            isPrimary = useThickBar,
                                            baseTextSize = textSize,
                                            lineHeight = lineHeight,
                                            boldScale = boldScaleForPlatform,
                                            annotatedCache = stableAnnotatedCache,
                                            diacritics = diacritics,
                                            // The context menu acts on this verse, as from its first reading
                                            onContextClick = {
                                                selectionContext.setCurrentLineId(line.id)
                                                selectionContext.setActiveCommentaryColumn(emptyList())
                                            },
                                        )
                                    }
                                    shnayimMikraTargum.targumOf(line.heRef)?.let {
                                        ShnayimMikraTargum(it, targumFontFamily, textSize, lineHeight, diacritics)
                                    }
                                }
                                if (markedLines?.last == line.lineIndex) MarkedEnd(if (shnayimMikra) "סוף הקריאה" else "סוף הלימוד")
                            }
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

            // Content-aware scrollbar overlay. Lives inside the same Box as the LazyColumn
            // so it floats over the 16dp end gutter reserved by the column padding.
            //
            // All three pixel-space inputs are deterministic and exact: `capacity` is
            // measured by `TextMeasurer` against the captured text-layout width, the
            // font settings and font family — so it never drifts during scroll. The
            // scrollbar itself no longer averages visible items' sizes: that was the
            // root cause of the thumb resizing, since per-item padding and item-mix
            // variation broke the `Σ size / Σ lineCount` assumption.
            val textMeasurer = rememberTextMeasurer()
            val lineHeightPx = with(density) { (textSize * lineHeight).sp.toPx() }
            val paddingPerItemPx = with(density) { (LineItemVerticalPaddingPerSide * 2).toPx() }
            val capacity by remember(textLayoutWidthPx, textSize, lineHeight, hebrewFontFamily) {
                derivedStateOf {
                    if (textLayoutWidthPx <= 0) {
                        0
                    } else {
                        val result =
                            textMeasurer.measure(
                                text = AnnotatedString(CAPACITY_REFERENCE),
                                style =
                                    TextStyle(
                                        fontSize = textSize.sp,
                                        fontFamily = hebrewFontFamily,
                                        lineHeight = (textSize * lineHeight).sp,
                                    ),
                                constraints = Constraints(maxWidth = textLayoutWidthPx),
                            )
                        (CAPACITY_REFERENCE.length / result.lineCount.coerceAtLeast(1))
                            .coerceAtLeast(1)
                    }
                }
            }
            ContentScrollbar(
                listState = listState,
                lazyPagingItems = lazyPagingItems,
                bookCharCounts = bookCharCounts,
                capacity = capacity,
                lineHeightPx = lineHeightPx,
                paddingPerItemPx = paddingPerItemPx,
                onScrollToLineIndex = { idx -> onEvent(BookContentEvent.ContentScrollToLineIndex(idx)) },
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
            )
        }

        // Find-in-page bar overlay, top end as in browsers
        if (showFind) {
            // Counter only once the results are those of the query as typed (none while computing)
            val results =
                findResults.takeIf {
                    persistedFindQuery.length >= 2 && it.query == persistedFindQuery && it.smart == smartModeEnabled && it.bookId == bookId
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .zIndex(2f),
                contentAlignment = Alignment.TopEnd,
            ) {
                FindInPageBar(
                    state = findState,
                    onEnterNext = { navigateToMatch(true, scope) },
                    onEnterPrev = { navigateToMatch(false, scope) },
                    onClose = { appSettings.closeFindBar(tabId) },
                    onActivate = ::activateCurrentMatch,
                    focusRequest = findFocusRequest,
                    matchCount = results?.matches?.occurrences,
                    matchPosition = liveMatch?.position,
                    smartModeEnabled = smartModeEnabled,
                    // Also while on without the addon (set by opening a search result), so it can be turned off
                    onToggleSmartMode =
                        { appSettings.toggleFindSmartMode(tabId) }.takeIf { installedSemanticSearch != null || smartModeEnabled },
                )
                LaunchedEffect(findState.text, showFind) {
                    val q = findState.text.toString()
                    appSettings.setFindQuery(tabId, if (q.length >= 2) q else "")
                }
            }
        }
    }
}

// Per-side vertical padding applied by the `LineItem`'s wrapper Box (see the
// `Box(modifier = Modifier.padding(vertical = LineItemVerticalPaddingPerSide))`
// usage above). Exposed so the scrollbar can derive the exact per-item padding
// contribution as `2 × LineItemVerticalPaddingPerSide`. Single source of truth:
// changing this value updates both the layout and the scrollbar metrics.
internal val LineItemVerticalPaddingPerSide = 8.dp

// How many lines to pre-parse on either side of the viewport. Forward gets a larger buffer
// since scrolling down is the dominant direction in a reading app.
private const val HTML_PREFETCH_AHEAD = 16
private const val HTML_PREFETCH_BEHIND = 8

// Smart (embedding) find: max semantically-closest lines fetched per query for the current book.
private const val SMART_FIND_LIMIT = 60

// Literal find: pause after the last keystroke before querying the index
private val FIND_DEBOUNCE = 150.milliseconds

// Longest wait for the matches of a query just typed before stepping to the next one
private val FIND_RESULTS_TIMEOUT = 30_000.milliseconds

/** The matches computed for [query] in [smart] mode, so navigation can wait for those of the query as typed. */
private class FindResults(
    val query: String,
    val smart: Boolean,
    val bookId: Long,
    val matches: BookFindMatches,
) {
    companion object {
        val None = FindResults("", false, -1, BookFindMatches.Empty)
    }
}

/** The semantic lines found for [query] in [bookId]; empty when computed outside [smart] mode. */
private class SemanticFind(
    val query: String,
    val smart: Boolean,
    val bookId: Long,
    val ids: Set<Long>,
) {
    fun isFor(
        query: String,
        bookId: Long,
    ) = smart && this.query == query && this.bookId == bookId
}

/** The running find-in-page navigation, cancelled by the next one. */
private class FindNavigation {
    var job: Job? = null
}

// Longest wait for the pager rebuilt around a far match before giving up on scrolling to it
private val FIND_JUMP_TIMEOUT = 3000.milliseconds

// A frame callback runs before that frame's layout: the second one follows a completed layout
private suspend fun awaitNextLayout() = repeat(2) { withFrameNanos { } }

// Find bar overlaying the top of the text: a match under it isn't readable
private val FIND_BAR_RESERVE = 64.dp

// Bottom margin under which a match counts as hidden
private val FIND_BOTTOM_RESERVE = 48.dp

/**
 * Where the current find-in-page match sits, for the list to scroll it into view. Published by the
 * line showing it on layout and read on demand after a scroll: plain fields, not state, so
 * publishing never recomposes.
 */
private class FindMatchLocator {
    var list: LayoutCoordinates? = null
    var matchStart: Int = 0
    private var lineId: Long = -1
    private var text: LayoutCoordinates? = null
    private var layout: TextLayoutResult? = null

    fun publishText(
        lineId: Long,
        coordinates: LayoutCoordinates,
    ) {
        if (this.lineId != lineId) layout = null
        this.lineId = lineId
        text = coordinates
    }

    /** Drops what [lineId] published: its node may be reused for another line. */
    fun forget(lineId: Long) {
        if (this.lineId != lineId) return
        text = null
        layout = null
    }

    fun publishLayout(
        lineId: Long,
        result: TextLayoutResult,
    ) {
        if (this.lineId != lineId) text = null
        this.lineId = lineId
        layout = result
    }

    /** Top of the match in [lineId], in the list's viewport; null until that line is laid out. */
    fun matchTop(lineId: Long): Float? {
        if (this.lineId != lineId) return null
        val list = list?.takeIf { it.isAttached } ?: return null
        val text = text?.takeIf { it.isAttached } ?: return null
        val layout = layout ?: return null
        val length = layout.layoutInput.text.length
        val top = if (length == 0) 0f else layout.getBoundingBox(matchStart.coerceIn(0, length - 1)).top
        return list.localPositionOf(text, Offset(0f, top)).y
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

@Composable
private fun AltHeadingItem(
    entryId: Long,
    level: Int,
    text: String,
    onClick: () -> Unit,
    baseTextSize: Float = 16f,
) {
    val fontSize =
        when (level) {
            0 -> (baseTextSize * 1.25f).sp
            1 -> (baseTextSize * 1.125f).sp
            else -> baseTextSize.sp
        }
    val paddingTop = if (level == 0) 4.dp else 0.dp
    val paddingBottom = 4.dp

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = paddingTop, bottom = paddingBottom)
                .pointerInput(entryId) {
                    detectTapGestures(onTap = { onClick() })
                },
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun LineItem(
    lineId: Long,
    lineContent: String,
    fontFamily: FontFamily,
    onClick: (isModifierPressed: Boolean) -> Unit,
    onHover: () -> Unit = {},
    isSelected: Boolean = false,
    isPrimary: Boolean = false,
    baseTextSize: Float = 16f,
    lineHeight: Float = 1.5f,
    boldScale: Float = 1.0f,
    highlightQuery: String? = null,
    // Smart find: the line highlighted for this query as a find by meaning (its passage closest in meaning, and the
    // query's words), on the text as displayed
    smartQuery: String? = null,
    currentMatchOrdinal: Int? = null,
    // Given to the line showing the current match only, which publishes where the match sits
    findMatchLocator: FindMatchLocator? = null,
    annotatedCache: StableAnnotatedCache? = null,
    diacritics: DiacriticsMode = DiacriticsMode.All,
    userHighlights: List<UserHighlight> = emptyList(),
    userNotes: List<UserNote> = emptyList(),
    onLayoutWidthMeasure: (Int) -> Unit = {},
    onContextClick: () -> Unit = {},
) {
    // Process content: remove the diacritics the current mode hides
    val processedContent = remember(lineContent, diacritics) { diacritics.apply(lineContent) }

    // Footnote marker color from theme
    val footnoteMarkerColor = JewelTheme.globalColors.outlines.focused

    // Theme-aware color filter for embedded images: invert black-on-white glyphs in dark mode.
    val isDarkTheme = JewelTheme.isDark
    val imageColorFilter: @Composable () -> ColorFilter? =
        remember(isDarkTheme) {
            { if (isDarkTheme) SkiaHtmlImageBuilder.InvertColorFilter else null }
        }

    val textModifier =
        remember(lineId) {
            Modifier.fillMaxWidth()
        }.pointerInput(lineId) {
            awaitEachGesture {
                // Wait for press and capture keyboard modifiers from the event.
                // Mouse reports a primary button; touch contacts have an empty
                // button mask (PointerType.Touch, like iOS), so accept either.
                val downEvent = awaitPointerEvent(PointerEventPass.Main)
                val isTouchDown = downEvent.changes.any { it.type == PointerType.Touch && it.pressed }
                if (!downEvent.buttons.isPrimaryPressed && !isTouchDown) return@awaitEachGesture
                val isModifier = downEvent.keyboardModifiers.isCtrlPressed || downEvent.keyboardModifiers.isMetaPressed
                // Wait for release
                val up = waitForUpOrCancellation()
                if (up != null && !up.isConsumed) {
                    onClick(isModifier)
                }
            }
        }.onPointerEvent(PointerEventType.Enter) { onHover() }
            .onPointerEvent(PointerEventType.Press) { event ->
                // Record this line as the right-click target so the context menu can offer a
                // "copy link to this line" action even when no text is selected.
                if (event.buttons.isSecondaryPressed) onContextClick()
            }

    val localAnnotatedCache = remember { StableAnnotatedCache() }
    val annotationCache = annotatedCache ?: localAnnotatedCache
    val annotationCacheKey =
        remember(lineId, processedContent, baseTextSize, boldScale, footnoteMarkerColor, isDarkTheme) {
            htmlAnnotationCacheKey(
                lineId = lineId,
                processedContent = processedContent,
                baseTextSize = baseTextSize,
                boldScale = boldScale,
                footnoteMarkerColor = footnoteMarkerColor,
                invertImages = isDarkTheme,
            )
        }

    val lineAnnotation =
        rememberAsyncHtmlAnnotation(
            cacheKey = annotationCacheKey,
            html = processedContent,
            baseTextSize = baseTextSize,
            boldScale = boldScale,
            footnoteMarkerColor = footnoteMarkerColor,
            imageColorFilter = imageColorFilter,
            annotatedCache = annotationCache,
        )

    if (E2e.enabled) SideEffect { E2eReader.lineDrawn(lineId, ready = lineAnnotation != null) }
    if (lineAnnotation == null) {
        Text(
            text = htmlAnnotationPlaceholderText(processedContent.length),
            textAlign = TextAlign.Justify,
            fontFamily = fontFamily,
            fontSize = baseTextSize.sp,
            lineHeight = (baseTextSize * lineHeight).sp,
            modifier = textModifier,
            onTextLayout = { result ->
                val cw = result.layoutInput.constraints.maxWidth
                if (cw > 0 && cw != Int.MAX_VALUE) onLayoutWidthMeasure(cw)
            },
        )
        return
    }

    val annotated = lineAnnotation.annotated
    val inlineImageContent = lineAnnotation.inlineContent

    // Plain text of the line WITH diacritics: user-highlight offsets are stored against this
    // representation, so it is needed to remap them when diacritics are hidden. The text is
    // independent of font size, so [lineContent] alone is a safe cache key.
    // Only needed to remap highlights or notes when diacritics are hidden: skip a second parse otherwise.
    val needsOriginalText = diacritics != DiacriticsMode.All && (userHighlights.isNotEmpty() || userNotes.isNotEmpty())
    val originalPlainText =
        remember(lineContent, needsOriginalText) {
            if (needsOriginalText) buildAnnotatedFromHtml(lineContent, baseTextSize, boldScale = 1f).text else null
        }

    val searchEngine = LocalAppGraph.current.searchEngine
    val smartRanges by produceState<List<IntRange>?>(null, annotated.text, smartQuery) {
        value =
            smartQuery?.let { query ->
                runSuspendCatching {
                    searchEngine
                        .highlights(
                            listOf(annotated.text),
                            query,
                            alwaysByMeaning = true,
                        ).single()
                        .ranges
                }.getOrNull()
            }
    }

    // Build highlighted text when a query is active (>= 2 chars)
    val baseHl =
        JewelTheme.globalColors.outlines.focused
            .copy(alpha = 0.22f)
    val currentHl = CurrentFindMatchColor
    val displayText: AnnotatedString =
        remember(
            annotated,
            highlightQuery,
            currentMatchOrdinal,
            smartRanges,
            baseHl,
            currentHl,
            userHighlights,
            diacritics,
            originalPlainText,
        ) {
            // User highlights first, then search highlights on top. Note markers are drawn
            // separately (dotted underline) in drawBehind, not baked into the AnnotatedString.
            val withUserHighlights =
                applyUserHighlights(
                    annotated = annotated,
                    highlights = userHighlights,
                    originalText = originalPlainText,
                    diacritics = diacritics,
                )
            val ranges = smartRanges
            if (ranges != null) {
                // The line is one smart match: all its spans take the current color when it is the current one
                withUserHighlights.withBackground(ranges, if (currentMatchOrdinal != null) currentHl else baseHl)
            } else {
                io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotatedWithCurrent(
                    annotated = withUserHighlights,
                    query = highlightQuery,
                    currentIndex = currentMatchOrdinal,
                    baseColor = baseHl,
                    currentColor = currentHl,
                )
            }
        }

    // Dotted grey underline marking the noted ranges (drawn from the text layout so it supports
    // wrapping and RTL). Offsets are remapped to the displayed text when diacritics are hidden.
    val noteRanges =
        remember(userNotes, originalPlainText, diacritics, displayText) {
            noteDisplayRanges(userNotes, originalPlainText, diacritics, displayText.length)
        }
    val noteUnderlineColor = JewelTheme.globalColors.text.info
    var noteLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    if (findMatchLocator != null) {
        val matchStart =
            remember(displayText, highlightQuery, smartRanges, currentMatchOrdinal) {
                (smartRanges ?: highlightQuery?.let { findAllMatchesOriginal(displayText.text, it) })
                    ?.getOrNull(if (smartRanges != null) 0 else currentMatchOrdinal ?: 0)
                    ?.first ?: 0
            }
        SideEffect { findMatchLocator.matchStart = matchStart }
        DisposableEffect(findMatchLocator, lineId) { onDispose { findMatchLocator.forget(lineId) } }
    }

    Text(
        text = displayText,
        textAlign = TextAlign.Justify,
        fontFamily = fontFamily,
        lineHeight = (baseTextSize * lineHeight).sp,
        modifier =
            textModifier
                .drawBehind {
                    noteLayout?.let { drawNoteUnderlines(it, noteRanges, noteUnderlineColor) }
                }.then(
                    if (findMatchLocator != null) {
                        Modifier.onGloballyPositioned { findMatchLocator.publishText(lineId, it) }
                    } else {
                        Modifier
                    },
                ),
        inlineContent = inlineImageContent,
        onTextLayout = { result ->
            noteLayout = result
            findMatchLocator?.publishLayout(lineId, result)
            val cw = result.layoutInput.constraints.maxWidth
            if (cw > 0 && cw != Int.MAX_VALUE) onLayoutWidthMeasure(cw)
        },
    )
}

// The selection bar's slot at the start of a line: the same whether the bar is thick or thin, so content never shifts.
private val SelectionBarWidth = 4.dp

/**
 * Draws a line's selection bar in the slot at its start, the line's full height, reaching down to the next line
 * when it is selected too. Drawn rather than laid out: a bar child sized to its line needed the line's intrinsic
 * height, laying its text out twice.
 */
private fun Modifier.selectionBar(
    isSelected: Boolean,
    isNextSelected: Boolean,
    color: Color,
    isPrimary: Boolean,
): Modifier =
    drawBehind {
        if (!isSelected) return@drawBehind
        val slot = SelectionBarWidth.toPx()
        val w = (if (isPrimary) 4.dp else 2.dp).toPx()
        val inSlot = (slot - w) / 2f
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - slot + inSlot else inSlot
        val extraBottom = if (isNextSelected) 8.dp.toPx() else 0f
        drawRect(color = color, topLeft = Offset(x, 0f), size = Size(w, size.height + extraBottom))
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

@Composable
private fun markedTint() =
    JewelTheme.globalColors.outlines.focused
        .copy(alpha = 0.07f)

/** Where a marked passage ends: [label] between two rules, "סוף הלימוד" or, for an aliya read, "סוף הקריאה". */
@Composable
private fun MarkedEnd(label: String) {
    val color = JewelTheme.globalColors.outlines.focused
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.5f)))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.5f)))
    }
}
