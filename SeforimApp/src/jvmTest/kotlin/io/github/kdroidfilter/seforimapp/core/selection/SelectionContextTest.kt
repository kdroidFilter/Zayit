package io.github.kdroidfilter.seforimapp.core.selection

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionContextTest {
    private lateinit var context: SelectionContext

    @BeforeTest
    fun setup() {
        context = DefaultSelectionContext()
    }

    private fun book(
        id: Long,
        title: String = "ספר",
    ): Book =
        Book(
            id = id,
            categoryId = 1L,
            sourceId = 0L,
            title = title,
            heRef = title,
        )

    private fun line(
        bookId: Long,
        index: Int = 0,
    ): Line =
        Line(
            id = bookId * 1000 + index,
            bookId = bookId,
            lineIndex = index,
            content = "x",
            heRef = null,
        )

    // ---------------------------------------------------------------------------
    // selectedText
    // ---------------------------------------------------------------------------

    @Test
    fun `selectedText starts empty and round-trips updates`() {
        assertEquals("", context.selectedText.value)
        context.setSelectedText("שלום")
        assertEquals("שלום", context.selectedText.value)
        context.setSelectedText("")
        assertEquals("", context.selectedText.value)
    }

    @Test
    fun `clearSelectedText resets value`() {
        context.setSelectedText("hello")
        context.clearSelectedText()
        assertEquals("", context.selectedText.value)
    }

    // ---------------------------------------------------------------------------
    // activeBook
    // ---------------------------------------------------------------------------

    @Test
    fun `setActiveBook with null clears the slot`() {
        context.setActiveBook(book(1L), "תנ\"ך")
        assertEquals(
            1L,
            context.activeBook.value
                ?.book
                ?.id,
        )
        context.setActiveBook(null, null)
        assertNull(context.activeBook.value)
    }

    @Test
    fun `clearActiveBookIf only clears when ids match`() {
        context.setActiveBook(book(42L), "תלמוד")
        // A different book's dispose handler must not wipe the foreground tab's publish.
        context.clearActiveBookIf(99L)
        assertEquals(
            42L,
            context.activeBook.value
                ?.book
                ?.id,
        )
        // Matching id wipes.
        context.clearActiveBookIf(42L)
        assertNull(context.activeBook.value)
    }

    @Test
    fun `clearActiveBookIf with null only clears a null-published slot`() {
        context.setActiveBook(book(1L), null)
        context.clearActiveBookIf(null)
        assertEquals(
            1L,
            context.activeBook.value
                ?.book
                ?.id,
        )
    }

    // ---------------------------------------------------------------------------
    // visibleLines
    // ---------------------------------------------------------------------------

    @Test
    fun `setVisibleLines round-trips`() {
        val lines = listOf(line(1, 0), line(1, 1))
        context.setVisibleLines(lines)
        assertEquals(2, context.visibleLines.value.size)
    }

    @Test
    fun `clearVisibleLinesIf only clears when bookId matches the snapshot owner`() {
        context.setVisibleLines(listOf(line(7L, 0), line(7L, 1)))
        context.clearVisibleLinesIf(99L)
        assertEquals(2, context.visibleLines.value.size, "must not clear when bookId mismatches")
        context.clearVisibleLinesIf(7L)
        assertTrue(context.visibleLines.value.isEmpty())
    }

    @Test
    fun `clearVisibleLinesIf is a no-op when snapshot already empty`() {
        context.clearVisibleLinesIf(123L)
        assertTrue(context.visibleLines.value.isEmpty())
    }
}
