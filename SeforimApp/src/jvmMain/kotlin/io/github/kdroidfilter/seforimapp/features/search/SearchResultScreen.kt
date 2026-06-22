package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.presentation.components.CustomToggleableChip
import io.github.kdroidfilter.seforimapp.core.presentation.components.FindInPageBar
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotatedWithCurrent
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.SplitDefaults
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EndVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.StartVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.asStable
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.BookContentPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.ContentAwareScrollbarShell
import io.github.kdroidfilter.seforimapp.features.search.domain.TocTree
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.*

@Stable
data class SearchShellActions(
    val onSubmit: (query: String) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onGlobalExtendedChange: (Boolean) -> Unit,
    val onScroll: (anchorId: Long, anchorIndex: Int, index: Int, offset: Int) -> Unit,
    val onCancelSearch: () -> Unit,
    val onOpenResult: (SearchResult, openInNewTab: Boolean) -> Unit,
    val onRequestBreadcrumb: (SearchResult) -> Unit,
    val onLoadMore: () -> Unit,
    val onCategoryCheckedChange: (Long, Boolean) -> Unit,
    val onBookCheckedChange: (Long, Boolean) -> Unit,
    val onEnsureScopeBookForToc: (Long) -> Unit,
    val onTocToggle: (io.github.kdroidfilter.seforimlibrary.core.models.TocEntry, Boolean) -> Unit,
    val onTocFilter: (io.github.kdroidfilter.seforimlibrary.core.models.TocEntry) -> Unit,
)

@Composable
private fun SearchToolbar(
    initialQuery: String,
    onSubmit: (query: String) -> Unit,
    onQueryChange: (String) -> Unit,
    globalExtended: Boolean,
    onGlobalExtendedChange: (Boolean) -> Unit,
    baseBooksHadNoResults: Boolean = false,
) {
    val searchState = remember { TextFieldState() }
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)

    // Keep the field in sync with initial/current query
    LaunchedEffect(initialQuery) {
        val text = searchState.text.toString()
        if (text != initialQuery) {
            searchState.edit { replace(0, length, initialQuery) }
        }
    }

    // Persist live edits so session restore reopens with the last typed text
    LaunchedEffect(Unit) {
        snapshotFlow { searchState.text.toString() }.distinctUntilChanged().collect { q -> currentOnQueryChange(q) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Query field
        TextField(
            state = searchState,
            modifier =
                Modifier.weight(1f).height(36.dp).onPreviewKeyEvent { ev ->
                    if ((ev.key == androidx.compose.ui.input.key.Key.Enter || ev.key == androidx.compose.ui.input.key.Key.NumPadEnter) &&
                        ev.type == androidx.compose.ui.input.key.KeyEventType.KeyUp
                    ) {
                        val q = searchState.text.toString()
                        onSubmit(q)
                        true
                    } else {
                        false
                    }
                },
            placeholder = { Text(stringResource(Res.string.search_placeholder)) },
            leadingIcon = {
                IconButton(modifier = Modifier.pointerHoverIcon(PointerIcon.Hand), onClick = {
                    val q = searchState.text.toString()
                    onSubmit(q)
                }) {
                    Icon(
                        key = AllIconsKeys.Actions.Find,
                        contentDescription = stringResource(Res.string.search_icon_description),
                    )
                }
            },
            textStyle =
                androidx.compose.ui.text
                    .TextStyle(fontSize = 13.sp),
        )

        // Global extended toggle (default off → base books only)
        CustomToggleableChip(
            checked = globalExtended,
            onClick = onGlobalExtendedChange,
            tooltipText =
                if (baseBooksHadNoResults) {
                    stringResource(Res.string.search_extended_no_base_results)
                } else {
                    stringResource(Res.string.search_extended_tooltip)
                },
            enabled = !baseBooksHadNoResults,
        )
    }
}

