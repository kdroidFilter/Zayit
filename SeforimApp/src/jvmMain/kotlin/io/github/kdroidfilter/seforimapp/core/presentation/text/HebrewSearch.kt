package io.github.kdroidfilter.seforimapp.core.presentation.text

/*
 * Utilities to perform Hebrew-aware, diacritic- and punctuation-insensitive search.
 * Strips nikud (vowel points), ta'amim (cantillation) and punctuation and normalizes final letters
 * to their base forms for matching, while preserving a mapping back to original indices
 * so we can highlight the right character ranges.
 */

/**
 * Returns the string without nikud+teamim and an index map from plain index -> original index.
 */
fun stripDiacriticsWithMap(src: String): Pair<String, IntArray> {
    val nikudOrTeamim: (Char) -> Boolean = { c -> isNikudOrTeamim(c) }
    val out = StringBuilder(src.length)
    val map = IntArray(src.length)
    var count = 0
    var i = 0
    while (i < src.length) {
        val ch = src[i]
        // Drop nikud/ta'amim and also gershayim/geresh for matching
        if (!nikudOrTeamim(ch) && ch != '\u05F4' && ch != '\u05F3') {
            out.append(ch)
            map[count++] = i
        }
        i++
    }
    val arr = if (count == map.size) map else map.copyOf(count)
    return out.toString() to arr
}

/**
 * Strips the characters [mode] hides (keeping geresh/gershayim ׳ ״), mirroring
 * [DiacriticsMode.apply] — the function that produces the text actually rendered. Returns the
 * stripped string plus an index map from stripped index -> original index.
 *
 * Use this (not [stripDiacriticsWithMap], which also drops ׳ ״ for search matching) whenever
 * offsets must line up with the displayed text.
 */
fun stripNikudTeamimWithMap(
    src: String,
    mode: DiacriticsMode = DiacriticsMode.None,
): Pair<String, IntArray> {
    val out = StringBuilder(src.length)
    val map = IntArray(src.length)
    var count = 0
    for (i in src.indices) {
        val ch = src[i]
        if (!mode.hides(ch)) {
            out.append(ch)
            map[count++] = i
        }
    }
    val arr = if (count == map.size) map else map.copyOf(count)
    return out.toString() to arr
}

internal fun replaceFinalsWithBase(text: String): String =
    text
        .replace('\u05DA', '\u05DB') // ך -> כ
        .replace('\u05DD', '\u05DE') // ם -> מ
        .replace('\u05DF', '\u05E0') // ן -> נ
        .replace('\u05E3', '\u05E4') // ף -> פ
        .replace('\u05E5', '\u05E6') // ץ -> צ

/**
 * Normalizes a find query exactly as [findAllMatchesNormalized] normalizes the text it scans:
 * nikud and ta'amim dropped, punctuation (maqaf included) treated as a word break, quotes inside
 * a word (רש"י, רש״י) dropped, whitespace collapsed and trimmed, final letters folded, lowercased.
 */
internal fun normalizeQueryForHebrew(raw: String): String {
    if (raw.isBlank()) return ""
    return foldForFind(stripForFindWithMap(raw).first)
}

internal fun mapToOrigIndex(
    mapToOrig: IntArray,
    plainIndex: Int,
): Int {
    if (mapToOrig.isEmpty()) return plainIndex
    val idx = plainIndex.coerceIn(0, mapToOrig.size - 1)
    return mapToOrig[idx]
}

/**
 * Maps original index -> stripped index for the text displayed in [mode]. Strips ONLY what
 * [DiacriticsMode.apply] strips (keeps ׳ ״) so offsets line up with what is rendered.
 * `result[origIndex]` is the stripped index, or -1 if that character was stripped.
 */
fun createOriginalToStrippedMap(
    src: String,
    mode: DiacriticsMode = DiacriticsMode.None,
): IntArray {
    val result = IntArray(src.length) { -1 }
    var strippedIndex = 0
    for (i in src.indices) {
        if (!mode.hides(src[i])) {
            result[i] = strippedIndex
            strippedIndex++
        }
    }
    return result
}

/**
 * Maps an [originalOffset] (with diacritics) to the stripped text. If it points to a
 * stripped character, returns the next valid stripped position.
 */
fun mapOriginalToStripped(
    originalOffset: Int,
    originalToStrippedMap: IntArray,
): Int {
    if (originalToStrippedMap.isEmpty()) return originalOffset
    val strippedLength = originalToStrippedMap.count { it >= 0 }
    if (originalOffset >= originalToStrippedMap.size) return strippedLength
    val safeOffset = originalOffset.coerceAtLeast(0)
    if (originalToStrippedMap[safeOffset] >= 0) return originalToStrippedMap[safeOffset]
    for (i in safeOffset until originalToStrippedMap.size) {
        if (originalToStrippedMap[i] >= 0) return originalToStrippedMap[i]
    }
    return strippedLength
}

/**
 * Find all diacritic-insensitive matches of [query] in [text], returning original
 * character index ranges [start, end) suitable for highlighting.
 */
internal fun findAllMatchesOriginal(
    text: String,
    query: String,
): List<IntRange> = findAllMatchesNormalized(text, normalizeQueryForHebrew(query))

