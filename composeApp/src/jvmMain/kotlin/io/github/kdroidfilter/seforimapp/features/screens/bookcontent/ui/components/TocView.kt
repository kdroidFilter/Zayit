package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.VisibleTocEntry
import org.jetbrains.jewel.ui.component.Text

@Composable
fun TocView(
    tocEntries: List<TocEntry>,
    expandedEntries: Set<Long>,
    tocChildren: Map<Long, List<TocEntry>>,
    onEntryClick: (TocEntry) -> Unit,
    onEntryExpand: (TocEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleEntries = remember(tocEntries, expandedEntries, tocChildren) {
        buildVisibleTocEntries(tocEntries, expandedEntries, tocChildren)
    }

    LazyColumn(
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
    tocChildren: Map<Long, List<TocEntry>>
): List<VisibleTocEntry> = buildList {
    fun addEntries(currentEntries: List<TocEntry>, level: Int) {
        currentEntries.forEach { entry ->
            val hasCheckedForChildren = tocChildren.containsKey(entry.id)
            val hasNoChildren = hasCheckedForChildren && tocChildren[entry.id].isNullOrEmpty()

            add(
                VisibleTocEntry(
                    entry = entry,
                    level = level,
                    isExpanded = expandedEntries.contains(entry.id),
                    hasChildren = !hasNoChildren
                )
            )

            if (expandedEntries.contains(entry.id)) {
                tocChildren[entry.id]?.let { children ->
                    addEntries(children, level + 1)
                }
            }
        }
    }

    addEntries(entries, 0)
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
            .padding(start = (visibleEntry.level * 16).dp, top = 4.dp, bottom = 4.dp),
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
