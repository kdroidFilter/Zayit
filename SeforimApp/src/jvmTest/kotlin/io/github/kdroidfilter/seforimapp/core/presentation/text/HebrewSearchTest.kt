package io.github.kdroidfilter.seforimapp.core.presentation.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HebrewSearchTest {
    // replaceFinalsWithBase tests
    @Test
    fun `replaceFinalsWithBase replaces final kaf`() {
        val result = replaceFinalsWithBase("מלך")
        assertEquals("מלכ", result)
    }

    @Test
    fun `replaceFinalsWithBase replaces final mem`() {
        val result = replaceFinalsWithBase("שלום")
        assertEquals("שלומ", result)
    }

    @Test
    fun `replaceFinalsWithBase replaces final nun`() {
        val result = replaceFinalsWithBase("אמן")
        assertEquals("אמנ", result)
    }

    @Test
    fun `replaceFinalsWithBase replaces final pe`() {
        val result = replaceFinalsWithBase("כסף")
        assertEquals("כספ", result)
    }

    @Test
    fun `replaceFinalsWithBase replaces final tsade`() {
        val result = replaceFinalsWithBase("ארץ")
        assertEquals("ארצ", result)
    }

    @Test
    fun `replaceFinalsWithBase replaces multiple finals`() {
        val result = replaceFinalsWithBase("מלך שלום")
        assertEquals("מלכ שלומ", result)
    }

    @Test
    fun `replaceFinalsWithBase handles empty string`() {
        val result = replaceFinalsWithBase("")
        assertEquals("", result)
    }

    @Test
    fun `replaceFinalsWithBase preserves non-final letters`() {
        val result = replaceFinalsWithBase("אבג")
        assertEquals("אבג", result)
    }

    // normalizeQueryForHebrew tests
    @Test
    fun `normalizeQueryForHebrew trims whitespace`() {
        val result = normalizeQueryForHebrew("  שלום  ")
        assertEquals("שלומ", result)
    }

    @Test
    fun `normalizeQueryForHebrew returns empty for blank`() {
        val result = normalizeQueryForHebrew("   ")
        assertEquals("", result)
    }

    @Test
    fun `normalizeQueryForHebrew lowercases Latin`() {
        val result = normalizeQueryForHebrew("Hello")
        assertEquals("hello", result)
    }

    @Test
    fun `normalizeQueryForHebrew removes nikud`() {
        val result = normalizeQueryForHebrew("שָׁלוֹם")
        assertEquals("שלומ", result)
    }

    @Test
    fun `normalizeQueryForHebrew replaces maqaf with space`() {
        val result = normalizeQueryForHebrew("בית־ספר")
        assertEquals("בית ספר", result)
    }

    @Test
    fun `normalizeQueryForHebrew removes geresh`() {
        val result = normalizeQueryForHebrew("צ׳")
        assertEquals("צ", result)
    }

    // stripDiacriticsWithMap tests
    @Test
    fun `stripDiacriticsWithMap returns plain text without diacritics`() {
        val (plain, _) = stripDiacriticsWithMap("שָׁלוֹם")
        assertEquals("שלום", plain)
    }

    @Test
    fun `stripDiacriticsWithMap returns correct map size`() {
        val text = "שָׁלוֹם"
        val (plain, map) = stripDiacriticsWithMap(text)
        assertEquals(plain.length, map.size)
    }

    @Test
    fun `stripDiacriticsWithMap handles empty string`() {
        val (plain, map) = stripDiacriticsWithMap("")
        assertEquals("", plain)
        assertEquals(0, map.size)
    }

    @Test
    fun `stripDiacriticsWithMap preserves Latin text`() {
        val (plain, map) = stripDiacriticsWithMap("Hello")
        assertEquals("Hello", plain)
        assertEquals(5, map.size)
    }

    // mapToOrigIndex tests
    @Test
    fun `mapToOrigIndex returns same index for empty map`() {
        val result = mapToOrigIndex(intArrayOf(), 5)
        assertEquals(5, result)
    }

    @Test
    fun `mapToOrigIndex returns mapped value`() {
        val map = intArrayOf(0, 2, 4, 6)
        val result = mapToOrigIndex(map, 2)
        assertEquals(4, result)
    }

    @Test
    fun `mapToOrigIndex coerces negative index`() {
        val map = intArrayOf(0, 2, 4)
        val result = mapToOrigIndex(map, -1)
        assertEquals(0, result)
    }

    @Test
    fun `mapToOrigIndex coerces index beyond array`() {
        val map = intArrayOf(0, 2, 4)
        val result = mapToOrigIndex(map, 10)
        assertEquals(4, result)
    }

    // findAllMatchesOriginal tests
    @Test
    fun `findAllMatchesOriginal returns empty for short query`() {
        val result = findAllMatchesOriginal("שלום עולם", "ש")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findAllMatchesOriginal finds simple match`() {
        val result = findAllMatchesOriginal("שלום עולם", "שלום")
        assertEquals(1, result.size)
        assertEquals(0, result[0].first)
    }

    @Test
    fun `findAllMatchesOriginal finds multiple matches`() {
        val result = findAllMatchesOriginal("שלום שלום שלום", "שלום")
        assertEquals(3, result.size)
    }

    @Test
    fun `findAllMatchesOriginal is case insensitive for Latin`() {
        val result = findAllMatchesOriginal("Hello World", "hello")
        assertEquals(1, result.size)
    }

    @Test
    fun `findAllMatchesOriginal ignores nikud in source`() {
        val result = findAllMatchesOriginal("שָׁלוֹם", "שלום")
        assertEquals(1, result.size)
    }

    @Test
    fun `findAllMatchesOriginal handles final letters`() {
        val result = findAllMatchesOriginal("מלך שלום", "מלכ")
        assertEquals(1, result.size)
    }

    @Test
    fun `findAllMatchesOriginal returns empty for no match`() {
        val result = findAllMatchesOriginal("שלום עולם", "בוקר")
        assertTrue(result.isEmpty())
    }

    // Punctuation-insensitive find
    @Test
    fun `isSearchPunctuation covers ASCII, Hebrew and Unicode punctuation`() {
        val ascii = ",.;:!?\"'`()[]{}<>-_/|+=*&^%#@~\\\$"
        // maqaf, sof pasuq, paseq, geresh, gershayim, curly quotes, dashes, guillemets, ellipsis
        val unicode = "\u05BE\u05C3\u05C0\u05F3\u05F4\u201C\u201D\u2018\u2019\u2014\u2013\u00AB\u00BB\u2026"
        val punctuation = ascii + unicode
        for (c in punctuation) assertTrue(isSearchPunctuation(c), "expected punctuation: U+%04X".format(c.code))
        for (c in "אבa Z09\t\u05B0\u0591") assertFalse(isSearchPunctuation(c), "expected not punctuation: U+%04X".format(c.code))
    }

    @Test
    fun `stripForFindWithMap turns punctuation runs into one space and maps back`() {
        val text = "שלום,  עולם."
        val (plain, map) = stripForFindWithMap(text)
        assertEquals("שלום עולם", plain)
        assertEquals(plain.length, map.size)
        // The space stands for the comma, the first character of the run
        assertEquals(4, map[4])
        assertEquals(7, map[5])
        assertEquals(10, map.last())
    }

    @Test
    fun `stripForFindWithMap drops leading and trailing punctuation`() {
        assertEquals("שלום", stripForFindWithMap(" (שלום)! ").first)
        assertEquals("", stripForFindWithMap(" ,.; ").first)
    }

    @Test
    fun `find matches across a comma between words`() {
        val text = "שלום, עולם"
        val result = findAllMatchesOriginal(text, "שלום עולם")
        assertEquals(1, result.size)
        assertEquals(text, text.substring(result[0]))
        assertEquals(1, findAllMatchesOriginal("שלום,עולם", "שלום עולם").size)
    }

    @Test
    fun `find ignores punctuation typed in the query only`() {
        assertEquals(1, findAllMatchesOriginal("שלום עולם", "שלום, עולם!").size)
        assertEquals(1, findAllMatchesOriginal("שלום עולם", "שלום-עולם").size)
        assertEquals(1, findAllMatchesOriginal("שלום עולם", "\"שלום\"").size)
        assertEquals(1, findAllMatchesOriginal("שלום   עולם", "שלום עולם").size)
    }

    @Test
    fun `find matches acronyms with or without gershayim, both ways`() {
        val ascii = "כתב רש\"י כאן"
        val hebrew = "כתב רש\u05F4י כאן"
        for (text in listOf(ascii, hebrew)) {
            val result = findAllMatchesOriginal(text, "רשי")
            assertEquals(1, result.size)
            assertEquals(text.substring(4, 8), text.substring(result[0]))
        }
        assertEquals(1, findAllMatchesOriginal("כתב רשי כאן", "רש\"י").size)
        assertEquals(1, findAllMatchesOriginal("כתב רשי כאן", "רש\u05F4י").size)
        assertEquals(1, findAllMatchesOriginal(ascii, "רש\u05F4י").size)
        assertEquals(1, findAllMatchesOriginal(hebrew, "רש\"י").size)
        assertEquals(1, findAllMatchesOriginal("כתב רש''י כאן", "רשי").size)
        assertEquals(1, findAllMatchesOriginal("ס' שלום", "ס שלום").size)
    }

    @Test
    fun `quote marks opening or closing a quoted word are word breaks, not dropped`() {
        val text = "ואמר \"שלום\" לו"
        assertEquals("ואמר שלום לו", stripForFindWithMap(text).first)
        assertEquals("שלום", text.substring(findAllMatchesOriginal(text, "שלום")[0]))
        assertEquals(1, findAllMatchesOriginal(text, "ואמר שלום לו").size)
        assertEquals(1, findAllMatchesOriginal("ואמר “שלום” לו", "ואמר שלום").size)
    }

    @Test
    fun `find matches a Hebrew geresh abbreviation both ways`() {
        assertEquals(1, findAllMatchesOriginal("ס׳ שלום", "ס שלום").size)
        assertEquals(1, findAllMatchesOriginal("ס' שלום", "ס׳ שלום").size)
        assertEquals(1, findAllMatchesOriginal("ס שלום", "ס׳ שלום").size)
    }

    @Test
    fun `find matches words joined by a maqaf`() {
        val text = "את כל\u05BEהארץ"
        assertEquals(1, findAllMatchesOriginal(text, "כל הארץ").size)
        // Used to never match: the query maqaf became a space while the text kept its own
        assertEquals(1, findAllMatchesOriginal(text, "כל\u05BEהארץ").size)
        assertEquals(1, findAllMatchesOriginal("את כל הארץ", "כל\u05BEהארץ").size)
        assertEquals("כל\u05BEהארץ", text.substring(findAllMatchesOriginal(text, "כל הארץ")[0]))
    }

    @Test
    fun `find matches across a sof pasuq and leaves it out of a final match`() {
        val text = "ויהי אור\u05C3 וירא"
        assertEquals(1, findAllMatchesOriginal(text, "אור וירא").size)
        assertEquals("אור", text.substring(findAllMatchesOriginal(text, "אור")[0]))
        assertEquals("אור", text.substring(findAllMatchesOriginal(text, "אור\u05C3")[0]))
    }

    @Test
    fun `find ignores punctuation inside pointed text`() {
        val text = "בְּרֵאשִׁית, בָּרָא"
        val result = findAllMatchesOriginal(text, "בראשית ברא")
        assertEquals(1, result.size)
        assertEquals(0, result[0].first)
        assertEquals(text.length - 1, result[0].last)
        assertEquals(1, findAllMatchesOriginal("רַשִׁ\"י", "רשי").size)
    }

    @Test
    fun `find offsets point into the original text`() {
        val text = "(שלום) ושלום"
        val result = findAllMatchesOriginal(text, "שלום")
        assertEquals(listOf(1..4, 8..11), result)
    }

    @Test
    fun `highlight range excludes surrounding punctuation`() {
        val text = "אמר: \"שלום, עולם.\""
        val result = findAllMatchesOriginal(text, "שלום עולם.")
        assertEquals(1, result.size)
        assertEquals("שלום, עולם", text.substring(result[0]))
        assertEquals("עולם", text.substring(findAllMatchesOriginal(text, "עולם")[0]))
    }

    @Test
    fun `highlight range keeps the points of the last matched letter`() {
        val text = "\u05E9\u05C1\u05B8\u05DC\u05D5\u05B9\u05DD"
        assertEquals(listOf(0..5), findAllMatchesOriginal(text, "שלו"))
    }

    @Test
    fun `normalizeQueryForHebrew drops punctuation like the text`() {
        assertEquals("שלומ עולמ", normalizeQueryForHebrew("  שלום,   עולם! "))
        assertEquals("רשי", normalizeQueryForHebrew("רש\"י"))
        assertEquals("רשי", normalizeQueryForHebrew("רש\u05F4י"))
        assertEquals("כל הארצ", normalizeQueryForHebrew("כל\u05BEהארץ"))
        assertEquals("", normalizeQueryForHebrew("!?"))
    }
}
