package io.github.kdroidfilter.seforimapp.core

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import org.jsoup.Jsoup

private val WHITESPACE_REGEX = Regex("\\s+")
private const val MIN_EDGE_OVERLAP = 8

// Title of the Talmud root category in the Sefaria-imported catalog. Used to apply the
// daf-citation rule (drop the in-page line segment, e.g. "סוכה ב., ב" → "סוכה ב.").
private const val TALMUD_ROOT_TITLE = "תלמוד"

/**
 * Normalize text for fuzzy matching between a free-form selection and the underlying line
 * content. We strip Hebrew diacritics (the displayed text may have them removed depending on
 * user settings) and collapse whitespace so the two sides compare consistently.
 */
private fun normalizeForMatch(text: String): String =
    HebrewTextUtils
        .removeAllDiacritics(text)
        .replace(WHITESPACE_REGEX, " ")
        .trim()

private fun stripHtmlToPlainText(html: String): String = normalizeForMatch(Jsoup.parse(html).text())

/**
 * Best-effort matching of a free-form text selection against a list of currently loaded lines.
 * The selection is a single string (concatenated by SelectionContainer), so we identify the
 * first/last lines that overlap with it. Edge lines may be partially selected, so we also
 * consider non-trivial prefix/suffix overlap.
 */
fun resolveLineRangeFromSelection(
    selectedText: String,
    lines: List<Line>,
): Pair<Line, Line>? {
    val cleanedSelection = normalizeForMatch(selectedText)
    if (cleanedSelection.isEmpty() || lines.isEmpty()) return null

    val matches =
        lines.mapNotNull { line ->
            val plain = stripHtmlToPlainText(line.content)
            if (plain.isEmpty()) return@mapNotNull null
            val isMatch =
                cleanedSelection.contains(plain) ||
                    plain.contains(cleanedSelection) ||
                    (plain.length >= MIN_EDGE_OVERLAP && cleanedSelection.endsWith(plain.take(MIN_EDGE_OVERLAP))) ||
                    (plain.length >= MIN_EDGE_OVERLAP && cleanedSelection.startsWith(plain.takeLast(MIN_EDGE_OVERLAP)))
            if (isMatch) line else null
        }
    if (matches.isEmpty()) return null
    val sorted = matches.sortedBy { it.lineIndex }
    return sorted.first() to sorted.last()
}

fun formatHebrewSourceReference(
    book: Book,
    rootTitle: String?,
    firstLine: Line,
    lastLine: Line,
): String {
    val bookRef = book.heRef?.takeIf { it.isNotBlank() } ?: book.title
    val isTalmud = rootTitle == TALMUD_ROOT_TITLE
    // Many catalogs already embed the book identity inside line.heRef (e.g.
    // "תלמוד ירושלמי דמאי א, א, א"). When that's the case we use the heRef verbatim instead
    // of stripping + re-concatenating, which would lose the comma separating book from locator.
    val candidatePrefixes =
        listOfNotNull(book.heRef, book.title)
            .filter { it.isNotBlank() }
            .distinct()

    fun fullRefFor(heRef: String?): String? {
        if (heRef.isNullOrBlank()) return null
        val hasEmbeddedPrefix = candidatePrefixes.any { heRef.startsWith(it) }
        val base = if (hasEmbeddedPrefix) heRef else "$bookRef $heRef"
        // Talmud refs end with an in-page line marker ("דף, אמוד, שורה"); never part of the
        // standard citation, so drop the last comma-separated segment.
        val trimmed = if (isTalmud) base.substringBeforeLast(',').trim() else base.trim()
        // Some heRefs in the catalog contain stray double spaces (data quality artifact).
        // Always emit a single-space output regardless of input shape.
        val normalized = trimmed.replace(WHITESPACE_REGEX, " ")
        return normalized.ifEmpty { null }
    }

    val first = fullRefFor(firstLine.heRef)
    val last = fullRefFor(lastLine.heRef)
    return when {
        first == null && last == null -> bookRef
        first == null -> last!!
        last == null || first == last -> first
        else -> "$first – $last"
    }
}

fun buildCopyWithSourcePayload(
    selectedText: String,
    book: Book,
    rootTitle: String?,
    loadedLines: List<Line>,
): String {
    val range = resolveLineRangeFromSelection(selectedText, loadedLines)
    val reference =
        if (range != null) {
            formatHebrewSourceReference(book, rootTitle, range.first, range.second)
        } else {
            book.heRef?.takeIf { it.isNotBlank() } ?: book.title
        }
    return "$selectedText\n\n($reference)"
}
