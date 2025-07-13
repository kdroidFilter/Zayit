package io.github.kdroidfilter.seforimapp.features.screens.bookcontent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text

/**
 * A tree view component that displays categories and books.
 */
@Composable
fun CategoryBookTree(
    rootCategories: List<Category>,
    expandedCategories: Set<Long>,
    categoryChildren: Map<Long, List<Category>>,
    booksInCategory: Set<Book>,
    selectedCategory: Category?,
    selectedBook: Book?,
    onCategoryClick: (Category) -> Unit,
    onBookClick: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().heightIn(max = 500.dp)
    ) {
        items(rootCategories) { category ->
            CategoryTreeItem(
                category = category,
                level = 0,
                expandedCategories = expandedCategories,
                categoryChildren = categoryChildren,
                booksInCategory = booksInCategory,
                selectedCategory = selectedCategory,
                selectedBook = selectedBook,
                onCategoryClick = onCategoryClick,
                onBookClick = onBookClick
            )
        }
    }
}

/**
 * A recursive component that displays a category and its children.
 */
@Composable
fun CategoryTreeItem(
    category: Category,
    level: Int,
    expandedCategories: Set<Long>,
    categoryChildren: Map<Long, List<Category>>,
    booksInCategory: Set<Book>,
    selectedCategory: Category?,
    selectedBook: Book?,
    onCategoryClick: (Category) -> Unit,
    onBookClick: (Book) -> Unit
) {
    val isExpanded = expandedCategories.contains(category.id)
    val isSelected = selectedCategory?.id == category.id

    // Display the category item
    CategoryItem(
        category = category,
        level = level,
        isExpanded = isExpanded,
        isSelected = isSelected,
        onClick = { onCategoryClick(category) }
    )

    // If expanded, show children and/or books
    if (isExpanded) {
        // Show books in this category
        val booksInThisCategory = booksInCategory.filter { it.categoryId == category.id }
        booksInThisCategory.forEach { book ->
            BookItem(
                book = book,
                level = level + 1,
                isSelected = selectedBook?.id == book.id,
                onClick = { onBookClick(book) }
            )
        }

        // Show child categories
        val children = categoryChildren[category.id] ?: emptyList()
        children.forEach { childCategory ->
            CategoryTreeItem(
                category = childCategory,
                level = level + 1,
                expandedCategories = expandedCategories,
                categoryChildren = categoryChildren,
                booksInCategory = booksInCategory,
                selectedCategory = selectedCategory,
                selectedBook = selectedBook,
                onCategoryClick = onCategoryClick,
                onBookClick = onBookClick
            )
        }
    }
}

/**
 * A component that displays a category.
 */
@Composable
fun CategoryItem(
    category: Category,
    level: Int,
    isExpanded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (level * 16).dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon for expand/collapse
        Text(
            text = if (isExpanded) "-" else "+",
            modifier = Modifier.width(24.dp)
        )

        // Icon for folder
        Text(
            text = "📁",
            modifier = Modifier.width(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Category title
        Text(
            text = category.title
        )
    }
}

/**
 * A component that displays a book.
 */
@Composable
fun BookItem(
    book: Book,
    level: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (level * 16).dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(24.dp))

        // Icon for book
        Text(
            text = "📕",
            modifier = Modifier.width(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Book title
        Text(
            text = book.title
        )
    }
}
