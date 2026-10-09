package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.presentation.components.FindInPageBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.syncFindField
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotated
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.features.author.AUTHOR_ERAS
import io.github.kdroidfilter.seforimapp.features.author.authorYears
import io.github.kdroidfilter.seforimapp.features.author.plainPersonLinks
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookTabUi
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.BookContentPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.ContentAwareScrollbarShell
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.features.search.domain.SearchEntity
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.WritingHand
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.TextFieldMetrics
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.textFieldStyle
import seforimapp.seforimapp.generated.resources.*
import java.text.NumberFormat

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
    val onShowOnlyCategory: (Long?) -> Unit = {},
    val onOpenBook: (Long) -> Unit = {},
    val onOpenAuthor: (Long) -> Unit = {},
)

@Composable
private fun SearchToolbar(
    initialQuery: String,
    onSubmit: (query: String) -> Unit,
    onQueryChange: (String) -> Unit,
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

    // A pill-shaped field, as on Google
    val baseStyle = JewelTheme.textFieldStyle
    val pillStyle =
        remember(baseStyle) {
            TextFieldStyle(
                colors = baseStyle.colors,
                metrics =
                    TextFieldMetrics(
                        borderWidth = baseStyle.metrics.borderWidth,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        cornerSize = CornerSize(50),
                        minSize = baseStyle.metrics.minSize,
                    ),
                iconButtonStyle = baseStyle.iconButtonStyle,
            )
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Query field
        TextField(
            state = searchState,
            style = pillStyle,
            modifier =
                Modifier.weight(1f).height(40.dp).onPreviewKeyEvent { ev ->
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
            trailingIcon = {
                if (searchState.text.isNotEmpty()) {
                    Icon(
                        AllIconsKeys.Actions.Close,
                        stringResource(Res.string.search_clear),
                        Modifier.clickable { searchState.clearText() }.pointerHoverIcon(PointerIcon.Hand),
                    )
                }
            },
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
                    .TextStyle(fontSize = 15.sp),
        )
    }
}

/**
 * The text of a search tab: the results, or the book opened from them. Its facet panes (category
 * tree, contents) and the book's panes are dock satellites of the window (see `ReaderPanes`).
 */
@Composable
fun SearchResultInBookShellMvi(
    bookUiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    diacritics: DiacriticsMode,
    // Search state
    searchUi: SearchUiState,
    visibleResults: ImmutableList<SearchResult>,
    isFiltering: Boolean,
    breadcrumbs: ImmutableMap<Long, List<String>>,
    bookCounts: Map<Long, Int>,
    // The results' top categories, as tabs above them
    categories: ImmutableList<SearchResultViewModel.SearchTreeCategory>,
    selectedCategoryIds: Set<Long>,
    // The book or author the query names, beside the results
    entity: SearchEntity?,
    actions: SearchShellActions,
    tabUi: BookTabUi,
) {
    val tabId = bookUiState.tabId
    val currentOnEvent by rememberUpdatedState(onEvent)
    DisposableEffect(Unit) {
        onDispose { currentOnEvent(BookContentEvent.SaveState) }
    }

    val panelCardModifier =
        Modifier
            .fillMaxSize()
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(JewelTheme.globalColors.panelBackground)

    val showBookContent = bookUiState.navigation.selectedBook != null && bookUiState.providers != null
    if (showBookContent) {
        BookContentPanel(
            uiState = bookUiState,
            onEvent = onEvent,
            diacritics = diacritics,
            tabUi = tabUi,
        )
    } else {
        Box(modifier = panelCardModifier) {
            SearchResultContentMvi(
                state = searchUi,
                visibleResults = visibleResults,
                isFiltering = isFiltering,
                breadcrumbs = breadcrumbs,
                bookCounts = bookCounts,
                categories = categories,
                selectedCategoryIds = selectedCategoryIds,
                entity = entity,
                actions = actions,
                tabId = tabId,
            )
        }
    }
}

@Composable
private fun SearchResultContentMvi(
    state: SearchUiState,
    visibleResults: ImmutableList<SearchResult>,
    isFiltering: Boolean,
    breadcrumbs: ImmutableMap<Long, List<String>>,
    bookCounts: Map<Long, Int>,
    categories: ImmutableList<SearchResultViewModel.SearchTreeCategory>,
    selectedCategoryIds: Set<Long>,
    entity: SearchEntity?,
    actions: SearchShellActions,
    tabId: String,
) {
    val appSettings = LocalAppGraph.current.appSettings
    val listState = rememberLazyListState()
    // A flat list by relevance, as on Google: at most two passages of a book in a row
    val items = remember(visibleResults, bookCounts) { flattenResults(visibleResults, bookCounts) }
    val lineToItemIndex =
        remember(items) {
            buildMap {
                items.forEachIndexed { index, item -> item.lineIds.forEach { put(it, index) } }
            }
        }
    val findQuery by appSettings.findQueryFlow(tabId).collectAsState("")
    val showFind by appSettings.findBarOpenFlow(tabId).collectAsState()
    val findFocusRequest by appSettings.findFocusRequestFlow(tabId).collectAsState()
    val activeFindQuery = if (showFind) findQuery else ""
    val scope = rememberCoroutineScope()
    // Match BookContent main text font settings
    val rawTextSize by appSettings.textSizeFlow.collectAsState()
    val isTabSelected = LocalTabSelected.current
    val zoomAnimSpec = if (isTabSelected) tween<Float>(durationMillis = 200) else snap()
    val mainTextSize by animateFloatAsState(
        targetValue = rawTextSize,
        animationSpec = zoomAnimSpec,
        label = "searchMainTextSizeAnim",
    )
    val bookFontCode by appSettings.bookFontCodeFlow.collectAsState()
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
                // index is an item index; anchor on the item's passage
                val anchorId = items.getOrNull(index)?.hit?.lineId ?: -1L
                actions.onScroll(anchorId, 0, index, offset)
            }
    }

    // Restore scroll/anchor when a new anchor timestamp is emitted.
    // We restore exactly once per timestamp to handle new searches and filter changes.
    var lastRestoredTs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.scrollToAnchorTimestamp, items) {
        if (items.isNotEmpty() && lastRestoredTs != state.scrollToAnchorTimestamp) {
            val anchorIdx = if (state.anchorId > 0) lineToItemIndex[state.anchorId] else null
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
    LaunchedEffect(findQuery) { syncFindField(findState, findQuery) }
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
                val itemIndex = lineToItemIndex[vis[i].lineId] ?: 0
                scope.launch { listState.scrollToItem(itemIndex, 24) }
                break
            }
        }
    }

    // Ctrl/Cmd+G and F3 come from the window's shortcuts
    val navigateToLatest by rememberUpdatedState<(Boolean) -> Unit> { navigateTo(it, scope) }
    LaunchedEffect(tabId) {
        appSettings.findStepRequests(tabId).collect { forward -> navigateToLatest(forward) }
    }

    val keyHandler = remember { { _: KeyEvent -> false } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().onPreviewKeyEvent(keyHandler)) {
        val showPanel = entity != null && maxWidth >= PANEL_MIN_WIDTH
        // One centered reading column for the bar, the status and the results, as on Google,
        // with the panel of the book or author the query names beside it
        Row(modifier = Modifier.align(Alignment.TopCenter).fillMaxHeight()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = 760.dp)
                        .weight(1f, fill = false)
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top persistent search toolbar
                SearchToolbar(
                    initialQuery = state.query,
                    onSubmit = actions.onSubmit,
                    onQueryChange = actions.onQueryChange,
                )

                if (categories.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    CategoryTabs(categories, selectedCategoryIds, actions.onShowOnlyCategory)
                }
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
                    SearchStatusLine(
                        total = totalResults,
                        isLoading = showProgress,
                        globalExtended = state.globalExtended,
                        baseBooksHadNoResults = state.baseBooksHadNoResults,
                        onGlobalExtendedChange = actions.onGlobalExtendedChange,
                        modifier = Modifier.padding(end = 12.dp),
                    )

                    // The progress shows while searching only
                    if (showProgress) {
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
                    } else {
                        Spacer(Modifier.weight(1f))
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
                                modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                itemsIndexed(items = items, key = { _, item -> item.hit.lineId }) { _, item ->
                                    val windowInfo = LocalWindowInfo.current
                                    ResultView(
                                        item = item,
                                        findQuery = activeFindQuery,
                                        bookFontCode = bookFontCode,
                                        breadcrumbs = breadcrumbs,
                                        onRequestBreadcrumb = actions.onRequestBreadcrumb,
                                        onOpenResult = { result ->
                                            val mods = windowInfo.keyboardModifiers
                                            val openInNewTab = !(mods.isCtrlPressed || mods.isMetaPressed)
                                            actions.onOpenResult(result, openInNewTab)
                                        },
                                        onMoreInBook = { actions.onBookCheckedChange(item.hit.bookId, true) },
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
                                loadedCount = items.size,
                                totalCount = items.size,
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
            if (showPanel && entity != null) {
                EntityPanel(
                    entity = entity,
                    onOpenBook = actions.onOpenBook,
                    onSearchInBook = { actions.onBookCheckedChange(it, true) },
                    onOpenAuthor = actions.onOpenAuthor,
                    modifier = Modifier.padding(top = 112.dp, end = 16.dp).width(PANEL_WIDTH),
                )
            }
        }

        // Find bar overlay
        if (showFind) {
            LaunchedEffect(findState.text, showFind) {
                if (showFind) {
                    val q = findState.text.toString()
                    appSettings.setFindQuery(tabId, if (q.length >= 2) q else "")
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).zIndex(2f)) {
                FindInPageBar(
                    state = findState,
                    onEnterNext = { navigateTo(true, scope) },
                    onEnterPrev = { navigateTo(false, scope) },
                    onClose = { appSettings.closeFindBar(tabId) },
                    focusRequest = findFocusRequest,
                )
            }
        }
    }
}

/**
 * One result of the flat list: a passage, maybe a second one of the same book shown under it
 * (as Google does for two pages of a site), and how many more the book has ([more], on the
 * book's first result only).
 */
@Stable
private data class ResultItem(
    val hit: SearchResult,
    val sub: SearchResult?,
    val more: Int,
) {
    val lineIds: List<Long> get() = listOfNotNull(hit.lineId, sub?.lineId)
}

/**
 * The results in relevance order, at most two passages of a book in a row: a run of one book
 * shows its first passage with its second under it, and skips the rest. A book may come back
 * lower with a later run. [bookCounts] (exact per-book totals) gives the "more in this book"
 * count of a book's first result.
 */
private fun flattenResults(
    results: List<SearchResult>,
    bookCounts: Map<Long, Int>,
): List<ResultItem> {
    val runs = ArrayList<Pair<SearchResult, SearchResult?>>()
    var runBook = -1L
    var runLength = 0
    for (r in results) {
        if (r.bookId == runBook) {
            runLength++
            if (runLength == 2) runs[runs.lastIndex] = runs.last().first to r
        } else {
            runBook = r.bookId
            runLength = 1
            runs += r to null
        }
    }
    val seen = HashSet<Long>()
    return runs.map { (hit, sub) ->
        val shown = if (sub != null) 2 else 1
        val more = if (seen.add(hit.bookId)) ((bookCounts[hit.bookId] ?: shown) - shown).coerceAtLeast(0) else 0
        ResultItem(hit, sub, more)
    }
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
    // The matched words' color: gilt by default, the text's own for Google-like plain bold
    boldColor: Color = giltColor(),
): AnnotatedString {
    // On macOS, some Hebrew fonts lack bold faces; scale slightly to keep emphasis visible.
    val boldScaleForPlatform = FontCatalog.boldScaleFor(bookFontCode)
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
    return remember(annotated, findQuery, baseHl) { highlightAnnotated(annotated, findQuery, baseHl) }
}

/**
 * A result as on Google: its categories in small, the book as the colored title with the passage's
 * place beside it in grey, the passage in grey with the matched words bold, then maybe a second
 * passage of the book indented and a link to the book's other results.
 */
@Composable
private fun ResultView(
    item: ResultItem,
    findQuery: String?,
    bookFontCode: String,
    breadcrumbs: ImmutableMap<Long, List<String>>,
    onRequestBreadcrumb: (SearchResult) -> Unit,
    onOpenResult: (SearchResult) -> Unit,
    onMoreInBook: () -> Unit,
) {
    val hit = item.hit
    val accent = JewelTheme.globalColors.outlines.focused
    val grey = JewelTheme.globalColors.text.info
    val currentOnRequestBreadcrumb by rememberUpdatedState(onRequestBreadcrumb)
    val pieces = breadcrumbs[hit.lineId]
    LaunchedEffect(hit.lineId) { if (pieces == null) currentOnRequestBreadcrumb(hit) }
    val (categories, place) = remember(pieces, hit.bookTitle) { categoriesAndPlace(pieces, hit.bookTitle) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        if (categories.isNotEmpty()) {
            Text(categories, color = grey, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ResultLink(hit.bookTitle, accent, 18.sp) { onOpenResult(hit) }
            if (place != null) {
                Text(
                    "· $place",
                    color = grey,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Snippet(hit, findQuery, bookFontCode) { onOpenResult(hit) }

        item.sub?.let { sub ->
            val subPieces = breadcrumbs[sub.lineId]
            LaunchedEffect(sub.lineId) { if (subPieces == null) currentOnRequestBreadcrumb(sub) }
            val subPlace = remember(subPieces, sub.bookTitle) { categoriesAndPlace(subPieces, sub.bookTitle).second }
            Column(modifier = Modifier.padding(start = 22.dp, top = 8.dp)) {
                ResultLink(subPlace ?: sub.bookTitle, accent, 14.sp) { onOpenResult(sub) }
                Snippet(sub, findQuery, bookFontCode) { onOpenResult(sub) }
            }
        }
        if (item.more > 0) {
            Box(Modifier.padding(start = if (item.sub != null) 22.dp else 0.dp, top = 6.dp)) {
                ResultLink(
                    stringResource(Res.string.search_more_from_book, item.more, hit.bookTitle),
                    accent,
                    13.sp,
                    onClick = onMoreInBook,
                )
            }
        }
    }
}

/** A passage: two lines of grey text, the matched words bold in the text's color. */
@Composable
private fun Snippet(
    hit: SearchResult,
    findQuery: String?,
    bookFontCode: String,
    onClick: () -> Unit,
) {
    val ink = JewelTheme.globalColors.text.normal
    val display = rememberSnippetDisplay(hit.snippet, SNIPPET_SIZE, findQuery, bookFontCode, boldColor = ink)
    Text(
        text = display,
        color = JewelTheme.globalColors.text.info,
        fontSize = SNIPPET_SIZE.sp,
        lineHeight = (SNIPPET_SIZE * 1.65f).sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick),
    )
}

private const val SNIPPET_SIZE = 14f

/** The result's categories (הלכה › ראשונים) and its place in the book (סימן קד), once its breadcrumb is known. */
private fun categoriesAndPlace(
    pieces: List<String>?,
    bookTitle: String,
): Pair<String, String?> {
    val bookIndex = pieces?.indexOf(bookTitle) ?: -1
    if (pieces == null || bookIndex < 0) return "" to null
    val place =
        pieces
            .drop(bookIndex + 1)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { null }
    return pieces.take(bookIndex).joinToString(" › ") to place
}

/** A text link in [color], underlined on hover. */
@Composable
private fun ResultLink(
    text: String,
    color: Color,
    fontSize: TextUnit,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (hovered) TextDecoration.Underline else null,
        modifier = Modifier.hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick),
    )
}

/**
 * The results' top categories as tabs, as Google's "All · Images · News": the most frequent first,
 * one at a time; "הכל" shows them all.
 */
@Composable
private fun CategoryTabs(
    categories: List<SearchResultViewModel.SearchTreeCategory>,
    selectedCategoryIds: Set<Long>,
    onShowOnly: (Long?) -> Unit,
) {
    val sorted = remember(categories) { categories.sortedByDescending { it.count } }
    val selected = sorted.firstOrNull { it.category.id in selectedCategoryIds }?.category?.id
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        CategoryTab(stringResource(Res.string.search_tab_all), null, selected == null) { onShowOnly(null) }
        sorted.forEach { node ->
            CategoryTab(node.category.title, node.count, selected == node.category.id) { onShowOnly(node.category.id) }
        }
    }
}

@Composable
private fun CategoryTab(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    val shape = RoundedCornerShape(50)
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier =
            Modifier
                .clip(shape)
                .background(
                    when {
                        selected -> accent.copy(alpha = 0.15f)
                        hovered ->
                            JewelTheme.globalColors.text.normal
                                .copy(alpha = 0.06f)
                        else -> Color.Transparent
                    },
                ).border(1.dp, if (selected) accent else JewelTheme.globalColors.borders.normal, shape)
                .hoverable(hover)
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) accent else JewelTheme.globalColors.text.normal, maxLines = 1)
        if (count != null) {
            Text(count.toString(), fontSize = 11.sp, color = JewelTheme.globalColors.text.info, maxLines = 1)
        }
    }
}

