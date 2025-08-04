package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.core.models.LineTocMapping
import io.github.kdroidfilter.seforimlibrary.core.extensions.findTocEntryId
import io.github.kdroidfilter.seforimlibrary.core.extensions.hasTocEntry
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.VisibleTocEntry
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
// TocView.kt simplifié

@OptIn(FlowPreview::class)
@Composable
fun TocView(
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
        buildVisibleTocEntries(tocEntries, expandedEntries, tocChildren, lines, lineTocMappings)
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

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
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
}

private fun buildVisibleTocEntries(
    entries: List<TocEntry>,
    expandedEntries: Set<Long>,
    tocChildren: Map<Long, List<TocEntry>>,
    lines: List<Line> = emptyList(),
    lineTocMappings: List<LineTocMapping> = emptyList()
): List<VisibleTocEntry> {
    val result = mutableListOf<VisibleTocEntry>()

    fun addEntries(currentEntries: List<TocEntry>, level: Int) {
        currentEntries.forEach { entry ->
            // SIMPLIFICATION : Utiliser directement le champ hasChildren de l'entrée
            result += VisibleTocEntry(
                entry = entry,
                level = level,
                isExpanded = expandedEntries.contains(entry.id),
                hasChildren = entry.hasChildren,  // Directement depuis le modèle !
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
            .clickable { onEntryClick(visibleEntry.entry) }
            .padding(
                start = (visibleEntry.level * 16).dp,
                top = 4.dp,
                bottom = if (isLastChild) 8.dp else 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (visibleEntry.hasChildren) {  // Utilise directement hasChildren
            Text(
                text = if (visibleEntry.isExpanded) "-" else "+",
                modifier = Modifier
                    .width(24.dp)
                    .clickable { onEntryExpand(visibleEntry.entry) }
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Text(
            text = visibleEntry.entry.text,
            fontWeight = if (isLastChild) FontWeight.Bold else FontWeight.Normal
        )
    }
}