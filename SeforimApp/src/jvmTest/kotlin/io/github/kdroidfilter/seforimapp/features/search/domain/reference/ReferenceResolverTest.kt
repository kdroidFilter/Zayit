package io.github.kdroidfilter.seforimapp.features.search.domain.reference

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReferenceResolverTest {
    private val chullin = Book(id = 132, categoryId = 1, sourceId = 1, title = "חולין", heRef = "חולין", isBaseBook = true)
    private val bereshit = Book(id = 1, categoryId = 1, sourceId = 1, title = "בראשית", heRef = "בראשית", isBaseBook = true)
    private val otzaria = Book(id = 9, categoryId = 1, sourceId = 2, title = "ספר", heRef = null)

    private val tocs =
        mapOf(
            132L to
                listOf(
                    TocEntry(id = 1, bookId = 132, text = "חולין", level = 0, lineId = 1000),
                    TocEntry(id = 2, bookId = 132, parentId = 1, text = "דף ב.", level = 1, lineId = 1001),
                    TocEntry(id = 3, bookId = 132, parentId = 1, text = "דף יב.", level = 1, lineId = 1012),
                    TocEntry(id = 4, bookId = 132, parentId = 1, text = "דף יב:", level = 1, lineId = 1013),
                ),
            1L to
                listOf(
                    TocEntry(id = 10, bookId = 1, text = "בראשית", level = 0, lineId = 1),
                    TocEntry(id = 11, bookId = 1, parentId = 10, text = "פרק א", level = 1, lineId = 2),
                ),
            9L to listOf(TocEntry(id = 20, bookId = 9, text = "סימן ה", level = 0, lineId = 500)),
        )

    private val lines =
        mapOf(
            11L to
                (1..3).map {
                    Line(
                        id = 2L + it,
                        bookId = 1,
                        lineIndex = 1 + it,
                        content = "",
                        heRef = "בראשית א, ${ReferenceParser.hebrewNumeral(it)}",
                    )
                },
            4L to
                listOf(
                    Line(id = 1013, bookId = 132, lineIndex = 13, content = "", heRef = "חולין יב:, א"),
                    Line(id = 1014, bookId = 132, lineIndex = 14, content = "", heRef = "חולין יב:, ב"),
                ),
            20L to
                listOf(
                    Line(id = 501, bookId = 9, lineIndex = 1, content = "<b>(א)</b> ..."),
                    Line(id = 502, bookId = 9, lineIndex = 2, content = "(ב) ..."),
                ),
        )

    private val resolver =
        ReferenceResolver(
            object : ReferenceSource {
                override suspend fun booksNamed(normalizedName: String) =
                    listOf(chullin, bereshit, otzaria).filter { it.title == normalizedName }

                override suspend fun toc(bookId: Long) = tocs[bookId].orEmpty()

                override suspend fun linesOf(tocEntryId: Long) = lines[tocEntryId].orEmpty()
            },
        )

    private fun resolve(query: String) = runBlocking { resolver.resolve(query) }

    @Test
    fun dafDefaultsToAmudAleph() {
        val ref = resolve("חולין יב").single()
        assertEquals(1012, ref.lineId)
        assertEquals("דף יב.", ref.label)
    }

    @Test
    fun dafAmudBet() {
        assertEquals(1013, resolve("חולין יב:").single().lineId)
        assertEquals(1013, resolve("חולין דף יב ע\"ב").single().lineId)
    }

    @Test
    fun segmentOfADafByHeRef() {
        val ref = resolve("חולין יב: ב").single()
        assertEquals(1014, ref.lineId)
        assertEquals("דף יב:, ב", ref.label)
    }

    @Test
    fun verseByHeRef() {
        val ref = resolve("בראשית א ג").single()
        assertEquals(5, ref.lineId)
        assertEquals("א, ג", ref.label)
    }

    @Test
    fun chapterWhenTheVerseIsMissing() {
        val ref = resolve("בראשית א ט").single()
        assertEquals(2, ref.lineId)
        assertEquals("א", ref.label)
    }

    @Test
    fun lineNumberWithoutHeRef() {
        assertEquals(502, resolve("ספר ה ב").single().lineId)
    }

    @Test
    fun unknownBookOrPlace() {
        assertTrue(resolve("ספר לא קיים ב").isEmpty())
        assertTrue(resolve("חולין קמג").isEmpty())
    }
}
