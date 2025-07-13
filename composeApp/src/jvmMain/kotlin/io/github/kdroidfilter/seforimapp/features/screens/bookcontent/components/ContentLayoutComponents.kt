package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import seforimapp.composeapp.generated.resources.*

/**
 * Component that displays the table of contents panel.
 */
@Composable
fun TocPanel(
    state: BookContentState,
    onEvents: (BookContentEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxHeight()
    ) {
        Text(
            text = stringResource(Res.string.table_of_contents),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (state.selectedBook != null) {
            // Show TOC for selected book
            // Check if we have root entries stored with the special key (-1L)
            val rootEntries = state.tocChildren[-1L] ?: emptyList()
            val displayTocEntries = rootEntries.ifEmpty {
                // Otherwise, use the original tocEntries
                state.tocEntries
            }

            Box(modifier = Modifier.fillMaxHeight()) {
                TocView(
                    tocEntries = displayTocEntries,
                    expandedEntries = state.expandedTocEntries,
                    tocChildren = state.tocChildren,
                    onEntryClick = { tocEntry ->
                        // Handle TOC entry click
                        tocEntry.lineId?.let { lineId ->
                            // Check if the line is already loaded
                            val existingLine = state.bookLines.find { it.id == lineId }
                            if (existingLine != null) {
                                // If the line is already loaded, just select it
                                onEvents(BookContentEvents.OnLineSelected(existingLine))
                            } else {
                                // If the line is not loaded, we need to load it first
                                onEvents(BookContentEvents.OnLoadAndSelectLine(lineId))
                            }
                        }
                    },
                    onEntryExpand = { tocEntry ->
                        onEvents(BookContentEvents.OnTocEntryExpanded(tocEntry))
                    },
                    modifier = Modifier.fillMaxHeight()
                )

                // Show loading indicator when loading TOC entries
                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x80FFFFFF)), // Semi-transparent white background
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
            // Show placeholder when no book is selected
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.select_book_for_toc))
            }
        }
    }
}

/**
 * Component that displays the book content with or without commentaries.
 */
@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun BookContentPanel(
    state: BookContentState,
    onEvents: (BookContentEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.selectedBook != null) {
        if (state.showCommentaries) {
            // Show book content with commentaries in a vertical split pane
            EnhancedVerticalSplitPane(
                splitPaneState = state.contentSplitPaneState,
                modifier = modifier,
                firstContent = {
                    // Book content
                    BookContentView(
                        book = state.selectedBook,
                        lines = state.bookLines,
                        selectedLine = state.selectedLine,
                        onLineSelected = { line ->
                            onEvents(BookContentEvents.OnLineSelected(line))
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                },
                secondContent = {
                    // Commentaries panel
                    LineCommentsView(
                        selectedLine = state.selectedLine,
                        commentaries = state.commentaries,
                        onCommentClick = { /* Handle comment click if needed */ }
                    )
                }
            )
        } else {
            // Show only book content
            BookContentView(
                book = state.selectedBook,
                lines = state.bookLines,
                selectedLine = state.selectedLine,
                onLineSelected = { line ->
                    onEvents(BookContentEvents.OnLineSelected(line))
                },
                modifier = modifier.padding(16.dp)
            )
        }
    } else {
        // Show placeholder content when no book is selected
        Box(
            modifier = modifier
                .padding(16.dp)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(Res.string.select_book))
        }
    }
}

/**
 * Component that displays the category tree panel with search functionality.
 */
@Composable
fun CategoryTreePanel(
    state: BookContentState,
    onEvents: (BookContentEvents) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
    ) {
        // Search field
        val searchFieldState = rememberTextFieldState(state.searchText)

        // Synchronize state with the viewmodel
        LaunchedEffect(state.searchText) {
            if (searchFieldState.text.toString() != state.searchText) {
                searchFieldState.edit {
                    replace(0, length, state.searchText)
                }
            }
        }

        LaunchedEffect(searchFieldState.text) {
            onEvents(BookContentEvents.OnSearchTextChange(searchFieldState.text.toString()))
        }

        TextField(
            state = searchFieldState,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(Res.string.search_placeholder)) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Show category tree
        CategoryBookTree(
            rootCategories = state.rootCategories,
            expandedCategories = state.expandedCategories,
            categoryChildren = state.categoryChildren,
            booksInCategory = state.booksInCategory,
            selectedCategory = state.selectedCategory,
            selectedBook = state.selectedBook,
            onCategoryClick = { category -> 
                onEvents(BookContentEvents.OnCategorySelected(category))
            },
            onBookClick = { book ->
                onEvents(BookContentEvents.OnBookSelected(book))
            },
            modifier = Modifier
        )
    }
}