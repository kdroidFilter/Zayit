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
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.VisibleTocEntry
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.jewel.ui.component.Text

/**
 * Table-of-contents list with collapsible nodes and scroll-state persistence.
 *
 * Two fixes compared to the previous version:
 * 1. **No debounce** when emitting scroll events, so the very last position is
 *    always sent to the ViewModel even if the user switches tabs quickly.
 * 2. **Explicit restore** of the saved scroll position once the list has real
 *    content (otherwise Compose would clamp the requested index to 0 when the
 *    list was still empty).
 */
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
    modifier: Modifier = Modifier
) {
    /* ---------------------------------------------------------------------
     * Build the flat list of visible entries.
     * -------------------------------------------------------------------- */
    val visibleEntries = remember(tocEntries, expandedEntries, tocChildren) {
        buildVisibleTocEntries(tocEntries, expandedEntries, tocChildren)
    }

    /* ---------------------------------------------------------------------
     * Remember the LazyListState from the saved index/offset.
     * -------------------------------------------------------------------- */
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollIndex,
        initialFirstVisibleItemScrollOffset = scrollOffset
    )

    /* ---------------------------------------------------------------------
     * 1) Send scroll updates upstream without debounce so we never lose the
     *    very last position.
     * -------------------------------------------------------------------- */
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) -> onScroll(index, offset) }
    }

    /* ---------------------------------------------------------------------
     * 2) Once the list actually has content, make sure we are scrolled to the
     *    stored position (Compose might have reset us to 0 when it was empty).
     * -------------------------------------------------------------------- */
    LaunchedEffect(visibleEntries.size, scrollIndex, scrollOffset) {
        if (visibleEntries.isNotEmpty()) {
            listState.scrollToItem(scrollIndex, scrollOffset)
        }
    }

    /* ---------------------------------------------------------------------
     * The UI.
     * -------------------------------------------------------------------- */
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

/* -------------------------------------------------------------------------
 * Helpers
 * ---------------------------------------------------------------------- */
private fun buildVisibleTocEntries(
    entries: List<TocEntry>,
    expandedEntries: Set<Long>,
    tocChildren: Map<Long, List<TocEntry>>
): List<VisibleTocEntry> {
    val result = mutableListOf<VisibleTocEntry>()

    fun addEntries(currentEntries: List<TocEntry>, level: Int) {
        currentEntries.forEach { entry ->
            val hasCheckedForChildren = tocChildren.containsKey(entry.id)
            val hasNoChildren = hasCheckedForChildren && tocChildren[entry.id].isNullOrEmpty()

            result += VisibleTocEntry(
                entry = entry,
                level = level,
                isExpanded = expandedEntries.contains(entry.id),
                hasChildren = !hasNoChildren
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEntryClick(visibleEntry.entry) }
            .padding(
                start = (visibleEntry.level * 16).dp,
                top = 4.dp,
                bottom = 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (visibleEntry.hasChildren) {
            Text(
                text = if (visibleEntry.isExpanded) "-" else "+",
                modifier = Modifier
                    .width(24.dp)
                    .clickable { onEntryExpand(visibleEntry.entry) }
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Text(text = visibleEntry.entry.text)
    }
}