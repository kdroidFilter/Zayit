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
                        // Build counts and TOC structure for the scoped book
                        val tocCountsState = androidx.compose.runtime.produceState(
                            initialValue = emptyMap<Long, Int>(),
                            searchUi.results, searchUi.scopeBook?.id
                        ) {
                            value = kotlin.runCatching { searchViewModel.computeTocCountsForSelectedBook() }.getOrDefault(emptyMap())
                        }
                        val tocTreeState = androidx.compose.runtime.produceState<io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.TocTree>(
                            initialValue = io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.TocTree(emptyList(), emptyMap()),
                            searchUi.scopeBook?.id
                        ) {
                            value = kotlin.runCatching { searchViewModel.getTocStructureForScopeBook() ?: io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.TocTree(emptyList(), emptyMap()) }.getOrDefault(io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.TocTree(emptyList(), emptyMap()))
                        }
                        // Keep local expansion state in search mode and auto-expand nodes with results
                        var expanded by remember(tocTreeState.value) { mutableStateOf<Set<Long>>(emptySet()) }
                        var expandedInitialized by remember(tocTreeState.value, tocCountsState.value) { mutableStateOf(false) }
                        LaunchedEffect(tocTreeState.value, tocCountsState.value) {
                            if (!expandedInitialized) {
                                val idsWithChildren = tocTreeState.value.children.keys
                                val withResults = tocCountsState.value.keys
                                expanded = withResults.intersect(idsWithChildren).toSet()
                                expandedInitialized = true
                            }
                        }

                        BookTocView(
                            tocEntries = tocTreeState.value.rootEntries,
                            expandedEntries = expanded,
                            tocChildren = tocTreeState.value.children,
                            scrollIndex = uiState.toc.scrollIndex,
                            scrollOffset = uiState.toc.scrollOffset,
                            onEntryClick = { entry -> searchViewModel.filterByTocId(entry.id) },
                            onEntryExpand = { entry ->
                                expanded = if (expanded.contains(entry.id)) expanded - entry.id else expanded + entry.id
                            },
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
