package io.github.kdroidfilter.seforimapp.core.presentation.text

/*
 * Utilities to perform Hebrew-aware, diacritic-insensitive search.
 * Strips nikud (vowel points) and ta'amim (cantillation) and normalizes final letters
 * to their base forms for matching, while preserving a mapping back to original indices
 * so we can highlight the right character ranges.
 */

/** True for nikud (vowel points) and ta'amim (cantillation) characters. */
private fun isNikudOrTeamim(c: Char): Boolean =
    (c.code in 0x0591..0x05AF) ||
        // teamim
        (c.code in 0x05B0..0x05BD) ||
        // nikud + meteg
        (c == '\u05C1') ||
        (c == '\u05C2') ||
        (c == '\u05C7')

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
 * Strips ONLY nikud+teamim (keeping geresh/gershayim ׳ ״), mirroring
 * [io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils.removeAllDiacritics] — the
 * function that produces the text actually rendered when diacritics are hidden. Returns the
 * stripped string plus an index map from stripped index -> original index.
 *
 * Use this (not [stripDiacriticsWithMap], which also drops ׳ ״ for search matching) whenever
 * offsets must line up with the displayed, diacritics-hidden text.
 */
fun stripNikudTeamimWithMap(src: String): Pair<String, IntArray> {
    val out = StringBuilder(src.length)
    val map = IntArray(src.length)
    var count = 0
    for (i in src.indices) {
        val ch = src[i]
        if (!isNikudOrTeamim(ch)) {
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

internal fun normalizeQueryForHebrew(raw: String): String {
    if (raw.isBlank()) return ""
    var s = raw.trim()
    // Remove biblical cantillation marks (teamim) U+0591–U+05AF
    s = s.replace("[\u0591-\u05AF]".toRegex(), "")
    // Remove nikud signs including meteg and qamatz qatan
    s = s.replace("[\u05B0\u05B1\u05B2\u05B3\u05B4\u05B5\u05B6\u05B7\u05B8\u05B9\u05BB\u05BC\u05BD\u05C1\u05C2\u05C7]".toRegex(), "")
    // Replace maqaf with space and remove gershayim/geresh
    s = s.replace('\u05BE', ' ')
    s = s.replace("\u05F4", "").replace("\u05F3", "")
    // Normalize Hebrew final letters (sofit) to base forms
    s = replaceFinalsWithBase(s)
    // Lowercase for case-insensitive match (safe for Latin)
    s = s.lowercase()
    return s
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
 * Maps original index -> stripped index for the diacritics-hidden display text. Strips ONLY
 * nikud+teamim (keeps ׳ ״), matching `HebrewTextUtils.removeAllDiacritics` so offsets line up
 * with what is rendered. `result[origIndex]` is the stripped index, or -1 if that character
 * was stripped.
 */
fun createOriginalToStrippedMap(src: String): IntArray {
    val result = IntArray(src.length) { -1 }
    var strippedIndex = 0
    for (i in src.indices) {
        if (!isNikudOrTeamim(src[i])) {
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
): List<IntRange> {
    val q = normalizeQueryForHebrew(query)
    if (q.length < 2) return emptyList()

    val (plain, map) = stripDiacriticsWithMap(text)
    val plainSearch = replaceFinalsWithBase(plain).lowercase()

    val out = mutableListOf<IntRange>()
    var from = 0
    while (from <= plainSearch.length - q.length) {
        val idx = plainSearch.indexOf(q, startIndex = from)
        if (idx == -1) break
        val startOrig = mapToOrigIndex(map, idx)
        val endOrig = mapToOrigIndex(map, idx + q.length - 1) + 1
        if (endOrig > startOrig) out += (startOrig until endOrig)
        from = idx + q.length
    }
    return out
}
