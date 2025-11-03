package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
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
import io.github.kdroidfilter.seforimapp.core.presentation.components.AnimatedHorizontalProgressBar
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.breadcrumb_separator
import seforimapp.seforimapp.generated.resources.search_near_label
import seforimapp.seforimapp.generated.resources.search_no_results
import seforimapp.seforimapp.generated.resources.search_results_for
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
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.*
import seforimapp.seforimapp.generated.resources.search_level_1_value
import seforimapp.seforimapp.generated.resources.search_level_2_value
import seforimapp.seforimapp.generated.resources.search_level_3_value
import seforimapp.seforimapp.generated.resources.search_level_4_value
import seforimapp.seforimapp.generated.resources.search_level_5_value
 

@Composable
fun SearchResultScreen(viewModel: SearchResultViewModel) {
    // Content-only variant (no shell); useful for previews or nested usage
    SearchResultContent(viewModel = viewModel)
}

@Composable
private fun SearchToolbar(
    initialQuery: String,
    near: Int,
    onSubmit: (query: String, near: Int) -> Unit,
    onNearChange: (Int) -> Unit,
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
                        val showBookContent = bookUiState.navigation.selectedBook != null && bookUiState.providers != null
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
            }
        )

        Spacer(Modifier.height(12.dp))
        // Header
        GroupHeader(
            text = stringResource(Res.string.search_results_for, state.query),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        // Scope breadcrumb if filtered to a book or category
        if (state.scopeBook != null || state.scopeCategoryPath.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                val pieces = buildList {
                    addAll(state.scopeCategoryPath.map { it.title })
                    state.scopeBook?.let { add(it.title) }
                }
                pieces.forEachIndexed { index, piece ->
                    if (index > 0) Text(text = stringResource(Res.string.breadcrumb_separator), color = JewelTheme.globalColors.text.disabled, fontSize = commentSize.sp)
                    Text(text = piece, fontSize = commentSize.sp)
                }
            }
        }

        // Near level + dynamic results count (filtered list)
        val visibleResults = viewModel.visibleResultsFlow.collectAsState().value
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.search_near_label, state.near),
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.padding(bottom = 8.dp),
                fontSize = commentSize.sp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.search_result_count, visibleResults.size),
                color = JewelTheme.globalColors.text.normal,
                modifier = Modifier.padding(bottom = 8.dp),
                fontSize = commentSize.sp
            )
            Spacer(Modifier.weight(1f))
            if (state.isLoading || state.isLoadingMore) {
                IconActionButton(
                    key = AllIconsKeys.Windows.Close,
                    onClick = { viewModel.cancelSearch() },
                    contentDescription = "Cancel search"
                )
            }
        }

        if (state.isLoading || state.isLoadingMore) {
            Spacer(Modifier.height(6.dp))
            val total = state.progressTotal
            if (total != null && total > 0L) {
                val fraction = (state.progressCurrent.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                AnimatedHorizontalProgressBar(value = fraction, modifier = Modifier.fillMaxWidth())
            } else {
                // Fallback indeterminate indicator when total is unknown (e.g., TOC scope)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        }

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
                VerticallyScrollableContainer(scrollState = listState as ScrollableState) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleResults) { result ->
                            val windowInfo = LocalWindowInfo.current
                            ResultRow(
                                title = null,
                                badgeText = result.bookTitle,
                                snippet = result.snippet,
                                textSize = mainTextSize,
                                lineHeight = mainLineHeight,
                                fontFamily = hebrewFontFamily,
                                bottomContent = {
                                    ResultBreadcrumb(
                                        viewModel = viewModel,
                                        result = result,
                                        textSize = mainTextSize,
                                        lineHeight = mainLineHeight,
                                        fontFamily = hebrewFontFamily
                                    )
                                },
                                onClick = {
                                    val mods = windowInfo.keyboardModifiers
                                    // Inverted behavior: Ctrl/Meta = same tab, otherwise new tab
                                    val openInNewTab = !(mods.isCtrlPressed || mods.isMetaPressed)
                                    viewModel.openResult(result, openInNewTab)
                                }
                            )
                        }
                        if (state.isLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(Res.string.search_searching), fontSize = commentSize.sp)
                                }
                            }
                        }
                        if (state.hasMore && !state.isLoading && !state.isLoadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                    DefaultButton(onClick = { viewModel.loadMore() }) {
                                        Text(stringResource(Res.string.search_load_more), fontSize = commentSize.sp)
                                    }
                                }
                            }
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(Res.string.search_searching), fontSize = commentSize.sp)
                                }
                            }
                        }
                    }
                }
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
                val annotated: AnnotatedString = buildAnnotatedFromHtml(snippet, textSize, boldScale = 1.1f)
                Text(
                    text = annotated,
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
    val piecesState = androidx.compose.runtime.produceState(initialValue = emptyList<String>(), result.bookId, result.lineId) {
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
