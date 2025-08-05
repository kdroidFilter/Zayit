package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

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
    rootCategories: List<Category>,
    categoryChildren: Map<Long, List<Category>>,
    onTocEntryClick: (TocEntry) -> Unit,
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    // Build the breadcrumb path from the category root to the selected line
    val breadcrumbPath = remember(book, selectedLine, tocEntries, tocChildren, rootCategories, categoryChildren) {
        buildBreadcrumbPath(book, selectedLine, tocEntries, tocChildren, rootCategories, categoryChildren)
    }
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        // Display each item in the breadcrumb path
        breadcrumbPath.forEachIndexed { index, item ->
            if (index > 0) {
                // Add separator between items
                Text(
                    text = " > ",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    fontSize = 12.sp
                )
            }

            // Display the item based on its type
            when (item) {
                is BreadcrumbItem.CategoryItem -> {
                    Text(
                        text = item.category.title,
                        fontWeight = if (index == breadcrumbPath.lastIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { onCategoryClick(item.category) },
                        fontSize = 12.sp

                    )
                }
                is BreadcrumbItem.BookItem -> {
                    Text(
                        text = item.book.title,
                        fontWeight = if (index == breadcrumbPath.lastIndex) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp

                    )
                }
                is BreadcrumbItem.TocItem -> {
                    Text(
                        text = item.tocEntry.text,
                        fontWeight = if (index == breadcrumbPath.lastIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { onTocEntryClick(item.tocEntry) },
                        fontSize = 12.sp

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
private fun buildBreadcrumbPath(
    book: Book,
    selectedLine: Line?,
    tocEntries: List<TocEntry>,
    tocChildren: Map<Long, List<TocEntry>>,
    rootCategories: List<Category>,
    categoryChildren: Map<Long, List<Category>>
): List<BreadcrumbItem> {
    val result = mutableListOf<BreadcrumbItem>()
    
    // First, build the category path
    val categoryPath = buildCategoryPath(book.categoryId, rootCategories, categoryChildren)
    result.addAll(categoryPath)
    
    // Add the book
    result.add(BreadcrumbItem.BookItem(book))
    
    // If no line is selected, return just the categories and book
    if (selectedLine == null) {
        return result
    }
    
    // Find the TOC entry associated with the selected line
    val lineEntry = findTocEntryForLine(selectedLine.id, tocEntries, tocChildren)
        ?: return result
    
    // Build the path from the line entry to the root of the TOC
    val tocPath = mutableListOf<TocEntry>()
    var currentEntry: TocEntry? = lineEntry
    
    // Add entries from the line to the root (excluding the root)
    while (currentEntry != null) {
        tocPath.add(0, currentEntry)
        currentEntry = if (currentEntry.parentId != null) {
            findTocEntryById(currentEntry.parentId, tocEntries, tocChildren)
        } else {
            null
        }
    }
    
    // Add TOC entries to the result
    result.addAll(tocPath.map { BreadcrumbItem.TocItem(it) })
    
    return result
}

/**
 * Builds a path of categories from the root to the specified category.
 */
private fun buildCategoryPath(
    categoryId: Long,
    rootCategories: List<Category>,
    categoryChildren: Map<Long, List<Category>>
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
    categoryChildren: Map<Long, List<Category>>
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
    class CategoryItem(val category: Category) : BreadcrumbItem()
    class BookItem(val book: Book) : BreadcrumbItem()
    class TocItem(val tocEntry: TocEntry) : BreadcrumbItem()
}

/**
 * Finds a TOC entry by its ID.
 */
private fun findTocEntryById(
    id: Long?,
    tocEntries: List<TocEntry>,
    tocChildren: Map<Long, List<TocEntry>>
): TocEntry? {
    if (id == null) return null
    
    // Check root entries
    tocEntries.find { it.id == id }?.let { return it }
    
    // Check all children
    for ((_, children) in tocChildren) {
        children.find { it.id == id }?.let { return it }
    }
    
    return null
}

/**
 * Finds the TOC entry associated with a line by recursively searching through all TOC entries.
 */
private fun findTocEntryForLine(
    lineId: Long,
    tocEntries: List<TocEntry>,
    tocChildren: Map<Long, List<TocEntry>>
): TocEntry? {
    // Check root entries first
    tocEntries.find { it.lineId == lineId }?.let { return it }
    
    // Recursively search through all entries
    fun searchInChildren(entries: List<TocEntry>): TocEntry? {
        // Check if any entry in this level matches
        entries.find { it.lineId == lineId }?.let { return it }
        
        // Check children of each entry
        for (entry in entries) {
            val children = tocChildren[entry.id] ?: continue
            
            // First check if any direct child matches
            children.find { it.lineId == lineId }?.let { return it }
            
            // Then recursively search in their children
            searchInChildren(children)?.let { return it }
        }
        
        return null
    }
    
    // Start recursive search from root entries
    return searchInChildren(tocEntries)
}