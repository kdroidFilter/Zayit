package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import org.jetbrains.jewel.ui.component.Text
import kotlin.collections.iterator

/**
 * A breadcrumb component that displays the hierarchical path from the category root to the selected line.
 *
 * @param book The book being displayed
 * @param selectedLine The currently selected line
 * @param tocEntries All TOC entries for the book
 * @param tocChildren Map of parent ID to list of child TOC entries
 * @param rootCategories List of top-level categories
 * @param categoryChildren Map of category ID to list of child categories
 * @param onTocEntryClick Callback when a TOC entry in the breadcrumb is clicked
 * @param onCategoryClick Callback when a category in the breadcrumb is clicked
 * @param modifier Modifier for the breadcrumb
 */
@Composable
fun BreadcrumbView(
    book: Book,
    selectedLine: Line?,
    tocEntries: List<TocEntry>,
    tocChildren: Map<Long, List<TocEntry>>,
    tocPath: List<TocEntry>,
    rootCategories: List<Category>,
    categoryChildren: Map<Long, List<Category>>,
    onTocEntryClick: (TocEntry) -> Unit,
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Build the breadcrumb path from the category root through book to the owning TOC hierarchy
    val breadcrumbPath =
        remember(
            book,
            selectedLine?.id,
            tocPath,
            tocEntries,
            tocChildren,
            rootCategories,
            categoryChildren,
        ) {
            val result = mutableListOf<BreadcrumbItem>()

            // Categories path then book
            result += buildCategoryPath(book.categoryId, rootCategories, categoryChildren)
            result += BreadcrumbItem.BookItem(book)

            // Append the TOC path computed by use case, deduplicating consecutive identical names anywhere
            if (tocPath.isNotEmpty()) {
                // If first TOC equals book title, drop it to avoid duplication with the book item
                val adjustedToc = if (tocPath.first().text == book.title) tocPath.drop(1) else tocPath
                result += adjustedToc.map { BreadcrumbItem.TocItem(it) }
            }

            result
        }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        // Display each item in the breadcrumb path
        breadcrumbPath.forEachIndexed { index, item ->
            if (index > 0) {
                // Add separator between items
                Text(
                    text = " > ",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    fontSize = 12.sp,
                )
            }

            // Display the item based on its type
            when (item) {
                is BreadcrumbItem.CategoryItem -> {
                    Text(
                        text = item.category.title,
                        fontWeight = if (index == breadcrumbPath.lastIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier =
                            Modifier
                                .clickable { onCategoryClick(item.category) },
                        fontSize = 12.sp,
                    )
                }
                is BreadcrumbItem.BookItem -> {
                    Text(
                        text = item.book.title,
                        fontWeight = if (index == breadcrumbPath.lastIndex) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                    )
                }
                is BreadcrumbItem.TocItem -> {
                    Text(
                        text = item.tocEntry.text,
                        fontWeight = if (index == breadcrumbPath.lastIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier =
                            Modifier
                                .clickable { onTocEntryClick(item.tocEntry) },
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * Builds a breadcrumb path from the category root through the book to the selected line.
 *
 * @param book The book being displayed
 * @param selectedLine The currently selected line
 * @param tocEntries All TOC entries for the book
 * @param tocChildren Map of parent ID to list of child TOC entries
 * @param rootCategories List of top-level categories
 * @param categoryChildren Map of category ID to list of child categories
 * @return A list of breadcrumb items representing the path from the category root to the selected line
 */
@Composable
fun BreadcrumbView(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = uiState.navigation.selectedBook ?: return
    BreadcrumbView(
        book = book,
        selectedLine = uiState.content.selectedLine,
        tocEntries = uiState.toc.entries,
        tocChildren = uiState.toc.children,
        tocPath = uiState.toc.breadcrumbPath,
        rootCategories = uiState.navigation.rootCategories,
        categoryChildren = uiState.navigation.categoryChildren,
        onTocEntryClick = { entry ->
            entry.lineId?.let { lineId ->
                onEvent(BookContentEvent.LoadAndSelectLine(lineId))
            }
        },
        onCategoryClick = { category ->
            onEvent(BookContentEvent.CategorySelected(category))
        },
        modifier = modifier,
    )
}

// Old recursive breadcrumb builder removed in favor of repository-backed mapping

/**
 * Builds a path of categories from the root to the specified category.
 */
private fun buildCategoryPath(
    categoryId: Long,
    rootCategories: List<Category>,
    categoryChildren: Map<Long, List<Category>>,
): List<BreadcrumbItem> {
    // Find the category in the root categories
    val rootCategory = rootCategories.find { it.id == categoryId }
    if (rootCategory != null) {
        return listOf(BreadcrumbItem.CategoryItem(rootCategory))
    }

    // Search for the category in the children
    for (root in rootCategories) {
        val path = findCategoryPath(root, categoryId, categoryChildren)
        if (path.isNotEmpty()) {
            return path
        }
    }

    return emptyList()
}

/**
 * Recursively finds the path to a category.
 */
private fun findCategoryPath(
    current: Category,
    targetId: Long,
    categoryChildren: Map<Long, List<Category>>,
): List<BreadcrumbItem> {
    // If this is the target, return a path with just this category
    if (current.id == targetId) {
        return listOf(BreadcrumbItem.CategoryItem(current))
    }

    // Check children
    val children = categoryChildren[current.id] ?: return emptyList()
    for (child in children) {
        val path = findCategoryPath(child, targetId, categoryChildren)
        if (path.isNotEmpty()) {
            // Found the target in this subtree, add current category to the path
            return listOf(BreadcrumbItem.CategoryItem(current)) + path
        }
    }

    return emptyList()
}

/**
 * Represents an item in the breadcrumb path.
 */
sealed class BreadcrumbItem {
    class CategoryItem(
        val category: Category,
    ) : BreadcrumbItem()

    class BookItem(
        val book: Book,
    ) : BreadcrumbItem()

    class TocItem(
        val tocEntry: TocEntry,
    ) : BreadcrumbItem()
}
