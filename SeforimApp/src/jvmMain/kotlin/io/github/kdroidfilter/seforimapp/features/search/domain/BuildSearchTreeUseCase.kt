package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

class BuildSearchTreeUseCase(
    private val repository: SeforimRepository,
) {
    private val bookCache: MutableMap<Long, Book> = mutableMapOf()
    private val categoryCache: MutableMap<Long, Category> = mutableMapOf()
    private val categoryPathCache: MutableMap<Long, List<Category>> = mutableMapOf()

    /**
     * Build search tree from search results (original implementation).
     * Iterates through results to compute counts and build the tree.
     */
    suspend operator fun invoke(results: List<SearchResult>): List<SearchResultViewModel.SearchTreeCategory> {
        if (results.isEmpty()) return emptyList()

        val bookCounts = mutableMapOf<Long, Int>()
        val booksById = mutableMapOf<Long, Book>()
        val categoryCounts = mutableMapOf<Long, Int>()
        val categoriesById = mutableMapOf<Long, Category>()

        suspend fun resolveBook(bookId: Long): Book? {
            bookCache[bookId]?.let { return it }
            val b = repository.getBookCore(bookId)
            if (b != null) bookCache[bookId] = b
            return b
        }

        for (res in results) {
            val book = resolveBook(res.bookId) ?: continue
            booksById[book.id] = book
            bookCounts[book.id] = (bookCounts[book.id] ?: 0) + 1
            val path =
                categoryPathCache[book.categoryId] ?: buildCategoryPath(book.categoryId).also {
                    categoryPathCache[book.categoryId] = it
                }
            for (cat in path) {
                categoriesById[cat.id] = cat
                categoryCounts[cat.id] = (categoryCounts[cat.id] ?: 0) + 1
            }
        }

        return buildTreeFromMaps(categoriesById, categoryCounts, booksById, bookCounts)
    }

    /**
     * Build search tree from pre-computed facet counts.
     * This is faster than iterating through results when counts are already available.
     * Book-to-category mapping is loaded from the repository.
     */
    suspend fun invoke(
        facetCategoryCounts: Map<Long, Int>,
        facetBookCounts: Map<Long, Int>,
    ): List<SearchResultViewModel.SearchTreeCategory> {
        if (facetCategoryCounts.isEmpty() && facetBookCounts.isEmpty()) return emptyList()

        val categoriesById = mutableMapOf<Long, Category>()
        val booksById = mutableMapOf<Long, Book>()

        // Load categories from cache or repository
        for (catId in facetCategoryCounts.keys) {
            val cat = categoryCache[catId]
                ?: repository.getCategory(catId)?.also { categoryCache[catId] = it }
            if (cat != null) {
                categoriesById[cat.id] = cat
            }
        }

        // Load books and ensure their categories are included
        for (bookId in facetBookCounts.keys) {
            val book = bookCache[bookId]
                ?: repository.getBookCore(bookId)?.also { bookCache[bookId] = it }
            if (book != null) {
                booksById[book.id] = book
                // Ensure the book's category is in the tree
                if (!categoriesById.containsKey(book.categoryId)) {
                    val cat = categoryCache[book.categoryId]
                        ?: repository.getCategory(book.categoryId)?.also { categoryCache[book.categoryId] = it }
                    if (cat != null) {
                        categoriesById[cat.id] = cat
                    }
                }
            }
        }

        return buildTreeFromMaps(categoriesById, facetCategoryCounts, booksById, facetBookCounts)
    }

    private fun buildTreeFromMaps(
        categoriesById: Map<Long, Category>,
        categoryCounts: Map<Long, Int>,
        booksById: Map<Long, Book>,
        bookCounts: Map<Long, Int>,
    ): List<SearchResultViewModel.SearchTreeCategory> {
        if (categoriesById.isEmpty()) return emptyList()

        val childrenByParent: MutableMap<Long?, MutableList<Category>> = mutableMapOf()
        for (cat in categoriesById.values) {
            val list = childrenByParent.getOrPut(cat.parentId) { mutableListOf() }
            list += cat
        }
        childrenByParent.values.forEach { it.sortBy { c -> c.title } }

        fun buildNode(cat: Category): SearchResultViewModel.SearchTreeCategory {
            val childCats = childrenByParent[cat.id].orEmpty().map { buildNode(it) }
            val booksInCat =
                booksById.values
                    .filter { it.categoryId == cat.id }
                    .sortedBy { it.title }
                    .map { b -> SearchResultViewModel.SearchTreeBook(b, bookCounts[b.id] ?: 0) }
            return SearchResultViewModel.SearchTreeCategory(
                category = cat,
                count = categoryCounts[cat.id] ?: 0,
                children = childCats,
                books = booksInCat,
            )
        }

        val encounteredIds = categoriesById.keys
        val roots =
            categoriesById.values
                .filter { it.parentId == null || it.parentId !in encounteredIds }
                .sortedBy { it.title }
                .map { buildNode(it) }
        return roots
    }

    private suspend fun buildCategoryPath(categoryId: Long): List<Category> {
        val path = mutableListOf<Category>()
        var currentId: Long? = categoryId
        while (currentId != null) {
            val cat = categoryCache[currentId]
                ?: repository.getCategory(currentId)?.also { categoryCache[currentId] = it }
            if (cat == null) break
            path += cat
            currentId = cat.parentId
        }
        return path.asReversed()
    }
}
