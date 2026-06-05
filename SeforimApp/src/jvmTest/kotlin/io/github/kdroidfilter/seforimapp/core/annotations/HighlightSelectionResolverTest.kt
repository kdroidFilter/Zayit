package io.github.kdroidfilter.seforimapp.core.annotations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HighlightSelectionResolverTest {
    @Test
    fun resolves_substring_with_diacritics_shown() {
        val line = "בראשית ברא אלהים"
        val range = resolveHighlightRange(line, "אלהים", showDiacritics = true)
        assertEquals(11 until 16, range)
    }

    @Test
    fun returns_first_occurrence_for_repeated_text() {
        val line = "שלום שלום"
        assertEquals(0 until 4, resolveHighlightRange(line, "שלום", showDiacritics = true))
    }

    @Test
    fun trims_selection_whitespace() {
        val line = "abc def ghi"
        assertEquals(4 until 7, resolveHighlightRange(line, "  def  ", showDiacritics = true))
    }

    @Test
    fun returns_null_when_not_found() {
        assertNull(resolveHighlightRange("hello world", "xyz", showDiacritics = true))
    }

    @Test
    fun returns_null_for_blank_selection() {
        assertNull(resolveHighlightRange("hello", "   ", showDiacritics = true))
    }

    @Test
    fun maps_stripped_selection_back_to_original_offsets() {
        // "שָׁלוֹם" with nikud; stripped form is "שלום".
        val line = "שָׁלוֹם עוֹלָם"
        val (stripped, _) =
            io.github.kdroidfilter.seforimapp.core.presentation.text
                .stripNikudTeamimWithMap(line)
        // Sanity: the stripped first word is "שלום".
        assertEquals("שלום", stripped.substringBefore(' '))

        val range = resolveHighlightRange(line, stripped.substringBefore(' '), showDiacritics = false)!!
        // Original range must start at 0 and cover the full first (diacriticized) word.
        assertEquals(0, range.first)
        assertEquals("שָׁלוֹם", line.substring(range.first, range.last + 1))
    }

    @Test
    fun diacritics_hidden_selection_with_gershayim_resolves() {
        // Gershayim ״ is KEPT in the displayed (diacritics-hidden) text, like רמב״ם.
        // The stripped form keeps ״, so the selection must still be located and mapped.
        val line = "אָמַר רַמְבַּ״ם כֵּן"
        val stripped =
            io.github.kdroidfilter.seforimapp.core.presentation.text
                .stripNikudTeamimWithMap(line)
                .first
        assertEquals("אמר רמב״ם כן", stripped) // ״ preserved, nikud removed

        val range = resolveHighlightRange(line, "רמב״ם", showDiacritics = false)!!
        // The resolved original range must cover exactly the diacriticized "רַמְבַּ״ם".
        assertEquals("רַמְבַּ״ם", line.substring(range.first, range.last + 1))
    }

    // All visible lines, ordered; the selection's first segment is anchored among them.
    private val visible =
        listOf(
            1L to "intro line zero",
            2L to "hello world",
            3L to "second line",
            4L to "third one",
            5L to "trailing line four",
        )

    @Test
    fun single_line_selection_resolves_one_range() {
        val ranges =
            resolveHighlightRangesForSelection(
                sortedVisibleLines = visible,
                selectedText = "world",
                showDiacritics = true,
            )
        // "world" lives in line 2 at offset 6.
        assertEquals(listOf(LineHighlightRange(2L, 6 until 11)), ranges)
    }

    @Test
    fun multi_line_selection_partial_first_and_last() {
        // Selection starts mid-line-2 (suffix), spans full line-3, ends mid-line-4 (prefix).
        val ranges =
            resolveHighlightRangesForSelection(
                sortedVisibleLines = visible,
                selectedText = "world\nsecond line\nthird",
                showDiacritics = true,
            )
        assertEquals(3, ranges.size)
        assertEquals(LineHighlightRange(2L, 6 until 11), ranges[0]) // "world" (suffix of line 2)
        assertEquals(LineHighlightRange(3L, 0 until 11), ranges[1]) // full middle line
        assertEquals(LineHighlightRange(4L, 0 until 5), ranges[2]) // "third" (prefix of line 4)
    }

    @Test
    fun multi_line_full_lines_plus_half_last_line() {
        // User's case: two full lines + half of the last one.
        val ranges =
            resolveHighlightRangesForSelection(
                sortedVisibleLines = visible,
                selectedText = "hello world\nsecond line\nthi",
                showDiacritics = true,
            )
        assertEquals(LineHighlightRange(2L, 0 until 11), ranges[0]) // full
        assertEquals(LineHighlightRange(3L, 0 until 11), ranges[1]) // full
        assertEquals(LineHighlightRange(4L, 0 until 3), ranges[2]) // "thi"
    }

    @Test
    fun selection_not_found_returns_empty() {
        val ranges =
            resolveHighlightRangesForSelection(
                sortedVisibleLines = visible,
                selectedText = "nonexistent text",
                showDiacritics = true,
            )
        assertEquals(emptyList(), ranges)
    }
}
