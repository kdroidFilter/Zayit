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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun BookContentView(
    book: Book,
    lines: List<Line>,
    selectedLine: Line?,
    onLineSelected: (Line) -> Unit,
    modifier: Modifier = Modifier,
    preservedListState: LazyListState? = null
) {
    val listState = preservedListState ?: rememberLazyListState()
    var lastScrolledLineId by remember { mutableStateOf<Long?>(null) }

    // Only scroll when the selected line actually changes, without animation
    LaunchedEffect(selectedLine?.id) {
        selectedLine?.let { selected ->
            if (selected.id != lastScrolledLineId) {
                lines.indexOfFirst { it.id == selected.id }.takeIf { it >= 0 }?.let { index ->
                    // Check if the item is not already visible
                    val visibleItems = listState.layoutInfo.visibleItemsInfo
                    val isAlreadyVisible = visibleItems.any { it.index == index }

                    if (!isAlreadyVisible) {
                        // Set the first visible item index directly to disable animation
                        listState.scrollToItem(index, 0)
                    }
                    lastScrolledLineId = selected.id
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
