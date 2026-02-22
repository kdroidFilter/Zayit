package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/**
 * Use case for category navigation operations.
 *
 * This class provides functionality to:
 * - Build the path from root to a given category
 * - Collect all book IDs under a category tree
 *
 * @property repository The Seforim database repository
 */
class CategoryNavigationUseCase(
    private val repository: SeforimRepository,
) {
    // Cache for category lookups
    private val categoryCache: MutableMap<Long, Category> = mutableMapOf()

    // Cache for category paths
    private val categoryPathCache: MutableMap<Long, List<Category>> = mutableMapOf()

    // Cache for books under category
    private val booksUnderCategoryCache: MutableMap<Long, Set<Long>> = mutableMapOf()

    /**
     * Builds the path from root category to the given category.
     * The path is ordered from root to the target category.
     *
     * @param categoryId The target category ID
     * @return List of categories from root to target, or empty list if category not found
     */
    suspend fun buildCategoryPath(categoryId: Long): List<Category> {
        // Check cache first
        categoryPathCache[categoryId]?.let { return it }

        val path = mutableListOf<Category>()
        var currentId: Long? = categoryId

        while (currentId != null) {
            val cat =
                categoryCache[currentId]
                    ?: repository.getCategory(currentId)?.also { categoryCache[currentId] = it }
                    ?: break
            path += cat
            currentId = cat.parentId
        }

        val result = path.asReversed()
        categoryPathCache[categoryId] = result
        return result
    }

    /**
     * Collects all book IDs under a category tree.
     * This includes books in the given category and all its descendant categories.
     *
     * @param categoryId The root category ID
     * @return Set of book IDs under the category tree
     */
    suspend fun collectBookIdsUnderCategory(categoryId: Long): Set<Long> {
        // Check cache first
        booksUnderCategoryCache[categoryId]?.let { return it }

        // Use bulk query for efficiency
        val books =
            runSuspendCatching { repository.getBooksUnderCategoryTree(categoryId) }
                .getOrDefault(emptyList())

        val result = books.mapTo(mutableSetOf()) { it.id }
        booksUnderCategoryCache[categoryId] = result
        return result
    }

    /**
     * Gets a category by ID, using cache.
     *
     * @param categoryId The category ID
     * @return The category, or null if not found
     */
    suspend fun getCategory(categoryId: Long): Category? {
        categoryCache[categoryId]?.let { return it }
        return repository.getCategory(categoryId)?.also { categoryCache[categoryId] = it }
    }

    /**
     * Clears all caches. Call this when category data may have changed.
     */
    fun clearCache() {
        categoryCache.clear()
        categoryPathCache.clear()
        booksUnderCategoryCache.clear()
    }
}
