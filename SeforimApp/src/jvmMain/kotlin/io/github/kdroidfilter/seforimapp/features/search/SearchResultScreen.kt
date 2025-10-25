package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_near_label
import seforimapp.seforimapp.generated.resources.search_no_results
import seforimapp.seforimapp.generated.resources.search_results_for
import seforimapp.seforimapp.generated.resources.search_scope
import seforimapp.seforimapp.generated.resources.search_searching

@Composable
fun SearchResultScreen(viewModel: SearchResultViewModel) {
    val state = viewModel.uiState.collectAsState().value

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                    if (index > 0) Text(text = " > ", color = JewelTheme.globalColors.text.disabled)
                    Text(text = piece)
                }
            }
        }

        // Near level info
        Text(
            text = stringResource(Res.string.search_near_label, state.near),
            color = JewelTheme.globalColors.text.info,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Results list
        Box(modifier = Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.search_searching))
                    }
                }

                state.results.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(Res.string.search_no_results))
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val hideBookTitle = state.scopeBook != null || state.scopeCategoryPath.isNotEmpty()
                        items(state.results) { result ->
                            ResultRow(
                                title = if (hideBookTitle) null else result.bookTitle,
                                lineIndex = result.lineIndex + 1,
                                snippet = result.snippet,
                                onClick = { viewModel.openResult(result) }
                            )
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
    lineIndex: Int,
    snippet: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Transparent)
            .border(1.dp, JewelTheme.globalColors.borders.disabled, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(text = title, color = JewelTheme.globalColors.text.normal)
                    Spacer(Modifier.height(4.dp))
                }
                val annotated: AnnotatedString = buildAnnotatedFromHtml(snippet, baseTextSize = 13f)
                Text(text = annotated)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.align(Alignment.Top)
                    .clip(RoundedCornerShape(6.dp))
                    .background(JewelTheme.globalColors.panelBackground)
                    .border(1.dp, JewelTheme.globalColors.borders.disabled, RoundedCornerShape(6.dp))
            ) {
                Text(
                    text = lineIndex.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
