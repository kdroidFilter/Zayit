package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.CategoryAgg
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.SearchTreeBook
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.SearchTreeCategory
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [SearchStatePersistenceUseCase].
 */
class SearchStatePersistenceUseCaseTest {
    private val useCase = SearchStatePersistenceUseCase()

    private fun createTestResult(
        bookId: Long,
        lineId: Long,
    ): SearchResult =
        SearchResult(
            bookId = bookId,
            bookTitle = "Book $bookId",
            lineId = lineId,
            lineIndex = 0,
            snippet = "Test snippet",
            rank = 1.0,
        )

    private fun createTestCategory(
        id: Long,
        title: String,
        parentId: Long? = null,
    ): Category =
        Category(
            id = id,
            title = title,
            level = 0,
            order = 1,
            parentId = parentId,
        )

    private fun createTestBook(
        id: Long,
        title: String,
        categoryId: Long,
    ): Book =
        Book(
            id = id,
            title = title,
            categoryId = categoryId,
            sourceId = 1L,
        )

    private fun createTestTocEntry(
        id: Long,
        text: String,
        bookId: Long,
        parentId: Long? = null,
    ): TocEntry =
        TocEntry(
            id = id,
            text = text,
            level = if (parentId == null) 0 else 1,
            bookId = bookId,
            parentId = parentId,
        )

    @Test
    fun `buildSnapshot creates snapshot with results`() {
        val results =
            listOf(
                createTestResult(1, 100),
                createTestResult(1, 101),
                createTestResult(2, 200),
            )

        val snapshot =
            useCase.buildSnapshot(
                results = results,
                categoryAgg = CategoryAgg(emptyMap(), emptyMap(), emptyMap()),
                tocTree = null,
                searchTree = emptyList(),
                tocCounts = emptyMap(),
                progressTotal = 100,
                hasMore = true,
            )

        assertEquals(3, snapshot.results.size)
        assertEquals(100, snapshot.totalHits)
        assertTrue(snapshot.hasMore)
    }

    @Test
    fun `buildSnapshot creates snapshot with category aggregation`() {
        val categoryCounts = mapOf(1L to 10, 2L to 5)
        val bookCounts = mapOf(100L to 7, 101L to 3, 200L to 5)

        val snapshot =
            useCase.buildSnapshot(
                results = emptyList(),
                categoryAgg = CategoryAgg(categoryCounts, bookCounts, emptyMap()),
                tocTree = null,
                searchTree = emptyList(),
                tocCounts = emptyMap(),
                progressTotal = 15,
                hasMore = false,
            )

        assertEquals(categoryCounts, snapshot.categoryAgg.categoryCounts)
        assertEquals(bookCounts, snapshot.categoryAgg.bookCounts)
    }

    @Test
    fun `buildSnapshot creates snapshot with TOC tree`() {
        val rootEntry = createTestTocEntry(1, "Root", 100)
        val childEntry = createTestTocEntry(2, "Child", 100, 1)

        val tocTree =
            TocTree(
                rootEntries = listOf(rootEntry),
                children = mapOf(1L to listOf(childEntry)),
            )

        val snapshot =
            useCase.buildSnapshot(
                results = emptyList(),
                categoryAgg = CategoryAgg(emptyMap(), emptyMap(), emptyMap()),
                tocTree = tocTree,
                searchTree = emptyList(),
                tocCounts = emptyMap(),
                progressTotal = 0,
                hasMore = false,
            )

        assertNotNull(snapshot.tocTree)
        assertEquals(1, snapshot.tocTree!!.rootEntries.size)
        assertEquals(1, snapshot.tocTree!!.children.size)
    }

    @Test
    fun `buildSnapshot creates snapshot with search tree`() {
        val category = createTestCategory(1, "Test Category")
        val book = createTestBook(100, "Test Book", 1)

        val searchTree =
            listOf(
                SearchTreeCategory(
                    category = category,
                    count = 10,
                    children = emptyList(),
                    books = listOf(SearchTreeBook(book, 10)),
                ),
            )

        val snapshot =
            useCase.buildSnapshot(
                results = emptyList(),
                categoryAgg = CategoryAgg(emptyMap(), emptyMap(), emptyMap()),
                tocTree = null,
                searchTree = searchTree,
                tocCounts = emptyMap(),
                progressTotal = 10,
                hasMore = false,
            )

        assertNotNull(snapshot.searchTree)
        assertEquals(1, snapshot.searchTree!!.size)
        assertEquals(1, snapshot.searchTree!![0].category.id)
        assertEquals(10, snapshot.searchTree!![0].count)
        assertEquals(1, snapshot.searchTree!![0].books.size)
    }

    @Test
    fun `buildSnapshot creates snapshot with TOC counts`() {
        val tocCounts = mapOf(1L to 5, 2L to 3, 3L to 2)

        val snapshot =
            useCase.buildSnapshot(
                results = emptyList(),
                categoryAgg = CategoryAgg(emptyMap(), emptyMap(), emptyMap()),
                tocTree = null,
                searchTree = emptyList(),
                tocCounts = tocCounts,
                progressTotal = 10,
                hasMore = false,
            )

        assertEquals(tocCounts, snapshot.tocCounts)
    }

    @Test
    fun `buildSnapshot uses results size when progressTotal is null`() {
        val results =
            listOf(
                createTestResult(1, 100),
                createTestResult(1, 101),
            )

        val snapshot =
            useCase.buildSnapshot(
                results = results,
                categoryAgg = CategoryAgg(emptyMap(), emptyMap(), emptyMap()),
                tocTree = null,
                searchTree = emptyList(),
                tocCounts = emptyMap(),
                progressTotal = null,
                hasMore = false,
            )

        assertEquals(2, snapshot.totalHits)
    }

    @Test
    fun `buildSnapshot returns null searchTree for empty tree`() {
        val snapshot =
            useCase.buildSnapshot(
                results = emptyList(),
                categoryAgg = CategoryAgg(emptyMap(), emptyMap(), emptyMap()),
                tocTree = null,
                searchTree = emptyList(),
                tocCounts = emptyMap(),
                progressTotal = 0,
                hasMore = false,
            )

        assertNull(snapshot.searchTree)
    }

    @Test
    fun `buildSnapshot handles nested search tree categories`() {
        val parentCategory = createTestCategory(1, "Parent")
        val childCategory = createTestCategory(2, "Child", 1)
        val book = createTestBook(100, "Test Book", 2)

        val searchTree =
            listOf(
                SearchTreeCategory(
                    category = parentCategory,
                    count = 15,
                    children =
                        listOf(
                            SearchTreeCategory(
                                category = childCategory,
                                count = 5,
                                children = emptyList(),
                                books = listOf(SearchTreeBook(book, 5)),
                            ),
                        ),
                    books = emptyList(),
                ),
            )

        val snapshot =
            useCase.buildSnapshot(
                results = emptyList(),
                categoryAgg = CategoryAgg(emptyMap(), emptyMap(), emptyMap()),
                tocTree = null,
                searchTree = searchTree,
                tocCounts = emptyMap(),
                progressTotal = 15,
                hasMore = false,
            )

        assertNotNull(snapshot.searchTree)
        assertEquals(1, snapshot.searchTree!!.size)
        assertEquals(1, snapshot.searchTree!![0].children.size)
        assertEquals(
            2,
            snapshot.searchTree!![0]
                .children[0]
                .category.id,
        )
    }
}
