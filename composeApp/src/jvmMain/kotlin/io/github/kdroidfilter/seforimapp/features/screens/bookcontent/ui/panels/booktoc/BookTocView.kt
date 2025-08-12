package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels.booktoc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.icons.ChevronDown
import io.github.kdroidfilter.seforimapp.core.presentation.icons.ChevronRight
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.BookContentUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state.VisibleTocEntry
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.LineTocMapping
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text

/**
 * Table-of-contents list with collapsible nodes and scroll-state persistence.
 *
 * Updates:
 * 1. Added debounce to reduce the frequency of scroll events
 * 2. Added hasRestored logic to ensure scroll events are only collected after position restoration
 * 3. Explicit restore of the saved scroll position once the list has real content
 *    (otherwise Compose would clamp the requested index to 0 when the list was still empty)
 */

@OptIn(FlowPreview::class)
@Composable
fun BookTocView(
    tocEntries: List<TocEntry>,
    expandedEntries: Set<Long>,
    tocChildren: Map<Long, List<TocEntry>>,
    scrollIndex: Int,
    scrollOffset: Int,
    onEntryClick: (TocEntry) -> Unit,
    onEntryExpand: (TocEntry) -> Unit,
    onScroll: (Int, Int) -> Unit,
    lines: List<Line> = emptyList(),
    lineTocMappings: List<LineTocMapping> = emptyList(),
    modifier: Modifier = Modifier
) {
    val visibleEntries = remember(tocEntries, expandedEntries, tocChildren, lines, lineTocMappings) {
        buildVisibleTocEntries(tocEntries, expandedEntries, tocChildren)
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollIndex,
        initialFirstVisibleItemScrollOffset = scrollOffset
    )

    var hasRestored by remember { mutableStateOf(false) }

    LaunchedEffect(listState, hasRestored) {
        if (hasRestored) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .debounce(250)
                .collect { (index, offset) -> onScroll(index, offset) }
        }
    }

    LaunchedEffect(visibleEntries.size) {
        if (visibleEntries.isNotEmpty() && !hasRestored) {
            val safeIndex = scrollIndex.coerceIn(0, visibleEntries.lastIndex)
            listState.scrollToItem(safeIndex, scrollOffset)
            hasRestored = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 16.dp)
        ) {
            items(
                items = visibleEntries,
                key = { it.entry.id }
            ) { visibleEntry ->
                TocEntryItem(
                    visibleEntry = visibleEntry,
                    onEntryClick = onEntryClick,
                    onEntryExpand = onEntryExpand
                )
            }
        }
        
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState)
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
fun BookTocView(
    uiState: BookContentUiState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val rootEntries = uiState.toc.children[-1L] ?: uiState.toc.entries
    var displayEntries by remember(uiState.toc.entries, uiState.toc.children, uiState.navigation.selectedBook?.id) {
        mutableStateOf(rootEntries.ifEmpty { uiState.toc.entries })
    }

    if (displayEntries.size == 1) {
        val soleParent = displayEntries.first()
        val directChildren = uiState.toc.children[soleParent.id]

        if (directChildren.isNullOrEmpty()) {
            if (soleParent.hasChildren && !uiState.toc.expandedEntries.contains(soleParent.id)) {
                LaunchedEffect(uiState.navigation.selectedBook?.id, soleParent.id) {
                    onEvent(BookContentEvent.TocEntryExpanded(soleParent))
                }
            }
        } else {
            displayEntries = directChildren
        }
    }

    BookTocView(
        tocEntries = displayEntries,
        expandedEntries = uiState.toc.expandedEntries,
        tocChildren = uiState.toc.children,
        scrollIndex = uiState.toc.scrollIndex,
        scrollOffset = uiState.toc.scrollOffset,
        onEntryClick = { entry ->
            entry.lineId?.let { lineId ->
                onEvent(BookContentEvent.LoadAndSelectLine(lineId))
            }
        },
        onEntryExpand = { entry ->
            onEvent(BookContentEvent.TocEntryExpanded(entry))
        },
        onScroll = { index, offset ->
            onEvent(BookContentEvent.TocScrolled(index, offset))
        },
        modifier = modifier
    )
}

private fun buildVisibleTocEntries(
    entries: List<TocEntry>,
    expandedEntries: Set<Long>,
    tocChildren: Map<Long, List<TocEntry>>,
): List<VisibleTocEntry> {
    val result = mutableListOf<VisibleTocEntry>()

    fun addEntries(currentEntries: List<TocEntry>, level: Int) {
        currentEntries.forEach { entry ->
            result += VisibleTocEntry(
                entry = entry,
                level = level,
                isExpanded = expandedEntries.contains(entry.id),
                hasChildren = entry.hasChildren,
                isLastChild = entry.isLastChild
            )

            if (expandedEntries.contains(entry.id)) {
                tocChildren[entry.id]?.let { children ->
                    addEntries(children, level + 1)
                }
            }
        }
    }

    addEntries(entries, 0)
    return result
}

@Composable
private fun TocEntryItem(
    visibleEntry: VisibleTocEntry,
    onEntryClick: (TocEntry) -> Unit,
    onEntryExpand: (TocEntry) -> Unit
) {
    val isLastChild = visibleEntry.isLastChild

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (visibleEntry.hasChildren) {
                    onEntryExpand(visibleEntry.entry)
                } else {
                    onEntryClick(visibleEntry.entry)
                }
            }
            .padding(
                start = (visibleEntry.level * 16).dp,
                top = 4.dp,
                bottom = if (isLastChild) 8.dp else 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (visibleEntry.hasChildren) {
            Icon(if (visibleEntry.isExpanded) ChevronDown else ChevronRight,
                contentDescription = "",
                modifier = Modifier.height(12.dp).width(24.dp))
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Text(
            text = visibleEntry.entry.text,
            fontWeight = FontWeight.Normal
        )
    }
}