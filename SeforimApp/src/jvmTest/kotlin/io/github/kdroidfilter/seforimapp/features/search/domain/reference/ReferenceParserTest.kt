package io.github.kdroidfilter.seforimapp.features.search.domain.reference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceParserTest {
    private fun first(query: String) = ReferenceParser.splits(query).first()

    // The reading naming [book], among those the resolver tries
    private fun readingOf(
        query: String,
        book: String,
    ) = ReferenceParser.splits(query).single { it.bookName == book }

    @Test
    fun dafWithoutAmud() {
        assertEquals(ReferenceSplit("חולין", ReferenceLocator(listOf(12))), first("חולין יב"))
    }

    @Test
    fun amudFromPunctuation() {
        assertEquals(ReferenceLocator(listOf(12), Amud.B), first("חולין יב:").locator)
        assertEquals(ReferenceLocator(listOf(12), Amud.A), first("חולין יב.").locator)
    }

    @Test
    fun amudFromWords() {
        assertEquals(ReferenceLocator(listOf(12), Amud.B), first("חולין דף יב ע\"ב").locator)
        assertEquals(ReferenceLocator(listOf(12), Amud.A), first("חולין דף יב ע״א").locator)
    }

    @Test
    fun unquotedAyinAlefIsSeventyOne() {
        assertEquals(ReferenceLocator(listOf(71)), first("חולין עא").locator)
    }

    @Test
    fun arabicDaf() {
        assertEquals(ReferenceLocator(listOf(12), Amud.B), first("חולין 12b").locator)
    }

    @Test
    fun severalSectionsAndLocatorWords() {
        assertEquals(ReferenceLocator(listOf(1, 3)), readingOf("בראשית א ג", "בראשית").locator)
        assertEquals(ReferenceLocator(listOf(1, 3)), readingOf("בראשית פרק א פסוק ג", "בראשית").locator)
        assertEquals(ReferenceLocator(listOf(123)), first("תהלים קכ\"ג").locator)
    }

    @Test
    fun bookNameKeepsAcronymsComparable() {
        assertEquals(listOf(328, 3), readingOf("שו\"ע או\"ח שכח ג", "שוע אוח").locator.sections)
    }

    @Test
    fun longestBookNameComesFirst() {
        val names = ReferenceParser.splits("משנה ברכות א א").map { it.bookName }
        assertEquals(listOf("משנה ברכות א", "משנה ברכות"), names)
    }

    @Test
    fun wordsAreNotNumbers() {
        assertNull(ReferenceParser.hebrewNumeralValue("שבת"))
        assertNull(ReferenceParser.hebrewNumeralValue("פרק"))
        assertEquals(15, ReferenceParser.hebrewNumeralValue("טו"))
        assertTrue(ReferenceParser.splits("מסכת שבת").isEmpty())
        assertTrue(ReferenceParser.splits("חולין").isEmpty())
    }
}
