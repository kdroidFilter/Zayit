package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [BuildSearchTreeUseCase].
 */
class BuildSearchTreeUseCaseTest : SearchIntegrationTestBase() {
    @Test
    fun `returns empty list for empty results`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = BuildSearchTreeUseCase(requireRepository())

            val tree = useCase(emptyList())

            assertTrue(tree.isEmpty())
        }

    @Test
    fun `builds tree from search results`() =
        runBlocking {
            skipIfNoIndex()
            val engine = requireSearchEngine()

            // Perform a real search to get results
            val session = engine.openSession("בראשית", near = 5)
            if (session == null) {
                println("SKIPPED: No search results for query")
                return@runBlocking
            }

            val page = session.nextPage(50)
            session.close()

            if (page == null || page.hits.isEmpty()) {
                println("SKIPPED: No search results found")
                return@runBlocking
            }

            // Convert hits to SearchResults
            val results =
                page.hits.map { hit ->
                    SearchResult(
                        bookId = hit.bookId,
                        bookTitle = hit.bookTitle,
                        lineId = hit.lineId,
                        lineIndex = hit.lineIndex,
                        snippet = hit.snippet,
                        rank = hit.score.toDouble(),
                    )
                }

            val useCase = BuildSearchTreeUseCase(requireRepository())
            val tree = useCase(results)

            // Tree should have at least one root category
            assertTrue(tree.isNotEmpty(), "Tree should not be empty for results")

            // Count total results in tree
            var treeResultCount = 0

            fun countResults(categories: List<io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.SearchTreeCategory>) {
                for (cat in categories) {
                    for (book in cat.books) {
                        treeResultCount += book.count
                    }
                    countResults(cat.children)
                }
            }
            countResults(tree)

            // Tree should account for all results
            assertEquals(results.size, treeResultCount, "Tree should contain all results")
        }

    @Test
    fun `builds tree from facet counts`() =
        runBlocking {
            skipIfNoIndex()
            val engine = requireSearchEngine()

            // Compute facets for a search query
            val facets = engine.computeFacets("בראשית", near = 5)

            if (facets == null || (facets.categoryCounts.isEmpty() && facets.bookCounts.isEmpty())) {
                println("SKIPPED: No facets computed for query")
                return@runBlocking
            }

            val useCase = BuildSearchTreeUseCase(requireRepository())
            val tree =
                useCase.invoke(
                    facetCategoryCounts = facets.categoryCounts,
                    facetBookCounts = facets.bookCounts,
                )

            // Tree should have at least one root category
            assertTrue(tree.isNotEmpty(), "Tree should not be empty for facets")

            // Verify book counts match facets
            var foundBooks = 0

            fun countBooks(categories: List<io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.SearchTreeCategory>) {
                for (cat in categories) {
                    foundBooks += cat.books.size
                    countBooks(cat.children)
                }
            }
            countBooks(tree)

            assertTrue(foundBooks > 0, "Tree should contain books")
        }

    @Test
    fun `tree categories are sorted by order`() =
        runBlocking {
            skipIfNoIndex()
            val engine = requireSearchEngine()

            val facets = engine.computeFacets("בראשית", near = 5)

            if (facets == null || facets.categoryCounts.size < 2) {
                println("SKIPPED: Not enough categories to test sorting")
                return@runBlocking
            }

            val useCase = BuildSearchTreeUseCase(requireRepository())
            val tree =
                useCase.invoke(
                    facetCategoryCounts = facets.categoryCounts,
                    facetBookCounts = facets.bookCounts,
                )

            // Check that root categories are sorted by order
            if (tree.size >= 2) {
                for (i in 0 until tree.size - 1) {
                    val current = tree[i]
                    val next = tree[i + 1]
                    assertTrue(
                        current.category.order <= next.category.order,
                        "Categories should be sorted by order: ${current.category.title} (${current.category.order}) should come before ${next.category.title} (${next.category.order})",
                    )
                }
            }
        }
}
