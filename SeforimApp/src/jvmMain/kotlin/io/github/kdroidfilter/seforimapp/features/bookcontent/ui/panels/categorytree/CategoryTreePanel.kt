package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.categorytree

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneSearchButton
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneSearchLayout
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.rememberPaneSearchFocus
import io.github.kdroidfilter.seforimapp.framework.search.MIN_BOOK_QUERY_LENGTH
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.book_list
import seforimapp.seforimapp.generated.resources.book_search
import seforimapp.seforimapp.generated.resources.book_search_no_results

@Composable
fun CategoryTreePanel(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paneHoverSource = remember { MutableInteractionSource() }
    val search = uiState.navigation.search
    val searchFocus = rememberPaneSearchFocus()
    Column(modifier = modifier.hoverable(paneHoverSource)) {
        PaneHeader(
            label = stringResource(Res.string.book_list),
            interactionSource = paneHoverSource,
            onHide = { onEvent(BookContentEvent.ToggleBookTree) },
            actions = {
                PaneSearchButton(
                    isOpen = search != null,
                    focus = searchFocus,
                    onClick = { onEvent(BookContentEvent.ToggleBookTreeSearch) },
                    contentDescription = stringResource(Res.string.book_search),
                )
            },
        )
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            PaneSearchLayout(
                search = search,
                focus = searchFocus,
                placeholder = stringResource(Res.string.book_search),
                noResults = stringResource(Res.string.book_search_no_results),
                onQueryChange = { onEvent(BookContentEvent.BookTreeSearchQueryChanged(it)) },
                onSelect = { onEvent(BookContentEvent.BookTreeSearchBookSelected(it)) },
                onClose = { onEvent(BookContentEvent.CloseBookTreeSearch) },
                minQueryLength = MIN_BOOK_QUERY_LENGTH,
                regular = { NavigationTree(uiState = uiState, onEvent = onEvent) },
            ) { result, activeId ->
                CategoryBookTreeView(
                    navigationState =
                        uiState.navigation.copy(
                            rootCategories = result.roots,
                            categoryChildren = result.children,
                            booksInCategory = result.books,
                            expandedCategories = result.categoryIds,
                            selectedCategory = null,
                            selectedBook = null,
                            scrollIndex = 0,
                            scrollOffset = 0,
                            categoryReveal = null,
                        ),
                    onCategoryClick = {},
                    onBookClick = bookClick(onEvent) { onEvent(BookContentEvent.BookTreeSearchBookSelected(it.id)) },
                    onScroll = { _, _ -> },
                    selectedBookIdOverride = activeId,
                )
            }
        }
    }
}

@Composable
private fun NavigationTree(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
) {
    CategoryBookTreeView(
        navigationState = uiState.navigation,
        onCategoryClick = { onEvent(BookContentEvent.CategorySelected(it)) },
        onBookClick = bookClick(onEvent) { onEvent(BookContentEvent.BookSelected(it)) },
        onScroll = { index, offset -> onEvent(BookContentEvent.BookTreeScrolled(index, offset)) },
        onCategoryRevealComplete = { onEvent(BookContentEvent.CategoryRevealed) },
    )
}

/** A book click: Ctrl/Cmd opens it in a new tab, a plain click runs [onOpen]. */
@Composable
private fun bookClick(
    onEvent: (BookContentEvent) -> Unit,
    onOpen: (Book) -> Unit,
): (Book) -> Unit {
    val windowInfo = LocalWindowInfo.current
    return { book ->
        val mods = windowInfo.keyboardModifiers
        if (mods.isCtrlPressed || mods.isMetaPressed) onEvent(BookContentEvent.BookSelectedInNewTab(book)) else onOpen(book)
    }
}

// Search mode variant: lambdas-only for events (no onEvent)

@Composable
fun SearchCategoryTreePanel(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    searchTree: ImmutableList<io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.SearchTreeCategory>,
    isFiltering: Boolean,
    selectedCategoryIds: Set<Long>,
    selectedBookIds: Set<Long>,
    onCategoryCheckedChange: (Long, Boolean) -> Unit,
    onBookCheckedChange: (Long, Boolean) -> Unit,
    onEnsureScopeBookForToc: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paneHoverSource = remember { MutableInteractionSource() }
    Column(modifier = modifier.hoverable(paneHoverSource)) {
        PaneHeader(
            label = stringResource(Res.string.book_list),
            interactionSource = paneHoverSource,
            onHide = { onEvent(BookContentEvent.ToggleBookTree) },
        )
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            SearchResultCategoryTreeView(
                expandedCategoryIds = uiState.navigation.expandedCategories,
                scrollIndex = uiState.navigation.scrollIndex,
                scrollOffset = uiState.navigation.scrollOffset,
                searchTree = searchTree,
                isFiltering = isFiltering,
                selectedCategoryIds = selectedCategoryIds,
                selectedBookIds = selectedBookIds,
                onCategoryRowClick = { onEvent(BookContentEvent.CategorySelected(it)) },
                onPersistScroll = { index, offset -> onEvent(BookContentEvent.BookTreeScrolled(index, offset)) },
                onCategoryCheckedChange = onCategoryCheckedChange,
                onBookCheckedChange = onBookCheckedChange,
                onEnsureScopeBookForToc = onEnsureScopeBookForToc,
            )
        }
    }
}
