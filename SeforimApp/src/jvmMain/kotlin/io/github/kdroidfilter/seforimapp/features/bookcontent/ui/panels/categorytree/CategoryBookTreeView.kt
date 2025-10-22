package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.categorytree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.font.FontWeight.Companion.Normal
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.components.ChevronIcon
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.NavigationUiState
import io.github.kdroidfilter.seforimapp.icons.Book_2
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.iconButtonStyle


@Stable
private data class TreeItem(
    val id: String,
    val level: Int,
    val content: @Composable () -> Unit
)

@OptIn(FlowPreview::class)
@Composable
fun CategoryBookTreeView(
    navigationState: NavigationUiState,
    onCategoryClick: (Category) -> Unit,
    onBookClick: (Book) -> Unit,
    onScroll: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    /* ---------------------------------------------------------------------
     * Build the flat hierarchical list to display.
     * -------------------------------------------------------------------- */
    val treeItems = remember(
        navigationState.rootCategories,
        navigationState.expandedCategories,
        navigationState.categoryChildren,
        navigationState.booksInCategory,
        navigationState.selectedCategory,
        navigationState.selectedBook
    ) {
        buildTreeItems(
            rootCategories = navigationState.rootCategories,
            expandedCategories = navigationState.expandedCategories,
            categoryChildren = navigationState.categoryChildren,
            booksInCategory = navigationState.booksInCategory,
            selectedCategory = navigationState.selectedCategory,
            selectedBook = navigationState.selectedBook,
            onCategoryClick = onCategoryClick,
            onBookClick = onBookClick
        )
    }

    /* ---------------------------------------------------------------------
     * Restore the LazyListState.
     * -------------------------------------------------------------------- */
    val listState: LazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = navigationState.scrollIndex,
        initialFirstVisibleItemScrollOffset = navigationState.scrollOffset
    )

    var hasRestored by remember { mutableStateOf(false) }


    /* ---------------- 1. Restore when the list is truly ready -------- */
    LaunchedEffect(treeItems.size) {
        if (treeItems.isNotEmpty() && !hasRestored) {
            val safeIndex = navigationState.scrollIndex
                .coerceIn(0, treeItems.lastIndex)
            listState.scrollToItem(safeIndex, navigationState.scrollOffset)
            hasRestored = true          // ← wait until finished before listening to scrolls
        }
    }

    /* ---------------- 2. Propagate scrolls *after* restoration ---------- */
    LaunchedEffect(listState, hasRestored) {
        if (hasRestored) {
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
                .distinctUntilChanged()
                .debounce(250)
                .collect { (i, o) -> onScroll(i, o) }
        }
    }


    /* ---------------------------------------------------------------------
     * UI.
     * -------------------------------------------------------------------- */
    Box(modifier = modifier.fillMaxSize().padding(bottom = 8.dp)) {
        VerticallyScrollableContainer(
            scrollState = listState as ScrollableState,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 16.dp)
            ) {
                items(
                    items = treeItems,
                    key = { it.id }
                ) { item ->
                    Box(modifier = Modifier.padding(start = (item.level * 16).dp)) {
                        item.content()
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------
 * Helpers
 * ---------------------------------------------------------------------- */
private fun buildTreeItems(
    rootCategories: List<Category>,
    expandedCategories: Set<Long>,
    categoryChildren: Map<Long, List<Category>>,
    booksInCategory: Set<Book>,
    selectedCategory: Category?,
    selectedBook: Book?,
    onCategoryClick: (Category) -> Unit,
    onBookClick: (Book) -> Unit
): List<TreeItem> = buildList {
    fun addCategory(category: Category, level: Int) {
        add(
            TreeItem(
                id = "category_${category.id}",
                level = level,
                content = {
                    CategoryItem(
                        category = category,
                        isExpanded = expandedCategories.contains(category.id),
                        isSelected = selectedCategory?.id == category.id,
                        onClick = { onCategoryClick(category) }
                    )
                }
            )
        )

        if (expandedCategories.contains(category.id)) {
            // Books in this category
            booksInCategory
                .filter { it.categoryId == category.id }
                .forEach { book ->
                    add(
                        TreeItem(
                            id = "book_${book.id}",
                            level = level + 1,
                            content = {
                                BookItem(
                                    book = book,
                                    isSelected = selectedBook?.id == book.id,
                                    onClick = { onBookClick(book) }
                                )
                            }
                        )
                    )
                }

            // Subcategories
            categoryChildren[category.id]?.forEach { child ->
                addCategory(child, level + 1)
            }
        }
    }

    rootCategories.forEach { category ->
        addCategory(category, 0)
    }
}

@Composable
private fun CategoryItem(
    category: Category,
    isExpanded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
            .pointerHoverIcon(PointerIcon.Hand),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ChevronIcon(
            expanded = isExpanded,
            tint = JewelTheme.globalColors.text.normal,
            contentDescription = ""
        )
        Icon(
            key = AllIconsKeys.Nodes.Folder,
            contentDescription = null,
        )
        Text(text = category.title)
    }
}

@Composable
private fun BookItem(
    book: Book,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) JewelTheme.iconButtonStyle.colors.backgroundFocused else Color.Transparent)
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
           imageVector = Book_2,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isSelected) JewelTheme.globalColors.text.selected else JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
        )
        Text(text = book.title, fontWeight = if (isSelected) Bold else Normal)
    }
}