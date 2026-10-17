package io.github.kdroidfilter.seforimapp.core.presentation.text

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
