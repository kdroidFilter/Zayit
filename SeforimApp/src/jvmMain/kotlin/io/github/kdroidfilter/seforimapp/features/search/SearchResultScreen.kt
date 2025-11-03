package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import androidx.compose.foundation.gestures.ScrollableState
// removed: AnimatedHorizontalProgressBar (classic separator instead)
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.breadcrumb_separator
import seforimapp.seforimapp.generated.resources.search_no_results
import seforimapp.seforimapp.generated.resources.search_searching
import seforimapp.seforimapp.generated.resources.search_load_more
import seforimapp.seforimapp.generated.resources.search_result_count
import seforimapp.seforimapp.generated.resources.search_placeholder
import seforimapp.seforimapp.generated.resources.search_near_selector_label
import seforimapp.seforimapp.generated.resources.search_icon_description
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.StartVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EndVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EnhancedHorizontalSplitPane
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.categorytree.CategoryTreePanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc.BookTocPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.BookContentPanel
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import io.github.kdroidfilter.seforimapp.core.presentation.components.FindInPageBar
import kotlinx.coroutines.launch
import androidx.compose.ui.zIndex
import seforimapp.seforimapp.generated.resources.search_level_1_value
import seforimapp.seforimapp.generated.resources.search_level_2_value
import seforimapp.seforimapp.generated.resources.search_level_3_value
import seforimapp.seforimapp.generated.resources.search_level_4_value
import seforimapp.seforimapp.generated.resources.search_level_5_value

