package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.booktoc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.hoverable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier,
    // Optional: integrate with search to show only TOC entries with results and counts
    searchViewModel: io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel? = null
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
            val searchUi = if (searchViewModel != null) searchViewModel.uiState.collectAsState().value else null
            when {
                // Prefer search scope book when in search mode
                searchViewModel != null && searchUi?.scopeBook != null -> {
                    Box(modifier = Modifier.fillMaxHeight()) {
                        // Use fast StateFlows provided by the ViewModel (restored from snapshot)
                        val tocCountsState = searchViewModel.tocCountsFlow.collectAsState()
                        val tocTreeState = searchViewModel.tocTreeFlow.collectAsState()
                        // Persist expansion state via BookContentState (no local-only state)
                        val expanded: Set<Long> = uiState.toc.expandedEntries
                        // On first display, auto-expand nodes that contain results
                        var autoExpanded by remember(searchUi.scopeBook?.id, tocCountsState.value, tocTreeState.value) { mutableStateOf(false) }
                        LaunchedEffect(searchUi.scopeBook?.id, tocCountsState.value, tocTreeState.value) {
                            if (!autoExpanded) {
                                // Expand ancestors that have children and results
                                val idsWithChildren = tocTreeState.value?.children?.keys ?: emptySet()
                                val withResults = tocCountsState.value.keys
                                val targetToExpand = withResults.intersect(idsWithChildren) - expanded
                                if (targetToExpand.isNotEmpty()) {
                                    // Helper to find TocEntry by id in the current tree
                                    fun findEntryById(id: Long): io.github.kdroidfilter.seforimlibrary.core.models.TocEntry? {
                                        val tree = tocTreeState.value
                                        if (tree == null) return null
                                        if (tree.rootEntries.any { it.id == id }) {
                                            return tree.rootEntries.first { it.id == id }
                                        }
                                        // BFS over children map
                                        val queue = ArrayDeque<Long>()
                                        queue.addAll(tree.children.keys)
                                        while (queue.isNotEmpty()) {
                                            val pid = queue.removeFirst()
                                            val children = tree.children[pid].orEmpty()
                                            for (child in children) {
                                                if (child.id == id) return child
                                            }
                                            // continue traversal
                                            tree.children[pid]?.forEach { c ->
                                                if (tree.children.containsKey(c.id)) queue.addLast(c.id)
                                            }
                                        }
                                        return null
                                    }
                                    targetToExpand.forEach { id ->
                                        findEntryById(id)?.let { entry -> onEvent(io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent.TocEntryExpanded(entry)) }
                                    }
                                }
                                autoExpanded = true
                            }
                        }

                        BookTocView(
                            tocEntries = tocTreeState.value?.rootEntries ?: emptyList(),
                            expandedEntries = expanded,
                            tocChildren = tocTreeState.value?.children ?: emptyMap(),
                            scrollIndex = uiState.toc.scrollIndex,
                            scrollOffset = uiState.toc.scrollOffset,
                            onEntryClick = { entry -> searchViewModel.filterByTocId(entry.id) },
                            onEntryExpand = { entry -> onEvent(io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent.TocEntryExpanded(entry)) },
                            onScroll = { index, offset -> onEvent(BookContentEvent.TocScrolled(index, offset)) },
                            selectedTocEntryId = searchUi.scopeTocId,
                            modifier = Modifier.fillMaxHeight(),
                            showCounts = true,
                            onlyWithResults = true,
                            tocCounts = tocCountsState.value,
                            selectedTocOverride = searchUi.scopeTocId,
                            onTocFilter = { entry -> searchViewModel.filterByTocId(entry.id) }
                        )
                    }
                }
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
