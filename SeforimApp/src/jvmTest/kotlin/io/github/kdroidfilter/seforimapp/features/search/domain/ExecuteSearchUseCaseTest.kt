package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.search.LineHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [ExecuteSearchUseCase].
 */
class ExecuteSearchUseCaseTest {

    private val useCase = ExecuteSearchUseCase()

    private fun createLineHit(
        bookId: Long,
        lineId: Long,
        lineIndex: Int = 0,
        score: Float = 1.0f,
        snippet: String = "test snippet",
        rawText: String = "test raw text",
        bookTitle: String = "Book $bookId",
    ): LineHit =
        LineHit(
            bookId = bookId,
            bookTitle = bookTitle,
            lineId = lineId,
            lineIndex = lineIndex,
            snippet = snippet,
            rawText = rawText,
            score = score,
        )

    @Test
    fun `hitsToResults converts hits to results`() {
        val hits = listOf(
            createLineHit(bookId = 1, lineId = 100, score = 2.5f),
            createLineHit(bookId = 2, lineId = 200, score = 1.5f),
        )

        val results = useCase.hitsToResults(hits, "test query")

        assertEquals(2, results.size)
        assertEquals(1, results[0].bookId)
        assertEquals(100, results[0].lineId)
        assertEquals(2, results[1].bookId)
        assertEquals(200, results[1].lineId)
    }

    @Test
    fun `hitsToResults returns empty list for empty hits`() {
        val results = useCase.hitsToResults(emptyList(), "query")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `hitsToResults uses snippet when available`() {
        val hit = createLineHit(
            bookId = 1,
            lineId = 100,
            snippet = "my snippet",
            rawText = "my raw text",
        )

        val results = useCase.hitsToResults(listOf(hit), "query")

        assertEquals("my snippet", results[0].snippet)
    }

    @Test
    fun `hitsToResults uses rawText when snippet is blank`() {
        val hit = createLineHit(
            bookId = 1,
            lineId = 100,
            snippet = "",
            rawText = "my raw text",
        )

        val results = useCase.hitsToResults(listOf(hit), "query")

        assertEquals("my raw text", results[0].snippet)
    }

    @Test
    fun `hitsToResults boosts exact matches`() {
        val hitWithExact = createLineHit(
            bookId = 1,
            lineId = 100,
            score = 1.0f,
            rawText = "contains exact query here",
        )
        val hitWithoutExact = createLineHit(
            bookId = 2,
            lineId = 200,
            score = 1.0f,
            rawText = "does not contain the term",
        )

        val results = useCase.hitsToResults(listOf(hitWithExact, hitWithoutExact), "exact query")

        // Hit with exact match should have higher rank
        assertTrue(results[0].rank > results[1].rank)
    }

    @Test
    fun `hitsToResults does not boost when query is empty`() {
        val hit = createLineHit(
            bookId = 1,
            lineId = 100,
            score = 1.0f,
            rawText = "contains anything",
        )

        val results = useCase.hitsToResults(listOf(hit), "")

        assertEquals(1.0, results[0].rank)
    }

    @Test
    fun `hitsToResults preserves book title`() {
        val hit = createLineHit(
            bookId = 1,
            lineId = 100,
            bookTitle = "My Book Title",
        )

        val results = useCase.hitsToResults(listOf(hit), "query")

        assertEquals("My Book Title", results[0].bookTitle)
    }

    @Test
    fun `hitsToResults preserves line index`() {
        val hit = createLineHit(
            bookId = 1,
            lineId = 100,
            lineIndex = 42,
        )

        val results = useCase.hitsToResults(listOf(hit), "query")

        assertEquals(42, results[0].lineIndex)
    }

    @Test
    fun `filterHitsByLineIds returns all hits when allowed set is empty`() {
        val hits = listOf(
            createLineHit(bookId = 1, lineId = 100),
            createLineHit(bookId = 2, lineId = 200),
        )

        val filtered = useCase.filterHitsByLineIds(hits, emptySet())

        assertEquals(2, filtered.size)
    }

    @Test
    fun `filterHitsByLineIds filters to allowed line IDs`() {
        val hits = listOf(
            createLineHit(bookId = 1, lineId = 100),
            createLineHit(bookId = 1, lineId = 101),
            createLineHit(bookId = 2, lineId = 200),
        )

        val filtered = useCase.filterHitsByLineIds(hits, setOf(100L, 200L))

        assertEquals(2, filtered.size)
        assertEquals(100, filtered[0].lineId)
        assertEquals(200, filtered[1].lineId)
    }

    @Test
    fun `filterHitsByLineIds returns empty when no hits match`() {
        val hits = listOf(
            createLineHit(bookId = 1, lineId = 100),
            createLineHit(bookId = 2, lineId = 200),
        )

        val filtered = useCase.filterHitsByLineIds(hits, setOf(999L))

        assertTrue(filtered.isEmpty())
    }
}