@Composable
private fun SearchToolbar(
    initialQuery: String,
    near: Int,
    onSubmit: (query: String, near: Int) -> Unit,
    onNearChange: (Int) -> Unit,
    onQueryChange: (String) -> Unit,
) {
    var currentNear by remember { mutableStateOf(near) }
    LaunchedEffect(near) { currentNear = near }

    val searchState = remember { TextFieldState() }
    // Keep the field in sync with initial/current query
    LaunchedEffect(initialQuery) {
        val text = searchState.text.toString()
        if (text != initialQuery) {
            searchState.edit { replace(0, length, initialQuery) }
        }
    }

    // Persist live edits so session restore reopens with the last typed text
    LaunchedEffect(Unit) {
        snapshotFlow { searchState.text.toString() }
            .distinctUntilChanged()
            .collect { q -> onQueryChange(q) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Query field
        TextField(
            state = searchState,
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .onPreviewKeyEvent { ev ->
                    if ((ev.key == androidx.compose.ui.input.key.Key.Enter || ev.key == androidx.compose.ui.input.key.Key.NumPadEnter) && ev.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) {
                        val q = searchState.text.toString()
                        onSubmit(q, currentNear)
                        true
                    } else false
                },
            placeholder = { Text(stringResource(Res.string.search_placeholder)) },
            leadingIcon = {
                IconButton(onClick = {
                    val q = searchState.text.toString()
                    onSubmit(q, currentNear)
                }) {
                    Icon(
                        key = AllIconsKeys.Actions.Find,
                        contentDescription = stringResource(Res.string.search_icon_description)
                    )
                }
            },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )

        // NEAR selector
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = stringResource(Res.string.search_near_selector_label), fontSize = 13.sp)
            // Mirror HomeView's semantic levels → NEAR values mapping
            val nearValues = remember { listOf(0, 3, 5, 10, 20) }
            val labels = listOf(
                stringResource(Res.string.search_level_1_value),
                stringResource(Res.string.search_level_2_value),
                stringResource(Res.string.search_level_3_value),
                stringResource(Res.string.search_level_4_value),
                stringResource(Res.string.search_level_5_value),
            )
            val selectedIndex = nearValues.indexOf(currentNear).let { if (it >= 0) it else 2 }
            ListComboBox(
                items = labels,
                selectedIndex = selectedIndex,
                modifier = Modifier.width(160.dp),
                onSelectedItemChange = { idx ->
                    val newNear = nearValues.getOrNull(idx) ?: return@ListComboBox
                    if (newNear != currentNear) {
                        currentNear = newNear
                        onNearChange(newNear)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalSplitPaneApi::class, FlowPreview::class)
@Composable
fun SearchResultInBookShell(
    bookUiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    viewModel: SearchResultViewModel
) {
    // Observe split panes and persist positions similar to BookContentView
    val splitPaneConfigs = listOf(
        SplitPaneConfig(
            splitState = bookUiState.layout.mainSplitState,
            isVisible = bookUiState.navigation.isVisible,
            positionFilter = { it > 0 }
        ),
        SplitPaneConfig(
            splitState = bookUiState.layout.tocSplitState,
            isVisible = bookUiState.toc.isVisible,
            positionFilter = { it > 0 }
        )
    )

    splitPaneConfigs.forEach { config ->
        LaunchedEffect(config.splitState, config.isVisible) {
            if (config.isVisible) {
                snapshotFlow { config.splitState.positionPercentage }
                    .map { ((it * 100).toInt() / 100f) }
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
        StartVerticalBar(uiState = bookUiState, onEvent = onEvent)

        EnhancedHorizontalSplitPane(
            splitPaneState = bookUiState.layout.mainSplitState,
            modifier = Modifier.weight(1f),
            firstMinSize = if (bookUiState.navigation.isVisible) io.github.kdroidfilter.seforimapp.features.bookcontent.state.SplitDefaults.MIN_MAIN else 0f,
            firstContent = {
                if (bookUiState.navigation.isVisible) {
                    CategoryTreePanel(uiState = bookUiState, onEvent = onEvent, searchViewModel = viewModel)
                }
            },
            secondContent = {
                EnhancedHorizontalSplitPane(
                    splitPaneState = bookUiState.layout.tocSplitState,
                    firstMinSize = if (bookUiState.toc.isVisible) io.github.kdroidfilter.seforimapp.features.bookcontent.state.SplitDefaults.MIN_TOC else 0f,
                    firstContent = {
                        if (bookUiState.toc.isVisible) {
                            BookTocPanel(uiState = bookUiState, onEvent = onEvent, searchViewModel = viewModel)
                        }
                    },
                    secondContent = {
                        // Prefer book content only when providers are ready; otherwise keep search visible
                        val showBookContent =
                            bookUiState.navigation.selectedBook != null && bookUiState.providers != null
                        if (showBookContent) {
                            BookContentPanel(uiState = bookUiState, onEvent = onEvent)
                        } else {
                            SearchResultContent(viewModel = viewModel)
                        }
                    },
                    showSplitter = bookUiState.toc.isVisible
                )
            },
            showSplitter = bookUiState.navigation.isVisible
        )

        EndVerticalBar(uiState = bookUiState, onEvent = onEvent)
    }
}

private data class SplitPaneConfig @OptIn(ExperimentalSplitPaneApi::class) constructor(
    val splitState: SplitPaneState,
    val isVisible: Boolean,
    val positionFilter: (Float) -> Boolean
)

@Composable
private fun SearchResultContent(viewModel: SearchResultViewModel) {
    val state = viewModel.uiState.collectAsState().value
    val listState = rememberLazyListState()
    val findQuery by AppSettings.findQueryFlow.collectAsState()
    val visibleResults by viewModel.visibleResultsFlow.collectAsState()
    val scope = rememberCoroutineScope()
    // Match BookContent main text font settings
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    val mainTextSize by animateFloatAsState(
        targetValue = rawTextSize,
        animationSpec = tween(durationMillis = 200),
        label = "searchMainTextSizeAnim"
    )
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()
    val mainLineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = tween(durationMillis = 200),
        label = "searchLineHeightAnim"
    )
    val bookFontCode by AppSettings.bookFontCodeFlow.collectAsState()
    val hebrewFontFamily: FontFamily = FontCatalog.familyFor(bookFontCode)
    // Auxiliary size for small labels
    val commentSize by animateFloatAsState(
        targetValue = mainTextSize * 0.875f,
        animationSpec = tween(durationMillis = 200),
        label = "searchCommentTextSizeAnim"
    )

    // Persist scroll/anchor as the user scrolls (disabled while loading)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .filter { !viewModel.uiState.value.isLoading }
            .collect { (index, offset) ->
                val items = viewModel.uiState.value.results
                val anchorId = items.getOrNull(index)?.lineId ?: -1L
                viewModel.onScroll(anchorId = anchorId, anchorIndex = 0, index = index, offset = offset)
            }
    }

    // Restore scroll/anchor as soon as the anchor is available (even while loading)
    var hasRestored by remember { mutableStateOf(false) }
    LaunchedEffect(state.scrollToAnchorTimestamp, state.results) {
        if (!hasRestored && state.results.isNotEmpty()) {
            val anchorIdx = if (state.anchorId > 0) {
                state.results.indexOfFirst { it.lineId == state.anchorId }.takeIf { it >= 0 }
            } else null
            val targetIndex = anchorIdx ?: state.scrollIndex
            val targetOffset = state.scrollOffset
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex, targetOffset)
                hasRestored = true
            }
        }
    }

    // Find-in-page state (global open state)
    val showFind by AppSettings.findBarOpenFlow.collectAsState()
    val findState = remember { TextFieldState() }
    var currentHitIndex by remember { mutableStateOf(-1) }
    var currentMatchStart by remember { mutableStateOf(-1) }

    fun recomputeMatches(query: String) { /* removed: counter not needed */
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
            val start = text.indexOf(q, ignoreCase = true)
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
                near = state.near,
                onSubmit = { query, nearValue ->
                    viewModel.setQuery(query)
                    viewModel.setNear(nearValue)
                    viewModel.executeSearch()
                },
                onNearChange = { newNear ->
                    // Only update NEAR; wait for Enter/click to run search
                    viewModel.setNear(newNear)
                },
                onQueryChange = { q -> viewModel.setQuery(q) }
            )

            Spacer(Modifier.height(12.dp))
            // Header row: results count + classic separator + optional cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroupHeader(
                    text = stringResource(Res.string.search_result_count, visibleResults.size),
                    modifier = Modifier.padding(end = 12.dp)
                )

                // Classic thin separator line
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(JewelTheme.globalColors.borders.disabled)
                )

                if (state.isLoading || state.isLoadingMore) {
                    Spacer(Modifier.width(8.dp))
                    IconActionButton(
                        key = AllIconsKeys.Windows.Close,
                        onClick = { viewModel.cancelSearch() },
                        contentDescription = "Cancel search"
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Scope breadcrumb if filtered to a book or category
            if (state.scopeBook != null || state.scopeCategoryPath.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    val pieces = buildList {
                        addAll(state.scopeCategoryPath.map { it.title })
                        state.scopeBook?.let { add(it.title) }
                    }
                    pieces.forEachIndexed { index, piece ->
                        if (index > 0) Text(
                            text = stringResource(Res.string.breadcrumb_separator),
                            color = JewelTheme.globalColors.text.disabled,
                            fontSize = commentSize.sp
                        )
                        Text(text = piece, fontSize = commentSize.sp)
                    }
                }
            }

            // Inline progress above replaces the old loading row/spinner

            // Results list
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .background(JewelTheme.globalColors.panelBackground)
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
                    VerticallyScrollableContainer(
                        scrollState = listState as ScrollableState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(items = visibleResults, key = { _, it -> it.lineId }) { idx, result ->
                                val windowInfo = LocalWindowInfo.current
                                SearchResultItemGoogleStyle(
                                    result = result,
                                    textSize = mainTextSize,
                                    lineHeight = mainLineHeight,
                                    fontFamily = hebrewFontFamily,
                                    findQuery = findQuery,
                                    currentMatchStart = if (idx == currentHitIndex) currentMatchStart else null,
                                    onClick = {
                                        val mods = windowInfo.keyboardModifiers
                                        val openInNewTab = !(mods.isCtrlPressed || mods.isMetaPressed)
                                        viewModel.openResult(result, openInNewTab)
                                    },
                                    viewModel = viewModel,
                                    bookFontCode = bookFontCode
                                )
                            }
                            if (state.isLoading) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(stringResource(Res.string.search_searching), fontSize = commentSize.sp)
                                    }
                                }
                            }
                            if (state.hasMore && !state.isLoading && !state.isLoadingMore) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DefaultButton(onClick = { viewModel.loadMore() }) {
                                            Text(stringResource(Res.string.search_load_more), fontSize = commentSize.sp)
                                        }
                                    }
                                }
                            }
                            if (state.isLoadingMore) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(stringResource(Res.string.search_searching), fontSize = commentSize.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Find bar overlay
        if (showFind) {
            LaunchedEffect(findState.text, showFind) {
                val q = findState.text.toString()
                AppSettings.setFindQuery(if (showFind && q.length >= 2) q else "")
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).zIndex(2f)) {
                FindInPageBar(
                    state = findState,
                    onEnterNext = { navigateTo(true) },
                    onEnterPrev = { navigateTo(false) },
                    onClose = { AppSettings.closeFindBar(); AppSettings.setFindQuery("") }
                )
            }
        }
    }
}

