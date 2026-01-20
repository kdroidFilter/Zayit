package io.github.kdroidfilter.seforimapp.integration

import io.github.kdroidfilter.seforimapp.core.presentation.text.findAllMatchesOriginal
import io.github.kdroidfilter.seforimapp.core.presentation.text.mapToOrigIndex
import io.github.kdroidfilter.seforimapp.core.presentation.text.normalizeQueryForHebrew
import io.github.kdroidfilter.seforimapp.core.presentation.text.replaceFinalsWithBase
import io.github.kdroidfilter.seforimapp.core.presentation.text.stripDiacriticsWithMap
import io.github.kdroidfilter.seforimlibrary.search.HebrewTextUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for Hebrew text processing utilities.
 * Tests diacritic stripping, final letter normalization, and search matching.
 */
class HebrewTextProcessingIntegrationTest {
    // ==================== HebrewTextUtils.normalizeHebrew Tests ====================

    @Test
    fun `normalizeHebrew removes nikud vowel points`() {
        // בְּרֵאשִׁית -> בראשית
        val withNikud = "בְּרֵאשִׁית"
        val result = HebrewTextUtils.normalizeHebrew(withNikud)

        assertEquals("בראשית", result)
    }

    @Test
    fun `normalizeHebrew removes teamim cantillation marks`() {
        // With etnachta and other cantillation marks
        val withTeamim = "בְּרֵאשִׁ֖ית"
        val result = HebrewTextUtils.normalizeHebrew(withTeamim)

        assertEquals("בראשית", result)
    }

    @Test
    fun `normalizeHebrew replaces final letters with base forms`() {
        // ך -> כ, ם -> מ, ן -> נ, ף -> פ, ץ -> צ
        val withFinals = "מלך שלום אמן קוף ארץ"
        val result = HebrewTextUtils.normalizeHebrew(withFinals)

        assertTrue(!result.contains('ך'))
        assertTrue(!result.contains('ם'))
        assertTrue(!result.contains('ן'))
        assertTrue(!result.contains('ף'))
        assertTrue(!result.contains('ץ'))
    }

    @Test
    fun `normalizeHebrew replaces maqaf with space`() {
        // כל־הארץ -> כל הארץ
        val withMaqaf = "כל־הארץ"
        val result = HebrewTextUtils.normalizeHebrew(withMaqaf)

        assertTrue(!result.contains('־'))
        assertTrue(result.contains(' '))
    }

    @Test
    fun `normalizeHebrew removes gershayim`() {
        // ה״ -> ה
        val withGershayim = "ה״שם"
        val result = HebrewTextUtils.normalizeHebrew(withGershayim)

        assertTrue(!result.contains('״'))
    }

    @Test
    fun `normalizeHebrew removes geresh`() {
        val withGeresh = "ה׳"
        val result = HebrewTextUtils.normalizeHebrew(withGeresh)

        assertTrue(!result.contains('׳'))
    }

    @Test
    fun `normalizeHebrew handles empty string`() {
        val result = HebrewTextUtils.normalizeHebrew("")
        assertEquals("", result)
    }

    @Test
    fun `normalizeHebrew handles blank string`() {
        val result = HebrewTextUtils.normalizeHebrew("   ")
        assertEquals("", result)
    }

    @Test
    fun `normalizeHebrew collapses multiple spaces`() {
        val withSpaces = "word1    word2"
        val result = HebrewTextUtils.normalizeHebrew(withSpaces)

        assertEquals("word1 word2", result)
    }

    // ==================== HebrewTextUtils.replaceFinalsWithBase Tests ====================

    @Test
    fun `replaceFinalsWithBase replaces final kaf`() {
        val result = HebrewTextUtils.replaceFinalsWithBase("מלך")
        assertEquals("מלכ", result)
    }

    @Test
    fun `replaceFinalsWithBase replaces final mem`() {
        val result = HebrewTextUtils.replaceFinalsWithBase("שלום")
        assertEquals("שלומ", result)
    }

