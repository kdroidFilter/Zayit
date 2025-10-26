package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Text
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.breadcrumb_separator
import seforimapp.seforimapp.generated.resources.search_near_label
import seforimapp.seforimapp.generated.resources.search_no_results
import seforimapp.seforimapp.generated.resources.search_results_for
import seforimapp.seforimapp.generated.resources.search_scope
import seforimapp.seforimapp.generated.resources.search_searching

@Composable
fun SearchResultScreen(viewModel: SearchResultViewModel) {
    val state = viewModel.uiState.collectAsState().value
    val listState = rememberLazyListState()
    val textSize = state.textSize

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

    // Restore scroll/anchor when results arrive or when signaled by timestamp
    LaunchedEffect(state.scrollToAnchorTimestamp, state.results, state.isLoading) {
        if (!state.isLoading && state.results.isNotEmpty()) {
            val anchorIdx = if (state.anchorId > 0) {
                state.results.indexOfFirst { it.lineId == state.anchorId }.takeIf { it >= 0 }
            } else null
            val targetIndex = anchorIdx ?: state.scrollIndex
            val targetOffset = if (anchorIdx != null) state.scrollOffset else state.scrollOffset
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex, targetOffset)
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
                    if (index > 0) Text(text = stringResource(Res.string.breadcrumb_separator), color = JewelTheme.globalColors.text.disabled, fontSize = textSize.sp)
                    Text(text = piece, fontSize = textSize.sp)
                }
            }
        }

        // Near level info
        Text(
            text = stringResource(Res.string.search_near_label, state.near),
            color = JewelTheme.globalColors.text.info,
            modifier = Modifier.padding(bottom = 8.dp),
            fontSize = textSize.sp
        )

        // Results list
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .background(JewelTheme.globalColors.panelBackground)
        ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.search_searching), fontSize = textSize.sp)
                    }
                }

                state.results.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(Res.string.search_no_results), fontSize = textSize.sp)
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.results) { result ->
                            ResultRow(
                                title = null,
                                badgeText = result.bookTitle,
                                snippet = result.snippet,
                                textSize = textSize,
                                onClick = { viewModel.openResult(result) })
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
                val annotated: AnnotatedString = buildAnnotatedFromHtml(snippet, baseTextSize = textSize)
                Text(text = annotated, fontSize = textSize.sp)
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