@Composable
private fun ResultRow(
    title: String?,
    badgeText: String,
    snippet: String,
    textSize: Float,
    lineHeight: Float,
    fontFamily: FontFamily,
    findQuery: String?,
    currentMatchStart: Int? = null,
    bottomContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Transparent)
            .border(1.dp, JewelTheme.globalColors.borders.disabled, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(
                        text = title,
                        color = JewelTheme.globalColors.text.normal,
                        fontSize = textSize.sp,
                        fontFamily = fontFamily,
                        lineHeight = (textSize * lineHeight).sp
                    )
                    Spacer(Modifier.height(4.dp))
                }
                val annotated: AnnotatedString =
                    remember(snippet, textSize) { buildAnnotatedFromHtml(snippet, textSize, boldScale = 1.1f) }
                val baseHl = JewelTheme.globalColors.outlines.focused.copy(alpha = 0.22f)
                val currentHl = JewelTheme.globalColors.outlines.focused.copy(alpha = 0.42f)
                val display = remember(annotated, findQuery, currentMatchStart, baseHl, currentHl) {
                    io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotatedWithCurrent(
                        annotated = annotated,
                        query = findQuery,
                        currentStart = currentMatchStart?.takeIf { it >= 0 },
                        currentLength = findQuery?.length,
                        baseColor = baseHl,
                        currentColor = currentHl
                    )
                }
                Text(
                    text = display,
                    fontFamily = fontFamily,
                    lineHeight = (textSize * lineHeight).sp,
                    textAlign = TextAlign.Justify
                )
                if (bottomContent != null) {
                    Spacer(Modifier.height(4.dp))
                    bottomContent()
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.align(Alignment.Top).clip(RoundedCornerShape(6.dp))
                    .background(JewelTheme.globalColors.panelBackground)
                    .border(1.dp, JewelTheme.globalColors.borders.disabled, RoundedCornerShape(6.dp))
            ) {
                Text(
                    text = badgeText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = textSize.sp,
                    fontFamily = fontFamily,
                    lineHeight = (textSize * lineHeight).sp
                )
            }
        }
    }
}


