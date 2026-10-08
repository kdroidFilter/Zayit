package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookFindMatchesTest {
    private val lines =
        listOf(
            Line(id = 10, bookId = 1, lineIndex = 0, content = "בְּרֵאשִׁית בָּרָא"),
            Line(id = 11, bookId = 1, lineIndex = 1, content = "<b>והארץ</b> היתה"),
            Line(id = 12, bookId = 1, lineIndex = 2, content = "ויברא אלהים, ויברא"),
        )

    // What the index answers: every line, like a query too short to filter on; null when it can't
    private var candidates: LongArray? = lines.map { it.id }.reversed().toLongArray()
    private val loadedIds = mutableListOf<Long>()

    private val engine =
        object : SearchEngine {
            override fun findInBookCandidates(
                query: String,
                bookId: Long,
            ) = candidates

            override fun openSession(
                query: String,
                near: Int,
                bookFilter: Long?,
                categoryFilter: Long?,
                bookIds: Collection<Long>?,
                lineIds: Collection<Long>?,
                baseBookOnly: Boolean,
            ): SearchSession? = null

            override fun searchBooksByTitlePrefix(
                query: String,
                limit: Int,
            ) = emptyList<Long>()

            override fun buildSnippet(
                rawText: String,
                query: String,
                near: Int,
            ) = rawText

            override fun computeFacets(
                query: String,
                near: Int,
                bookFilter: Long?,
                categoryFilter: Long?,
                bookIds: Collection<Long>?,
                lineIds: Collection<Long>?,
                baseBookOnly: Boolean,
            ): SearchFacets? = null

            override fun close() = Unit
        }

    private suspend fun find(
        query: String,
        narrowing: BookFindMatches? = null,
    ) = findInBook(
        searchEngine = engine,
        loadLines = { ids -> lines.filter { it.id in ids }.also { found -> loadedIds += found.map { it.id } } },
        loadRange = { start, end -> lines.filter { it.lineIndex in start..end }.also { found -> loadedIds += found.map { it.id } } },
        bookId = 1,
        query = query,
        narrowing = narrowing,
    )

    @Test
    fun `scans the whole book when the index can't answer`() =
        runTest {
            candidates = null
            assertEquals(4, find("ברא").occurrences)
        }

    @Test
    fun `a query extending the previous one only rechecks the lines that held it`() =
        runTest {
            val previous = find("ברא")
            loadedIds.clear()
            val matches = find("בראשית", narrowing = previous)
            assertEquals(1, matches.occurrences)
            assertEquals(listOf(10L, 12L), loadedIds.sorted())
            assertTrue(queryNarrows("בְּרֵאשִׁית", "ברא"))
            assertFalse(queryNarrows("ברא", "בראשית"))
            assertFalse(queryNarrows("ברא", "ב"))
        }

    @Test
    fun `tells whether a match kept from older results still belongs`() =
        runTest {
            val matches = find("ברא")
            val second = matches.next(matches.nextAfterLine(-1, forward = true)!!, forward = true)!!
            assertTrue(second in matches)
            assertFalse(second in find("בראשית"))
            assertFalse(second.copy(ordinal = 5) in matches)
        }

    @Test
    fun `confirms candidates literally, nikud ignored, in reading order`() =
        runTest {
            val matches = find("ברא")
            // Twice in line 0 (inside בראשית, then ברא), twice in line 2
            assertEquals(4, matches.occurrences)
            assertEquals(BookFindMatches.Match(0, 10, ordinal = 0, position = 1), matches.nextAfterLine(-1, forward = true))
            assertEquals(BookFindMatches.Match(2, 12, ordinal = 1, position = 4), matches.nextAfterLine(3, forward = false))
        }

    @Test
    fun `matches against the displayed text, not the html`() =
        runTest {
            assertEquals(0, find("<b>").occurrences)
            assertEquals(1, find("והארץ").occurrences)
        }

    @Test
    fun `steps through every match, line after line, wrapping both ways`() =
        runTest {
            val matches = find("ברא")
            val first = matches.nextAfterLine(-1, forward = true)!!
            val forward = generateSequence(first) { matches.next(it, forward = true) }.take(5).toList()
            assertEquals(listOf(1, 2, 3, 4, 1), forward.map { it.position })
            assertEquals(listOf(0 to 0, 0 to 1, 2 to 0, 2 to 1, 0 to 0), forward.map { it.lineIndex to it.ordinal })
            val backward = generateSequence(first) { matches.next(it, forward = false) }.take(3).toList()
            assertEquals(listOf(1, 4, 3), backward.map { it.position })
            assertNull(BookFindMatches.Empty.nextAfterLine(0, forward = true))
        }

    @Test
    fun `whole-line matches, one occurrence each`() {
        val matches = BookFindMatches.ofLines(lines.reversed())
        assertEquals(3, matches.occurrences)
        assertEquals(BookFindMatches.Match(1, 11, ordinal = 0, position = 2), matches.nextAfterLine(0, forward = true))
    }
}
