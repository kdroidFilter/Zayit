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
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.select_book_for_toc
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
                    Box(modifier = Modifier.fillMaxHeight()) {
                        BookTocView(
                            uiState = uiState,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxHeight()
                        )
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
    onToggle: (io.github.kdroidfilter.seforimlibrary.core.models.TocEntry, Boolean) -> Unit,
    onTocFilter: (io.github.kdroidfilter.seforimlibrary.core.models.TocEntry) -> Unit,
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
                                fun findEntryById(id: Long): io.github.kdroidfilter.seforimlibrary.core.models.TocEntry? {
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
