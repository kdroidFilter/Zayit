package io.github.kdroidfilter.seforimapp.integration

import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for search domain data structures.
 * Tests SearchTreeCategory, SearchTreeBook, and SearchResult classes.
 */
class SearchDomainIntegrationTest {
    // ==================== SearchResult Tests ====================

    @Test
    fun `SearchResult contains all required fields`() {
        val result =
            SearchResult(
                bookId = 100L,
                bookTitle = "Genesis",
                lineId = 1000L,
                lineIndex = 1,
                snippet = "In the beginning God created...",
                rank = 0.95,
            )

        assertEquals(100L, result.bookId)
        assertEquals("Genesis", result.bookTitle)
        assertEquals(1000L, result.lineId)
        assertEquals(1, result.lineIndex)
        assertEquals("In the beginning God created...", result.snippet)
        assertEquals(0.95, result.rank)
    }

    @Test
    fun `SearchResult copy preserves unmodified fields`() {
        val original =
            SearchResult(
                bookId = 100L,
                bookTitle = "Genesis",
                lineId = 1000L,
                lineIndex = 1,
                snippet = "original snippet",
                rank = 0.9,
            )

        val modified = original.copy(snippet = "modified snippet")

        assertEquals(100L, modified.bookId)
        assertEquals("Genesis", modified.bookTitle)
        assertEquals(1000L, modified.lineId)
        assertEquals("modified snippet", modified.snippet)
        assertEquals(0.9, modified.rank)
    }

    @Test
    fun `SearchResult equality works correctly`() {
        val result1 =
            SearchResult(
                bookId = 100L,
                bookTitle = "Genesis",
                lineId = 1000L,
                lineIndex = 1,
                snippet = "text",
                rank = 0.9,
            )
        val result2 =
            SearchResult(
                bookId = 100L,
                bookTitle = "Genesis",
                lineId = 1000L,
                lineIndex = 1,
                snippet = "text",
                rank = 0.9,
            )

        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
    }

    // ==================== Category Tests ====================

    @Test
    fun `Category has sensible defaults`() {
        val category = Category(id = 1, title = "Torah")

        assertEquals(1L, category.id)
        assertEquals("Torah", category.title)
        assertEquals(null, category.parentId)
        assertEquals(0, category.level)
        assertEquals(999, category.order)
    }

    @Test
    fun `Category can have parent reference`() {
        val parent = Category(id = 1, title = "Tanach", level = 0)
        val child = Category(id = 2, title = "Torah", parentId = 1, level = 1)

        assertEquals(null, parent.parentId)
        assertEquals(1L, child.parentId)
        assertEquals(0, parent.level)
        assertEquals(1, child.level)
    }

    @Test
    fun `Category copy preserves unmodified fields`() {
        val original =
            Category(
                id = 1,
                title = "Torah",
                parentId = null,
                level = 0,
                order = 1,
            )

        val modified = original.copy(title = "Modified Torah")

        assertEquals(1L, modified.id)
        assertEquals("Modified Torah", modified.title)
        assertEquals(null, modified.parentId)
        assertEquals(0, modified.level)
        assertEquals(1, modified.order)
    }

    // ==================== Book Tests ====================

    @Test
    fun `Book contains required fields`() {
        val book =
            Book(
                id = 100,
                categoryId = 1,
                sourceId = 1,
                title = "Genesis",
            )

        assertEquals(100L, book.id)
        assertEquals(1L, book.categoryId)
        assertEquals(1L, book.sourceId)
        assertEquals("Genesis", book.title)
    }

    @Test
    fun `Book has sensible defaults`() {
        val book =
            Book(
                id = 100,
                categoryId = 1,
                sourceId = 1,
                title = "Genesis",
            )

        assertTrue(book.authors.isEmpty())
        assertTrue(book.topics.isEmpty())
        assertTrue(book.pubPlaces.isEmpty())
        assertTrue(book.pubDates.isEmpty())
        assertEquals(null, book.heShortDesc)
        assertEquals(999f, book.order)
        assertEquals(0, book.totalLines)
        assertEquals(false, book.isBaseBook)
    }

