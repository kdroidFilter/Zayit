package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.core.presentation.text.mapToOrigIndex
import io.github.kdroidfilter.seforimapp.core.presentation.text.normalizeQueryForHebrew
import io.github.kdroidfilter.seforimapp.core.presentation.text.replaceFinalsWithBase
import io.github.kdroidfilter.seforimapp.core.presentation.text.stripDiacriticsWithMap
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.TocFilterResult
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
private val WORD = Regex("[\\p{L}\\p{N}]+")

// ASCII stand-ins for gershayim/geresh, as most titles write them (קכ"א, ס'): part of a word
// when between two letters
private fun isInWordQuote(
    text: CharSequence,
    i: Int,
): Boolean =
    (text[i] == '"' || text[i] == '\'') &&
        i > 0 &&
        i < text.length - 1 &&
        text[i - 1].isLetter() &&
        text[i + 1].isLetter()

/** Splits a normalized query into its words, dropping punctuation. */
internal fun tocQueryTokens(query: String): List<String> {
    val normalized = normalizeQueryForHebrew(query)
    val joined = buildString { normalized.forEachIndexed { i, c -> if (!isInWordQuote(normalized, i)) append(c) } }
    return joined.split(NON_WORD).filter { it.isNotEmpty() }
}

/** A TOC title that matched every query token: highlight ranges in the original text, and how many tokens were whole words. */
internal data class TocTextMatch(
    val ranges: List<IntRange>,
    val exactWords: Int,
)

/**
 * Matches [text] against [tokens]: each token must be the start of its own word of the title
 * (diacritics, quotes/geresh/gershayim and final letters ignored). Whole-word hits are taken
 * first, so "סימן א" highlights "א" rather than the first word starting with alef, and a word
 * serves one token only: "סימן ס" does not match "סימן ג".
 */
internal fun matchTocText(
    text: String,
    tokens: List<String>,
): TocTextMatch? {
    if (tokens.isEmpty()) return null
    val (stripped, strippedMap) = stripDiacriticsWithMap(text)
    val plain = StringBuilder(stripped.length)
    val toOriginal = IntArray(stripped.length)
    stripped.forEachIndexed { i, c ->
        if (!isInWordQuote(stripped, i)) {
            toOriginal[plain.length] = strippedMap[i]
            plain.append(c)
        }
    }
    val map = toOriginal.copyOf(plain.length)
    val words = WORD.findAll(replaceFinalsWithBase(plain.toString()).lowercase()).toList()

    val used = BooleanArray(words.size)
    val starts = IntArray(tokens.size) { -1 }
    var exactWords = 0
    tokens.forEachIndexed { t, token ->
        val w = words.indices.firstOrNull { !used[it] && words[it].value == token } ?: return@forEachIndexed
        used[w] = true
        starts[t] = words[w].range.first
        exactWords++
    }
    // Longest tokens first, so a short one does not take the only word a longer one fits
    tokens.indices.filter { starts[it] < 0 }.sortedByDescending { tokens[it].length }.forEach { t ->
        val w = words.indices.firstOrNull { !used[it] && words[it].value.startsWith(tokens[t]) } ?: return null
        used[w] = true
        starts[t] = words[w].range.first
    }
    val ranges = tokens.indices.map { t -> mapToOrigIndex(map, starts[t])..mapToOrigIndex(map, starts[t] + tokens[t].length - 1) }
    return TocTextMatch(ranges, exactWords)
}

/**
 * Filters the full TOC of a book ([entries], in book order) down to the entries matching
 * [query] plus their ancestors, as a tree ready to render fully expanded.
 */
internal fun buildTocFilter(
    entries: List<TocEntry>,
    query: String,
): TocFilterResult {
    val tokens = tocQueryTokens(query)
    if (tokens.isEmpty()) return TocFilterResult()

    val byId = entries.associateBy { it.id }
    val highlights = HashMap<Long, List<IntRange>>()
    val exactWords = HashMap<Long, Int>()
    val visible = HashSet<Long>()
    for (entry in entries) {
        val match = matchTocText(entry.text, tokens) ?: continue
        highlights[entry.id] = match.ranges
        exactWords[entry.id] = match.exactWords
        var current: TocEntry? = entry
        while (current != null && visible.add(current.id)) {
            current = current.parentId?.let(byId::get)
        }
    }
    if (highlights.isEmpty()) return TocFilterResult()

    val childrenByParent = entries.filter { it.id in visible }.groupBy { it.parentId }

    // Re-flag hasChildren/isLastChild so the filtered tree draws like a regular one
    val children = HashMap<Long, List<TocEntry>>()
    val ordered = ArrayList<Long>(highlights.size)

    fun walk(parentId: Long?): List<TocEntry> {
        val siblings = childrenByParent[parentId].orEmpty()
        return siblings.mapIndexed { index, entry ->
            if (entry.id in highlights) ordered += entry.id
            val kids = walk(entry.id)
            if (kids.isNotEmpty()) children[entry.id] = kids
            entry.copy(hasChildren = kids.isNotEmpty(), isLastChild = index == siblings.lastIndex)
        }
    }

    var roots = walk(null)
    // Like the regular TOC view, skip a lone wrapping root (usually the book title)
    val soleRoot = roots.singleOrNull()
    if (soleRoot != null && soleRoot.id !in highlights) {
        roots = children[soleRoot.id].orEmpty()
    }

    return TocFilterResult(
        roots = roots,
        children = children,
        matchIds = ordered,
        highlights = highlights,
        // The first entry, in display order, with the most whole-word hits
        bestMatchId = ordered.maxByOrNull { exactWords.getValue(it) },
    )
}