@OptIn(ExperimentalSplitPaneApi::class, FlowPreview::class)
@Composable
fun SearchResultInBookShellMvi(
    bookUiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    showDiacritics: Boolean,
    // Search state
    searchUi: SearchUiState,
    visibleResults: ImmutableList<SearchResult>,
    isFiltering: Boolean,
    breadcrumbs: ImmutableMap<Long, List<String>>,
    searchTree: ImmutableList<SearchResultViewModel.SearchTreeCategory>,
    selectedCategoryIds: Set<Long>,
    selectedBookIds: Set<Long>,
    selectedTocIds: Set<Long>,
    tocCounts: Map<Long, Int>,
    tocTree: TocTree?,
    bookCounts: Map<Long, Int>,
    loadBookHits: suspend (Long) -> List<SearchResult>,
    actions: SearchShellActions,
) {
    val tabId = bookUiState.tabId
    val currentOnEvent by rememberUpdatedState(onEvent)
    val splitPaneConfigs =
        listOf(
            SplitPaneConfig(
                splitState = bookUiState.layout.mainSplitState,
                isVisible = bookUiState.navigation.isVisible,
                positionFilter = { it > 0 },
            ),
            SplitPaneConfig(
                splitState = bookUiState.layout.tocSplitState,
                isVisible = bookUiState.toc.isVisible,
                positionFilter = { it > 0 },
            ),
        )

    splitPaneConfigs.forEach { config ->
        LaunchedEffect(config.splitState, config.isVisible) {
            if (config.isVisible) {
                snapshotFlow { config.splitState.positionPercentage }
                    .map { ((it * 100).toInt() / 100f) }
                    .distinctUntilChanged()
                    .debounce(300)
                    .filter(config.positionFilter)
                    .collect { currentOnEvent(BookContentEvent.SaveState) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { currentOnEvent(BookContentEvent.SaveState) }
    }

    val isIslands = ThemeUtils.isIslandsStyle()
    val panelCardModifier =
        if (isIslands) {
            Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp, horizontal = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(JewelTheme.globalColors.panelBackground)
        } else {
            Modifier
        }

    Row(modifier = Modifier.fillMaxSize()) {
        StartVerticalBar(uiState = bookUiState, onEvent = onEvent)

        EnhancedHorizontalSplitPane(
            splitPaneState = bookUiState.layout.mainSplitState.asStable(),
            modifier = Modifier.weight(1f),
            firstMinSize = if (bookUiState.navigation.isVisible) SplitDefaults.MIN_MAIN else 0f,
            firstContent = {
                if (bookUiState.navigation.isVisible) {
                    io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.categorytree.SearchCategoryTreePanel(
                        uiState = bookUiState,
                        onEvent = onEvent,
                        searchTree = searchTree,
                        isFiltering = isFiltering,
                        selectedCategoryIds = selectedCategoryIds,
                        selectedBookIds = selectedBookIds,
                        onCategoryCheckedChange = actions.onCategoryCheckedChange,
                        onBookCheckedChange = actions.onBookCheckedChange,
                        onEnsureScopeBookForToc = actions.onEnsureScopeBookForToc,
                        modifier = panelCardModifier,
                    )
                }
            },
            secondContent = {
                EnhancedHorizontalSplitPane(
                    splitPaneState = bookUiState.layout.tocSplitState.asStable(),
                    firstMinSize = if (bookUiState.toc.isVisible) SplitDefaults.MIN_TOC else 0f,
                    firstContent = {
                        if (bookUiState.toc.isVisible) {
                            io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc.SearchBookTocPanel(
                                uiState = bookUiState,
                                onEvent = onEvent,
                                searchUi = searchUi,
                                tocTree = tocTree,
                                tocCounts = tocCounts,
                                selectedTocIds = selectedTocIds,
                                onToggle = actions.onTocToggle,
                                onTocFilter = actions.onTocFilter,
                                modifier = panelCardModifier,
                            )
                        }
                    },
                    secondContent = {
                        val showBookContent =
                            bookUiState.navigation.selectedBook != null && bookUiState.providers != null
                        if (showBookContent) {
                            BookContentPanel(
                                uiState = bookUiState,
                                onEvent = onEvent,
                                showDiacritics = showDiacritics,
                            )
                        } else {
                            Box(modifier = panelCardModifier) {
                                SearchResultContentMvi(
                                    state = searchUi,
                                    visibleResults = visibleResults,
                                    isFiltering = isFiltering,
                                    breadcrumbs = breadcrumbs,
                                    bookCounts = bookCounts,
                                    loadBookHits = loadBookHits,
                                    actions = actions,
                                    tabId = tabId,
                                )
                            }
                        }
                    },
                    showSplitter = bookUiState.toc.isVisible,
                )
            },
            showSplitter = bookUiState.navigation.isVisible,
        )

        EndVerticalBar(uiState = bookUiState, onEvent = onEvent, showDiacritics = showDiacritics)
    }
}

@Stable
private data class SplitPaneConfig
    @OptIn(ExperimentalSplitPaneApi::class)
    constructor(
        val splitState: SplitPaneState,
        val isVisible: Boolean,
        val positionFilter: (Float) -> Boolean,
    )

@Composable
private fun SearchResultContentMvi(
    state: SearchUiState,
    visibleResults: ImmutableList<SearchResult>,
    isFiltering: Boolean,
    breadcrumbs: ImmutableMap<Long, List<String>>,
    bookCounts: Map<Long, Int>,
    loadBookHits: suspend (Long) -> List<SearchResult>,
    actions: SearchShellActions,
    tabId: String,
) {
    val listState = rememberLazyListState()
    // Group consecutive same-book results into Google-style cards. Cards are derived
    // purely from the loaded list, so an already-shown card never grows beyond its cap.
    val groups = remember(visibleResults, bookCounts) { groupResultsByBook(visibleResults, bookCounts) }
    val lineToGroupIndex =
        remember(groups) {
            buildMap {
                groups.forEachIndexed { gi, g -> g.allLineIds.forEach { put(it, gi) } }
            }
        }
    // Expansion state, hoisted so it survives LazyColumn item recycling and regrouping.
    val expandedBooks = remember { mutableStateMapOf<Long, Boolean>() }
    val expandedHits = remember { mutableStateMapOf<Long, List<SearchResult>>() }
    val contentScope = rememberCoroutineScope()
    val currentLoadBookHits by rememberUpdatedState(loadBookHits)
    val onToggleExpand: (BookGroup) -> Unit = { group ->
        val id = group.bookId
        if (expandedBooks[id] == true) {
            expandedBooks[id] = false
        } else {
            expandedBooks[id] = true
            val loadedCount = 1 + group.secondaries.size
            if (group.totalCount > loadedCount && id !in expandedHits) {
                contentScope.launch {
                    // Always store (even an empty result) so the loading spinner resolves.
                    expandedHits[id] = runCatching { currentLoadBookHits(id) }.getOrDefault(emptyList())
                }
            }
        }
    }
    val findQuery by AppSettings.findQueryFlow(tabId).collectAsState("")
    val showFind by AppSettings.findBarOpenFlow(tabId).collectAsState()
    val activeFindQuery = if (showFind) findQuery else ""
    val scope = rememberCoroutineScope()
    // Match BookContent main text font settings
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    val isTabSelected = LocalTabSelected.current
    val zoomAnimSpec = if (isTabSelected) tween<Float>(durationMillis = 200) else snap()
    val mainTextSize by animateFloatAsState(
        targetValue = rawTextSize,
        animationSpec = zoomAnimSpec,
        label = "searchMainTextSizeAnim",
    )
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()
    val mainLineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = zoomAnimSpec,
        label = "searchLineHeightAnim",
    )
    val bookFontCode by AppSettings.bookFontCodeFlow.collectAsState()
    val hebrewFontFamily: FontFamily = FontCatalog.familyFor(bookFontCode)
    // Auxiliary size for small labels
    val commentSize by animateFloatAsState(
        targetValue = mainTextSize * 0.875f,
        animationSpec = zoomAnimSpec,
        label = "searchCommentTextSizeAnim",
    )

    // Persist scroll/anchor as the user scrolls (disabled while loading)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .filter { !state.isLoading }
            .collect { (index, offset) ->
                // index is a group index; anchor on the group's primary line
                val anchorId = groups.getOrNull(index)?.primary?.lineId ?: -1L
                actions.onScroll(anchorId, 0, index, offset)
            }
    }

    // Restore scroll/anchor when a new anchor timestamp is emitted.
    // We restore exactly once per timestamp to handle new searches and filter changes.
    var lastRestoredTs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.scrollToAnchorTimestamp, groups) {
        if (groups.isNotEmpty() && lastRestoredTs != state.scrollToAnchorTimestamp) {
            val anchorIdx = if (state.anchorId > 0) lineToGroupIndex[state.anchorId] else null
            val targetIndex = anchorIdx ?: state.scrollIndex
            val targetOffset = state.scrollOffset
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex, targetOffset)
                lastRestoredTs = state.scrollToAnchorTimestamp
            }
        }
    }

    // Infinite scroll: automatically load more when approaching the end of the list
    val currentOnLoadMore by rememberUpdatedState(actions.onLoadMore)
    val hasMore = state.hasMore
    val isLoadingMore = state.isLoadingMore
    val isLoading = state.isLoading
    LaunchedEffect(listState, hasMore, isLoadingMore, isLoading) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Trigger when within 10 items of the end
            lastVisibleItem >= totalItems - 10
        }.distinctUntilChanged()
            .filter { it && hasMore && !isLoadingMore && !isLoading }
            .collect { currentOnLoadMore() }
    }

    val findState = remember(tabId) { TextFieldState() }
    LaunchedEffect(findQuery) {
        val current = findState.text.toString()
        if (current != findQuery) {
            findState.edit { replace(0, length, findQuery) }
        }
    }
    var currentHitIndex by remember { mutableStateOf(-1) }

    fun navigateTo(
        next: Boolean,
        @StructuredScope scope: CoroutineScope,
    ) {
        val q = findState.text.toString()
        if (q.length < 2) return
        val vis = visibleResults
        if (vis.isEmpty()) return
        val size = vis.size
        var i = (if (currentHitIndex in 0 until size) currentHitIndex else 0).coerceIn(0, size - 1)
        val step = if (next) 1 else -1
        var guard = 0
        while (guard++ < size) {
            i = (i + step + size) % size
            val text = buildAnnotatedFromHtml(vis[i].snippet, state.textSize).text
            val start =
                io.github.kdroidfilter.seforimapp.core.presentation.text
                    .findAllMatchesOriginal(text, q)
                    .firstOrNull()
                    ?.first ?: -1
            if (start >= 0) {
                currentHitIndex = i
                val groupIndex = lineToGroupIndex[vis[i].lineId] ?: 0
                scope.launch { listState.scrollToItem(groupIndex, 24) }
                break
            }
        }
    }

    val keyHandler = remember { { _: KeyEvent -> false } }

    Box(modifier = Modifier.fillMaxSize().onPreviewKeyEvent(keyHandler)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Top persistent search toolbar
            SearchToolbar(
                initialQuery = state.query,
                onSubmit = actions.onSubmit,
                onQueryChange = actions.onQueryChange,
                globalExtended = state.globalExtended,
                onGlobalExtendedChange = actions.onGlobalExtendedChange,
                baseBooksHadNoResults = state.baseBooksHadNoResults,
            )

            Spacer(Modifier.height(12.dp))
            val loadedResults = maxOf(state.progressCurrent, visibleResults.size)
            val totalResults =
                maxOf(
                    loadedResults,
                    (state.progressTotal ?: loadedResults.toLong()).coerceAtLeast(loadedResults.toLong()).toInt(),
                )
            // Only show top progress bar during initial search, not lazy loading
            // (lazy loading has its own spinner at the bottom of the list)
            val showProgress = state.isLoading
            val hasTotal = (state.progressTotal ?: 0L) > 0L
            val headerText =
                if (showProgress && hasTotal) {
                    stringResource(Res.string.search_result_count, loadedResults, totalResults)
                } else {
                    stringResource(Res.string.search_result_count_complete, totalResults)
                }
            val progressFraction by animateFloatAsState(
                targetValue =
                    if (showProgress && hasTotal) {
                        val total = (state.progressTotal ?: 1L).coerceAtLeast(1L)
                        (state.progressCurrent.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                animationSpec = tween(durationMillis = 250, easing = LinearEasing),
                label = "searchProgress",
            )

            // Header row: results count + inline loader + optional cancel (space reserved to avoid width jitter)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = headerText,
                    modifier = Modifier.padding(end = 12.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                )

                Box(
                    modifier =
                        Modifier
                            .height(4.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                JewelTheme.globalColors.borders.disabled
                                    .copy(alpha = 0.6f),
                            ),
                ) {
                    if (showProgress && progressFraction > 0f) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressFraction)
                                    .background(JewelTheme.globalColors.outlines.focused),
                        )
                    }
                }

                // Reserve space for the cancel button to prevent width jumps
                Box(
                    modifier = Modifier.width(40.dp).height(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Only show cancel during initial search, not lazy loading
                    if (state.isLoading) {
                        IconActionButton(
                            key = AllIconsKeys.Windows.Close,
                            onClick = actions.onCancelSearch,
                            contentDescription = stringResource(Res.string.search_stop),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Inline progress above replaces the old loading row/spinner

            // Results list
            Box(
                modifier = Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground),
            ) {
                if (visibleResults.isEmpty()) {
                    if (state.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(Res.string.search_searching), fontSize = commentSize.sp)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(Res.string.search_no_results), fontSize = commentSize.sp)
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(start = 32.dp, end = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // One card per book group; primary line's id is unique per group
                            itemsIndexed(items = groups, key = { _, g -> g.primary.lineId }) { _, group ->
                                val windowInfo = LocalWindowInfo.current
                                BookResultCard(
                                    group = group,
                                    textSize = mainTextSize,
                                    lineHeight = mainLineHeight,
                                    fontFamily = hebrewFontFamily,
                                    findQuery = activeFindQuery,
                                    bookFontCode = bookFontCode,
                                    breadcrumbs = breadcrumbs,
                                    onRequestBreadcrumb = actions.onRequestBreadcrumb,
                                    onOpenResult = { result ->
                                        val mods = windowInfo.keyboardModifiers
                                        val openInNewTab = !(mods.isCtrlPressed || mods.isMetaPressed)
                                        actions.onOpenResult(result, openInNewTab)
                                    },
                                    isExpanded = expandedBooks[group.bookId] == true,
                                    expandedHits = expandedHits[group.bookId],
                                    onToggleExpand = onToggleExpand,
                                )
                            }
                            // Loading indicator at the end of the list (only for lazy loading)
                            if (state.isLoadingMore) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                        StableListScrollbar(
                            listState = listState,
                            loadedCount = groups.size,
                            totalCount = maxOf(bookCounts.size, groups.size),
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }

                // Loader overlay while applying filters (category/book/TOC) with quick fade
                androidx.compose.animation.AnimatedVisibility(
                    visible = isFiltering,
                    enter = fadeIn(tween(durationMillis = 120, easing = LinearEasing)),
                    exit = fadeOut(tween(durationMillis = 120, easing = LinearEasing)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.4f))
                                .zIndex(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        // Find bar overlay
        if (showFind) {
            LaunchedEffect(findState.text, showFind) {
                if (showFind) {
                    val q = findState.text.toString()
                    AppSettings.setFindQuery(tabId, if (q.length >= 2) q else "")
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).zIndex(2f)) {
                FindInPageBar(
                    state = findState,
                    onEnterNext = { navigateTo(true, scope) },
                    onEnterPrev = { navigateTo(false, scope) },
                    onClose = { AppSettings.closeFindBar(tabId) },
                )
            }
        }
    }
}

/**
 * A book group for the grouped (Google-style) results view: a primary hit plus the
 * secondary hits of the same book that were loaded contiguously after it.
 * [totalCount] is the exact facet count for the book (may exceed loaded hits).
 */
@Stable
private data class BookGroup(
    val bookId: Long,
    val bookTitle: String,
    val primary: SearchResult,
    val secondaries: List<SearchResult>,
    val totalCount: Int,
) {
    val allLineIds: List<Long>
        get() =
            buildList(secondaries.size + 1) {
                add(primary.lineId)
                secondaries.forEach { add(it.lineId) }
            }
}

/**
 * Group results into one [BookGroup] per book, in first-appearance (relevance) order.
 * A book gets exactly one card: the first time it appears it opens a group, and the same-book
 * hits that immediately follow become its preview secondaries. Once another book intervenes,
 * the group is sealed — later hits of an already-seen book are dropped from the list so the
 * same book never reappears lower down (they are still counted via facets and fetched on expand).
 * The fold is deterministic over the prefix, so earlier cards stay put as more pages load.
 * [bookCounts] gives the exact per-book total (from Lucene facets), coerced to the loaded count.
 */
private fun groupResultsByBook(
    results: List<SearchResult>,
    bookCounts: Map<Long, Int>,
): List<BookGroup> {
    if (results.isEmpty()) return emptyList()
    val groups = ArrayList<BookGroup>()
    val seen = HashSet<Long>()
    var primary: SearchResult? = null
    var secondaries: ArrayList<SearchResult>? = null

    fun seal() {
        val p = primary ?: return
        val secs = secondaries ?: emptyList<SearchResult>()
        val total = (bookCounts[p.bookId] ?: (1 + secs.size)).coerceAtLeast(1 + secs.size)
        groups.add(BookGroup(p.bookId, p.bookTitle, p, secs, total))
    }

    for (r in results) {
        when {
            // Continuation of the book currently being built → keep as a preview secondary.
            primary != null && r.bookId == primary.bookId -> secondaries!!.add(r)
            // A book already shown above → skip so it never appears as a second card.
            r.bookId in seen -> Unit
            // A new book → seal the current group and open one for this book.
            else -> {
                seal()
                seen.add(r.bookId)
                primary = r
                secondaries = ArrayList()
            }
        }
    }
    seal()
    return groups
}

// "Gilt" — the matched search term glows gold like a gilded letter (Torah-ornament palette).
// Two tones so it reads on both light paper and the dark study surface.
private val GiltLight = Color(0xFF9C6B08)
private val GiltDark = Color(0xFFE8B53C)

@Composable
private fun giltColor(): Color = if (JewelTheme.isDark) GiltDark else GiltLight

/** Build the highlighted snippet, shared by primary and secondary rows. */
@Composable
private fun rememberSnippetDisplay(
    snippet: String,
    textSize: Float,
    findQuery: String?,
    bookFontCode: String,
): AnnotatedString {
    // On macOS, some Hebrew fonts lack bold faces; scale slightly to keep emphasis visible.
    val boldScaleForPlatform =
        remember(bookFontCode) {
            val lacksBold = bookFontCode in setOf("notoserifhebrew", "notorashihebrew", "frankruhllibre")
            if (PlatformInfo.isMacOS && lacksBold) 1.08f else 1.0f
        }
    val boldColor = giltColor()
    val footnoteMarkerColor = JewelTheme.globalColors.outlines.focused
    val annotated =
        remember(snippet, textSize, boldScaleForPlatform, boldColor, footnoteMarkerColor) {
            buildAnnotatedFromHtml(
                snippet,
                textSize,
                boldScale = boldScaleForPlatform,
                boldColor = boldColor,
                footnoteMarkerColor = footnoteMarkerColor,
            )
        }
    val baseHl =
        JewelTheme.globalColors.outlines.focused
            .copy(alpha = 0.12f)
    val currentHl =
        JewelTheme.globalColors.outlines.focused
            .copy(alpha = 0.28f)
    return remember(annotated, findQuery, baseHl, currentHl) {
        highlightAnnotatedWithCurrent(
            annotated = annotated,
            query = findQuery,
            currentStart = null,
            currentLength = findQuery?.length,
            baseColor = baseHl,
            currentColor = currentHl,
        )
    }
}

/** Last breadcrumb piece (the TOC leaf) for a result, or null if not resolvable/cached. */
private fun tocLeafOf(
    pieces: List<String>?,
    bookTitle: String,
): String? {
    val list = pieces ?: return null
    val bookIndex = list.indexOfFirst { it == bookTitle }
    return if (bookIndex >= 0 && bookIndex < list.lastIndex) list.last() else null
}

/**
 * Google-style card: a book header with its exact result count, the primary hit shown
 * prominently, a few secondary hits indented, and an inline expander for the rest.
 */
@Composable
private fun BookResultCard(
    group: BookGroup,
    textSize: Float,
    lineHeight: Float,
    fontFamily: FontFamily,
    findQuery: String?,
    bookFontCode: String,
    breadcrumbs: ImmutableMap<Long, List<String>>,
    onRequestBreadcrumb: (SearchResult) -> Unit,
    onOpenResult: (SearchResult) -> Unit,
    isExpanded: Boolean,
    expandedHits: List<SearchResult>?,
    onToggleExpand: (BookGroup) -> Unit,
) {
    val collapsedCap = 3
    val loadedCount = 1 + group.secondaries.size
    val needsFetch = group.totalCount > loadedCount
    val isLoadingExpand = isExpanded && needsFetch && expandedHits == null

    // Hits to render: full set when expanded+fetched, all loaded when expanded, capped otherwise.
    val effectiveHits: List<SearchResult> =
        when {
            isExpanded && !expandedHits.isNullOrEmpty() -> expandedHits
            isExpanded ->
                buildList {
                    add(group.primary)
                    addAll(group.secondaries)
                }
            else ->
                buildList {
                    add(group.primary)
                    addAll(group.secondaries.take(collapsedCap))
                }
        }
    val primary = effectiveHits.first()
    val secondaries = effectiveHits.drop(1)
    val remaining = (group.totalCount - effectiveHits.size).coerceAtLeast(0)
    val showExpander = isExpanded || remaining > 0

    val accent = JewelTheme.globalColors.outlines.focused
    val cardShape = RoundedCornerShape(14.dp)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = cardShape)
                .clip(cardShape)
                .background(JewelTheme.globalColors.panelBackground)
                .border(1.dp, JewelTheme.globalColors.borders.normal, cardShape),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            // Header: book title (sans, bold) + filled count chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = group.bookTitle,
                    color = JewelTheme.globalColors.text.normal,
                    fontSize = (textSize * 1.15f).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onOpenResult(group.primary) },
                )
                if (group.totalCount > 1) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent)
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = group.totalCount.toString(),
                            color = Color.White,
                            fontSize = (textSize * 0.8f).sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Primary hit: serif snippet (gilt term) with its location on the side,
            // consistent with the secondaries below so the snippets read as one list.
            val primaryPieces = breadcrumbs[primary.lineId]
            val currentOnRequestBreadcrumb by rememberUpdatedState(onRequestBreadcrumb)
            LaunchedEffect(primary.lineId) { if (primaryPieces == null) currentOnRequestBreadcrumb(primary) }
            val primaryLeaf = remember(primaryPieces, primary.bookTitle) { tocLeafOf(primaryPieces, primary.bookTitle) }
            val primaryDisplay = rememberSnippetDisplay(primary.snippet, textSize, findQuery, bookFontCode)
            Row(
                verticalAlignment = Alignment.Top,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenResult(primary) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(vertical = 5.dp),
            ) {
                // Reference column (leading): the location, aligned like a concordance index.
                Text(
                    text = primaryLeaf.orEmpty(),
                    color = accent,
                    fontSize = (textSize * 0.78f).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = (textSize * 4.4f).dp).padding(top = (textSize * 0.22f).dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = primaryDisplay,
                    color = JewelTheme.globalColors.text.normal,
                    fontFamily = fontFamily,
                    lineHeight = (textSize * lineHeight).sp,
                    fontSize = textSize.sp,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.weight(1f),
                )
            }

            // Secondary hits: a thin divider tops every row (the first one also separates them
            // from the primary). Each row is a fixed two-line height, so rows are uniform.
            if (secondaries.isNotEmpty()) {
                val dividerColor =
                    JewelTheme.globalColors.borders.normal
                        .copy(alpha = 0.7f)
                val secRow: @Composable (SearchResult) -> Unit = { sec ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                        SecondaryResultRow(
                            result = sec,
                            textSize = textSize,
                            lineHeight = lineHeight,
                            fontFamily = fontFamily,
                            findQuery = findQuery,
                            bookFontCode = bookFontCode,
                            pieces = breadcrumbs[sec.lineId],
                            onRequestBreadcrumb = onRequestBreadcrumb,
                            onClick = { onOpenResult(sec) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (isExpanded) {
                    // A non-lazy scrolling Column so the height ADAPTS to the content (a
                    // LazyColumn with fillMaxSize would always take the max). It grows with the
                    // results, caps at ~4 rows, then scrolls. The expand already loads every hit,
                    // so a plain Column is fine and its ScrollState gives an exact, stable thumb.
                    val secScroll = rememberScrollState()
                    val showBar = secScroll.maxValue > 0
                    val maxViewportDp = (textSize * lineHeight * 2f + 31f) * 4f
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = maxViewportDp.dp)) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(secScroll)
                                    .padding(end = if (showBar) 12.dp else 0.dp),
                        ) {
                            secondaries.forEach { sec -> secRow(sec) }
                        }
                        if (showBar) {
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                adapter = rememberScrollbarAdapter(secScroll),
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        secondaries.forEach { sec -> secRow(sec) }
                    }
                }
            }

            // Inline expander
            if (showExpander) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onToggleExpand(group) }
                            .padding(vertical = 4.dp, horizontal = 6.dp),
                ) {
                    if (isLoadingExpand) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text =
                            if (isExpanded) {
                                stringResource(Res.string.search_collapse)
                            } else {
                                stringResource(Res.string.search_more_in_book, remaining)
                            },
                        color = accent,
                        fontSize = (textSize * 0.85f).sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        key = if (isExpanded) AllIconsKeys.General.ChevronUp else AllIconsKeys.General.ChevronDown,
                        contentDescription = stringResource(Res.string.chevron_icon_description),
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }

        // Book "binding": a bold accent strip on the start (right, in RTL) edge.
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(accent),
        )
    }
}

