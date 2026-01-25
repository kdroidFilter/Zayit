package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.presentation.components.CustomToggleableChip
import io.github.kdroidfilter.seforimapp.core.presentation.components.FindInPageBar
import io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotatedWithCurrent
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
import io.github.kdroidfilter.seforimapp.features.search.domain.TocTree
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.*

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
            tooltipText = stringResource(Res.string.search_extended_tooltip),
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
    visibleResults: List<SearchResult>,
    isFiltering: Boolean,
    breadcrumbs: Map<Long, List<String>>,
    searchTree: List<SearchResultViewModel.SearchTreeCategory>,
    selectedCategoryIds: Set<Long>,
    selectedBookIds: Set<Long>,
    selectedTocIds: Set<Long>,
    tocCounts: Map<Long, Int>,
    tocTree: TocTree?,
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
                            SearchResultContentMvi(
                                state = searchUi,
                                visibleResults = visibleResults,
                                isFiltering = isFiltering,
                                breadcrumbs = breadcrumbs,
                                actions = actions,
                                tabId = tabId,
                            )
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
    visibleResults: List<SearchResult>,
    isFiltering: Boolean,
    breadcrumbs: Map<Long, List<String>>,
    actions: SearchShellActions,
    tabId: String,
) {
    val listState = rememberLazyListState()
    val findQuery by AppSettings.findQueryFlow(tabId).collectAsState("")
    val showFind by AppSettings.findBarOpenFlow(tabId).collectAsState()
    val activeFindQuery = if (showFind) findQuery else ""
    val scope = rememberCoroutineScope()
    // Match BookContent main text font settings
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    val mainTextSize by animateFloatAsState(
        targetValue = rawTextSize,
        animationSpec = tween(durationMillis = 200),
        label = "searchMainTextSizeAnim",
    )
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()
    val mainLineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = tween(durationMillis = 200),
        label = "searchLineHeightAnim",
    )
    val bookFontCode by AppSettings.bookFontCodeFlow.collectAsState()
    val hebrewFontFamily: FontFamily = FontCatalog.familyFor(bookFontCode)
    // Auxiliary size for small labels
    val commentSize by animateFloatAsState(
        targetValue = mainTextSize * 0.875f,
        animationSpec = tween(durationMillis = 200),
        label = "searchCommentTextSizeAnim",
    )

    // Persist scroll/anchor as the user scrolls (disabled while loading)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .filter { !state.isLoading }
            .collect { (index, offset) ->
                val items = state.results
                val anchorId = items.getOrNull(index)?.lineId ?: -1L
                actions.onScroll(anchorId, 0, index, offset)
            }
    }

    // Restore scroll/anchor when a new anchor timestamp is emitted.
    // We restore exactly once per timestamp to handle new searches and filter changes.
    var lastRestoredTs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.scrollToAnchorTimestamp, state.results) {
        if (state.results.isNotEmpty() && lastRestoredTs != state.scrollToAnchorTimestamp) {
            val anchorIdx =
                if (state.anchorId > 0) {
                    state.results.indexOfFirst { it.lineId == state.anchorId }.takeIf { it >= 0 }
                } else {
                    null
                }
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
    var currentMatchStart by remember { mutableStateOf(-1) }

    fun recomputeMatches(query: String) { // removed: counter not needed
    }

    fun navigateTo(next: Boolean) {
        val q = findState.text.toString()
        if (q.length < 2) return
        val vis = visibleResults
        if (vis.isEmpty()) return
        val size = vis.size
        var i = if (currentHitIndex in 0 until size) currentHitIndex else listState.firstVisibleItemIndex
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
                currentMatchStart = start
                scope.launch { listState.scrollToItem(i, 24) }
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Use a composite key to ensure uniqueness across books and pages
                        itemsIndexed(items = visibleResults, key = {
                            index,
                            it,
                            ->
                            Pair(it.bookId, Pair(it.lineId, index))
                        }) { idx, result ->
                            val windowInfo = LocalWindowInfo.current
                            SearchResultItemGoogleStyle(
                                result = result,
                                textSize = mainTextSize,
                                lineHeight = mainLineHeight,
                                fontFamily = hebrewFontFamily,
                                findQuery = activeFindQuery,
                                currentMatchStart = if (idx == currentHitIndex) currentMatchStart else null,
                                onClick = {
                                    val mods = windowInfo.keyboardModifiers
                                    val openInNewTab = !(mods.isCtrlPressed || mods.isMetaPressed)
                                    actions.onOpenResult(result, openInNewTab)
                                },
                                breadcrumbs = breadcrumbs,
                                onRequestBreadcrumb = actions.onRequestBreadcrumb,
                                bookFontCode = bookFontCode,
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
                    onEnterNext = { navigateTo(true) },
                    onEnterPrev = { navigateTo(false) },
                    onClose = { AppSettings.closeFindBar(tabId) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultItemGoogleStyle(
    result: SearchResult,
    textSize: Float,
    lineHeight: Float,
    fontFamily: FontFamily,
    findQuery: String?,
    onClick: () -> Unit,
    breadcrumbs: Map<Long, List<String>>,
    onRequestBreadcrumb: (SearchResult) -> Unit,
    bookFontCode: String,
    currentMatchStart: Int? = null,
) {
    // Breadcrumb pieces come from state; request on-demand via callback
    val pieces = breadcrumbs[result.lineId]
    val currentOnRequestBreadcrumb by rememberUpdatedState(onRequestBreadcrumb)
    LaunchedEffect(result.lineId) { if (pieces == null) currentOnRequestBreadcrumb(result) }

    // Derive book title and TOC leaf for the header line
    val bookTitle = result.bookTitle
    val tocLeaf: String? =
        remember(pieces, bookTitle) {
            val list = pieces ?: emptyList()
            val bookIndex = list.indexOfFirst { it == bookTitle }
            if (bookIndex >= 0 && bookIndex < list.lastIndex) list.last() else null
        }

    // Full path string for the footer line
    val sep = stringResource(Res.string.breadcrumb_separator)
    val fullPath: String? = if (pieces.isNullOrEmpty()) null else pieces.joinToString(sep)

    // Build annotated snippet with bold segments coming from HTML (<b> ... )
    // On macOS, some Hebrew fonts in our catalog don't include bold faces.
    // Apply a subtle boldScale to keep emphasis visible on those fonts.
    val boldScaleForPlatform =
        remember(bookFontCode) {
            val lacksBold = bookFontCode in setOf("notoserifhebrew", "notorashihebrew", "frankruhllibre")
            if (PlatformInfo.isMacOS && lacksBold) 1.08f else 1.0f
        }
    val boldColor = JewelTheme.globalColors.outlines.focused
    val footnoteMarkerColor = JewelTheme.globalColors.outlines.focused
    val annotated: AnnotatedString =
        remember(result.snippet, textSize, boldScaleForPlatform, boldColor, footnoteMarkerColor) {
            // Log the snippet with bold tags for debugging
            debugln { "[SearchResult] Book: ${result.bookTitle}, LineId: ${result.lineId}" }
            debugln { "[SearchResult] Snippet with bold tags: ${result.snippet}" }
            debugln { "[SearchResult] ---" }
            // Keep keyword emphasis without oversized glyphs (slight scale on mac for non-bold fonts)
            buildAnnotatedFromHtml(
                result.snippet,
                textSize,
                boldScale = boldScaleForPlatform,
                boldColor = boldColor,
                footnoteMarkerColor = footnoteMarkerColor,
            )
        }
    // Softer overlays for better legibility
    val baseHl =
        JewelTheme.globalColors.outlines.focused
            .copy(alpha = 0.12f)
    val currentHl =
        JewelTheme.globalColors.outlines.focused
            .copy(alpha = 0.28f)
    val display =
        remember(annotated, findQuery, currentMatchStart, baseHl, currentHl) {
            highlightAnnotatedWithCurrent(
                annotated = annotated,
                query = findQuery,
                currentStart = currentMatchStart?.takeIf { it >= 0 },
                currentLength = findQuery?.length,
                baseColor = baseHl,
                currentColor = currentHl,
            )
        }

    // Visual layout inspired by Google results, styled with Jewel
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
    ) {
        // Top: small book title – toc leaf
        Row(
            modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
        ) {
            val header =
                if (tocLeaf.isNullOrBlank()) {
                    bookTitle
                } else {
                    buildString {
                        append(bookTitle)
                        append(stringResource(Res.string.breadcrumb_separator))
                        append(tocLeaf)
                    }
                }
            Text(
                text = header,
                color = JewelTheme.globalColors.text.selected,
                fontSize = (textSize * 1.1f).sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))

        // Middle: the snippet text with bold keywords
        Text(
            text = display,
            color = JewelTheme.globalColors.text.normal,
            fontFamily = fontFamily,
            lineHeight = (textSize * lineHeight).sp,
            fontSize = textSize.sp,
            textAlign = TextAlign.Justify,
        )

        // Bottom: smaller full path of the book
        if (!fullPath.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = fullPath,
                color = JewelTheme.globalColors.text.disabledSelected,
                fontFamily = fontFamily,
                fontSize = (textSize * 0.8f).sp,
                maxLines = 1,
            )
        }
    }
}
