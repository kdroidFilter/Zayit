package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [ResultsIndexingUseCase].
 *
 * These tests use synthetic data and don't require the real database or Lucene index.
 */
class ResultsIndexingUseCaseTest {
    private fun createTestResult(
        bookId: Long,
        lineId: Long,
        lineIndex: Int = 0,
    ): SearchResult =
        SearchResult(
            bookId = bookId,
            bookTitle = "Book $bookId",
            lineId = lineId,
            lineIndex = lineIndex,
            snippet = "Test snippet",
            rank = 1.0,
        )

    private fun createTestResults(): List<SearchResult> =
        listOf(
            createTestResult(bookId = 1, lineId = 100),
            createTestResult(bookId = 1, lineId = 101),
            createTestResult(bookId = 1, lineId = 102),
            createTestResult(bookId = 2, lineId = 200),
            createTestResult(bookId = 2, lineId = 201),
            createTestResult(bookId = 3, lineId = 300),
        )

    @Test
    fun `ensureIndex creates valid index`() =
        runBlocking {
            val useCase = ResultsIndexingUseCase()
            val results = createTestResults()

            val index = useCase.ensureIndex(results)

            assertNotNull(index)
            assertEquals(System.identityHashCode(results), index.identity)

            // Check book to indices mapping
            assertEquals(3, index.bookToIndices.size)
            assertTrue(index.bookToIndices.containsKey(1L))
            assertTrue(index.bookToIndices.containsKey(2L))
            assertTrue(index.bookToIndices.containsKey(3L))

            // Book 1 has indices 0, 1, 2
            val book1Indices = index.bookToIndices[1L]!!
            assertEquals(3, book1Indices.size)
            assertTrue(book1Indices.contentEquals(intArrayOf(0, 1, 2)))

            // Book 2 has indices 3, 4
            val book2Indices = index.bookToIndices[2L]!!
            assertEquals(2, book2Indices.size)
            assertTrue(book2Indices.contentEquals(intArrayOf(3, 4)))

            // Book 3 has index 5
            val book3Indices = index.bookToIndices[3L]!!
            assertEquals(1, book3Indices.size)
            assertTrue(book3Indices.contentEquals(intArrayOf(5)))

            // Check line ID to index mapping
            assertEquals(6, index.lineIdToIndex.size)
            assertEquals(0, index.lineIdToIndex[100L])
            assertEquals(1, index.lineIdToIndex[101L])
            assertEquals(2, index.lineIdToIndex[102L])
            assertEquals(3, index.lineIdToIndex[200L])
            assertEquals(4, index.lineIdToIndex[201L])
            assertEquals(5, index.lineIdToIndex[300L])
        }

    @Test
    fun `ensureIndex caches result for same list`() =
        runBlocking {
            val useCase = ResultsIndexingUseCase()
            val results = createTestResults()

            val index1 = useCase.ensureIndex(results)
            val index2 = useCase.ensureIndex(results)

            // Should return the same cached index
            assertTrue(index1 === index2)
        }

    @Test
    fun `ensureIndex rebuilds for different list`() =
        runBlocking {
            val useCase = ResultsIndexingUseCase()
            val results1 = createTestResults()
            val results2 = createTestResults() // Different list instance

            val index1 = useCase.ensureIndex(results1)
            val index2 = useCase.ensureIndex(results2)

            // Different identity, so different index
            assertTrue(index1 !== index2)
        }

    @Test
    fun `indicesForTocSubtree returns correct indices`() =
        runBlocking {
            val useCase = ResultsIndexingUseCase()
            val results = createTestResults()
            val index = useCase.ensureIndex(results)

            // TOC subtree containing lines 100, 101
            val lineIds = longArrayOf(100L, 101L)
            val indices = useCase.indicesForTocSubtree(tocId = 1L, lineIds = lineIds, index = index)

            assertEquals(2, indices.size)
            assertTrue(indices.contentEquals(intArrayOf(0, 1)))
        }

    @Test
    fun `indicesForTocSubtree returns empty for non-matching lines`() =
        runBlocking {
            val useCase = ResultsIndexingUseCase()
            val results = createTestResults()
            val index = useCase.ensureIndex(results)

            // Lines that don't exist in results
            val lineIds = longArrayOf(999L, 998L)
            val indices = useCase.indicesForTocSubtree(tocId = 2L, lineIds = lineIds, index = index)

            assertEquals(0, indices.size)
        }

    @Test
    fun `indicesForTocSubtree caches results`() =
        runBlocking {
            val useCase = ResultsIndexingUseCase()
            val results = createTestResults()
            val index = useCase.ensureIndex(results)

            val lineIds = longArrayOf(100L, 101L)
            val indices1 = useCase.indicesForTocSubtree(tocId = 1L, lineIds = lineIds, index = index)
            val indices2 = useCase.indicesForTocSubtree(tocId = 1L, lineIds = lineIds, index = index)

            // Same cached array
            assertTrue(indices1 === indices2)
        }

