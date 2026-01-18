@file:OptIn(ExperimentalSplitPaneApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimapp.logger.errorln
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi

/**
 * Use case to manage navigation in the categories and books tree
 */
class NavigationUseCase(
    private val repository: SeforimRepository,
    private val stateManager: BookContentStateManager
) {
    
    /**
     * Load the root categories and the full tree from the precomputed catalog.
     * The catalog MUST be available, otherwise nothing is loaded.
     * Data is retrieved from CatalogCache which caches the extracted structures in memory.
     */
    suspend fun loadRootCategories() {
        val rootCategories = CatalogCache.getRootCategories()
        val categoryChildren = CatalogCache.getCategoryChildren()
        val allBooks = CatalogCache.getAllBooks()

        if (rootCategories == null || categoryChildren == null || allBooks == null) {
            errorln { "ERROR: Precomputed catalog not available!" }
            return
        }

        // Merge alt-structure flags from DB to ensure navigation tree knows about them
        val altFlags = runCatching { repository.getAllBookAltFlags() }.getOrDefault(emptyMap())
        val booksWithFlags: Set<Book> = allBooks.map { book ->
            altFlags[book.id]?.let { book.copy(hasAltStructures = it) } ?: book
        }.toSet()

        debugln { "✓ Using precomputed catalog for navigation tree" }
        // Catalog data is derived and reloaded every app start; do not persist it.
        // Persisting here can overwrite restored selection/book IDs before models are loaded.
        stateManager.updateNavigation(save = false) {
            copy(
                rootCategories = rootCategories,
                categoryChildren = categoryChildren,
                booksInCategory = booksWithFlags
            )
        }
    }
    
    /**
     * Select a category
     */
    suspend fun selectCategory(category: Category) {
        stateManager.updateNavigation {
            copy(selectedCategory = category)
        }
        expandCategory(category)
    }
    
    /**
     * Expand/collapse a category.
     * With the precomputed catalog, all data is already loaded,
     * so we simply toggle the expanded state.
     */
    suspend fun expandCategory(category: Category) {
        val currentState = stateManager.state.first().navigation
        val isExpanded = currentState.expandedCategories.contains(category.id)

        stateManager.updateNavigation {
            copy(
                expandedCategories = if (isExpanded) {
                    expandedCategories - category.id
                } else {
                    expandedCategories + category.id
                }
            )
        }
    }
    
    /**
     * Update the search text
     */
    fun updateSearchText(text: String) {
        stateManager.updateNavigation {
            copy(searchText = text)
        }
    }
    
    /**
     * Toggle the visibility of the navigation tree
     */
    fun toggleBookTree(): Float {
        val currentState = stateManager.state.value
        val isVisible = currentState.navigation.isVisible
        val newPosition: Float
        
        if (isVisible) {
            // Hide - save the current position
            val prev = currentState.layout.mainSplitState.positionPercentage
            stateManager.updateLayout {
                copy(
                    previousPositions = previousPositions.copy(
                        main = prev
                    )
                )
            }
            newPosition = 0f
            currentState.layout.mainSplitState.positionPercentage = newPosition
        } else {
            // Show - restore the previous position
            newPosition = currentState.layout.previousPositions.main
            currentState.layout.mainSplitState.positionPercentage = newPosition
        }
        
        stateManager.updateNavigation {
            copy(isVisible = !isVisible)
        }
        
        return newPosition
    }
    
    /**
     * Update the tree scroll position
     */
    fun updateBookTreeScrollPosition(index: Int, offset: Int) {
        stateManager.updateNavigation {
            copy(
                scrollIndex = index,
                scrollOffset = offset
            )
        }
    }
    
    /**
     * Select a book
     */
    fun selectBook(book: Book, save: Boolean = true) {
        stateManager.updateNavigation(save = save) {
            copy(selectedBook = book)
        }
    }

    /**
     * Expand the categories along the path to the given book so that the
     * tree shows the selected book in its category branch. Loads children
     * lists for each ancestor, and ensures the leaf category's books are
     * present so the book row can render.
     */
    suspend fun expandPathToBookId(bookId: Long, save: Boolean = true) {
        val book = runCatching { repository.getBookCore(bookId) }.getOrNull() ?: return
        expandPathToBook(book, save = save)
    }

    suspend fun expandPathToBook(book: Book, save: Boolean = true) {
        val leafCatId = book.categoryId
        val path = mutableListOf<Category>()
        var currentId: Long? = leafCatId
        var guard = 0

        // Build the path using the data already loaded from the catalog
        val navState = stateManager.state.first().navigation
        while (currentId != null && guard++ < 512) {
            val cat = navState.run {
                rootCategories.find { it.id == currentId }
                    ?: categoryChildren.values.flatten().find { it.id == currentId }
            } ?: break
            path += cat
            currentId = cat.parentId
        }

        if (path.isEmpty()) return
        val orderedPath = path.asReversed()
        val expandIds = orderedPath.map { it.id }.toSet()

        stateManager.updateNavigation(save = save) {
            copy(
                expandedCategories = expandedCategories + expandIds,
                selectedCategory = orderedPath.lastOrNull()
            )
        }
    }
}
