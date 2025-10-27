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

@Composable
fun SearchResultScreen(viewModel: SearchResultViewModel) {
    // Content-only variant (no shell); useful for previews or nested usage
    SearchResultContent(viewModel = viewModel)
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
                    CategoryTreePanel(uiState = bookUiState, onEvent = onEvent)
                }
            },
            secondContent = {
                EnhancedHorizontalSplitPane(
                    splitPaneState = bookUiState.layout.tocSplitState,
                    firstMinSize = if (bookUiState.toc.isVisible) io.github.kdroidfilter.seforimapp.features.bookcontent.state.SplitDefaults.MIN_TOC else 0f,
                    firstContent = {
                        if (bookUiState.toc.isVisible) {
                            BookTocPanel(uiState = bookUiState, onEvent = onEvent)
                        }
                    },
                    secondContent = {
                        // If a book is selected, render the book content; otherwise show search results
                        if (bookUiState.navigation.selectedBook == null) {
                            SearchResultContent(viewModel = viewModel)
                        } else {
                            BookContentPanel(uiState = bookUiState, onEvent = onEvent)
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
    // Match commentaries size (BookContent uses ~0.875x of main text)
    val baseSize = state.textSize
    val commentSize by animateFloatAsState(
        targetValue = baseSize * 0.875f,
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

        // Near level + dynamic results count
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.search_near_label, state.near),
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.padding(bottom = 8.dp),
                fontSize = commentSize.sp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.search_result_count, state.results.size),
                color = JewelTheme.globalColors.text.normal,
                modifier = Modifier.padding(bottom = 8.dp),
                fontSize = commentSize.sp
            )
        }

        // Results list
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .background(JewelTheme.globalColors.panelBackground)
        ) {
            if (state.results.isEmpty()) {
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
                    modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.results) { result ->
                        val windowInfo = LocalWindowInfo.current
                        ResultRow(
                            title = null,
                            badgeText = result.bookTitle,
                            snippet = result.snippet,
                            textSize = commentSize,
                            onClick = {
                                val mods = windowInfo.keyboardModifiers
                                val openInNewTab = mods.isCtrlPressed || mods.isMetaPressed
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

@Composable
private fun ResultRow(
    title: String?, badgeText: String, snippet: String, textSize: Float, onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Transparent)
            .border(1.dp, JewelTheme.globalColors.borders.disabled, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(text = title, color = JewelTheme.globalColors.text.normal, fontSize = textSize.sp)
                    Spacer(Modifier.height(4.dp))
                }
                val annotated: AnnotatedString = buildAnnotatedFromHtml(snippet, baseTextSize = 13f)
                Text(text = annotated)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.align(Alignment.Top).clip(RoundedCornerShape(6.dp))
                    .background(JewelTheme.globalColors.panelBackground)
                    .border(1.dp, JewelTheme.globalColors.borders.disabled, RoundedCornerShape(6.dp))
            ) {
                Text(text = badgeText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = textSize.sp)
            }
        }
    }
}