    @Test
    fun `replaceFinalsWithBase replaces final nun`() {
        val result = HebrewTextUtils.replaceFinalsWithBase("אמן")
        assertEquals("אמנ", result)
    }

    @Test
    fun `replaceFinalsWithBase replaces final pe`() {
        val result = HebrewTextUtils.replaceFinalsWithBase("כף")
        assertEquals("כפ", result)
    }

    @Test
    fun `replaceFinalsWithBase replaces final tsade`() {
        val result = HebrewTextUtils.replaceFinalsWithBase("ארץ")
        assertEquals("ארצ", result)
    }

    @Test
    fun `replaceFinalsWithBase replaces all finals in text`() {
        val text = "מלך שלום אמן כף ארץ"
        val result = HebrewTextUtils.replaceFinalsWithBase(text)

        assertEquals("מלכ שלומ אמנ כפ ארצ", result)
    }

    @Test
    fun `replaceFinalsWithBase preserves non-final letters`() {
        val text = "אבגדה"
        val result = HebrewTextUtils.replaceFinalsWithBase(text)

        assertEquals("אבגדה", result)
    }

    // ==================== HebrewTextUtils.stripDiacriticsWithMap Tests ====================

    @Test
    fun `stripDiacriticsWithMap removes nikud and creates correct map`() {
        val text = "בְּרֵאשִׁית"
        val (plain, map) = HebrewTextUtils.stripDiacriticsWithMap(text)

        assertEquals("בראשית", plain)
        assertEquals(plain.length, map.size)
    }

    @Test
    fun `stripDiacriticsWithMap map points to correct original indices`() {
        // ב + ְ + ּ + ר + ֵ + א + ש + ִ + ׁ + י + ת
        val text = "בְּרֵאשִׁית"
        val (plain, map) = HebrewTextUtils.stripDiacriticsWithMap(text)

        // First letter 'ב' is at index 0 in both
        assertEquals(0, map[0])
        // 'ר' follows the diacritics
        assertTrue(map[1] > 0)
    }

    @Test
    fun `stripDiacriticsWithMap handles text without diacritics`() {
        val text = "בראשית"
        val (plain, map) = HebrewTextUtils.stripDiacriticsWithMap(text)

        assertEquals(text, plain)
        // Map should be identity (0, 1, 2, 3, ...)
        for (i in map.indices) {
            assertEquals(i, map[i])
        }
    }

    @Test
    fun `stripDiacriticsWithMap handles empty string`() {
        val (plain, map) = HebrewTextUtils.stripDiacriticsWithMap("")

        assertEquals("", plain)
        assertEquals(0, map.size)
    }

    // ==================== stripDiacriticsWithMap (local version) Tests ====================

    @Test
    fun `local stripDiacriticsWithMap removes nikud`() {
        val text = "בְּרֵאשִׁית"
        val (plain, _) = stripDiacriticsWithMap(text)

        assertEquals("בראשית", plain)
    }

    @Test
    fun `local stripDiacriticsWithMap removes gershayim`() {
        val text = "ה״"
        val (plain, _) = stripDiacriticsWithMap(text)

        assertEquals("ה", plain)
    }

    @Test
    fun `local stripDiacriticsWithMap removes geresh`() {
        val text = "ה׳"
        val (plain, _) = stripDiacriticsWithMap(text)

        assertEquals("ה", plain)
    }

    // ==================== replaceFinalsWithBase (local version) Tests ====================

    @Test
    fun `local replaceFinalsWithBase works correctly`() {
        val text = "מלך"
        val result = replaceFinalsWithBase(text)

        assertEquals("מלכ", result)
    }

    // ==================== normalizeQueryForHebrew Tests ====================

    @Test
    fun `normalizeQueryForHebrew normalizes complete query`() {
        val query = "בְּרֵאשִׁ֖ית"
        val result = normalizeQueryForHebrew(query)

        // Should remove nikud, teamim, and apply lowercase
        assertEquals("בראשית", result)
    }

