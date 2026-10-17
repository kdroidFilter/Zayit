package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookFilterResult
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category

/**
 * Narrows the book tree to [hitIds] (best first) and the categories leading to them, keeping
 * the catalog order. Hits missing from the catalog are ignored.
 */
internal fun buildBookFilter(
    hitIds: List<Long>,
    rootCategories: List<Category>,
    categoryChildren: Map<Long, List<Category>>,
    books: Set<Book>,
): BookFilterResult {
    val hitSet = hitIds.toHashSet()
    val hits = books.filterTo(LinkedHashSet()) { it.id in hitSet }
    if (hits.isEmpty()) return BookFilterResult()

    val categoriesById = (rootCategories + categoryChildren.values.flatten()).associateBy { it.id }
    val visible = HashSet<Long>()
    for (book in hits) {
        var category = categoriesById[book.categoryId]
        while (category != null && visible.add(category.id)) {
            category = category.parentId?.let(categoriesById::get)
        }
    }

    val children =
        categoryChildren
            .filterKeys { it in visible }
            .mapValues { (_, kids) -> kids.filter { it.id in visible } }
            .filterValues { it.isNotEmpty() }
    val roots = rootCategories.filter { it.id in visible }

    // Display order, as the tree lists them: a category's books, then its subcategories
    val booksByCategory = hits.groupBy { it.categoryId }
    val ordered = ArrayList<Long>(hits.size)

    fun walk(categories: List<Category>) {
        categories.forEach { category ->
            booksByCategory[category.id]?.forEach { ordered += it.id }
            walk(children[category.id].orEmpty())
        }
    }
    walk(roots)
    val inTree = ordered.toHashSet()

    return BookFilterResult(
        roots = roots,
        children = children,
        books = hits,
        categoryIds = visible,
        matchIds = ordered,
        bestMatchId = hitIds.firstOrNull(inTree::contains),
    )
}
