package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimlibrary.core.models.AltTocEntry
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.ListComboBox
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.alt_table_of_contents
import seforimapp.seforimapp.generated.resources.no_alt_toc_available
import seforimapp.seforimapp.generated.resources.select_book_for_toc
import seforimapp.seforimapp.generated.resources.select_structure_for_toc
import seforimapp.seforimapp.generated.resources.table_of_contents

@Composable
fun BookTocPanel(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val paneHoverSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .hoverable(paneHoverSource)
    ) {
        PaneHeader(
            label = stringResource(Res.string.table_of_contents),
            interactionSource = paneHoverSource,
            onHide = { onEvent(BookContentEvent.ToggleToc) }
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            when {
                uiState.navigation.selectedBook == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(Res.string.select_book_for_toc))
                    }
                }
                else -> {
                    val hasAlt = uiState.navigation.selectedBook?.hasAltStructures == true
                    Column(modifier = Modifier.fillMaxHeight()) {
                        BookTocView(
                            uiState = uiState,
                            onEvent = onEvent,
                            modifier = if (hasAlt) Modifier.weight(1f) else Modifier.fillMaxHeight()
                        )
                        if (hasAlt) {
                            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                                HorizontalDivider()
                            }
                            AltBookTocSection(
                                uiState = uiState,
                                onEvent = onEvent,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBookTocPanel(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    searchUi: io.github.kdroidfilter.seforimapp.features.search.SearchUiState,
    tocTree: io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.TocTree?,
    tocCounts: Map<Long, Int>,
    selectedTocIds: Set<Long>,
    onToggle: (TocEntry, Boolean) -> Unit,
    onTocFilter: (TocEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val paneHoverSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .hoverable(paneHoverSource)
    ) {
        PaneHeader(
            label = stringResource(Res.string.table_of_contents),
            interactionSource = paneHoverSource,
            onHide = { onEvent(BookContentEvent.ToggleToc) }
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            if (searchUi.scopeBook != null) {
                Box(modifier = Modifier.fillMaxHeight()) {
                    val expanded: Set<Long> = uiState.toc.expandedEntries
                    var autoExpanded by remember(searchUi.scopeBook.id, tocCounts, tocTree) { mutableStateOf(false) }
                    LaunchedEffect(searchUi.scopeBook.id, tocCounts, tocTree) {
                        if (!autoExpanded) {
                            val idsWithChildren = tocTree?.children?.keys ?: emptySet()
                            val withResults = tocCounts.keys
                            val targetToExpand = withResults.intersect(idsWithChildren) - expanded
                            if (targetToExpand.isNotEmpty()) {
                                fun findEntryById(id: Long): TocEntry? {
                                    val tree = tocTree ?: return null
                                    if (tree.rootEntries.any { it.id == id }) {
                                        return tree.rootEntries.first { it.id == id }
                                    }
                                    val queue = ArrayDeque<Long>()
                                    queue.addAll(tree.children.keys)
                                    while (queue.isNotEmpty()) {
                                        val pid = queue.removeFirst()
                                        val children = tree.children[pid].orEmpty()
                                        for (child in children) {
                                            if (child.id == id) return child
                                        }
                                        tree.children[pid]?.forEach { c ->
                                            if (tree.children.containsKey(c.id)) queue.addLast(c.id)
                                        }
                                    }
                                    return null
                                }
                                targetToExpand.forEach { id ->
                                    findEntryById(id)?.let { entry -> onEvent(BookContentEvent.TocEntryExpanded(entry)) }
                                }
                            }
                            autoExpanded = true
                        }
                    }

                    BookTocView(
                        tocEntries = tocTree?.rootEntries ?: emptyList(),
                        expandedEntries = expanded,
                        tocChildren = tocTree?.children ?: emptyMap(),
                        scrollIndex = uiState.toc.scrollIndex,
                        scrollOffset = uiState.toc.scrollOffset,
                        onEntryClick = { entry ->
                            val checked = selectedTocIds.contains(entry.id)
                            onToggle(entry, !checked)
                        },
                        onEntryExpand = { entry -> onEvent(BookContentEvent.TocEntryExpanded(entry)) },
                        onScroll = { index, offset -> onEvent(BookContentEvent.TocScrolled(index, offset)) },
                        selectedTocEntryId = searchUi.scopeTocId,
                        modifier = Modifier.fillMaxHeight(),
                        showCounts = true,
                        onlyWithResults = true,
                        tocCounts = tocCounts,
                        selectedTocOverride = searchUi.scopeTocId,
                        onTocFilter = null,
                        multiSelectIds = selectedTocIds,
                        onToggle = onToggle
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(Res.string.select_book_for_toc))
                }
            }
        }
    }
}

@Composable
private fun AltBookTocSection(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val altState = uiState.altToc
    val bookId = uiState.navigation.selectedBook?.id ?: return
    Column(modifier = modifier.fillMaxWidth()) {
        val structures = altState.structures
        if (structures.isEmpty()) {
            Text(stringResource(Res.string.no_alt_toc_available))
            return
        }
        val rootEntries = altState.entries
            .map { it.toTocEntry(bookId) }

        val childrenMap = altState.children.mapValues { (_, children) -> children.map { it.toTocEntry(bookId) } }
        val altEntryById = remember(altState.entries, altState.children) {
            (altState.entries + altState.children.values.flatten()).associateBy { it.id }
        }
        var displayEntries by remember(rootEntries, childrenMap) { mutableStateOf(rootEntries) }
   

        BookTocView(
            tocEntries = displayEntries,
            expandedEntries = altState.expandedEntries,
            tocChildren = childrenMap,
            scrollIndex = altState.scrollIndex,
            scrollOffset = altState.scrollOffset,
            onEntryClick = { entry ->
                altEntryById[entry.id]?.let { onEvent(BookContentEvent.AltTocEntrySelected(it)) }
            },
            onEntryExpand = { entry ->
                altEntryById[entry.id]?.let { onEvent(BookContentEvent.AltTocEntryExpanded(it)) }
            },
            onScroll = { index, offset -> onEvent(BookContentEvent.AltTocScrolled(index, offset)) },
            selectedTocEntryId = altState.selectedEntryId,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun AltTocEntry.toTocEntry(bookId: Long): TocEntry =
    TocEntry(
        id = id,
        bookId = bookId,
        parentId = parentId,
        textId = textId,
        text = text,
        level = level,
        lineId = lineId,
        isLastChild = isLastChild,
        hasChildren = hasChildren
    )
