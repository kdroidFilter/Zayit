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
    private val categoryPathCache: MutableMap<Long, List<Category>> = mutableMapOf()

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
            val cat = repository.getCategory(currentId) ?: break
            path += cat
            currentId = cat.parentId
        }
        return path.asReversed()
    }
}
