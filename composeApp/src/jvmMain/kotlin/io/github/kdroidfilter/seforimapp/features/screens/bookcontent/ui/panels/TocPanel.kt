package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.TocUiState
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components.TocView
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.select_book_for_toc
import seforimapp.composeapp.generated.resources.table_of_contents

@Composable
fun TocPanel(
    selectedBook: Book?,
    tocState: TocUiState,
    isLoading: Boolean,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp).fillMaxHeight()
    ) {
        Text(
            text = stringResource(Res.string.table_of_contents),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        when {
            selectedBook == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(Res.string.select_book_for_toc))
                }
            }
            else -> {
                val rootEntries = tocState.children[-1L] ?: tocState.entries
                var displayEntries = rootEntries.ifEmpty { tocState.entries }

                // If there is exactly one top-level parent, auto-open and show its children
                if (displayEntries.size == 1) {
                    val soleParent = displayEntries.first()
                    val directChildren = tocState.children[soleParent.id]

                    if (directChildren.isNullOrEmpty()) {
                        // Children not loaded yet: trigger expansion once to load them
                        if (soleParent.hasChildren && !tocState.expandedEntries.contains(soleParent.id)) {
                            LaunchedEffect(selectedBook?.id, soleParent.id) {
                                onEvent(BookContentEvent.TocEntryExpanded(soleParent))
                            }
                        }
                        // Keep displaying the root until children are available
                    } else {
                        displayEntries = directChildren
                    }
                }

                Box(modifier = Modifier.fillMaxHeight()) {
                    TocView(
                        tocEntries = displayEntries,
                        expandedEntries = tocState.expandedEntries,
                        tocChildren = tocState.children,
                        scrollIndex = tocState.scrollIndex,
                        scrollOffset = tocState.scrollOffset,
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
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }
        }
    }
}

