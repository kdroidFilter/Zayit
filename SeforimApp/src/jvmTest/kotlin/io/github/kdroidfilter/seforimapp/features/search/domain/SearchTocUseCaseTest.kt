package io.github.kdroidfilter.seforimapp.features.search.domain

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [SearchTocUseCase].
 */
class SearchTocUseCaseTest : SearchIntegrationTestBase() {
    @Test
    fun `buildTocTreeForBook returns tree with root entries`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = SearchTocUseCase(requireRepository())

            // Search for a book with TOC
            val engine = requireSearchEngine()
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

            val bookId = page.hits.first().bookId
            val tree = useCase.buildTocTreeForBook(bookId)

            // Tree should have some structure
            assertTrue(tree.rootEntries.isNotEmpty() || tree.children.isNotEmpty(), "TOC tree should have entries")
        }

    @Test
    fun `buildTocTreeForBook returns empty tree for book without TOC`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = SearchTocUseCase(requireRepository())

            // Use a non-existent book ID
            val tree = useCase.buildTocTreeForBook(-999999L)

            assertTrue(tree.rootEntries.isEmpty(), "Root entries should be empty")
            assertTrue(tree.children.isEmpty(), "Children should be empty")
        }

    @Test
    fun `ensureTocLineIndex caches results`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = SearchTocUseCase(requireRepository())

            // Get a book with TOC
            val engine = requireSearchEngine()
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

            val bookId = page.hits.first().bookId

            // Get index twice
            val index1 = useCase.ensureTocLineIndex(bookId)
            val index2 = useCase.ensureTocLineIndex(bookId)

            // Should be the same instance (cached)
            assertTrue(index1 === index2, "Should return cached instance")
        }

    @Test
    fun `ensureTocLineIndex creates valid index with line mappings`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = SearchTocUseCase(requireRepository())

            // Get a book with TOC
            val engine = requireSearchEngine()
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

            val bookId = page.hits.first().bookId
            val index = useCase.ensureTocLineIndex(bookId)

            // Index should have some mappings (may be empty for some books)
            assertNotNull(index.tocToLines)
            assertNotNull(index.children)
            assertNotNull(index.parent)
            assertNotNull(index.lineIdToTocId)
        }

    @Test
    fun `collectLineIdsForTocSubtree returns lines for TOC entry`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = SearchTocUseCase(requireRepository())

            // Get a book with TOC
            val engine = requireSearchEngine()
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

            val bookId = page.hits.first().bookId
            val tree = useCase.buildTocTreeForBook(bookId)

            if (tree.rootEntries.isEmpty()) {
                println("SKIPPED: Book has no TOC entries")
                return@runBlocking
            }

            val tocId = tree.rootEntries.first().id
            val lineIds = useCase.collectLineIdsForTocSubtree(tocId, bookId)

            // May be empty for some TOC entries, but should not throw
            assertNotNull(lineIds)
        }

    @Test
    fun `getSubtreeLineIds returns LongArray`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = SearchTocUseCase(requireRepository())

            // Get a book with TOC
            val engine = requireSearchEngine()
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

            val bookId = page.hits.first().bookId
            val tree = useCase.buildTocTreeForBook(bookId)

            if (tree.rootEntries.isEmpty()) {
                println("SKIPPED: Book has no TOC entries")
                return@runBlocking
            }

            val tocId = tree.rootEntries.first().id
            val lineIds = useCase.getSubtreeLineIds(tocId, bookId)

            assertNotNull(lineIds)
            assertTrue(lineIds is LongArray)
        }

    @Test
    fun `computeTocCountsForLines returns counts with ancestor propagation`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = SearchTocUseCase(requireRepository())

            // Get a book with TOC
            val engine = requireSearchEngine()
            val session = engine.openSession("בראשית", near = 5)
            if (session == null) {
                println("SKIPPED: No search results for query")
                return@runBlocking
            }

            val page = session.nextPage(10)
            session.close()

            if (page == null || page.hits.isEmpty()) {
                println("SKIPPED: No search results found")
                return@runBlocking
            }

            val bookId = page.hits.first().bookId
            val index = useCase.ensureTocLineIndex(bookId)

            // Get line IDs from the hits
            val lineIds = page.hits.filter { it.bookId == bookId }.map { it.lineId }
            if (lineIds.isEmpty()) {
                println("SKIPPED: No hits for the book")
                return@runBlocking
            }

            val counts = useCase.computeTocCountsForLines(lineIds, index)

            // Counts should not be negative
            for ((_, count) in counts) {
                assertTrue(count > 0, "Count should be positive")
            }
        }

    @Test
    fun `clearCache removes cached indexes`() =
        runBlocking {
            skipIfNoIndex()
            val useCase = SearchTocUseCase(requireRepository())

            // Get a book with TOC
            val engine = requireSearchEngine()
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

            val bookId = page.hits.first().bookId

            // Cache the index
            useCase.ensureTocLineIndex(bookId)
            assertNotNull(useCase.getCachedIndex(bookId))

            // Clear cache
            useCase.clearCache()

            // Cache should be empty
            assertEquals(null, useCase.getCachedIndex(bookId))
        }
}