    @Test
    fun `Book copy preserves unmodified fields`() {
        val original =
            Book(
                id = 100,
                categoryId = 1,
                sourceId = 1,
                title = "Genesis",
                heShortDesc = "בראשית",
                order = 1f,
            )

        val modified = original.copy(title = "Bereshit")

        assertEquals(100L, modified.id)
        assertEquals(1L, modified.categoryId)
        assertEquals("Bereshit", modified.title)
        assertEquals("בראשית", modified.heShortDesc)
        assertEquals(1f, modified.order)
    }

    // ==================== SearchTreeCategory Tests ====================

    @Test
    fun `SearchTreeCategory contains correct data`() {
        val category = Category(id = 1, title = "Torah")
        val book = Book(id = 100, title = "Genesis", categoryId = 1, sourceId = 1)

        val treeBook = SearchResultViewModel.SearchTreeBook(book = book, count = 5)
        val treeCategory =
            SearchResultViewModel.SearchTreeCategory(
                category = category,
                count = 10,
                children = emptyList(),
                books = listOf(treeBook),
            )

        assertEquals("Torah", treeCategory.category.title)
        assertEquals(10, treeCategory.count)
        assertTrue(treeCategory.children.isEmpty())
        assertEquals(1, treeCategory.books.size)
        assertEquals(5, treeCategory.books[0].count)
    }

    @Test
    fun `SearchTreeCategory supports nested children`() {
        val parentCat = Category(id = 1, title = "Tanach")
        val childCat = Category(id = 2, title = "Torah", parentId = 1, level = 1)
        val book = Book(id = 100, title = "Genesis", categoryId = 2, sourceId = 1)

        val childNode =
            SearchResultViewModel.SearchTreeCategory(
                category = childCat,
                count = 5,
                children = emptyList(),
                books = listOf(SearchResultViewModel.SearchTreeBook(book, 5)),
            )

        val parentNode =
            SearchResultViewModel.SearchTreeCategory(
                category = parentCat,
                count = 5,
                children = listOf(childNode),
                books = emptyList(),
            )

        assertEquals(1, parentNode.children.size)
        assertEquals("Torah", parentNode.children[0].category.title)
        assertTrue(parentNode.books.isEmpty())
        assertEquals(1, parentNode.children[0].books.size)
    }

    @Test
    fun `SearchTreeCategory can have multiple books`() {
        val category = Category(id = 1, title = "Torah")
        val book1 = Book(id = 100, title = "Genesis", categoryId = 1, sourceId = 1)
        val book2 = Book(id = 101, title = "Exodus", categoryId = 1, sourceId = 1)
        val book3 = Book(id = 102, title = "Leviticus", categoryId = 1, sourceId = 1)

        val treeCategory =
            SearchResultViewModel.SearchTreeCategory(
                category = category,
                count = 15,
                children = emptyList(),
                books =
                    listOf(
                        SearchResultViewModel.SearchTreeBook(book1, 5),
                        SearchResultViewModel.SearchTreeBook(book2, 7),
                        SearchResultViewModel.SearchTreeBook(book3, 3),
                    ),
            )

        assertEquals(3, treeCategory.books.size)
        assertEquals(15, treeCategory.count)
        assertEquals(5, treeCategory.books[0].count)
        assertEquals(7, treeCategory.books[1].count)
        assertEquals(3, treeCategory.books[2].count)
    }

    @Test
    fun `SearchTreeCategory can have both children and books`() {
        val parentCat = Category(id = 1, title = "Tanach")
        val childCat = Category(id = 2, title = "Torah", parentId = 1, level = 1)
        val bookInParent = Book(id = 100, title = "Introduction", categoryId = 1, sourceId = 1)
        val bookInChild = Book(id = 101, title = "Genesis", categoryId = 2, sourceId = 1)

        val childNode =
            SearchResultViewModel.SearchTreeCategory(
                category = childCat,
                count = 10,
                children = emptyList(),
                books = listOf(SearchResultViewModel.SearchTreeBook(bookInChild, 10)),
            )

        val parentNode =
            SearchResultViewModel.SearchTreeCategory(
                category = parentCat,
                count = 15,
                children = listOf(childNode),
                books = listOf(SearchResultViewModel.SearchTreeBook(bookInParent, 5)),
            )

        assertEquals(1, parentNode.children.size)
        assertEquals(1, parentNode.books.size)
        assertEquals(15, parentNode.count)
    }