private val PANEL_WIDTH = 300.dp

// Below this the results take the whole width and the panel stays hidden
private val PANEL_MIN_WIDTH = 1000.dp

/** The book or author a query names, as Google's knowledge panel: what it is, and buttons to go there. */
@Composable
private fun EntityPanel(
    entity: SearchEntity,
    onOpenBook: (Long) -> Unit,
    onSearchInBook: (Long) -> Unit,
    onOpenAuthor: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grey = JewelTheme.globalColors.text.info
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(16.dp),
    ) {
        when (entity) {
            is SearchEntity.BookEntity -> {
                val book = entity.book
                Text(book.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = JewelTheme.globalColors.text.normal)
                if (entity.categories.isNotEmpty()) {
                    Text(entity.categories.joinToString(" › "), fontSize = 12.sp, color = grey)
                }
                book.authors.forEach { author ->
                    ResultLink(
                        AuthorNames.display(author.name),
                        JewelTheme.globalColors.outlines.focused,
                        14.sp,
                    ) { onOpenAuthor(author.id) }
                }
                book.heShortDesc?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 13.sp, lineHeight = 21.sp, color = grey, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    DefaultButton(onClick = { onOpenBook(book.id) }) { Text(stringResource(Res.string.row_action_open)) }
                    OutlinedButton(onClick = { onSearchInBook(book.id) }) { Text(stringResource(Res.string.row_action_search_inside)) }
                }
            }

            is SearchEntity.AuthorEntity -> {
                val details = entity.details
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(WritingHand, contentDescription = null, tint = grey, modifier = Modifier.size(20.dp))
                    Text(
                        AuthorNames.display(details.name),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = JewelTheme.globalColors.text.normal,
                    )
                }
                val facts = listOfNotNull(details.era?.let { AUTHOR_ERAS[it] }, authorYears(details))
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), fontSize = 12.sp, color = grey)
                details.bio?.summary?.let {
                    Text(
                        plainPersonLinks(it).replace("**", ""),
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        color = grey,
                        maxLines = 7,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(stringResource(Res.string.author_books_count, details.books.size), fontSize = 12.sp, color = grey)
                DefaultButton(onClick = { onOpenAuthor(details.id) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(Res.string.search_panel_author_page))
                }
            }
        }
    }
}

/**
 * Where the search looked and how many it found, with the other choice as a link: the base books
 * (the default) or all of them; when the base books had nothing, says so.
 */
@Composable
private fun SearchStatusLine(
    total: Int,
    isLoading: Boolean,
    globalExtended: Boolean,
    baseBooksHadNoResults: Boolean,
    onGlobalExtendedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grey = JewelTheme.globalColors.text.info
    val count = remember(total) { NumberFormat.getIntegerInstance().format(total) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text =
                when {
                    isLoading -> stringResource(Res.string.search_searching)
                    baseBooksHadNoResults -> stringResource(Res.string.search_status_fallback, count)
                    globalExtended -> stringResource(Res.string.search_status_all, count)
                    else -> stringResource(Res.string.search_status_base, count)
                },
            color = grey,
            fontSize = 13.sp,
            maxLines = 1,
        )
        if (!isLoading && !baseBooksHadNoResults) {
            Text("·", color = grey, fontSize = 13.sp)
            ResultLink(
                stringResource(if (globalExtended) Res.string.search_switch_base else Res.string.search_switch_all),
                JewelTheme.globalColors.outlines.focused,
                13.sp,
            ) { onGlobalExtendedChange(!globalExtended) }
        }
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