/**
 * Stable scrollbar reused by the results list and the expanded-secondaries list — wraps the
 * book pane's [ContentAwareScrollbarShell]. The thumb is sized against [totalCount] (the facet
 * book count for the paged results list; the item count for a fully-loaded expand list) using a
 * latched average item height captured on the first laid-out frame, so the thumb keeps a stable
 * size and does NOT shrink as more items load. [loadedCount] bounds drag targets to what is
 * currently in the list; position tracks the live scroll offset.
 */
@Composable
private fun StableListScrollbar(
    listState: LazyListState,
    loadedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    if (loadedCount <= 0 || totalCount <= 0) return
    // Capture the average card height once it is known; keep it fixed so the thumb size is
    // stable (re-captured only when the total changes, i.e. a new search).
    var avgItemPx by remember(totalCount) { mutableFloatStateOf(0f) }
    LaunchedEffect(totalCount, listState) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) 0f else visible.sumOf { it.size }.toFloat() / visible.size
        }.first { it > 0f }.let { avgItemPx = it }
    }
    if (avgItemPx <= 0f) return

    val geom by remember(listState, totalCount, avgItemPx) {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val total = avgItemPx * totalCount
            val thumb = (viewport / total).coerceIn(0f, 1f)
            val first = info.visibleItemsInfo.firstOrNull()
            val firstIdx = first?.index ?: 0
            val innerFraction = if (first != null && first.size > 0) (-first.offset.toFloat() / first.size).coerceIn(0f, 1f) else 0f
            val maxScroll = (total - viewport).coerceAtLeast(1f)
            val position = (((firstIdx + innerFraction) * avgItemPx) / maxScroll).coerceIn(0f, 1f)
            thumb to position
        }
    }
    val (thumbSize, position) = geom
    if (thumbSize >= 1f) return // everything fits — no scrollbar

    ContentAwareScrollbarShell(
        listState = listState,
        position = position,
        thumbSize = thumbSize,
        visualsLabel = "search_results_scrollbar",
        onApplyTarget = { ratio, _ ->
            // Jump within the loaded range; scrolling near the end triggers the existing
            // lazy-load, so the thumb can be dragged further as more pages arrive.
            val target = (ratio * (totalCount - 1)).toInt().coerceIn(0, loadedCount - 1)
            listState.requestScrollToItem(target)
        },
        modifier = modifier,
    )
}

