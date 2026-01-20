package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.font.FontWeight.Companion.Normal
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.components.ChevronIcon
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableRow
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.VisibleTocEntry
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

/**
 * Table-of-contents list with collapsible nodes and scroll-state
 * persistence.
 *
 * Updates:
 * 1. Added debounce to reduce the frequency of scroll events
 * 2. Added hasRestored logic to ensure scroll events are only collected
 *    after position restoration
 * 3. Explicit restore of the saved scroll position once the list has real
 *    content (otherwise Compose would clamp the requested index to 0 when
 *    the list was still empty)
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
    modifier: Modifier = Modifier,
    selectedTocEntryId: Long? = null,
    // Search integration
    showCounts: Boolean = false,
    onlyWithResults: Boolean = false,
    tocCounts: Map<Long, Int> = emptyMap(),
    selectedTocOverride: Long? = null,
    onTocFilter: ((TocEntry) -> Unit)? = null,
    // Multi-select integration: optional checkboxes per entry
    multiSelectIds: Set<Long> = emptySet(),
    onToggle: ((TocEntry, Boolean) -> Unit)? = null,
) {
    val visibleEntries =
        remember(tocEntries, expandedEntries, tocChildren, showCounts, onlyWithResults, tocCounts) {
            buildVisibleTocEntries(tocEntries, expandedEntries, tocChildren, onlyWithResults, tocCounts)
        }

    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = scrollIndex,
            initialFirstVisibleItemScrollOffset = scrollOffset,
        )

    var hasRestored by remember { mutableStateOf(false) }
    val currentOnScroll by rememberUpdatedState(onScroll)

    LaunchedEffect(listState, hasRestored) {
        if (hasRestored) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .debounce(250)
                .collect { (index, offset) -> currentOnScroll(index, offset) }
        }
    }

    LaunchedEffect(visibleEntries.size) {
        if (visibleEntries.isNotEmpty() && !hasRestored) {
            val safeIndex = scrollIndex.coerceIn(0, visibleEntries.lastIndex)
            listState.scrollToItem(safeIndex, scrollOffset)
            hasRestored = true
        }
    }

    // Bring selected TOC entry into view once per book after restore
    var didAutoCenter by remember(tocEntries) { mutableStateOf(false) }
    LaunchedEffect(selectedTocOverride ?: selectedTocEntryId, visibleEntries.size, hasRestored, didAutoCenter) {
        val selId = (selectedTocOverride ?: selectedTocEntryId) ?: return@LaunchedEffect
        if (!didAutoCenter && hasRestored && visibleEntries.isNotEmpty()) {
            val idx = visibleEntries.indexOfFirst { it.entry.id == selId }
            if (idx >= 0) {
                listState.scrollToItem(idx, 0)
                didAutoCenter = true
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().padding(bottom = 8.dp)) {
        VerticallyScrollableContainer(
            scrollState = listState as ScrollableState,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 16.dp),
            ) {
                items(
                    items = visibleEntries,
                    key = { it.entry.id },
                ) { visibleEntry ->
                    TocEntryItem(
                        visibleEntry = visibleEntry,
                        selectedTocEntryId = selectedTocOverride ?: selectedTocEntryId,
                        onEntryClick = { entry ->
                            if (onTocFilter != null) onTocFilter(entry) else onEntryClick(entry)
                        },
                        onEntryExpand = onEntryExpand,
                        showCount = showCounts,
                        count = tocCounts[visibleEntry.entry.id] ?: 0,
                        checkboxChecked = if (onToggle != null) multiSelectIds.contains(visibleEntry.entry.id) else null,
                        onCheckboxToggle = onToggle?.let { handler -> { checked: Boolean -> handler(visibleEntry.entry, checked) } },
                    )
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
fun BookTocView(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
    showCounts: Boolean = false,
    onlyWithResults: Boolean = false,
    selectedTocOverride: Long? = null,
    tocCounts: Map<Long, Int> = emptyMap(),
    onTocFilter: ((TocEntry) -> Unit)? = null,
    multiSelectIds: Set<Long> = emptySet(),
    onToggle: ((TocEntry, Boolean) -> Unit)? = null,
) {
    val rootEntries = uiState.toc.children[-1L] ?: uiState.toc.entries
    var displayEntries by remember(uiState.toc.entries, uiState.toc.children, uiState.navigation.selectedBook?.id) {
        mutableStateOf(rootEntries.ifEmpty { uiState.toc.entries })
    }
    val currentOnEvent by rememberUpdatedState(onEvent)

    if (displayEntries.size == 1) {
        val soleParent = displayEntries.first()
        val directChildren = uiState.toc.children[soleParent.id]

        if (directChildren.isNullOrEmpty()) {
            if (soleParent.hasChildren && !uiState.toc.expandedEntries.contains(soleParent.id)) {
                LaunchedEffect(uiState.navigation.selectedBook?.id, soleParent.id) {
                    currentOnEvent(BookContentEvent.TocEntryExpanded(soleParent))
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
            if (onTocFilter != null) {
                onTocFilter(entry)
            } else {
                entry.lineId?.let { lineId -> onEvent(BookContentEvent.LoadAndSelectLine(lineId)) }
            }
        },
        onEntryExpand = { entry ->
            onEvent(BookContentEvent.TocEntryExpanded(entry))
        },
        onScroll = { index, offset ->
            onEvent(BookContentEvent.TocScrolled(index, offset))
        },
        selectedTocEntryId = uiState.toc.selectedEntryId,
        modifier = modifier,
        showCounts = showCounts,
        onlyWithResults = onlyWithResults,
        tocCounts = tocCounts,
        selectedTocOverride = selectedTocOverride,
        onTocFilter = onTocFilter,
        multiSelectIds = multiSelectIds,
        onToggle = onToggle,
    )
}

private fun buildVisibleTocEntries(
    entries: List<TocEntry>,
    expandedEntries: Set<Long>,
    tocChildren: Map<Long, List<TocEntry>>,
    onlyWithResults: Boolean,
    tocCounts: Map<Long, Int>,
): List<VisibleTocEntry> {
    val result = mutableListOf<VisibleTocEntry>()

    fun addEntries(
        currentEntries: List<TocEntry>,
        level: Int,
    ) {
        currentEntries.forEach { entry ->
            val count = tocCounts[entry.id] ?: 0
            if (onlyWithResults && count <= 0) {
                // Skip entries without results when filtering is enabled
                return@forEach
            }
            result +=
                VisibleTocEntry(
                    entry = entry,
                    level = level,
                    isExpanded = expandedEntries.contains(entry.id),
                    hasChildren = entry.hasChildren,
                    isLastChild = entry.isLastChild,
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
    selectedTocEntryId: Long?,
    onEntryClick: (TocEntry) -> Unit,
    onEntryExpand: (TocEntry) -> Unit,
    showCount: Boolean = false,
    count: Int = 0,
    checkboxChecked: Boolean? = null,
    onCheckboxToggle: ((Boolean) -> Unit)? = null,
) {
    val isLastChild = visibleEntry.isLastChild
    val isSelected = selectedTocEntryId != null && visibleEntry.entry.id == selectedTocEntryId

    SelectableRow(
        modifier =
            Modifier
                .padding(
                    start = (visibleEntry.level * 16).dp,
                    top = 4.dp,
                    bottom = if (isLastChild) 8.dp else 4.dp,
                ),
        isSelected = isSelected,
        onClick = {
            if (visibleEntry.hasChildren) {
                onEntryExpand(visibleEntry.entry)
            } else {
                onEntryClick(visibleEntry.entry)
            }
        },
    ) {
        if (visibleEntry.hasChildren) {
            ChevronIcon(
                expanded = visibleEntry.isExpanded,
                modifier = Modifier.height(12.dp).width(24.dp),
                tint = JewelTheme.globalColors.text.normal,
                contentDescription = "",
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (checkboxChecked != null && onCheckboxToggle != null) {
                Checkbox(
                    checked = checkboxChecked,
                    onCheckedChange = onCheckboxToggle,
                )
            }
            Text(
                text = visibleEntry.entry.text,
                fontWeight = if (isSelected) Bold else Normal,
                modifier = Modifier.weight(1f),
            )
            if (showCount && count > 0) {
                CountBadge(count)
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        modifier =
            Modifier
                .padding(start = 8.dp)
                .height(18.dp),
    ) {
        Text(text = count.toString(), color = JewelTheme.globalColors.text.disabled, fontSize = 12.sp)
    }
}
