package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimapp.features.screens.bookcontent.models.NavigationUiState
import org.jetbrains.jewel.ui.component.Text

@Stable
private data class TreeItem(
    val id: String,
    val level: Int,
    val content: @Composable () -> Unit
)

@Composable
fun CategoryBookTree(
    navigationState: NavigationUiState,
    onCategoryClick: (Category) -> Unit,
    onBookClick: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    val treeItems = remember(
        navigationState.rootCategories,
        navigationState.expandedCategories,
        navigationState.categoryChildren,
        navigationState.booksInCategory
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
    
    LazyColumn(
        modifier = modifier.fillMaxWidth().fillMaxHeight()
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
            // Add books
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
            
            // Add child categories
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
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isExpanded) "-" else "+",
            modifier = Modifier.width(24.dp)
        )
        Text(text = "📁", modifier = Modifier.width(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
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
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(24.dp))
        Text(text = "📕", modifier = Modifier.width(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = book.title)
    }
}
