package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.core.presentation.text.mapToOrigIndex
import io.github.kdroidfilter.seforimapp.core.presentation.text.normalizeQueryForHebrew
import io.github.kdroidfilter.seforimapp.core.presentation.text.replaceFinalsWithBase
import io.github.kdroidfilter.seforimapp.core.presentation.text.stripDiacriticsWithMap
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.TocFilterResult
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
private val WORD = Regex("[\\p{L}\\p{N}]+")

/** Splits a normalized query into its words, dropping punctuation. */
internal fun tocQueryTokens(query: String): List<String> = normalizeQueryForHebrew(query).split(NON_WORD).filter { it.isNotEmpty() }

/** A TOC title that matched every query token: highlight ranges in the original text, and how many tokens were whole words. */
internal data class TocTextMatch(
    val ranges: List<IntRange>,
    val exactWords: Int,
)

/**
 * Matches [text] against [tokens]: each token must be the start of a word of the title
 * (diacritics, geresh/gershayim and final letters ignored). A token prefers a word it equals,
 * so "סימן א" highlights "א" rather than the first word starting with alef.
 */
internal fun matchTocText(
    text: String,
    tokens: List<String>,
): TocTextMatch? {
    if (tokens.isEmpty()) return null
    val (plain, map) = stripDiacriticsWithMap(text)
    val words = WORD.findAll(replaceFinalsWithBase(plain).lowercase()).toList()

    val ranges = ArrayList<IntRange>(tokens.size)
    var exactWords = 0
    for (token in tokens) {
        val word =
            words.firstOrNull { it.value == token }?.also { exactWords++ }
                ?: words.firstOrNull { it.value.startsWith(token) }
                ?: return null
        val start = word.range.first
        ranges += mapToOrigIndex(map, start)..mapToOrigIndex(map, start + token.length - 1)
    }
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
