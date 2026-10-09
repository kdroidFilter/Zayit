package io.github.kdroidfilter.seforimapp.features.search.domain.reference

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReferenceResolverTest {
    private val chullin = Book(id = 132, categoryId = 1, sourceId = 1, title = "חולין", heRef = "חולין", isBaseBook = true)
    private val bereshit = Book(id = 1, categoryId = 1, sourceId = 1, title = "בראשית", heRef = "בראשית", isBaseBook = true)
    private val tehilim = Book(id = 28, categoryId = 1, sourceId = 1, title = "תהילים", heRef = "תהילים", isBaseBook = true)
    private val otzaria = Book(id = 9, categoryId = 1, sourceId = 2, title = "ספר", heRef = null)

    private val tocs =
        mapOf(
            132L to
                listOf(
                    TocNode(1, null, "חולין", 1000),
                    TocNode(2, 1, "דף ב.", 1001),
                    TocNode(3, 1, "דף יב.", 1012),
                    TocNode(4, 1, "דף יב:", 1013),
                ),
            1L to listOf(TocNode(10, null, "בראשית", 1), TocNode(11, 10, "פרק א", 2)),
            28L to listOf(TocNode(30, null, "פרק כג", 300)),
            9L to listOf(TocNode(20, null, "סימן ה", 500)),
        )

    private val altTocs =
        mapOf(
            132L to listOf(listOf(TocNode(100, null, "הכל שוחטין", 1001), TocNode(101, null, "השוחט", 1040))),
            1L to
                listOf(
                    listOf(
                        TocNode(110, null, "בראשית", 1),
                        TocNode(111, null, "נח", 80),
                        TocNode(112, 111, "עליה ב", 85),
                        TocNode(113, null, "לך לך", 150),
                    ),
                ),
            28L to listOf(listOf(TocNode(120, null, "יום ד", 250), TocNode(121, null, "יום ה", 260))),
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
                    listOf(chullin, bereshit, tehilim, otzaria).filter { it.title == normalizedName }

                override suspend fun toc(bookId: Long) = tocs[bookId].orEmpty()

                override suspend fun altTocs(bookId: Long) = altTocs[bookId].orEmpty()

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
    fun chapterName() {
        val ref = resolve("חולין השוחט").single()
        assertEquals(1040, ref.lineId)
        assertEquals("השוחט", ref.label)
    }

    @Test
    fun parashaAlsoAsANumber() {
        // נח reads as 58: no such chapter, so the parasha
        assertEquals(80, resolve("בראשית נח").single().lineId)
        assertEquals(80, resolve("בראשית פרשת נח").single().lineId)
    }

    @Test
    fun parashaOfTwoWordsAndItsAliya() {
        assertEquals(150, resolve("בראשית לך לך").single().lineId)
        val aliya = resolve("בראשית נח עליה ב").single()
        assertEquals(85, aliya.lineId)
        assertEquals("נח, עליה ב", aliya.label)
    }

    @Test
    fun mainTocBeforeAlternativeOnes() {
        // כג is chapter 23, not the alternative `יום ה` entries
        assertEquals(300, resolve("תהילים כג").single().lineId)
        val day = resolve("תהילים יום ה").single()
        assertEquals(260, day.lineId)
        assertEquals("יום ה", day.label)
    }

    @Test
    fun unknownBookOrPlace() {
        assertTrue(resolve("ספר לא קיים ב").isEmpty())
        assertTrue(resolve("חולין קמג").isEmpty())
        assertTrue(resolve("בראשית ויאמר").isEmpty())
    }

    @Test
    fun namesCompleteWhileTyped() {
        assertEquals(listOf(150L), resolve("בראשית לך").map { it.lineId })
        assertEquals(listOf(1040L), resolve("חולין הש").map { it.lineId })
        // ברא begins the parasha בראשית
        assertEquals(listOf(1L), resolve("בראשית ברא").map { it.lineId })
    }

    @Test
    fun bareNumbersAreNotCompleted() {
        // יום ד / יום ה are names; כ must not complete to the bare chapter כג
        assertEquals(listOf(250L, 260L), resolve("תהילים יום").map { it.lineId })
        assertTrue(resolve("תהילים כגג").isEmpty())
    }
}
