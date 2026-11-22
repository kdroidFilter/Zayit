package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookTreeSearchTest {
    private val tanakh = Category(id = 1, title = "תנ״ך")
    private val torah = Category(id = 2, parentId = 1, title = "תורה")
    private val halacha = Category(id = 3, title = "הלכה")
    private val roots = listOf(tanakh, halacha)
    private val children = mapOf(1L to listOf(torah))

    private fun book(
        id: Long,
        categoryId: Long,
    ) = Book(id = id, categoryId = categoryId, sourceId = 0, title = "ספר $id")

    private val books = linkedSetOf(book(10, 2), book(11, 2), book(20, 3))

    @Test
    fun `keeps the hits and the categories leading to them`() {
        val result = buildBookFilter(listOf(11L), roots, children, books)
        assertEquals(listOf(tanakh), result.roots)
        assertEquals(listOf(torah), result.children[1L])
        assertEquals(setOf(1L, 2L), result.categoryIds)
        assertEquals(listOf(11L), result.books.map { it.id })
    }

    @Test
    fun `matches follow the tree while the best stays the top-scored hit`() {
        val result = buildBookFilter(listOf(20L, 10L), roots, children, books)
        assertEquals(listOf(10L, 20L), result.matchIds)
        assertEquals(20L, result.bestMatchId)
    }

    @Test
    fun `hits missing from the catalog are ignored`() {
        assertTrue(buildBookFilter(listOf(99L), roots, children, books).matchIds.isEmpty())
    }
}