/** [findAllMatchesOriginal] for a query already through [normalizeQueryForHebrew]: scanning many lines normalizes it once. */
internal fun findAllMatchesNormalized(
    text: String,
    q: String,
): List<IntRange> {
    if (q.length < 2) return emptyList()

    val (plain, map) = stripForFindWithMap(text)
    val plainSearch = foldForFind(plain)

    val out = mutableListOf<IntRange>()
    var from = 0
    while (from <= plainSearch.length - q.length) {
        val idx = plainSearch.indexOf(q, startIndex = from)
        if (idx == -1) break
        val startOrig = mapToOrigIndex(map, idx)
        var endOrig = mapToOrigIndex(map, idx + q.length - 1) + 1
        // Keep the vowel points/cantillation of the last matched letter inside the range
        while (endOrig < text.length && isNikudOrTeamim(text[endOrig])) endOrig++
        if (endOrig > startOrig) out += (startOrig until endOrig)
        from = idx + q.length
    }
    return out
}

// Quote marks that write geresh/gershayim (רש"י, ס'): dropped, not a word break, between two letters.
// A String rather than a Set<Char> so membership checks do not box every scanned character.
private const val WORD_QUOTES =
    "\"'`" +
        "´" + // ´ acute accent
        "‘’" + // ‘ ’
        "“”" + // “ ”
        "′″" + // ′ ″ prime, double prime
        "׳״" // ׳ geresh, ״ gershayim

// Unicode general categories of punctuation (P*)
private fun isUnicodePunctuationType(type: Int): Boolean =
    when (type.toByte()) {
        Character.CONNECTOR_PUNCTUATION,
        Character.DASH_PUNCTUATION,
        Character.START_PUNCTUATION,
        Character.END_PUNCTUATION,
        Character.INITIAL_QUOTE_PUNCTUATION,
        Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION,
        -> true
        else -> false
    }

/**
 * True for the characters find-in-page ignores: every printable ASCII character that is neither a
 * letter, a digit nor a space (brackets and symbols such as + = < > | ~ $ included), any Unicode
 * punctuation (general category P*: maqaf ־, sof pasuq ׃, paseq ׀, geresh ׳, gershayim ״, curly
 * quotes, dashes…), and the quote look-alikes in [WORD_QUOTES].
 */
fun isSearchPunctuation(c: Char): Boolean =
    when {
        c.code < 0x80 -> c.code in 0x21..0x7E && !c.isLetterOrDigit()
        c in WORD_QUOTES -> true
        else -> isUnicodePunctuationType(Character.getType(c))
    }

/**
 * The text find-in-page matches against, plus an index map from stripped index -> original index.
 * Nikud and ta'amim are dropped; a quote mark between two letters (רש"י, רש״י, רש''י) is dropped
 * too, so the acronym reads as one word; any other run of punctuation and/or whitespace becomes a
 * single space, mapped to the run's first character, so "שלום, עולם" and "שלום,עולם" both read
 * "שלום עולם". Leading and trailing runs are dropped, so a match never starts or ends on one.
 *
 * Unlike [stripDiacriticsWithMap] (kept as is for the TOC search), this also drops punctuation.
 */
fun stripForFindWithMap(src: String): Pair<String, IntArray> {
    val out = StringBuilder(src.length)
    val map = IntArray(src.length)
    // Original index of the first character of the pending word break, -1 when there is none
    var breakAt = -1
    for (i in src.indices) {
        val ch = src[i]
        when {
            isNikudOrTeamim(ch) -> Unit
            breakAt < 0 && isQuoteInsideWord(src, i, out) -> Unit
            ch.isWhitespace() || isSearchPunctuation(ch) -> if (breakAt < 0) breakAt = i
            else -> {
                if (breakAt >= 0 && out.isNotEmpty()) {
                    map[out.length] = breakAt
                    out.append(' ')
                }
                breakAt = -1
                map[out.length] = i
                out.append(ch)
            }
        }
    }
    val arr = if (out.length == map.size) map else map.copyOf(out.length)
    return out.toString() to arr
}

// Whether src[i] is a quote mark between the letter last kept in [out] and a letter further on
// (nikud and other quote marks skipped)
private fun isQuoteInsideWord(
    src: String,
    i: Int,
    out: CharSequence,
): Boolean {
    if (src[i] !in WORD_QUOTES || out.isEmpty() || !out.last().isLetter()) return false
    var j = i + 1
    while (j < src.length && (src[j] in WORD_QUOTES || isNikudOrTeamim(src[j]))) j++
    return j < src.length && src[j].isLetter()
}

// Folds final letters and case one character for one, so indices stay aligned with the map
private fun foldForFind(s: String): String =
    buildString(s.length) {
        for (c in s) {
            val base =
                when (c) {
                    'ך' -> 'כ' // ך -> כ
                    'ם' -> 'מ' // ם -> מ
                    'ן' -> 'נ' // ן -> נ
                    'ף' -> 'פ' // ף -> פ
                    'ץ' -> 'צ' // ץ -> צ
                    else -> c
                }
            append(base.lowercaseChar())
        }
    }
