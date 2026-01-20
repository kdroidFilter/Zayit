package io.github.kdroidfilter.seforimapp.core.presentation.text

/*
 * Utilities to perform Hebrew-aware, diacritic-insensitive search.
 * Strips nikud (vowel points) and ta'amim (cantillation) and normalizes final letters
 * to their base forms for matching, while preserving a mapping back to original indices
 * so we can highlight the right character ranges.
 */

/**
 * Returns the string without nikud+teamim and an index map from plain index -> original index.
 */
internal fun stripDiacriticsWithMap(src: String): Pair<String, IntArray> {
    val nikudOrTeamim: (Char) -> Boolean = { c ->
        (c.code in 0x0591..0x05AF) ||
            // teamim
            (c.code in 0x05B0..0x05BD) ||
            // nikud + meteg
            (c == '\u05C1') ||
            (c == '\u05C2') ||
            (c == '\u05C7')
    }
    val out = StringBuilder(src.length)
    val map = ArrayList<Int>(src.length)
    var i = 0
    while (i < src.length) {
        val ch = src[i]
        // Drop nikud/ta'amim and also gershayim/geresh for matching
        if (!nikudOrTeamim(ch) && ch != '\u05F4' && ch != '\u05F3') {
            out.append(ch)
            map.add(i)
        }
        i++
    }
    val arr = IntArray(map.size) { map[it] }
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
