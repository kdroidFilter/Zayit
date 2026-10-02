package io.github.kdroidfilter.seforimapp.features.home.widgets.library

import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlin.test.Test
import kotlin.test.assertEquals

class DictionaryWidgetTest {
    private fun lines(vararg contents: String) = contents.mapIndexed { i, c -> Line(id = 100L + i, bookId = 1, lineIndex = i, content = c) }

    private fun DictionaryEntry.short() = Triple(word, gloss, first..last)

    @Test
    fun arukhWithoutItsGermanNorItsHeadings() {
        val entries =
            Dictionary.ARUKH.parse(
                1,
                lines(
                    "<h2>אות האל\"ף</h2>",
                    "<b><big>אגמא</big></b>   [טרוריגקייט]. מפני <small>א\"ב</small> אגמת נפש",
                    "<b>נשלם אות הבית</b>",
                ),
            )
        assertEquals(listOf(Triple("אגמא", "מפני א\"ב אגמת נפש", 1..1)), entries.map { it.short() })
    }

    @Test
    fun haflaahFromItsFirstLetterWithoutTheStopAfterItsWord() {
        val entries =
            Dictionary.HAFLAAH.parse(
                1,
                lines("<b>רבינו נתן</b> בעל הערוך", "<h2>אות האל\"ף</h2>", "<b>אבלוסמוס.</b> (במוסף). בנוסחאות", "<b>נשלם אות האלף</b>"),
            )
        assertEquals(listOf(Triple("אבלוסמוס", "(במוסף). בנוסחאות", 2..2)), entries.map { it.short() })
    }

    @Test
    fun laazeiRashiWithTheMeaningFirstAndTheCorrectedWordLookedUp() {
        val entries =
            Dictionary.LAAZEI_RASHI.parse(
                1,
                lines(
                    "<h3>ברכות</h3>",
                    "17 / (ברכות כד:) / <b>(סנטרו) [סנטר]</b> מינטו\"ן / menton / <b>סנטר</b> <span dir=\"ltr\">✭ chin</span>",
                ),
            )
        assertEquals(listOf(Triple("(סנטרו) [סנטר]", "סנטר · מינטו\"ן (menton) · ברכות כד:", 1..1)), entries.map { it.short() })
        assertEquals("סנטר", entries.single().key)
    }

    @Test
    fun shorashimSpanFromTheRootToTheNextHeading() {
        val entries =
            Dictionary.SHORASHIM.parse(
                1,
                lines(
                    "<h3>הקדמה לספר השרשים</h3>",
                    "<h2>אות הא'</h2>",
                    "<h3>אבד</h3>",
                    "הַצַּדִיק <b>אָבָד</b>",
                    "עוד",
                    "<h3>אבה</h3>",
                    "וְלֹא <b>אָבָה</b>",
                    "<h3>בספר דניאל</h3>",
                ),
            )
        assertEquals(listOf(Triple("אבד", "הַצַּדִיק אָבָד", 2..4), Triple("אבה", "וְלֹא אָבָה", 5..6)), entries.map { it.short() })
    }

    @Test
    fun findsByPrefixIgnoringNikudAndFinalsTheWordItselfFirst() {
        val entries = listOf("אגמון", "אגם", "אגן").mapIndexed { i, w -> DictionaryEntry(w, "", Dictionary.ARUKH, 1, i.toLong(), i) }
        assertEquals(listOf(1L, 0L), lookup(entries, "אֲגַם").map { it.lineId })
        assertEquals(listOf(1L, 0L), lookup(entries, "אגמ").map { it.lineId })
        assertEquals(emptyList(), lookup(entries, " "))
    }

    @Test
    fun optionsKeepTheTickedDictionariesAndTheChipPickedAmongThem() {
        val options = DictionaryOptions(listOf(Dictionary.ARUKH, Dictionary.SHORASHIM), Dictionary.SHORASHIM)
        assertEquals(options, DictionaryOptions.decode(options.encode()))
        assertEquals(DictionaryOptions(), DictionaryOptions.decode(null))
        // Saved before the options page: the chip alone
        assertEquals(DictionaryOptions(picked = Dictionary.ARUKH), DictionaryOptions.decode("ARUKH"))
        // A chip on a dictionary no longer ticked, or none ticked
        assertEquals(DictionaryOptions(listOf(Dictionary.ARUKH)), DictionaryOptions.decode("ARUKH|HAFLAAH"))
        assertEquals(DictionaryOptions(), DictionaryOptions.decode("|"))
    }
}
