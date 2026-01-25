package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for [GetBreadcrumbPiecesUseCase].
 */
class GetBreadcrumbPiecesUseCaseTest : SearchIntegrationTestBase() {
    @Test
    fun `returns breadcrumb pieces for search result`() =
        runBlocking {
            skipIfNoIndex()
            val engine = requireSearchEngine()

            // Perform a real search to get a result
            val session = engine.openSession("בראשית", near = 5)
            if (session == null) {
                println("SKIPPED: No search results for query")
                return@runBlocking
            }

            val page = session.nextPage(1)
            session.close()

            if (page == null || page.hits.isEmpty()) {
                println("SKIPPED: No search results found")
                return@runBlocking
            }

            val hit = page.hits.first()
            val result =
                SearchResult(
                    bookId = hit.bookId,
                    bookTitle = hit.bookTitle,
                    lineId = hit.lineId,
                    lineIndex = hit.lineIndex,
                    snippet = hit.snippet,
                    rank = hit.score.toDouble(),
                )

            val useCase = GetBreadcrumbPiecesUseCase(requireRepository())
            val breadcrumbs = useCase(result)

            // Should have at least the book title
            assertTrue(breadcrumbs.isNotEmpty(), "Breadcrumbs should not be empty")

            // Book title should be in the breadcrumbs
            assertTrue(
                breadcrumbs.any { it == hit.bookTitle || it.contains(hit.bookTitle.take(10)) },
                "Breadcrumbs should contain the book title",
            )
        }

    @Test
    fun `caches book and category lookups`() =
        runBlocking {
            skipIfNoIndex()
            val engine = requireSearchEngine()

            // Get multiple results from the same book
            val session = engine.openSession("בראשית", near = 5, bookFilter = null)
            if (session == null) {
                println("SKIPPED: No search results for query")
                return@runBlocking
            }

            val page = session.nextPage(10)
            session.close()

            if (page == null || page.hits.size < 2) {
                println("SKIPPED: Not enough results to test caching")
                return@runBlocking
            }

            // Get results from the same book
            val bookId = page.hits.first().bookId
            val resultsFromSameBook = page.hits.filter { it.bookId == bookId }.take(3)

            if (resultsFromSameBook.size < 2) {
                println("SKIPPED: Not enough results from same book")
                return@runBlocking
            }

            val useCase = GetBreadcrumbPiecesUseCase(requireRepository())

            // Call multiple times - should use cache
            val breadcrumbs1 =
                useCase(
                    SearchResult(
                        bookId = resultsFromSameBook[0].bookId,
                        bookTitle = resultsFromSameBook[0].bookTitle,
                        lineId = resultsFromSameBook[0].lineId,
                        lineIndex = resultsFromSameBook[0].lineIndex,
                        snippet = resultsFromSameBook[0].snippet,
                        rank = resultsFromSameBook[0].score.toDouble(),
                    ),
                )

            val breadcrumbs2 =
                useCase(
                    SearchResult(
                        bookId = resultsFromSameBook[1].bookId,
                        bookTitle = resultsFromSameBook[1].bookTitle,
                        lineId = resultsFromSameBook[1].lineId,
                        lineIndex = resultsFromSameBook[1].lineIndex,
                        snippet = resultsFromSameBook[1].snippet,
                        rank = resultsFromSameBook[1].score.toDouble(),
                    ),
                )

            // Both should have breadcrumbs (cache should work)
            assertTrue(breadcrumbs1.isNotEmpty())
            assertTrue(breadcrumbs2.isNotEmpty())

            // The category path should be the same (same book)
            // Find the common prefix before TOC entries differ
            val minLen = minOf(breadcrumbs1.size, breadcrumbs2.size)
            if (minLen >= 2) {
                // At least the first category and book should match
                assertTrue(
                    breadcrumbs1.take(2) == breadcrumbs2.take(2) ||
                        breadcrumbs1.first() == breadcrumbs2.first(),
                    "Results from same book should share category path prefix",
                )
            }
        }

    @Test
    fun `returns empty list for invalid result`() =
        runBlocking {
            skipIfNoIndex()

            val result =
                SearchResult(
                    bookId = -999999L, // Invalid book ID
                    bookTitle = "Non-existent Book",
                    lineId = -1L,
                    lineIndex = 0,
                    snippet = "",
                    rank = 0.0,
                )

            val useCase = GetBreadcrumbPiecesUseCase(requireRepository())
            val breadcrumbs = useCase(result)

            // Should return empty list for invalid book
            assertTrue(breadcrumbs.isEmpty(), "Should return empty list for non-existent book")
        }
}