    @Test
    fun `normalizeQueryForHebrew handles Latin text with case`() {
        val query = "Test Query"
        val result = normalizeQueryForHebrew(query)

        assertEquals("test query", result)
    }

    @Test
    fun `normalizeQueryForHebrew replaces maqaf with space`() {
        val query = "כל־הארץ"
        val result = normalizeQueryForHebrew(query)

        assertTrue(result.contains(' '))
        assertTrue(!result.contains('־'))
    }

    // ==================== mapToOrigIndex Tests ====================

    @Test
    fun `mapToOrigIndex returns plain index for empty map`() {
        val result = mapToOrigIndex(IntArray(0), 5)
        assertEquals(5, result)
    }

    @Test
    fun `mapToOrigIndex clamps to valid range`() {
        val map = intArrayOf(0, 2, 4, 6)
        val result = mapToOrigIndex(map, 10) // Out of bounds

        assertEquals(6, result) // Should return last valid index
    }

    @Test
    fun `mapToOrigIndex handles negative index`() {
        val map = intArrayOf(0, 2, 4, 6)
        val result = mapToOrigIndex(map, -1)

        assertEquals(0, result) // Should clamp to 0
    }

    @Test
    fun `mapToOrigIndex returns correct mapping`() {
        val map = intArrayOf(0, 3, 5, 8)
        val result = mapToOrigIndex(map, 2)

        assertEquals(5, result)
    }

    // ==================== findAllMatchesOriginal Tests ====================

