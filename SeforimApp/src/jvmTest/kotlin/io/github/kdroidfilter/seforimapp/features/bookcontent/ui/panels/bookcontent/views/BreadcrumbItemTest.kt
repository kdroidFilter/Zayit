package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BreadcrumbItemTest {
    private val testCategory = Category(id = 1L, title = "Torah", parentId = null)
    private val testBook = Book(id = 1L, categoryId = 1L, sourceId = 1L, title = "Bereishit")
    private val testTocEntry = TocEntry(id = 1L, bookId = 1L, text = "Chapter 1", level = 1)

    @Test
    fun `CategoryItem stores category`() {
        val item = BreadcrumbItem.CategoryItem(testCategory)
        assertEquals(testCategory, item.category)
        assertEquals(1L, item.category.id)
        assertEquals("Torah", item.category.title)
    }

    @Test
    fun `CategoryItem is BreadcrumbItem`() {
        val item: BreadcrumbItem = BreadcrumbItem.CategoryItem(testCategory)
        assertIs<BreadcrumbItem.CategoryItem>(item)
    }

    @Test
    fun `BookItem stores book`() {
        val item = BreadcrumbItem.BookItem(testBook)
        assertEquals(testBook, item.book)
        assertEquals(1L, item.book.id)
        assertEquals("Bereishit", item.book.title)
    }

    @Test
    fun `BookItem is BreadcrumbItem`() {
        val item: BreadcrumbItem = BreadcrumbItem.BookItem(testBook)
        assertIs<BreadcrumbItem.BookItem>(item)
    }

    @Test
    fun `TocItem stores tocEntry`() {
        val item = BreadcrumbItem.TocItem(testTocEntry)
        assertEquals(testTocEntry, item.tocEntry)
        assertEquals(1L, item.tocEntry.id)
        assertEquals("Chapter 1", item.tocEntry.text)
    }

    @Test
    fun `TocItem is BreadcrumbItem`() {
        val item: BreadcrumbItem = BreadcrumbItem.TocItem(testTocEntry)
        assertIs<BreadcrumbItem.TocItem>(item)
    }

    @Test
    fun `when expression on BreadcrumbItem is exhaustive`() {
        val items: List<BreadcrumbItem> =
            listOf(
                BreadcrumbItem.CategoryItem(testCategory),
                BreadcrumbItem.BookItem(testBook),
                BreadcrumbItem.TocItem(testTocEntry),
            )

        items.forEach { item ->
            val result =
                when (item) {
                    is BreadcrumbItem.CategoryItem -> "category"
                    is BreadcrumbItem.BookItem -> "book"
                    is BreadcrumbItem.TocItem -> "toc"
                }

            when (item) {
                is BreadcrumbItem.CategoryItem -> assertEquals("category", result)
                is BreadcrumbItem.BookItem -> assertEquals("book", result)
                is BreadcrumbItem.TocItem -> assertEquals("toc", result)
            }
        }
    }
}