/**
 * Tests for [TocLineIndex] data class.
 */
class TocLineIndexTest {
    @Test
    fun `subtreeLineIds returns self lines for leaf TOC entry`() {
        val tocToLines =
            mapOf(
                1L to longArrayOf(100, 101, 102),
                2L to longArrayOf(200, 201),
            )
        val children = emptyMap<Long, List<Long>>()
        val parent = mapOf(1L to null, 2L to null)

        val index = TocLineIndex(tocToLines, children, parent)

        val result = index.subtreeLineIds(1L)
        assertTrue(result.contentEquals(longArrayOf(100, 101, 102)))
    }

    @Test
    fun `subtreeLineIds includes descendant lines`() {
        val tocToLines =
            mapOf(
                1L to longArrayOf(100),
                2L to longArrayOf(200, 201),
                3L to longArrayOf(300),
            )
        val children =
            mapOf(
                1L to listOf(2L, 3L),
            )
        val parent = mapOf(1L to null, 2L to 1L, 3L to 1L)

        val index = TocLineIndex(tocToLines, children, parent)

        val result = index.subtreeLineIds(1L)

        // Should contain lines from TOC 1, 2, and 3
        assertEquals(4, result.size)
        assertTrue(result.contains(100))
        assertTrue(result.contains(200))
        assertTrue(result.contains(201))
        assertTrue(result.contains(300))
    }

    @Test
    fun `subtreeLineIds caches results`() {
        val tocToLines =
            mapOf(
                1L to longArrayOf(100, 101),
            )
        val children = emptyMap<Long, List<Long>>()
        val parent = mapOf(1L to null)

        val index = TocLineIndex(tocToLines, children, parent)

        val result1 = index.subtreeLineIds(1L)
        val result2 = index.subtreeLineIds(1L)

        // Should be the same array instance (cached)
        assertTrue(result1 === result2)
    }

    @Test
    fun `subtreeLineIds returns empty for non-existent TOC`() {
        val tocToLines =
            mapOf(
                1L to longArrayOf(100),
            )
        val children = emptyMap<Long, List<Long>>()
        val parent = mapOf(1L to null)

        val index = TocLineIndex(tocToLines, children, parent)

        val result = index.subtreeLineIds(999L)
        assertEquals(0, result.size)
    }

    @Test
    fun `lineIdToTocId maps lines to TOC entries`() {
        val tocToLines =
            mapOf(
                1L to longArrayOf(100, 101),
                2L to longArrayOf(200),
            )
        val children = emptyMap<Long, List<Long>>()
        val parent = mapOf(1L to null, 2L to null)

        val index = TocLineIndex(tocToLines, children, parent)

        assertEquals(1L, index.lineIdToTocId[100])
        assertEquals(1L, index.lineIdToTocId[101])
        assertEquals(2L, index.lineIdToTocId[200])
        assertEquals(null, index.lineIdToTocId[999])
    }

    @Test
    fun `mergeLongArrays merges and deduplicates`() {
        val arrays =
            listOf(
                longArrayOf(1, 3, 5),
                longArrayOf(2, 3, 4),
                longArrayOf(5, 6),
            )

        val result = TocLineIndex.mergeLongArrays(arrays)

        assertEquals(6, result.size)
        assertTrue(result.contentEquals(longArrayOf(1, 2, 3, 4, 5, 6)))
    }

    @Test
    fun `mergeLongArrays handles empty input`() {
        val result = TocLineIndex.mergeLongArrays(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `mergeLongArrays handles single array`() {
        val arrays = listOf(longArrayOf(1, 2, 3))
        val result = TocLineIndex.mergeLongArrays(arrays)
        assertTrue(result === arrays[0])
    }
}
