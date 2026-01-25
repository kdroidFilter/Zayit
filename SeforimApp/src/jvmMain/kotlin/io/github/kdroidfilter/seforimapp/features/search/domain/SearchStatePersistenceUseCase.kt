package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.CategoryAgg
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.SearchTreeBook
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.SearchTreeCategory
import io.github.kdroidfilter.seforimapp.features.search.SearchTabCache
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult

/**
 * Use case for building search state snapshots for persistence.
 *
 * This class handles the creation of snapshots that can be used to restore
 * search state after cold boot restoration.
 */
class SearchStatePersistenceUseCase {
    /**
     * Builds a snapshot of the current search state for persistence.
     *
     * @param results The current search results
     * @param categoryAgg The category aggregation data
     * @param tocTree The current TOC tree (if a book is scoped)
     * @param searchTree The current search tree categories
     * @param tocCounts The TOC entry counts
     * @param progressTotal The total number of hits
     * @param hasMore Whether more results are available
     * @return A snapshot that can be persisted and restored later
     */
    fun buildSnapshot(
        results: List<SearchResult>,
        categoryAgg: CategoryAgg,
        tocTree: TocTree?,
        searchTree: List<SearchTreeCategory>,
        tocCounts: Map<Long, Int>,
        progressTotal: Long?,
        hasMore: Boolean,
    ): SearchTabCache.Snapshot {
        val treeSnap =
            tocTree?.let { t ->
                SearchTabCache.TocTreeSnapshot(t.rootEntries, t.children)
            }

        val searchTreeSnap: List<SearchTabCache.SearchTreeCategorySnapshot>? =
            if (searchTree.isEmpty()) {
                null
            } else {
                runCatching {
                    searchTree.map { mapSearchTreeNode(it) }
                }.getOrNull()
            }

        return SearchTabCache.Snapshot(
            results = results,
            categoryAgg =
                SearchTabCache.CategoryAggSnapshot(
                    categoryCounts = categoryAgg.categoryCounts,
                    bookCounts = categoryAgg.bookCounts,
                    booksForCategory = categoryAgg.booksForCategory,
                ),
            tocCounts = tocCounts,
            tocTree = treeSnap,
            searchTree = searchTreeSnap,
            totalHits = progressTotal ?: results.size.toLong(),
            hasMore = hasMore,
        )
    }

    /**
     * Maps a search tree category node to its snapshot representation.
     */
    private fun mapSearchTreeNode(node: SearchTreeCategory): SearchTabCache.SearchTreeCategorySnapshot =
        SearchTabCache.SearchTreeCategorySnapshot(
            category = node.category,
            count = node.count,
            children = node.children.map { mapSearchTreeNode(it) },
            books = node.books.map { mapSearchTreeBook(it) },
        )

    /**
     * Maps a search tree book to its snapshot representation.
     */
    private fun mapSearchTreeBook(book: SearchTreeBook): SearchTabCache.SearchTreeBookSnapshot =
        SearchTabCache.SearchTreeBookSnapshot(book.book, book.count)
}
