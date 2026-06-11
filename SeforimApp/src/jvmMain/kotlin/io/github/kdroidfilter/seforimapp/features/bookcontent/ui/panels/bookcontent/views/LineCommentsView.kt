package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.kdroidfilter.seforim.htmlparser.SkiaHtmlImageBuilder
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.annotations.UserHighlight
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.presentation.text.applyUserHighlights
import io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotated
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.selection.CommentaryLineRef
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorGroup
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.CommentatorItem
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.Providers
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.SafeSelectionContainer
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.asStable
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.icons.LayoutSidebarRight
import io.github.kdroidfilter.seforimapp.icons.LayoutSidebarRightOff
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import seforimapp.seforimapp.generated.resources.*
import kotlin.time.Duration.Companion.milliseconds

private val SCROLL_DEBOUNCE = 100.milliseconds

// Per-side vertical padding applied by the `CommentaryItem`'s Column (see the
// `padding(horizontal = 16.dp, vertical = CommentaryItemVerticalPaddingPerSide)`
// usage below). Exposed so the scrollbar can derive the exact per-item padding
// contribution as `2 × CommentaryItemVerticalPaddingPerSide`. Single source of
// truth: changing this value updates both the layout and the scrollbar metrics.
private val CommentaryItemVerticalPaddingPerSide = 8.dp

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun LineCommentsView(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    showDiacritics: Boolean,
    lineConnections: Map<Long, LineConnectionsSnapshot> = emptyMap(),
) {
    val contentState = uiState.content
    val selectedLine = contentState.primaryLine
    val selectedLineIds = contentState.selectedLineIds.toImmutableList()
    // Multi-sélection manuelle (Ctrl+click) = afficher commentaires de toutes les lignes
    // TOC entry selection = afficher commentaires seulement de la ligne primaire
    val isManualMultiSelection = selectedLineIds.size > 1 && !contentState.isTocEntrySelection

    // Pass only stable slices below — never the whole `uiState`. `providers` is equals-stable
    // across emissions, and these `content` fields keep their reference across an unrelated
    // `content.copy(...)` (e.g. main-list scroll), so the commentaries subtree no longer
    // recomposes on every scroll/paging churn of the book pane.
    val providers = uiState.providers
    val primarySelectedLineId = contentState.primarySelectedLineId
    val selectedCommentatorIds = contentState.selectedCommentatorIds
    val commentatorsListScrollIndex = contentState.commentatorsListScrollIndex
    val commentatorsListScrollOffset = contentState.commentatorsListScrollOffset
    val columnScroll =
        CommentariesColumnScroll(
            pageIndex = contentState.commentariesPageIndex,
            fallbackScrollIndex = contentState.commentariesScrollIndex,
            fallbackScrollOffset = contentState.commentariesScrollOffset,
            indexByCommentator = contentState.commentariesColumnScrollIndexByCommentator,
            offsetByCommentator = contentState.commentariesColumnScrollOffsetByCommentator,
        )

    // Animation settings with stable memorization
    val textSizes = rememberAnimatedTextSettings()
    val findQuery by AppSettings.findQueryFlow(uiState.tabId).collectAsState("")
    val showFind by AppSettings.findBarOpenFlow(uiState.tabId).collectAsState()
    val activeQuery = if (showFind) findQuery else ""

    val paneInteractionSource = remember { MutableInteractionSource() }

    Column(modifier = Modifier.fillMaxSize().hoverable(paneInteractionSource)) {
        // Header
        PaneHeader(
            label = stringResource(Res.string.commentaries),
            interactionSource = paneInteractionSource,
            onHide = { onEvent(BookContentEvent.ToggleCommentaries) },
            actions = {
                CommentatorsSidebarToggleButton(
                    isVisible = contentState.isCommentatorsListVisible,
                    onToggle = { onEvent(BookContentEvent.ToggleCommentatorsList) },
                )
            },
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            when {
                selectedLine == null -> {
                    CenteredMessage(stringResource(Res.string.select_line_for_commentaries))
                }

                providers == null -> Unit

                isManualMultiSelection -> {
                    MultiLineCommentariesContent(
                        selectedLineIds = selectedLineIds,
                        providers = providers,
                        primarySelectedLineId = primarySelectedLineId,
                        selectedCommentatorIds = selectedCommentatorIds,
                        commentatorsListScrollIndex = commentatorsListScrollIndex,
                        commentatorsListScrollOffset = commentatorsListScrollOffset,
                        columnScroll = columnScroll,
                        onEvent = onEvent,
                        textSizes = textSizes,
                        findQueryText = activeQuery,
                        isCommentatorsListVisible = contentState.isCommentatorsListVisible,
                        showDiacritics = showDiacritics,
                    )
                }

                else -> {
                    val prefetched = lineConnections[selectedLine.id]?.commentatorGroups
                    CommentariesContent(
                        selectedLineId = selectedLine.id,
                        providers = providers,
                        selectedCommentatorIds = selectedCommentatorIds,
                        commentatorsListScrollIndex = commentatorsListScrollIndex,
                        commentatorsListScrollOffset = commentatorsListScrollOffset,
                        columnScroll = columnScroll,
                        onEvent = onEvent,
                        textSizes = textSizes,
                        findQueryText = activeQuery,
                        isCommentatorsListVisible = contentState.isCommentatorsListVisible,
                        prefetchedGroups = prefetched,
                        showDiacritics = showDiacritics,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
private fun CommentariesContent(
    selectedLineId: Long,
    providers: Providers,
    selectedCommentatorIds: Set<Long>,
    commentatorsListScrollIndex: Int,
    commentatorsListScrollOffset: Int,
    columnScroll: CommentariesColumnScroll,
    onEvent: (BookContentEvent) -> Unit,
    textSizes: AnimatedTextSizes,
    findQueryText: String,
    isCommentatorsListVisible: Boolean,
    prefetchedGroups: List<CommentatorGroup>?,
    showDiacritics: Boolean,
) {
    val commentatorSelection =
        rememberCommentarySelectionData(
            lineId = selectedLineId,
            getCommentatorGroupsForLine = providers.getCommentatorGroupsForLine,
            prefetchedGroups = prefetchedGroups,
        )

    val titleToIdMap = commentatorSelection.titleToIdMap
    val commentatorGroups = commentatorSelection.groups

    if (titleToIdMap.isEmpty()) {
        CenteredMessage(stringResource(Res.string.no_commentaries_for_line))
        return
    }

    // Flatten the grouped commentators to preserve global ordering (category, then pubDate)
    val commentatorsInDisplayOrder =
        remember(commentatorGroups) {
            commentatorGroups.flatMap { group -> group.commentators.map(CommentatorItem::name) }
        }

    // Manage selected commentators
    val selectedCommentators =
        rememberSelectedCommentators(
            availableCommentators = commentatorsInDisplayOrder,
            initiallySelectedIds = selectedCommentatorIds,
            titleToIdMap = titleToIdMap,
            onSelectionChange = { ids ->
                onEvent(BookContentEvent.SelectedCommentatorsChanged(selectedLineId, ids))
            },
        )

    val splitState = rememberSplitPaneState(0.10f)

    LaunchedEffect(isCommentatorsListVisible) {
        if (!isCommentatorsListVisible) {
            splitState.positionPercentage = 0f
        } else if (splitState.positionPercentage <= 0f) {
            splitState.positionPercentage = 0.10f
        }
    }

    EnhancedHorizontalSplitPane(
        splitPaneState = splitState.asStable(),
        firstMinSize = if (isCommentatorsListVisible) 150f else 0f,
        showSplitter = isCommentatorsListVisible,
        dividerVisibleInIslands = true,
        firstContent = {
            if (isCommentatorsListVisible) {
                CommentatorsList(
                    groups = commentatorGroups,
                    selectedCommentators = selectedCommentators.value,
                    initialScrollIndex = commentatorsListScrollIndex,
                    initialScrollOffset = commentatorsListScrollOffset,
                    onScroll = { index, offset ->
                        onEvent(BookContentEvent.CommentatorsListScrolled(index, offset))
                    },
                    onSelectionChange = { name, checked ->
                        selectedCommentators.value =
                            if (checked) {
                                selectedCommentators.value + name
                            } else {
                                selectedCommentators.value - name
                            }
                    },
                )
            }
        },
        secondContent = {
            // Ensure selected commentators are always displayed in a stable order,
            // independent of the order in which they were selected.
            val selectedInDisplayOrder =
                remember(commentatorsInDisplayOrder, selectedCommentators.value) {
                    commentatorsInDisplayOrder.filter { it in selectedCommentators.value }.toImmutableList()
                }
            CommentariesDisplay(
                selectedCommentators = selectedInDisplayOrder,
                titleToIdMap = titleToIdMap,
                selection = LineSelection.Single(selectedLineId),
                providers = providers,
                columnScroll = columnScroll,
                onEvent = onEvent,
                textSizes = textSizes,
                findQueryText = findQueryText,
                showDiacritics = showDiacritics,
            )
        },
    )
}

/**
 * Multi-line version of CommentariesContent for multi-selection.
 * Aggregates commentators and commentaries from all selected lines.
 */
@OptIn(ExperimentalSplitPaneApi::class)
@Composable
private fun MultiLineCommentariesContent(
    selectedLineIds: ImmutableList<Long>,
    providers: Providers,
    primarySelectedLineId: Long?,
    selectedCommentatorIds: Set<Long>,
    commentatorsListScrollIndex: Int,
    commentatorsListScrollOffset: Int,
    columnScroll: CommentariesColumnScroll,
    onEvent: (BookContentEvent) -> Unit,
    textSizes: AnimatedTextSizes,
    findQueryText: String,
    isCommentatorsListVisible: Boolean,
    showDiacritics: Boolean,
) {
    val primaryLineId = primarySelectedLineId ?: selectedLineIds.firstOrNull() ?: return

    // Use multi-line provider to get aggregated commentator groups
    val commentatorGroups by produceState<List<CommentatorGroup>>(emptyList(), selectedLineIds) {
        value = providers.getCommentatorGroupsForLines(selectedLineIds)
    }

    val titleToIdMap =
        remember(commentatorGroups) {
            commentatorGroups.flatMap { it.commentators }.associate { it.name to it.bookId }
        }

    if (titleToIdMap.isEmpty()) {
        CenteredMessage(stringResource(Res.string.no_commentaries_for_line))
        return
    }

    val commentatorsInDisplayOrder =
        remember(commentatorGroups) {
            commentatorGroups.flatMap { group -> group.commentators.map(CommentatorItem::name) }
        }

    val selectedCommentators =
        rememberSelectedCommentators(
            availableCommentators = commentatorsInDisplayOrder,
            initiallySelectedIds = selectedCommentatorIds,
            titleToIdMap = titleToIdMap,
            onSelectionChange = { ids ->
                onEvent(BookContentEvent.SelectedCommentatorsChanged(primaryLineId, ids))
            },
        )

    val splitState = rememberSplitPaneState(0.10f)

    LaunchedEffect(isCommentatorsListVisible) {
        if (!isCommentatorsListVisible) {
            splitState.positionPercentage = 0f
        } else if (splitState.positionPercentage <= 0f) {
            splitState.positionPercentage = 0.10f
        }
    }

    EnhancedHorizontalSplitPane(
        splitPaneState = splitState.asStable(),
        firstMinSize = if (isCommentatorsListVisible) 150f else 0f,
        showSplitter = isCommentatorsListVisible,
        dividerVisibleInIslands = true,
        firstContent = {
            if (isCommentatorsListVisible) {
                CommentatorsList(
                    groups = commentatorGroups,
                    selectedCommentators = selectedCommentators.value,
                    initialScrollIndex = commentatorsListScrollIndex,
                    initialScrollOffset = commentatorsListScrollOffset,
                    onScroll = { index, offset ->
                        onEvent(BookContentEvent.CommentatorsListScrolled(index, offset))
                    },
                    onSelectionChange = { name, checked ->
                        selectedCommentators.value =
                            if (checked) {
                                selectedCommentators.value + name
                            } else {
                                selectedCommentators.value - name
                            }
                    },
                )
            }
        },
        secondContent = {
            val selectedInDisplayOrder =
                remember(commentatorsInDisplayOrder, selectedCommentators.value) {
                    commentatorsInDisplayOrder.filter { it in selectedCommentators.value }.toImmutableList()
                }
            CommentariesDisplay(
                selectedCommentators = selectedInDisplayOrder,
                titleToIdMap = titleToIdMap,
                selection = LineSelection.Multi(selectedLineIds),
                providers = providers,
                columnScroll = columnScroll,
                onEvent = onEvent,
                textSizes = textSizes,
                findQueryText = findQueryText,
                showDiacritics = showDiacritics,
            )
        },
    )
}

@OptIn(FlowPreview::class)
@Composable
private fun CommentatorsList(
    groups: List<CommentatorGroup>,
    selectedCommentators: Set<String>,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onScroll: (Int, Int) -> Unit,
    onSelectionChange: (String, Boolean) -> Unit,
) {
    val currentOnScroll by rememberUpdatedState(onScroll)
    Box(modifier = Modifier.fillMaxSize()) {
        val listState =
            rememberLazyListState(
                initialFirstVisibleItemIndex = initialScrollIndex,
                initialFirstVisibleItemScrollOffset = initialScrollOffset,
            )

        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .debounce(SCROLL_DEBOUNCE)
                .collect { (i, o) -> currentOnScroll(i, o) }
        }

        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            VerticallyScrollableContainer(
                scrollState = listState as ScrollableState,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    val showGroupHeaders = groups.size > 1
                    groups.forEachIndexed { groupIndex, group ->
                        if (group.commentators.isEmpty()) return@forEachIndexed

                        if (groupIndex > 0) {
                            item(key = "divider-$groupIndex") {
                                HorizontalDivider()
                            }
                        }

                        if (showGroupHeaders && group.label.isNotBlank()) {
                            item(key = "header-$groupIndex-${group.label}") {
                                CommentatorGroupHeader(
                                    label = group.label,
                                )
                            }
                        }

                        items(
                            count = group.commentators.size,
                            key = { index -> group.commentators[index].bookId },
                        ) { idx ->
                            val commentatorItem = group.commentators[idx]
                            val commentator = commentatorItem.name
                            val isSelected =
                                remember(selectedCommentators, commentator) {
                                    commentator in selectedCommentators
                                }
                            CheckboxRow(
                                text = commentator,
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    onSelectionChange(commentator, checked)
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentariesDisplay(
    selectedCommentators: ImmutableList<String>,
    titleToIdMap: Map<String, Long>,
    selection: LineSelection,
    providers: Providers,
    columnScroll: CommentariesColumnScroll,
    onEvent: (BookContentEvent) -> Unit,
    textSizes: AnimatedTextSizes,
    findQueryText: String,
    showDiacritics: Boolean,
) {
    if (selectedCommentators.isEmpty()) {
        CenteredMessage(
            message = stringResource(Res.string.select_at_least_one_commentator),
            fontSize = textSizes.commentTextSize,
        )
        return
    }

    val layoutConfig =
        rememberCommentariesLayoutConfig(
            selectedCommentators = selectedCommentators,
            titleToIdMap = titleToIdMap,
            textSizes = textSizes,
            findQueryText = findQueryText,
            showDiacritics = showDiacritics,
            onEvent = onEvent,
        )

    CommentatorsGrid(
        config = layoutConfig,
        selection = selection,
        providers = providers,
        columnScroll = columnScroll,
        onEvent = onEvent,
    )
}

@Composable
private fun CommentatorsGrid(
    config: CommentariesLayoutConfig,
    selection: LineSelection,
    providers: Providers,
    columnScroll: CommentariesColumnScroll,
    onEvent: (BookContentEvent) -> Unit,
) {
    val pagerFlowCache = remember(selection) { mutableMapOf<Long, Flow<PagingData<CommentaryWithText>>>() }
    val listStateCache = remember(selection) { mutableMapOf<Long, LazyListState>() }
    val restoredCommentatorIds = remember(selection) { mutableStateMapOf<Long, Boolean>() }

    CommentatorsGridScaffold(
        config = config,
        initialPage = columnScroll.pageIndex,
        onPageChange = { page -> onEvent(BookContentEvent.CommentariesPageChanged(page)) },
        onFlushPersist = { onEvent(BookContentEvent.FlushCommentariesState) },
    ) { commentatorId ->
        val pagerFlow =
            remember(commentatorId, pagerFlowCache) {
                // The open commentator's TEXT is built lazily here, on first display — it is NOT
                // covered by the connections prefetch (which only loads the commentator list).
                pagerFlowCache.getOrPut(commentatorId) {
                    providers.buildCommentariesPagerFor(selection, commentatorId)
                }
            }
        val initialIndex =
            columnScroll.indexByCommentator[commentatorId]
                ?: columnScroll.fallbackScrollIndex
        val initialOffset =
            columnScroll.offsetByCommentator[commentatorId]
                ?: columnScroll.fallbackScrollOffset
        val listState =
            remember(commentatorId, listStateCache) {
                listStateCache.getOrPut(commentatorId) {
                    LazyListState(
                        firstVisibleItemIndex = initialIndex.coerceAtLeast(0),
                        firstVisibleItemScrollOffset = initialOffset.coerceAtLeast(0),
                    )
                }
            }

        CommentariesPagedList(
            pagerFlow = pagerFlow,
            listState = listState,
            initialIndex = initialIndex,
            initialOffset = initialOffset,
            shouldRestore = restoredCommentatorIds[commentatorId] != true,
            onRestore = { restoredCommentatorIds[commentatorId] = true },
            onScrollSettle = { i, o ->
                onEvent(BookContentEvent.CommentaryColumnScrolled(commentatorId, i, o, persist = true))
            },
            config = config,
            selection = selection,
            commentatorId = commentatorId,
            getCharCountsForLine = providers.getCommentaryCharCountsForLine,
            getCharCountsForLines = providers.getCommentaryCharCountsForLines,
        )
    }
}

/**
 * Paged list of [CommentaryItem]s for one commentator, wrapped in a [SafeSelectionContainer].
 * Shared by single-line and multi-line views.
 *
 * IMPORTANT — [pagerFlow] identity drives the one-shot scroll restore. Callers MUST build it via
 * `remember(key1, key2, ...) { providers.buildPagerFor(...) }` so that the same logical source
 * yields the same [Flow] reference across recompositions. Passing a freshly-constructed Flow on
 * every recomposition (e.g. inline `providers.buildPagerFor(...).map { ... }`) will reset the
 * one-shot state on each frame and fight the user's scroll position.
 */
@OptIn(FlowPreview::class)
@Composable
private fun CommentariesPagedList(
    pagerFlow: Flow<PagingData<CommentaryWithText>>,
    listState: LazyListState,
    initialIndex: Int,
    initialOffset: Int,
    shouldRestore: Boolean,
    onRestore: () -> Unit,
    onScrollSettle: (Int, Int) -> Unit,
    config: CommentariesLayoutConfig,
    selection: LineSelection,
    commentatorId: Long,
    getCharCountsForLine: suspend (Long, Long) -> List<Int>,
    getCharCountsForLines: suspend (List<Long>, Long) -> List<Int>,
) {
    val currentOnRestore by rememberUpdatedState(onRestore)
    val currentOnScrollSettle by rememberUpdatedState(onScrollSettle)
    val lazyPagingItems = pagerFlow.collectAsLazyPagingItems()
    val annotationCache = remember(selection, commentatorId) { StableAnnotatedCache(mutableStateMapOf()) }

    // User highlights for the commentary books currently shown. Commentary lines belong to
    // their own target book, so highlights are keyed by (targetBookId, targetLineId) — the same
    // key used when that commentary is opened as a main book, so they stay in sync.
    val highlightStore = LocalAppGraph.current.highlightStore
    val selectionContext = LocalAppGraph.current.selectionContext
    val highlightsByBook by highlightStore.highlightsByBook.collectAsState()
    LaunchedEffect(lazyPagingItems, highlightStore) {
        snapshotFlow {
            lazyPagingItems.itemSnapshotList.items
                .map { it.link.targetBookId }
                .toSet()
        }.distinctUntilChanged()
            .collect { bookIds -> bookIds.forEach { highlightStore.loadBook(it) } }
    }
    val highlightsByBookLine =
        remember(highlightsByBook) {
            highlightsByBook.mapValues { (_, lines) -> lines.groupBy { it.lineId } }
        }

    // Ordered char-count vector for every commentary matching this selection ×
    // commentator (same filter + order as the pager). The scrollbar converts every
    // value to `ceil(charCount / capacity) * capacity` to derive visual-line count.
    // Sourced from the VM via `Providers` so failures fold to an empty list (no view
    // crash) and the data path mirrors `bookCharCounts` for the main book scrollbar.
    val getCharCountsForLineRef by rememberUpdatedState(getCharCountsForLine)
    val getCharCountsForLinesRef by rememberUpdatedState(getCharCountsForLines)
    val allCharCounts by produceState(initialValue = emptyList<Int>(), selection, commentatorId) {
        value =
            when (selection) {
                is LineSelection.Single -> getCharCountsForLineRef(selection.lineId, commentatorId)
                is LineSelection.Multi -> getCharCountsForLinesRef(selection.lineIds, commentatorId)
            }
    }

    // Scrollbar metrics, all exact and deterministic:
    //  - `textLayoutWidthPx` is the constraint Compose used to wrap each item's Text,
    //    captured once from the first rendered item's `onTextLayout`. Automatically
    //    reflects nested paddings without us having to know their values.
    //  - `capacity` is computed by `TextMeasurer` against that width and the exact font
    //    settings — deterministic, updates naturally on resize / font change, never
    //    fluctuates during scroll.
    //  - `lineHeightPx` comes from `fontSize × lineHeight multiplier`, converted via
    //    density. This is the actual per-visual-line pixel cost Compose applies.
    //  - `paddingPerItemPx` is the total vertical padding wrapping every `CommentaryItem`
    //    (see `padding(vertical = 8.dp)` below → 16 dp).
    var textLayoutWidthPx by remember(selection, commentatorId) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val lineHeightPx =
        with(density) {
            (config.textSizes.commentTextSize * config.textSizes.lineHeight).sp.toPx()
        }
    val paddingPerItemPx = with(density) { (CommentaryItemVerticalPaddingPerSide * 2).toPx() }
    val capacity by remember(textLayoutWidthPx, config.textSizes, config.fontFamily) {
        derivedStateOf {
            if (textLayoutWidthPx <= 0) {
                0
            } else {
                val reference = CAPACITY_REFERENCE
                val result =
                    textMeasurer.measure(
                        text = AnnotatedString(reference),
                        style =
                            TextStyle(
                                fontSize = config.textSizes.commentTextSize.sp,
                                fontFamily = config.fontFamily,
                                lineHeight = (config.textSizes.commentTextSize * config.textSizes.lineHeight).sp,
                            ),
                        constraints = Constraints(maxWidth = textLayoutWidthPx),
                    )
                (reference.length / result.lineCount.coerceAtLeast(1)).coerceAtLeast(1)
            }
        }
    }

    val restoreIndex = remember(pagerFlow) { initialIndex }
    val restoreOffset = remember(pagerFlow) { initialOffset }
    LaunchedEffect(pagerFlow, lazyPagingItems.loadState.refresh, shouldRestore) {
        if (shouldRestore && lazyPagingItems.loadState.refresh !is LoadState.Loading) {
            if (lazyPagingItems.itemCount > 0) {
                val safeIndex = restoreIndex.coerceIn(0, lazyPagingItems.itemCount - 1)
                val safeOffset = restoreOffset.coerceAtLeast(0)
                listState.scrollToItem(safeIndex, safeOffset)
                currentOnRestore()
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress,
            )
        }.distinctUntilChanged()
            .drop(1)
            .debounce(SCROLL_DEBOUNCE)
            .collect { (i, o, isScrollInProgress) ->
                if (!isScrollInProgress) {
                    currentOnScrollSettle(i, o)
                }
            }
    }

    SafeSelectionContainer(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
            ) {
                items(
                    count = lazyPagingItems.itemCount,
                    key = { index -> lazyPagingItems[index]?.link?.id ?: index },
                ) { index ->
                    lazyPagingItems[index]?.let { commentary ->
                        // Stable per-line list; non-highlighted lines get the emptyList() singleton.
                        val lineHighlights =
                            highlightsByBookLine[commentary.link.targetBookId]
                                ?.get(commentary.link.targetLineId) ?: emptyList()
                        CommentaryItem(
                            linkId = commentary.link.id,
                            targetText = commentary.targetText,
                            textSizes = config.textSizes,
                            fontFamily = config.fontFamily,
                            boldScale = config.boldScale,
                            highlightQuery = config.highlightQuery,
                            showDiacritics = config.showDiacritics,
                            annotationCache = annotationCache,
                            userHighlights = lineHighlights,
                            onSecondaryClick = {
                                // Publish the whole column (ordered) so a multi-line selection
                                // can be resolved across consecutive commentary entries.
                                selectionContext.setActiveCommentaryColumn(
                                    lazyPagingItems.itemSnapshotList.items.map {
                                        CommentaryLineRef(
                                            bookId = it.link.targetBookId,
                                            lineId = it.link.targetLineId,
                                            content = it.targetText,
                                        )
                                    },
                                )
                            },
                            onClick = { config.onCommentClick(commentary) },
                            onLayoutWidthMeasure = { width ->
                                if (textLayoutWidthPx == 0 && width > 0) {
                                    textLayoutWidthPx = width
                                }
                            },
                        )
                    }
                }

                when (val loadState = lazyPagingItems.loadState.refresh) {
                    is LoadState.Loading -> item { LoadingIndicator() }
                    is LoadState.Error -> item { ErrorMessage(loadState.error) }
                    else -> {}
                }
            }
            CommentariesScrollbar(
                listState = listState,
                lazyPagingItems = lazyPagingItems,
                allCharCounts = allCharCounts,
                capacity = capacity,
                lineHeightPx = lineHeightPx,
                paddingPerItemPx = paddingPerItemPx,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CommentaryItem(
    linkId: Long,
    targetText: String,
    textSizes: AnimatedTextSizes,
    fontFamily: FontFamily,
    highlightQuery: String,
    showDiacritics: Boolean,
    annotationCache: StableAnnotatedCache,
    onClick: () -> Unit,
    boldScale: Float = 1.0f,
    userHighlights: List<UserHighlight> = emptyList(),
    onSecondaryClick: () -> Unit = {},
    onLayoutWidthMeasure: (Int) -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = CommentaryItemVerticalPaddingPerSide)
                .onPointerEvent(PointerEventType.Press) { event ->
                    // Record this commentary line as the highlight target (analogous to the main
                    // pane's onContextClick) so the shared context menu can anchor here.
                    if (event.buttons.isSecondaryPressed) onSecondaryClick()
                }.pointerInput(onClick) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val modifiers = currentEvent.keyboardModifiers
                        val isCtrlMetaPrimary =
                            (modifiers.isCtrlPressed || modifiers.isMetaPressed) &&
                                currentEvent.buttons.isPrimaryPressed
                        if (isCtrlMetaPrimary) {
                            down.consume()
                        }
                        val up = waitForUpOrCancellation()
                        if (isCtrlMetaPrimary && up != null) {
                            up.consume()
                            onClick()
                        }
                    }
                },
    ) {
        val processedText =
            remember(linkId, targetText, showDiacritics) {
                if (showDiacritics) targetText else HebrewTextUtils.removeAllDiacritics(targetText)
            }

        // Footnote marker color from theme
        val footnoteMarkerColor = JewelTheme.globalColors.outlines.focused

        val isDarkTheme = JewelTheme.isDark
        val imageColorFilter: @Composable () -> ColorFilter? =
            remember(isDarkTheme) {
                { if (isDarkTheme) SkiaHtmlImageBuilder.InvertColorFilter else null }
            }

        val annotationCacheKey =
            remember(linkId, processedText, textSizes.commentTextSize, boldScale, footnoteMarkerColor, isDarkTheme) {
                HtmlAnnotationCacheKey(
                    itemId = linkId,
                    contentHash = processedText.hashCode(),
                    contentLength = processedText.length,
                    baseTextSize = textSizes.commentTextSize,
                    boldScale = boldScale,
                    footnoteMarkerColor = footnoteMarkerColor,
                    invertImages = isDarkTheme,
                )
            }
        val annotation =
            rememberAsyncHtmlAnnotation(
                cacheKey = annotationCacheKey,
                html = processedText,
                baseTextSize = textSizes.commentTextSize,
                boldScale = boldScale,
                footnoteMarkerColor = footnoteMarkerColor,
                imageColorFilter = imageColorFilter,
                annotatedCache = annotationCache,
            )

        if (annotation == null) {
            Text(
                text = htmlAnnotationPlaceholderText(processedText.length),
                textAlign = TextAlign.Justify,
                fontSize = textSizes.commentTextSize.sp,
                fontFamily = fontFamily,
                lineHeight = (textSizes.commentTextSize * textSizes.lineHeight).sp,
                onTextLayout = { result ->
                    val cw = result.layoutInput.constraints.maxWidth
                    if (cw > 0 && cw != Int.MAX_VALUE) onLayoutWidthMeasure(cw)
                },
            )
        } else {
            val annotated = annotation.annotated
            val inlineImageContent = annotation.inlineContent

            // Plain text WITH diacritics for offset remapping (see LineItem in BookContentView).
            val originalPlainText =
                remember(targetText) {
                    buildAnnotatedFromHtml(targetText, textSizes.commentTextSize, boldScale = 1f).text
                }

            val display: AnnotatedString =
                remember(annotated, highlightQuery, userHighlights, showDiacritics, originalPlainText) {
                    // User highlights first, then search highlight on top.
                    val withUserHighlights =
                        applyUserHighlights(
                            annotated = annotated,
                            highlights = userHighlights,
                            originalText = originalPlainText,
                            showDiacritics = showDiacritics,
                        )
                    if (highlightQuery.isBlank()) {
                        withUserHighlights
                    } else {
                        highlightAnnotated(withUserHighlights, highlightQuery)
                    }
                }

            Text(
                text = display,
                textAlign = TextAlign.Justify,
                fontFamily = fontFamily,
                lineHeight = (textSizes.commentTextSize * textSizes.lineHeight).sp,
                inlineContent = inlineImageContent,
                onTextLayout = { result ->
                    val cw = result.layoutInput.constraints.maxWidth
                    if (cw > 0 && cw != Int.MAX_VALUE) onLayoutWidthMeasure(cw)
                },
            )
        }
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    fontSize: Float = 14f,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontSize = fontSize.sp,
        )
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorMessage(error: Throwable) {
    Text(
        text = error.message ?: "Error loading commentaries",
        modifier = Modifier.padding(16.dp),
    )
}

// Helper functions and data classes

@Composable
private fun rememberAnimatedTextSettings(): AnimatedTextSizes {
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    val isTabSelected = LocalTabSelected.current
    val isBookContentZoomInProgress = LocalBookContentZoomInProgress.current
    val zoomAnimSpec = if (isTabSelected && !isBookContentZoomInProgress) tween<Float>(durationMillis = 200) else snap()
    val commentTextSize by animateFloatAsState(
        targetValue = rawTextSize * 0.875f,
        animationSpec = zoomAnimSpec,
        label = "commentTextSizeAnim",
    )
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()
    val lineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = zoomAnimSpec,
        label = "commentLineHeightAnim",
    )

    return remember(commentTextSize, lineHeight) {
        AnimatedTextSizes(commentTextSize, lineHeight)
    }
}

@Immutable
private data class CommentatorSelectionData(
    val titleToIdMap: Map<String, Long>,
    val groups: List<CommentatorGroup>,
)

@Composable
private fun rememberCommentarySelectionData(
    lineId: Long,
    getCommentatorGroupsForLine: suspend (Long) -> List<CommentatorGroup>,
    prefetchedGroups: List<CommentatorGroup>? = null,
): CommentatorSelectionData {
    var groups by remember(lineId, prefetchedGroups) {
        mutableStateOf(prefetchedGroups ?: emptyList())
    }
    val currentGetCommentatorGroupsForLine by rememberUpdatedState(getCommentatorGroupsForLine)

    LaunchedEffect(lineId, prefetchedGroups) {
        if (prefetchedGroups != null) {
            groups = prefetchedGroups
            return@LaunchedEffect
        }

        runSuspendCatching { currentGetCommentatorGroupsForLine(lineId) }
            .onSuccess { loaded -> groups = loaded }
            .onFailure { groups = emptyList() }
    }

    val titleToIdMap =
        remember(groups) {
            val map = LinkedHashMap<String, Long>()
            groups.forEach { group ->
                group.commentators.forEach { item ->
                    if (!map.containsKey(item.name)) {
                        map[item.name] = item.bookId
                    }
                }
            }
            map
        }

    return remember(groups, titleToIdMap) {
        CommentatorSelectionData(
            titleToIdMap = titleToIdMap,
            groups = groups,
        )
    }
}

@Composable
private fun CommentatorGroupHeader(label: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun rememberSelectedCommentators(
    availableCommentators: List<String>,
    initiallySelectedIds: Set<Long>,
    titleToIdMap: Map<String, Long>,
    onSelectionChange: (Set<Long>) -> Unit,
): MutableState<Set<String>> {
    val selectedCommentators =
        remember(availableCommentators) {
            mutableStateOf<Set<String>>(emptySet())
        }
    // Only skip emissions when we programmatically change selection
    val skipEmit = remember { mutableStateOf(false) }
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)

    // Initialize selection with optimization
    LaunchedEffect(initiallySelectedIds, titleToIdMap) {
        if (initiallySelectedIds.isNotEmpty() && titleToIdMap.isNotEmpty()) {
            val desiredNames =
                buildSet {
                    titleToIdMap.forEach { (name, id) ->
                        if (id in initiallySelectedIds) add(name)
                    }
                }
            if (desiredNames != selectedCommentators.value) {
                skipEmit.value = true
                selectedCommentators.value = desiredNames
            }
        }
    }

    // Emit selection changes with optimization
    LaunchedEffect(selectedCommentators.value, titleToIdMap) {
        val ids =
            buildSet {
                selectedCommentators.value.forEach { name ->
                    titleToIdMap[name]?.let { add(it) }
                }
            }
        if (skipEmit.value) {
            skipEmit.value = false
        } else {
            currentOnSelectionChange(ids)
        }
    }

    // Keep selection valid with optimization
    val availableSet =
        remember(availableCommentators) {
            availableCommentators.toSet()
        }

    LaunchedEffect(availableSet) {
        val filtered = selectedCommentators.value.intersect(availableSet)
        if (filtered != selectedCommentators.value) {
            skipEmit.value = true
            selectedCommentators.value = filtered
        }
    }

    return selectedCommentators
}

@Immutable
internal data class AnimatedTextSizes(
    val commentTextSize: Float,
    val lineHeight: Float,
)

/**
 * Stable slice of the per-commentator scroll/page state passed into the commentaries grid.
 * Holds only references that survive an unrelated `content.copy(...)` (e.g. main-list scroll),
 * so the commentaries subtree is not recomposed by book-pane scroll/paging churn.
 */
@Immutable
private data class CommentariesColumnScroll(
    val pageIndex: Int,
    val fallbackScrollIndex: Int,
    val fallbackScrollOffset: Int,
    val indexByCommentator: Map<Long, Int>,
    val offsetByCommentator: Map<Long, Int>,
)

/**
 * Source of lines feeding a commentary view: either a single selected line or a manual
 * multi-selection. Chooses which provider pager to build in [Providers.buildCommentariesPagerFor].
 */
private sealed interface LineSelection {
    data class Single(
        val lineId: Long,
    ) : LineSelection

    data class Multi(
        val lineIds: ImmutableList<Long>,
    ) : LineSelection
}

private fun Providers.buildCommentariesPagerFor(
    selection: LineSelection,
    commentatorId: Long,
): Flow<PagingData<CommentaryWithText>> =
    when (selection) {
        is LineSelection.Single -> buildCommentariesPagerFor(selection.lineId, commentatorId)
        is LineSelection.Multi -> buildCommentariesPagerForLines(selection.lineIds, commentatorId)
    }

/**
 * UI + callbacks shared between the single-line and multi-line commentary views.
 *
 * Deliberately does not carry the line id(s) being displayed — the pager is built by the
 * caller and injected via the [CommentatorsGridScaffold] column slot, so this config stays
 * agnostic of single vs multi-line mode.
 */
@Immutable
internal data class CommentariesLayoutConfig(
    val selectedCommentators: ImmutableList<String>,
    val titleToIdMap: Map<String, Long>,
    val onCommentClick: (CommentaryWithText) -> Unit,
    val onOpenCommentatorBook: (Long) -> Unit,
    val textSizes: AnimatedTextSizes,
    val fontFamily: FontFamily,
    val boldScale: Float,
    val highlightQuery: String,
    val showDiacritics: Boolean,
    val maxCommentatorsPerPage: Int,
)

@Composable
private fun rememberCommentariesLayoutConfig(
    selectedCommentators: ImmutableList<String>,
    titleToIdMap: Map<String, Long>,
    textSizes: AnimatedTextSizes,
    findQueryText: String,
    showDiacritics: Boolean,
    onEvent: (BookContentEvent) -> Unit,
): CommentariesLayoutConfig {
    val windowInfo = LocalWindowInfo.current
    val commentaryFontCode by AppSettings.commentaryFontCodeFlow.collectAsState()
    val commentaryFontFamily = FontCatalog.familyFor(commentaryFontCode)
    val maxCommentatorsPerPage by AppSettings.maxCommentatorsPerPageFlow.collectAsState()
    val boldScaleForPlatform =
        remember(commentaryFontCode) {
            val lacksBold = commentaryFontCode in setOf("notoserifhebrew", "notorashihebrew", "frankruhllibre")
            if (PlatformInfo.isMacOS && lacksBold) 1.08f else 1.0f
        }

    return remember(
        selectedCommentators,
        titleToIdMap,
        textSizes,
        commentaryFontFamily,
        boldScaleForPlatform,
        findQueryText,
        showDiacritics,
        maxCommentatorsPerPage,
    ) {
        CommentariesLayoutConfig(
            selectedCommentators = selectedCommentators,
            titleToIdMap = titleToIdMap,
            onCommentClick = { commentary ->
                val mods = windowInfo.keyboardModifiers
                if (mods.isCtrlPressed || mods.isMetaPressed) {
                    onEvent(
                        BookContentEvent.OpenCommentaryTarget(
                            bookId = commentary.link.targetBookId,
                            lineId = commentary.link.targetLineId,
                        ),
                    )
                }
            },
            onOpenCommentatorBook = { bookId ->
                onEvent(BookContentEvent.OpenBookByIdInNewTab(bookId))
            },
            textSizes = textSizes,
            fontFamily = commentaryFontFamily,
            boldScale = boldScaleForPlatform,
            highlightQuery = findQueryText,
            showDiacritics = showDiacritics,
            maxCommentatorsPerPage = maxCommentatorsPerPage,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentatorsSidebarToggleButton(
    isVisible: Boolean,
    onToggle: () -> Unit,
) {
    val icon: ImageVector = if (isVisible) LayoutSidebarRight else LayoutSidebarRightOff
    val toggleText =
        if (isVisible) {
            stringResource(Res.string.hide_commentators_sidebar)
        } else {
            stringResource(Res.string.show_commentators_sidebar)
        }
    val painter = rememberVectorPainter(icon)
    Tooltip({ Text(toggleText) }) {
        IconButton(onClick = onToggle) { _ ->
            Icon(
                painter = painter,
                contentDescription = toggleText,
                modifier = Modifier.size(16.dp),
                tint = JewelTheme.globalColors.text.normal,
            )
        }
    }
}