    @Test
    fun `findAllMatchesOriginal finds simple match`() {
        val text = "בראשית ברא"
        val query = "ברא"
        val matches = findAllMatchesOriginal(text, query)

        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun `findAllMatchesOriginal finds multiple matches`() {
        val text = "ברא ברא ברא"
        val query = "ברא"
        val matches = findAllMatchesOriginal(text, query)

        assertEquals(3, matches.size)
    }

    @Test
    fun `findAllMatchesOriginal is diacritic insensitive`() {
        val text = "בְּרֵאשִׁית"
        val query = "בראשית"
        val matches = findAllMatchesOriginal(text, query)

        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun `findAllMatchesOriginal handles final letter normalization`() {
        // Search for word with final letter using non-final form
        val text = "שלום"
        val query = "שלומ" // Using non-final mem
        val matches = findAllMatchesOriginal(text, query)

        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun `findAllMatchesOriginal returns empty for short query`() {
        val text = "בראשית"
        val query = "ב"
        val matches = findAllMatchesOriginal(text, query)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `findAllMatchesOriginal returns empty for no match`() {
        val text = "בראשית"
        val query = "שמות"
        val matches = findAllMatchesOriginal(text, query)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `findAllMatchesOriginal is case insensitive for Latin`() {
        val text = "Hello World"
        val query = "hello"
        val matches = findAllMatchesOriginal(text, query)

        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun `findAllMatchesOriginal returns correct ranges`() {
        val text = "אבג דהו"
        val query = "דהו"
        val matches = findAllMatchesOriginal(text, query)

        assertEquals(1, matches.size)
        val range = matches[0]
        assertEquals("דהו", text.substring(range))
    }

    @Test
    fun `findAllMatchesOriginal handles text with nikud`() {
        val text = "בְּרֵאשִׁית בָּרָא אֱלֹהִים"
        val query = "ברא"
        val matches = findAllMatchesOriginal(text, query)

        // Should find both "ברא" occurrences despite nikud
        assertTrue(matches.size >= 1)
    }

    @Test
    fun `findAllMatchesOriginal handles mixed Hebrew and Latin`() {
        val text = "בראשית Genesis ברא"
        val query = "genesis"
        val matches = findAllMatchesOriginal(text, query)

        assertTrue(matches.isNotEmpty())
    }

    // ==================== SOFIT_MAP Tests ====================

    @Test
    fun `SOFIT_MAP contains all final letters`() {
        val sofitMap = HebrewTextUtils.SOFIT_MAP

        assertEquals('כ', sofitMap['ך'])
        assertEquals('מ', sofitMap['ם'])
        assertEquals('נ', sofitMap['ן'])
        assertEquals('פ', sofitMap['ף'])
        assertEquals('צ', sofitMap['ץ'])
    }

    @Test
    fun `SOFIT_MAP has exactly 5 entries`() {
        assertEquals(5, HebrewTextUtils.SOFIT_MAP.size)
    }

    // ==================== isNikudOrTeamim Tests ====================

    @Test
    fun `isNikudOrTeamim returns true for patach`() {
        assertTrue(HebrewTextUtils.isNikudOrTeamim('\u05B7'))
    }

    @Test
    fun `isNikudOrTeamim returns true for shva`() {
        assertTrue(HebrewTextUtils.isNikudOrTeamim('\u05B0'))
    }

    @Test
    fun `isNikudOrTeamim returns true for etnachta`() {
        assertTrue(HebrewTextUtils.isNikudOrTeamim('\u0591'))
    }

    @Test
    fun `isNikudOrTeamim returns false for regular letter`() {
        assertTrue(!HebrewTextUtils.isNikudOrTeamim('א'))
    }

    @Test
    fun `isNikudOrTeamim returns false for Latin letter`() {
        assertTrue(!HebrewTextUtils.isNikudOrTeamim('A'))
    }

    @Test
    fun `isNikudOrTeamim returns false for space`() {
        assertTrue(!HebrewTextUtils.isNikudOrTeamim(' '))
    }

    // ==================== stripDiacritics Tests ====================

    @Test
    fun `stripDiacritics removes all diacritics`() {
        val text = "בְּרֵאשִׁ֖ית"
        val result = HebrewTextUtils.stripDiacritics(text)

        assertEquals("בראשית", result)
    }

    @Test
    fun `stripDiacritics preserves text without diacritics`() {
        val text = "בראשית"
        val result = HebrewTextUtils.stripDiacritics(text)

        assertEquals(text, result)
    }

    @Test
    fun `stripDiacritics handles empty string`() {
        val result = HebrewTextUtils.stripDiacritics("")
        assertEquals("", result)
    }

    // ==================== Complex Integration Tests ====================

    @Test
    fun `search works across word boundaries with nikud`() {
        val text = "אֵת הַשָּׁמַיִם וְאֵת הָאָרֶץ"
        val query = "הארץ"
        val matches = findAllMatchesOriginal(text, query)

        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun `search finds partial word match`() {
        val text = "בראשית"
        val query = "ראש"
        val matches = findAllMatchesOriginal(text, query)

        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun `full normalization and search pipeline`() {
        // Text with nikud, teamim, and final letters
        val text = "בְּרֵאשִׁ֖ית בָּרָ֣א אֱלֹהִ֑ים אֵ֥ת הַשָּׁמַ֖יִם וְאֵ֥ת הָאָֽרֶץ"
        // Query without diacritics
        val query = "אלהימ"
        val matches = findAllMatchesOriginal(text, query)

        // Should find אֱלֹהִ֑ים
        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun `search preserves correct original indices with diacritics`() {
        val text = "אָבְ גַּדֵּה"
        val query = "גדה"
        val matches = findAllMatchesOriginal(text, query)

        assertTrue(matches.isNotEmpty())
        val range = matches[0]
        // The extracted text should be the original with diacritics
        val extracted = text.substring(range)
        // Should contain the גדה letters (possibly with diacritics)
        assertTrue(extracted.contains('ג'))
        assertTrue(extracted.contains('ד'))
        assertTrue(extracted.contains('ה'))
    }

    @Test
    fun `search handles numbers in Hebrew text`() {
        val text = "פרק 1 פסוק 2"
        val query = "פרק"
        val matches = findAllMatchesOriginal(text, query)

        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun `search handles punctuation in Hebrew text`() {
        val text = "שלום, עולם!"
        val query = "שלומ"
        val matches = findAllMatchesOriginal(text, query)

        assertTrue(matches.isNotEmpty())
    }
}