@Composable
private fun ResultBreadcrumb(
    viewModel: SearchResultViewModel,
    result: io.github.kdroidfilter.seforimlibrary.core.models.SearchResult,
    textSize: Float,
    lineHeight: Float,
    fontFamily: FontFamily
) {
    val piecesState =
        androidx.compose.runtime.produceState(initialValue = emptyList<String>(), result.bookId, result.lineId) {
            value = kotlin.runCatching { viewModel.getBreadcrumbPiecesFor(result) }.getOrDefault(emptyList())
        }
    val pieces = piecesState.value
    if (pieces.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        pieces.forEachIndexed { index, piece ->
            if (index > 0) Text(
                text = stringResource(Res.string.breadcrumb_separator),
                color = JewelTheme.globalColors.text.disabled,
                fontSize = textSize.sp,
                fontFamily = fontFamily,
                maxLines = 1,
                lineHeight = (textSize * lineHeight).sp
            )
            Text(
                text = piece,
                color = JewelTheme.globalColors.text.normal,
                fontSize = textSize.sp,
                fontFamily = fontFamily,
                maxLines = 1,
                lineHeight = (textSize * lineHeight).sp
            )
        }
    }
}

@Composable
private fun SearchResultItemGoogleStyle(
    result: io.github.kdroidfilter.seforimlibrary.core.models.SearchResult,
    textSize: Float,
    lineHeight: Float,
    fontFamily: FontFamily,
    findQuery: String?,
    currentMatchStart: Int? = null,
    onClick: () -> Unit,
    viewModel: SearchResultViewModel,
    bookFontCode: String
) {
    // Compute breadcrumb pieces once per item
    val piecesState =
        androidx.compose.runtime.produceState(initialValue = emptyList<String>(), result.bookId, result.lineId) {
            value = kotlin.runCatching { viewModel.getBreadcrumbPiecesFor(result) }.getOrDefault(emptyList())
        }
    val pieces = piecesState.value

    // Derive book title and TOC leaf for the header line
    val bookTitle = result.bookTitle
    val tocLeaf: String? = remember(pieces, bookTitle) {
        val bookIndex = pieces.indexOfFirst { it == bookTitle }
        if (bookIndex >= 0 && bookIndex < pieces.lastIndex) pieces.last() else null
    }

    // Full path string for the footer line
    val sep = stringResource(Res.string.breadcrumb_separator)
    val fullPath: String? = if (pieces.isEmpty()) null else pieces.joinToString(sep)

    // Build annotated snippet with bold segments coming from HTML (<b> ... )
    // On macOS, some Hebrew fonts in our catalog don't include bold faces.
    // Apply a subtle boldScale to keep emphasis visible on those fonts.
    val boldScaleForPlatform = remember(bookFontCode) {
        val isMac = System.getProperty("os.name")?.contains("Mac", ignoreCase = true) == true
        val lacksBold = bookFontCode in setOf("notoserifhebrew", "notorashihebrew", "frankruhllibre")
        if (isMac && lacksBold) 1.08f else 1.0f
    }
    val annotated: AnnotatedString = remember(result.snippet, textSize, boldScaleForPlatform) {
        // Keep keyword emphasis without oversized glyphs (slight scale on mac for non-bold fonts)
        buildAnnotatedFromHtml(result.snippet, textSize, boldScale = boldScaleForPlatform)
    }
    // Softer overlays for better legibility
    val baseHl = JewelTheme.globalColors.outlines.focused.copy(alpha = 0.12f)
    val currentHl = JewelTheme.globalColors.outlines.focused.copy(alpha = 0.28f)
    val display = remember(annotated, findQuery, currentMatchStart, baseHl, currentHl) {
        io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotatedWithCurrent(
            annotated = annotated,
            query = findQuery,
            currentStart = currentMatchStart?.takeIf { it >= 0 },
            currentLength = findQuery?.length,
            baseColor = baseHl,
            currentColor = currentHl
        )
    }

    // Visual layout inspired by Google results, styled with Jewel
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        // Top: small book title – toc leaf
        Row(
            modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand)
        ) {
            val header = if (tocLeaf.isNullOrBlank()) bookTitle else buildString {
                append(bookTitle)
                append(stringResource(Res.string.breadcrumb_separator))
                append(tocLeaf)
            }
            Text(
                text = header,
                color = JewelTheme.globalColors.text.selected,
                fontSize = (textSize * 1.1f).sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            textAlign = TextAlign.Justify
        )

        // Bottom: smaller full path of the book
        if (!fullPath.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = fullPath,
                color = JewelTheme.globalColors.text.disabled,
                fontFamily = fontFamily,
                fontSize = (textSize * 0.8f).sp,
                maxLines = 1
            )
        }
    }
}
