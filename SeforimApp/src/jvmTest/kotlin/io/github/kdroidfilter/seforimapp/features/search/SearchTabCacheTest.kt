package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTabCacheTest {
    @BeforeTest
    fun setup() {
        // Clear cache before each test
        repeat(10) { i ->
            SearchTabCache.clear("tab-$i")
        }
    }

    private fun createTestSnapshot(): SearchTabCache.Snapshot =
        SearchTabCache.Snapshot(
            results = emptyList(),
            categoryAgg =
                SearchTabCache.CategoryAggSnapshot(
                    categoryCounts = emptyMap(),
                    bookCounts = emptyMap(),
                    booksForCategory = emptyMap(),
                ),
            tocCounts = emptyMap(),
            tocTree = null,
        )

    // SearchTabCache tests
    @Test
    fun `put stores snapshot`() {
        val snapshot = createTestSnapshot()
        SearchTabCache.put("test-tab", snapshot)
        assertNotNull(SearchTabCache.get("test-tab"))
    }

    @Test
    fun `get returns null for non-existent tab`() {
        assertNull(SearchTabCache.get("non-existent"))
    }

    @Test
    fun `clear removes snapshot`() {
        val snapshot = createTestSnapshot()
        SearchTabCache.put("clear-test", snapshot)
        SearchTabCache.clear("clear-test")
        assertNull(SearchTabCache.get("clear-test"))
    }

    @Test
    fun `put overwrites existing snapshot`() {
        val snapshot1 = createTestSnapshot()
        val snapshot2 =
            SearchTabCache.Snapshot(
                results = emptyList(),
                categoryAgg =
                    SearchTabCache.CategoryAggSnapshot(
                        categoryCounts = mapOf(1L to 10),
                        bookCounts = emptyMap(),
                        booksForCategory = emptyMap(),
                    ),
                tocCounts = emptyMap(),
                tocTree = null,
            )
        SearchTabCache.put("overwrite-test", snapshot1)
        SearchTabCache.put("overwrite-test", snapshot2)
        val result = SearchTabCache.get("overwrite-test")
        assertEquals(mapOf(1L to 10), result?.categoryAgg?.categoryCounts)
    }

    // CategoryAggSnapshot tests
    @Test
    fun `CategoryAggSnapshot stores all values`() {
        val categoryCounts = mapOf(1L to 5, 2L to 10)
        val bookCounts = mapOf(100L to 3)
        val book = Book(id = 100L, categoryId = 1L, sourceId = 1L, title = "Test")
        val booksForCategory = mapOf(1L to listOf(book))

        val snapshot =
            SearchTabCache.CategoryAggSnapshot(
                categoryCounts = categoryCounts,
                bookCounts = bookCounts,
                booksForCategory = booksForCategory,
            )

        assertEquals(categoryCounts, snapshot.categoryCounts)
        assertEquals(bookCounts, snapshot.bookCounts)
        assertEquals(booksForCategory, snapshot.booksForCategory)
    }

    // SearchTreeBookSnapshot tests
    @Test
    fun `SearchTreeBookSnapshot stores book and count`() {
        val book = Book(id = 1L, categoryId = 1L, sourceId = 1L, title = "Test Book")
        val snapshot = SearchTabCache.SearchTreeBookSnapshot(book = book, count = 42)

        assertEquals(book, snapshot.book)
        assertEquals(42, snapshot.count)
    }

    // SearchTreeCategorySnapshot tests
    @Test
    fun `SearchTreeCategorySnapshot stores all values`() {
        val category = Category(id = 1L, title = "Test Category")
        val book = Book(id = 1L, categoryId = 1L, sourceId = 1L, title = "Test Book")
        val bookSnapshot = SearchTabCache.SearchTreeBookSnapshot(book = book, count = 5)

        val snapshot =
            SearchTabCache.SearchTreeCategorySnapshot(
                category = category,
                count = 10,
                children = emptyList(),
                books = listOf(bookSnapshot),
            )

        assertEquals(category, snapshot.category)
        assertEquals(10, snapshot.count)
        assertTrue(snapshot.children.isEmpty())
        assertEquals(1, snapshot.books.size)
    }

    // TocTreeSnapshot tests
    @Test
    fun `TocTreeSnapshot stores root entries and children`() {
        val snapshot =
            SearchTabCache.TocTreeSnapshot(
                rootEntries = emptyList(),
                children = mapOf(1L to emptyList()),
            )

        assertTrue(snapshot.rootEntries.isEmpty())
        assertEquals(1, snapshot.children.size)
    }

    // Snapshot tests
    @Test
    fun `Snapshot can be created with null tocTree`() {
        val snapshot = createTestSnapshot()
        assertNull(snapshot.tocTree)
    }

    @Test
    fun `Snapshot can be created with null searchTree`() {
        val snapshot = createTestSnapshot()
        assertNull(snapshot.searchTree)
    }

    @Test
    fun `Snapshot equals works correctly`() {
        val snapshot1 = createTestSnapshot()
        val snapshot2 = createTestSnapshot()
        assertEquals(snapshot1, snapshot2)
    }
}
