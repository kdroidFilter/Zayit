package io.github.kdroidfilter.seforimapp.features.bookcontent.references

import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.LineReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineReferencesTest {
    private val tanakh = Category(id = 1, title = "תנ״ך")
    private val rishonim = Category(id = 2, parentId = 1, title = "ראשונים")
    private val acharonim = Category(id = 3, parentId = 1, title = "אחרונים")
    private val mishnah = Category(id = 4, title = "משנה")

    private fun ref(
        lineId: Long,
        bookId: Long,
        categoryId: Long,
        lineIndex: Int = lineId.toInt(),
        type: ConnectionType = ConnectionType.REFERENCE,
    ) = LineReference(
        connectionType = type,
        fromLineId = 0,
        line = Line(id = lineId, bookId = bookId, lineIndex = lineIndex, content = "line $lineId", heRef = "book$bookId, $lineIndex"),
        bookTitle = "book$bookId",
        categoryId = categoryId,
        bookOrderIndex = bookId.toInt(),
    )

    @Test
    fun splitsACategoryHoldingMostBooks() {
        val tabs =
            filterTabs(
                listOf(
                    listOf(tanakh, rishonim),
                    listOf(tanakh, rishonim),
                    listOf(tanakh, acharonim),
                    listOf(mishnah),
                ),
            )
        assertEquals(listOf(rishonim, rishonim, acharonim, mishnah), tabs)
    }

    @Test
    fun keepsTopCategoriesWhenNoneDominates() {
        val tabs = filterTabs(listOf(listOf(tanakh, rishonim), listOf(mishnah), listOf(mishnah, Category(id = 9, title = "x"))))
        assertEquals(listOf(tanakh, mishnah, mishnah), tabs)
    }

    @Test
    fun groupsByBookTheMostPlacesFirst() {
        val paths = mapOf(2L to listOf(tanakh, rishonim), 3L to listOf(tanakh, acharonim), 4L to listOf(mishnah))
        val references =
            groupReferences(
                listOf(ref(1, bookId = 10, categoryId = 4), ref(5, bookId = 20, categoryId = 2), ref(3, bookId = 20, categoryId = 2)),
                paths,
            ) { null }
        assertEquals(listOf(20L, 10L), references.books.map { it.bookId })
        // A book's places in its own order
        assertEquals(
            listOf(3L, 5L),
            references.books
                .first()
                .places
                .map { it.line.id },
        )
        assertEquals(3, references.placeCount)
        assertEquals(listOf(1L to "תנ״ך", 4L to "משנה"), references.categoryTabs)
        assertTrue(references.kindTabs.isEmpty())
    }

    @Test
    fun noTabsForASingleCategory() {
        val references =
            groupReferences(
                listOf(ref(1, bookId = 10, categoryId = 2), ref(2, bookId = 20, categoryId = 2)),
                mapOf(2L to listOf(tanakh, rishonim)),
            ) { null }
        assertTrue(references.categoryTabs.isEmpty())
        assertTrue(references.books.all { it.tabId == null })
    }

    @Test
    fun rulingsAndParallelsComeFirstWithTabsOfTheirOwn() {
        val references =
            groupReferences(
                listOf(
                    ref(1, bookId = 10, categoryId = 2),
                    ref(2, bookId = 10, categoryId = 2),
                    ref(3, bookId = 30, categoryId = 4, type = ConnectionType.MESORAT_HASHAS),
                    ref(4, bookId = 20, categoryId = 2, type = ConnectionType.EIN_MISHPAT),
                    // The same book as a plain reference and as a ruling: two entries
                    ref(5, bookId = 10, categoryId = 2, type = ConnectionType.EIN_MISHPAT),
                ),
                mapOf(2L to listOf(tanakh, rishonim), 4L to listOf(mishnah)),
            ) { null }
        assertEquals(
            listOf(ReferenceKind.EinMishpat, ReferenceKind.EinMishpat, ReferenceKind.MesoratHashas, ReferenceKind.Reference),
            references.books.map { it.kind },
        )
        assertEquals(listOf(ReferenceKind.EinMishpat, ReferenceKind.MesoratHashas), references.kindTabs)
        assertEquals(listOf(-1L, -1L, -2L, 2L), references.books.map { it.tabId })
    }

    @Test
    fun placeDropsTheBookTitle() {
        val line = Line(bookId = 1, lineIndex = 0, content = "", heRef = "רבנו בחיי, בראשית,  יב, ב")
        assertEquals("בראשית, יב, ב", placeOf(line, "רבנו בחיי"))
        assertNull(placeOf(line.copy(heRef = "רבנו בחיי"), "רבנו בחיי"))
    }

    @Test
    fun snippetStartsAWordBeforeTheQuote() {
        val text = "אחת שתים שלש ארבע חמש שש שבע שמונה תשע עשר אחת עשרה שתים עשרה שלש עשרה בראשית ברא אלהים ועוד"
        val start = text.indexOf("בראשית")
        val snippet = snippetAround(text, start until start + "בראשית ברא".length)
        assertTrue(snippet.startsWith("…"))
        assertTrue("<b>בראשית ברא</b> אלהים" in snippet)
        // Cut at a word: no half word after the ellipsis
        assertTrue(snippet.removePrefix("…").split(" ").first() in text.split(" "))
    }

    @Test
    fun snippetWithoutQuoteIsTheStartEscaped() {
        assertEquals("a &lt;b&gt; c", snippetAround("a <b> c", null))
    }
}