@Composable
private fun SecondaryResultRow(
    result: SearchResult,
    textSize: Float,
    lineHeight: Float,
    fontFamily: FontFamily,
    findQuery: String?,
    bookFontCode: String,
    pieces: List<String>?,
    onRequestBreadcrumb: (SearchResult) -> Unit,
    onClick: () -> Unit,
) {
    val currentOnRequestBreadcrumb by rememberUpdatedState(onRequestBreadcrumb)
    LaunchedEffect(result.lineId) { if (pieces == null) currentOnRequestBreadcrumb(result) }
    val tocLeaf = remember(pieces, result.bookTitle) { tocLeafOf(pieces, result.bookTitle) }
    val display = rememberSnippetDisplay(result.snippet, textSize, findQuery, bookFontCode)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Reference column (leading), aligned with the primary's.
        Text(
            text = tocLeaf.orEmpty(),
            color = JewelTheme.globalColors.text.disabledSelected,
            fontSize = (textSize * 0.72f).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.widthIn(min = (textSize * 4.4f).dp).padding(top = (textSize * 0.18f).dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = display,
            color = JewelTheme.globalColors.text.normal,
            fontFamily = fontFamily,
            lineHeight = (textSize * lineHeight).sp,
            fontSize = (textSize * 0.92f).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
