package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.LoadDirection
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.jewel.ui.component.Text

@OptIn(FlowPreview::class)
@Composable
fun BookContentView(
    book: Book,
    lines: List<Line>,
    selectedLine: Line?,
    onLineSelected: (Line) -> Unit,
    modifier: Modifier = Modifier,
    preservedListState: LazyListState? = null,
    scrollIndex: Int = 0,
    scrollOffset: Int = 0,
    onScroll: (Int, Int) -> Unit = { _, _ -> },
    onLoadMore: (LoadDirection) -> Unit = { _ -> }
) {
    val listState = preservedListState ?: rememberLazyListState(
        initialFirstVisibleItemIndex = scrollIndex,
        initialFirstVisibleItemScrollOffset = scrollOffset
    )

    // Collect text size from settings
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    
    // Animate text size changes for smoother transitions
    val textSize by animateFloatAsState(
        targetValue = rawTextSize,
        animationSpec = tween(durationMillis = 300),
        label = "textSizeAnimation"
    )
    
    // Collect line height from settings
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()
    
    // Animate line height changes for smoother transitions
    val lineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = tween(durationMillis = 300),
        label = "lineHeightAnimation"
    )

    var lastScrolledLineId by remember(book.id, lines) { mutableStateOf<Long?>(null) }
    var hasRestored by remember(book.id, lines) { mutableStateOf(false) }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && !hasRestored) {
            val index = selectedLine?.let { lines.indexOfFirst { it.id == selectedLine.id } } ?: -1
            if (index >= 0) {
                listState.scrollToItem(index)
                lastScrolledLineId = selectedLine?.id
            } else {
                listState.scrollToItem(scrollIndex.coerceIn(0, lines.lastIndex), scrollOffset)
            }
            hasRestored = true
        }
    }

    LaunchedEffect(selectedLine?.id) {
        if (hasRestored && selectedLine != null && selectedLine.id != lastScrolledLineId) {
            lines.indexOfFirst { it.id == selectedLine.id }.takeIf { it >= 0 }?.let { index ->
                if (!listState.layoutInfo.visibleItemsInfo.any { it.index == index }) {
                    listState.scrollToItem(index)
                }
                lastScrolledLineId = selectedLine.id
            }
        }
    }

    LaunchedEffect(listState, hasRestored) {
        if (hasRestored) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .debounce(250)
                .collect { (index, offset) -> onScroll(index, offset) }
        }
    }

    LaunchedEffect(listState, lines.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            lastVisibleItem >= (lines.size - 5)
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && lines.isNotEmpty()) {
                onLoadMore(LoadDirection.FORWARD)
            }
        }
    }

    LaunchedEffect(listState, lines.size) {
        snapshotFlow {
            (listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0) <= 5
        }.distinctUntilChanged().collect { nearStart ->
            if (nearStart && lines.isNotEmpty()) {
                val firstLineIndex = lines.firstOrNull()?.lineIndex ?: 0
                if (firstLineIndex > 0) onLoadMore(LoadDirection.BACKWARD)
            }
        }
    }

    Column(modifier = modifier) {
        Text(text = book.title, modifier = Modifier.padding(bottom = 16.dp))
        SelectionContainer {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(items = lines, key = { it.id }) { line ->
                    LineItem(
                        line = line, 
                        isSelected = selectedLine?.id == line.id,
                        baseTextSize = textSize,
                        lineHeight = lineHeight
                    ) {
                        onLineSelected(line)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LineItem(
    line: Line,
    isSelected: Boolean,
    baseTextSize: Float = 16f,
    lineHeight: Float = 1.5f,
    onClick: () -> Unit
) {
    val parsedElements = remember(line.id, line.content) {
        HtmlParser().parse(line.content)
    }

    // Builds a single annotated string => a single Text, no more "line after"
    // Include baseTextSize in remember dependencies to ensure recomposition when text size changes
    val annotated = remember(parsedElements, baseTextSize) {
        buildAnnotatedString {
            parsedElements.forEach { e ->
                if (e.text.isBlank()) return@forEach

                val start = length
                append(e.text)
                val end = length

                if (e.isBold) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                }
                if (e.isItalic) {
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                }
                if (e.isHeader || e.headerLevel != null) {
                    val size = when (e.headerLevel) {
                        1 -> (baseTextSize * 1.5f).sp
                        2 -> (baseTextSize * 1.25f).sp
                        3 -> (baseTextSize * 1.125f).sp
                        4 -> baseTextSize.sp
                        else -> baseTextSize.sp
                    }
                    addStyle(SpanStyle(fontSize = size), start, end)
                } else {
                    // Apply base text size to non-header text
                    addStyle(SpanStyle(fontSize = baseTextSize.sp), start, end)
                }
            }
        }
    }

    val textModifier = Modifier
        .fillMaxWidth()
        .pointerHoverIcon(PointerIcon.Hand)
        .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }

    Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        // A single Text => no artificial line break before/after
        Text(
            text = annotated,
            textAlign = TextAlign.Justify,
            lineHeight = (baseTextSize * lineHeight).sp,
            modifier = textModifier
        )
    }
}
