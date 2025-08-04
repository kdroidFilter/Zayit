package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.utils.debugln
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.LoadDirection
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import org.jetbrains.jewel.foundation.theme.JewelTheme
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
                    LineItem(line = line, isSelected = selectedLine?.id == line.id) {
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
    onClick: () -> Unit
) {
    val parsedElements = remember(line.id, line.content) {
        HtmlParser().parse(line.content)
    }

    // Construit une seule chaîne annotée => un seul Text, plus de "ligne après"
    val annotated = remember(parsedElements) {
        buildAnnotatedString {
            parsedElements.forEach { e ->
                if (e.text.isBlank()) return@forEach

                val start = length
                append(e.text)
                val end = length

                if (e.isBold) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                }
                if (e.isHeader || e.headerLevel != null) {
                    val size = when (e.headerLevel) {
                        1 -> 24.sp
                        2 -> 20.sp
                        3 -> 18.sp
                        4 -> 16.sp
                        else -> 16.sp
                    }
                    addStyle(SpanStyle(fontSize = size), start, end)
                }
            }
        }
    }

    val textModifier = Modifier
        .fillMaxWidth()
        .pointerHoverIcon(PointerIcon.Hand)
        .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }

    Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        // Un seul Text => pas de saut de ligne artificiel avant/après
        Text(
            text = annotated,
            textAlign = TextAlign.Justify,
            modifier = textModifier
        )
    }
}