    @Test
    fun `mergeSortedIndicesParallel merges two arrays`() =
        runBlocking {
            val useCase = ResultsIndexingUseCase()
            val arrays =
                listOf(
                    intArrayOf(1, 3, 5),
                    intArrayOf(2, 4, 6),
                )

            val merged = useCase.mergeSortedIndicesParallel(arrays)

            assertEquals(6, merged.size)
            assertTrue(merged.contentEquals(intArrayOf(1, 2, 3, 4, 5, 6)))
        }

    @Test
    fun `mergeSortedIndicesParallel removes duplicates`() =
        runBlocking {
            val useCase = ResultsIndexingUseCase()
            val arrays =
                listOf(
                    intArrayOf(1, 2, 3),
                    intArrayOf(2, 3, 4),
                )

            val merged = useCase.mergeSortedIndicesParallel(arrays)

            assertEquals(4, merged.size)
            assertTrue(merged.contentEquals(intArrayOf(1, 2, 3, 4)))
        }

    @Test
    fun `mergeSortedIndicesParallel handles empty input`() =
        runBlocking {
            val useCase = ResultsIndexingUseCase()

            val merged = useCase.mergeSortedIndicesParallel(emptyList())

            assertEquals(0, merged.size)
        }

    @Test
    fun `mergeSortedIndicesParallel handles single array`() =
        runBlocking {
            val useCase = ResultsIndexingUseCase()
            val arrays = listOf(intArrayOf(1, 2, 3))

            val merged = useCase.mergeSortedIndicesParallel(arrays)

            assertTrue(merged === arrays[0])
        }

    @Test
    fun `mergeSortedIndicesParallel handles multiple arrays`() =
        runBlocking {
            val useCase = ResultsIndexingUseCase()
            val arrays =
                listOf(
                    intArrayOf(1, 4, 7),
                    intArrayOf(2, 5, 8),
                    intArrayOf(3, 6, 9),
                    intArrayOf(1, 5, 9), // overlaps
                )

            val merged = useCase.mergeSortedIndicesParallel(arrays)

            assertEquals(9, merged.size)
            assertTrue(merged.contentEquals(intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)))
        }

    @Test
    fun `parallelFilterByBook filters correctly`() {
        val useCase = ResultsIndexingUseCase()
        val results = createTestResults()
        val allowedBooks = setOf(1L, 3L)

        val filtered = useCase.parallelFilterByBook(results, allowedBooks)

        assertEquals(4, filtered.size)
        assertTrue(filtered.all { it.bookId in allowedBooks })
    }

    @Test
    fun `fastFilterByBookSequential filters correctly`() {
        val useCase = ResultsIndexingUseCase()
        val results = createTestResults()
        val allowedBooks = setOf(2L)

        val filtered = useCase.fastFilterByBookSequential(results, allowedBooks)

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.bookId == 2L })
    }

    @Test
    fun `extractResultsAtIndices extracts correct results`() =
        runBlocking {
            val useCase = ResultsIndexingUseCase()
            val results = createTestResults()
            val indices = intArrayOf(0, 2, 5)

            val extracted = useCase.extractResultsAtIndices(results, indices)

            assertEquals(3, extracted.size)
            assertEquals(100L, extracted[0].lineId)
            assertEquals(102L, extracted[1].lineId)
            assertEquals(300L, extracted[2].lineId)
        }

    @Test
    fun `mergeTwo merges and deduplicates`() {
        val a = intArrayOf(1, 3, 5, 7)
        val b = intArrayOf(2, 3, 6, 7, 8)

        val merged = ResultsIndexingUseCase.mergeTwo(a, b)

        assertEquals(7, merged.size)
        assertTrue(merged.contentEquals(intArrayOf(1, 2, 3, 5, 6, 7, 8)))
    }

    @Test
    fun `mergeTwo handles empty arrays`() {
        val a = intArrayOf()
        val b = intArrayOf(1, 2, 3)

        val merged1 = ResultsIndexingUseCase.mergeTwo(a, b)
        assertTrue(merged1.contentEquals(intArrayOf(1, 2, 3)))

        val merged2 = ResultsIndexingUseCase.mergeTwo(b, a)
        assertTrue(merged2.contentEquals(intArrayOf(1, 2, 3)))
    }

    @Test
    fun `large dataset parallel filtering works`() {
        val useCase = ResultsIndexingUseCase()

        // Create large dataset
        val results =
            (0 until 10_000).map { i ->
                createTestResult(
                    bookId = (i % 100).toLong(),
                    lineId = i.toLong(),
                )
            }

        val allowedBooks = setOf(0L, 1L, 2L, 3L, 4L)
        val filtered = useCase.parallelFilterByBook(results, allowedBooks)

        // Should have 100 results per book * 5 books = 500
        assertEquals(500, filtered.size)
        assertTrue(filtered.all { it.bookId in allowedBooks })
    }
}
