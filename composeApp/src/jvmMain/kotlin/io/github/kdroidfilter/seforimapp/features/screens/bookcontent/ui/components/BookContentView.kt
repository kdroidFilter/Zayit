package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimapp.core.utils.debugln
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.LoadDirection
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    // Reset these state variables when book or lines change
    var lastScrolledLineId by remember(book.id, lines) { mutableStateOf<Long?>(null) }
    var hasRestored by remember(book.id, lines) { mutableStateOf(false) }

    // 1. First ensure the list is properly restored when it has content
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && !hasRestored) {
            // Handle selected line case
            if (selectedLine != null) {
                val index = lines.indexOfFirst { it.id == selectedLine.id }
                if (index >= 0) {
                    // Found the selected line in the list
                    listState.scrollToItem(index, 0)
                    lastScrolledLineId = selectedLine.id
                } else {
                    // Selected line not found, use saved scroll position
                    val safeIndex = scrollIndex.coerceIn(0, lines.lastIndex)
                    listState.scrollToItem(safeIndex, scrollOffset)
                }
            } else {
                // No selected line, use saved scroll position
                val safeIndex = scrollIndex.coerceIn(0, lines.lastIndex)
                listState.scrollToItem(safeIndex, scrollOffset)
            }
            
            // Mark as restored after position is set
            hasRestored = true
        }
    }
    
    // 2. Handle subsequent selected line changes after initial restoration
    LaunchedEffect(selectedLine?.id) {
        if (hasRestored && selectedLine != null && selectedLine.id != lastScrolledLineId) {
            lines.indexOfFirst { it.id == selectedLine.id }.takeIf { it >= 0 }?.let { index ->
                // Check if the item is not already visible
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val isAlreadyVisible = visibleItems.any { it.index == index }

                if (!isAlreadyVisible) {
                    // Set the first visible item index directly to disable animation
                    listState.scrollToItem(index, 0)
                }
                lastScrolledLineId = selectedLine.id
            }
        }
    }
    
    // 3. Collect scroll events only after restoration
    LaunchedEffect(listState, hasRestored) {
        if (hasRestored) {
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
                .distinctUntilChanged()
                .debounce(250)
                .collect { (index, offset) -> onScroll(index, offset) }
        }
    }
    
    // 4. Detect when user has scrolled near the end of the list and trigger loading more content
    LaunchedEffect(listState, lines.size) {
        debugln { "[DEBUG_LOG] LaunchedEffect for forward infinite scroll started, lines size: ${lines.size}" }
        snapshotFlow { 
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = lines.size
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            
            // Check if we're near the end of the list (last 5 items)
            val isNearEnd = lastVisibleItemIndex >= (totalItemsCount - 5)
            debugln { "[DEBUG_LOG] Last visible item index: $lastVisibleItemIndex, Total items: $totalItemsCount, Is near end: $isNearEnd" }
            isNearEnd
        }
        .distinctUntilChanged()
        .collect { isNearEnd ->
            debugln { "[DEBUG_LOG] Is near end changed to: $isNearEnd, Lines empty: ${lines.isEmpty()}" }
            if (isNearEnd && lines.isNotEmpty()) {
                debugln { "[DEBUG_LOG] Calling onLoadMore with FORWARD direction" }
                onLoadMore(LoadDirection.FORWARD)
            }
        }
    }
    
    // 5. Detect when user has scrolled near the beginning of the list and trigger loading more content
    LaunchedEffect(listState, lines.size) {
        debugln { "[DEBUG_LOG] LaunchedEffect for backward infinite scroll started, lines size: ${lines.size}" }
        snapshotFlow { 
            val layoutInfo = listState.layoutInfo
            val firstVisibleItemIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            
            // Check if we're near the beginning of the list (first 5 items)
            val isNearBeginning = firstVisibleItemIndex <= 5
            debugln { "[DEBUG_LOG] First visible item index: $firstVisibleItemIndex, Is near beginning: $isNearBeginning" }
            isNearBeginning
        }
        .distinctUntilChanged()
        .collect { isNearBeginning ->
            debugln { "[DEBUG_LOG] Is near beginning changed to: $isNearBeginning, Lines empty: ${lines.isEmpty()}" }
            if (isNearBeginning && lines.isNotEmpty()) {
                // Only load more if we're not already at the beginning (index 0)
                val firstLineIndex = lines.firstOrNull()?.lineIndex ?: 0
                if (firstLineIndex > 0) {
                    debugln { "[DEBUG_LOG] Calling onLoadMore with BACKWARD direction" }
                    onLoadMore(LoadDirection.BACKWARD)
                } else {
                    debugln { "[DEBUG_LOG] Already at the beginning of the book, not loading more" }
                }
            }
        }
    }

    Column(modifier = modifier) {
        Text(
            text = book.title, modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            state = listState, modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = lines, key = { it.id }) { line ->
                LineItem(
                    line = line, isSelected = selectedLine?.id == line.id, onClick = { onLineSelected(line) })
            }
        }
    }
}

@Composable
private fun LineItem(
    line: Line, isSelected: Boolean, onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Text(
            text = line.content,
            textAlign = TextAlign.Justify,
            color = if (isSelected) JewelTheme.globalColors.outlines.focused else JewelTheme.globalColors.text.normal,
            modifier = Modifier
                .hoverable(MutableInteractionSource(), enabled = false)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClick() })
                }
                .pointerHoverIcon(PointerIcon.Hand)
        )
    }
}