    // ==================== SearchTreeBook Tests ====================

    @Test
    fun `SearchTreeBook contains book and count`() {
        val book = Book(id = 100, title = "Genesis", categoryId = 1, sourceId = 1)
        val treeBook = SearchResultViewModel.SearchTreeBook(book = book, count = 42)

        assertEquals("Genesis", treeBook.book.title)
        assertEquals(42, treeBook.count)
    }

    @Test
    fun `SearchTreeBook count can be zero`() {
        val book = Book(id = 100, title = "Genesis", categoryId = 1, sourceId = 1)
        val treeBook = SearchResultViewModel.SearchTreeBook(book = book, count = 0)

        assertEquals(0, treeBook.count)
    }

    @Test
    fun `SearchTreeBook equality works correctly`() {
        val book = Book(id = 100, title = "Genesis", categoryId = 1, sourceId = 1)
        val treeBook1 = SearchResultViewModel.SearchTreeBook(book = book, count = 5)
        val treeBook2 = SearchResultViewModel.SearchTreeBook(book = book, count = 5)

        assertEquals(treeBook1, treeBook2)
    }

    // ==================== Complex Hierarchy Tests ====================

    @Test
    fun `complex search tree hierarchy scenario`() {
        // Build a realistic tree structure
        val tanachCat = Category(id = 1, title = "Tanach", level = 0)
        val torahCat = Category(id = 2, title = "Torah", parentId = 1, level = 1)
        val neviimCat = Category(id = 3, title = "Nevi'im", parentId = 1, level = 1)

        val genesis = Book(id = 100, title = "Genesis", categoryId = 2, sourceId = 1)
        val exodus = Book(id = 101, title = "Exodus", categoryId = 2, sourceId = 1)
        val joshua = Book(id = 200, title = "Joshua", categoryId = 3, sourceId = 1)

        val torahNode =
            SearchResultViewModel.SearchTreeCategory(
                category = torahCat,
                count = 20,
                children = emptyList(),
                books =
                    listOf(
                        SearchResultViewModel.SearchTreeBook(genesis, 12),
                        SearchResultViewModel.SearchTreeBook(exodus, 8),
                    ),
            )

        val neviimNode =
            SearchResultViewModel.SearchTreeCategory(
                category = neviimCat,
                count = 5,
                children = emptyList(),
                books = listOf(SearchResultViewModel.SearchTreeBook(joshua, 5)),
            )

        val tanachNode =
            SearchResultViewModel.SearchTreeCategory(
                category = tanachCat,
                count = 25,
                children = listOf(torahNode, neviimNode),
                books = emptyList(),
            )

        // Verify structure
        assertEquals("Tanach", tanachNode.category.title)
        assertEquals(25, tanachNode.count)
        assertEquals(2, tanachNode.children.size)
        assertTrue(tanachNode.books.isEmpty())

        // Verify Torah subtree
        val torah = tanachNode.children[0]
        assertEquals("Torah", torah.category.title)
        assertEquals(20, torah.count)
        assertEquals(2, torah.books.size)

        // Verify Nevi'im subtree
        val neviim = tanachNode.children[1]
        assertEquals("Nevi'im", neviim.category.title)
        assertEquals(5, neviim.count)
        assertEquals(1, neviim.books.size)
    }

    @Test
    fun `search results with Hebrew content`() {
        val result =
            SearchResult(
                bookId = 100L,
                bookTitle = "בראשית",
                lineId = 1000L,
                lineIndex = 1,
                snippet = "בראשית ברא אלהים את השמים ואת הארץ",
                rank = 1.0,
            )

        assertEquals("בראשית", result.bookTitle)
        assertEquals("בראשית ברא אלהים את השמים ואת הארץ", result.snippet)
    }

    @Test
    fun `category with Hebrew title`() {
        val category = Category(id = 1, title = "תורה")
        val book = Book(id = 100, title = "בראשית", categoryId = 1, sourceId = 1)

        val treeCategory =
            SearchResultViewModel.SearchTreeCategory(
                category = category,
                count = 5,
                children = emptyList(),
                books = listOf(SearchResultViewModel.SearchTreeBook(book, 5)),
            )

        assertEquals("תורה", treeCategory.category.title)
        assertEquals("בראשית", treeCategory.books[0].book.title)
    }
}
