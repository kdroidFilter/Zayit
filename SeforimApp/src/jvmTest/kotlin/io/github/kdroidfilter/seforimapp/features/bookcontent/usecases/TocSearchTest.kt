package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimlibrary.core.models.AltTocEntry
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TocSearchTest {
    private fun entry(
        id: Long,
        text: String,
        parentId: Long? = null,
    ) = TocEntry(id = id, bookId = 1, parentId = parentId, text = text, level = 0, lineId = id * 10)

    // Book title > two parts > simanim
    private val toc =
        listOf(
            entry(1, "שולחן ערוך"),
            entry(2, "אורח חיים", parentId = 1),
            entry(3, "סימן א", parentId = 2),
            entry(4, "סימן קכ״א", parentId = 2),
            entry(5, "יורה דעה", parentId = 1),
            entry(6, "סימן אב", parentId = 5),
            entry(7, "סימן קכ", parentId = 5),
        )

    @Test
    fun `tokens must each start a word, ignoring gershayim`() {
        assertNotNull(matchTocText("סימן קכ״א", listOf("קכא")))
        assertNotNull(matchTocText("סימן קכ״א", listOf("סי", "קכ")))
        assertNull(matchTocText("סימן קכ״א", listOf("ימן")))
        assertNull(matchTocText("סימן קכ״א", listOf("סימן", "ב")))
    }

    @Test
    fun `ASCII quotes inside a word count as gershayim and geresh`() {
        // Most titles of the database write them with ASCII quotes
        val title = "סימן קכ\"א"
        assertNotNull(matchTocText(title, tocQueryTokens("קכא")))
        assertNotNull(matchTocText(title, tocQueryTokens("קכ\"א")))
        assertNotNull(matchTocText(title, tocQueryTokens("קכ״א")))
        assertNotNull(matchTocText("סימן קכ״א", tocQueryTokens("קכ\"א")))
        assertNotNull(matchTocText("פרק ס'", tocQueryTokens("ס'")))
        val match = assertNotNull(matchTocText(title, tocQueryTokens("קכא")))
        assertEquals("קכ\"א", title.substring(match.ranges.single()))
    }

    @Test
    fun `each title word serves one query word only`() {
        assertNull(matchTocText("סימן ג", tocQueryTokens("סימן ס\"ג")))
        assertNull(matchTocText("סימן ג", tocQueryTokens("סימן ס")))
        assertNotNull(matchTocText("סימן סג", tocQueryTokens("סימן ס\"ג")))
        // A short token does not steal the only word a longer one fits
        assertNotNull(matchTocText("ספרים סב", tocQueryTokens("ס ספר")))
    }

    @Test
    fun `nikud and final letters are ignored`() {
        assertNotNull(matchTocText("הִלְכוֹת שַׁבָּת", listOf("הלכות", "שבת")))
        assertNotNull(matchTocText("סימן", tocQueryTokens("סימנ")))
    }

    @Test
    fun `highlight maps back to the original text`() {
        val text = "סימן קכ״א"
        val match = assertNotNull(matchTocText(text, listOf("קכא")))
        assertEquals("קכ״א", text.substring(match.ranges.single()))
    }

    @Test
    fun `filtered tree keeps ancestors and drops the lone root`() {
        val result = buildTocFilter(toc, "קכ")
        assertEquals(listOf(4L, 7L), result.matchIds)
        assertEquals(listOf(2L, 5L), result.roots.map { it.id })
        assertEquals(listOf(4L), result.children[2]?.map { it.id })
        assertTrue(result.roots.all { it.hasChildren })
    }

    @Test
    fun `best match prefers whole words, then display order`() {
        assertEquals(7L, buildTocFilter(toc, "קכ").bestMatchId)
        assertEquals(3L, buildTocFilter(toc, "סימן א").bestMatchId)
    }

    @Test
    fun `no match or blank query yields an empty result`() {
        assertTrue(buildTocFilter(toc, "zzz").matchIds.isEmpty())
        assertTrue(buildTocFilter(toc, "  ").matchIds.isEmpty())
    }

    @Test
    fun `alt TOC matches follow the TOC ones under negated ids`() {
        // Same ids as the TOC entries: the search must keep them apart
        val alt =
            listOf(
                AltTocEntry(id = 3, structureId = 1, parentId = null, text = "פרשת בראשית", level = 0, lineId = 30),
                AltTocEntry(id = 4, structureId = 1, parentId = 3, text = "סימן א", level = 1, lineId = 40),
            )
        val altFilter = buildTocFilter(alt.map { it.toSearchTocEntry(bookId = 1) }, "סימן א")
        val result = buildTocFilter(toc, "סימן א").withAlt(altFilter)

        assertEquals(listOf(3L, 6L, -4L), result.matchIds)
        assertEquals(3L, result.bestMatchId)
        // The lone wrapping root is skipped, as in the TOC
        assertEquals(listOf(-4L), result.altRoots.map { it.id })
        assertTrue(-4L in result.highlights)
        assertEquals(4L, altTocIdOfSearchMatch(-4L))
        assertNull(altTocIdOfSearchMatch(3L))
    }
}
