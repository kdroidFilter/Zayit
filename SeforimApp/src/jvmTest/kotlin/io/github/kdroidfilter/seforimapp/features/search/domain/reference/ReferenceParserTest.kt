package io.github.kdroidfilter.seforimapp.features.search.domain.reference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceParserTest {
    // The place read after [book], among the readings the resolver tries
    private fun placeOf(
        query: String,
        book: String,
    ) = ReferenceParser.splits(query).single { it.bookName == book }.place

    private fun number(
        text: String,
        value: Int,
        amud: Amud? = null,
    ) = ReferenceToken(text, value, amud)

    @Test
    fun dafWithoutAmud() {
        assertEquals(listOf(number("יב", 12)), placeOf("חולין יב", "חולין"))
    }

    @Test
    fun amudFromPunctuation() {
        assertEquals(listOf(number("יב", 12, Amud.B)), placeOf("חולין יב:", "חולין"))
        assertEquals(listOf(number("יב", 12, Amud.A)), placeOf("חולין יב.", "חולין"))
    }

    @Test
    fun amudFromWords() {
        assertEquals(listOf(number("יב", 12, Amud.B)), placeOf("חולין דף יב ע\"ב", "חולין"))
        assertEquals(listOf(number("יב", 12, Amud.A)), placeOf("חולין דף יב ע״א", "חולין"))
    }

    @Test
    fun unquotedAyinAlefIsSeventyOne() {
        assertEquals(listOf(number("עא", 71)), placeOf("חולין עא", "חולין"))
    }

    @Test
    fun arabicDaf() {
        assertEquals(listOf(number("12", 12, Amud.B)), placeOf("חולין 12b", "חולין"))
    }

    @Test
    fun severalNumbersAndPlaceWords() {
        val verse = listOf(number("א", 1), number("ג", 3))
        assertEquals(verse, placeOf("בראשית א ג", "בראשית"))
        assertEquals(verse, placeOf("בראשית פרק א פסוק ג", "בראשית"))
        assertEquals(listOf(number("קכג", 123)), placeOf("תהלים קכ\"ג", "תהלים"))
    }

    @Test
    fun namesAreKeptAsWords() {
        assertEquals(listOf(ReferenceToken("לך"), ReferenceToken("לך")), placeOf("בראשית פרשת לך לך", "בראשית"))
        // נח is both a name and the number 58: the resolver tries both
        assertEquals(listOf(number("נח", 58)), placeOf("בראשית נח", "בראשית"))
    }

    @Test
    fun acronymBookNames() {
        assertEquals(listOf(328, 3), placeOf("שו\"ע או\"ח שכח ג", "שוע אוח").map { it.number })
    }

    @Test
    fun longestBookNameComesFirst() {
        val names = ReferenceParser.splits("משנה ברכות א א").map { it.bookName }
        assertEquals(listOf("משנה ברכות א", "משנה ברכות", "משנה"), names)
    }

    @Test
    fun wordsAreNotNumbers() {
        assertNull(ReferenceParser.hebrewNumeralValue("שבת"))
        assertNull(ReferenceParser.hebrewNumeralValue("פרק"))
        assertEquals(15, ReferenceParser.hebrewNumeralValue("טו"))
        assertTrue(ReferenceParser.splits("חולין").isEmpty())
    }

    @Test
    fun amudNeedsANumber() {
        assertNull(ReferenceParser.parsePlace(listOf("ע\"ב")))
        assertNull(ReferenceParser.parsePlace(listOf("דף")))
    }
}
