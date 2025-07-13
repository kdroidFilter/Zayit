package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import org.jetbrains.jewel.ui.component.Text

/**
 * A component that displays the content of a book.
 */
@Composable
fun BookContentView(
    book: Book,
    lines: List<Line>,
    selectedLine: Line?,
    onLineSelected: (Line) -> Unit,
    modifier: Modifier = Modifier
) {
    // Create a LazyListState to control scrolling
    val listState = rememberLazyListState()

    // Find the index of the selected line in the list
    val selectedIndex by remember(selectedLine, lines) {
        derivedStateOf {
            selectedLine?.let { selected ->
                lines.indexOfFirst { it.id == selected.id }
            } ?: -1
        }
    }

    // Scroll to the selected line when it changes
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Column(modifier = modifier) {
        // Book title
        Text(
            text = book.title,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Book content
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().fillMaxHeight()
        ) {
            items(
                items = lines,
                key = { it.id } // Use the line ID as a stable key for better performance
            ) { line ->
                LineItem(
                    line = line,
                    isSelected = selectedLine?.id == line.id,
                    onClick = { onLineSelected(line) }
                )
            }
        }
    }
}

/**
 * A component that displays a line of text from a book.
 */
@Composable
fun LineItem(
    line: Line,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) Color.Blue.copy(alpha = 0.2f) else Color.Transparent)
            .padding(8.dp)
    ) {
        // Use Text component to display HTML content
        Text(
            text = line.plainText
        )
    }
}

/**
 * A data class representing a TOC entry that is visible in the UI.
 * This is used to flatten the hierarchical TOC structure into a single list.
 */
data class VisibleTocEntry(
    val entry: TocEntry,
    val level: Int,
    val isExpanded: Boolean,
    val hasChildren: Boolean
)

/**
 * A component that displays the table of contents of a book.
 * This implementation uses a flat list approach instead of recursion,
 * which is more efficient for large TOC structures.
 */
@Composable
fun TocView(
    tocEntries: List<TocEntry>,
    expandedEntries: Set<Long>,
    tocChildren: Map<Long, List<TocEntry>>,
    onEntryClick: (TocEntry) -> Unit,
    onEntryExpand: (TocEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    // Create a flat list of visible TOC entries
    val visibleEntries = remember(tocEntries, expandedEntries, tocChildren) {
        buildVisibleTocEntries(tocEntries, expandedEntries, tocChildren)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().fillMaxHeight()
    ) {
        items(
            items = visibleEntries,
            key = { it.entry.id } // Use the entry ID as a stable key for better performance
        ) { visibleEntry ->
            TocEntryItem(
                visibleEntry = visibleEntry,
                onEntryClick = onEntryClick,
                onEntryExpand = onEntryExpand
            )
        }
    }
}

/**
 * Builds a flat list of visible TOC entries from the hierarchical structure.
 */
private fun buildVisibleTocEntries(
    entries: List<TocEntry>,
    expandedEntries: Set<Long>,
    tocChildren: Map<Long, List<TocEntry>>
): List<VisibleTocEntry> {
    val result = mutableListOf<VisibleTocEntry>()

    // Helper function to recursively add entries to the flat list
    fun addEntries(currentEntries: List<TocEntry>, level: Int) {
        currentEntries.forEach { entry ->
            // Check if we've already attempted to load children for this entry
            val hasCheckedForChildren = tocChildren.containsKey(entry.id)
            // If we've checked and the list is empty, then it truly has no children
            val hasNoChildren = hasCheckedForChildren && (tocChildren[entry.id]?.isEmpty() ?: true)

            // Add the current entry to the result list
            result.add(
                VisibleTocEntry(
                    entry = entry,
                    level = level,
                    isExpanded = expandedEntries.contains(entry.id),
                    hasChildren = !hasNoChildren
                )
            )

            // If the entry is expanded and has children, add its children too
            if (expandedEntries.contains(entry.id)) {
                val children = tocChildren[entry.id] ?: emptyList()
                if (children.isNotEmpty()) {
                    addEntries(children, level + 1)
                }
            }
        }
    }

    // Start with the root entries
    addEntries(entries, 0)

    return result
}

/**
 * A component that displays a single TOC entry.
 * This is a non-recursive implementation that only renders the current entry.
 */
@Composable
fun TocEntryItem(
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
        // Show expand/collapse icon unless we've confirmed the entry has no children
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

        // Entry text
        Text(
            text = visibleEntry.entry.text
        )
    }
}
