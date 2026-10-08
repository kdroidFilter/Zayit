package io.github.kdroidfilter.seforimapp.features.search.domain.reference

import com.kdroid.gematria.converter.toGematria
import com.kdroid.gematria.converter.toHebrewNumeral

/** Side of a Talmud daf. */
enum class Amud { A, B }

/**
 * Where to go inside a book: the numbered sections from outermost to innermost
 * (daf/chapter, then segment/verse...), and the amud of the first one when given.
 */
data class ReferenceLocator(
    val sections: List<Int>,
    val amud: Amud? = null,
)

/** One way to read a typed reference: the words naming the book, then the locator. */
data class ReferenceSplit(
    val bookName: String,
    val locator: ReferenceLocator,
)

/**
 * Parses typed references such as `חולין יב:`, `חולין דף יב ע״ב`, `בראשית א ג` or `שו״ע או״ח שכח ג`.
 *
 * The parser knows nothing about books: it lists every split of the query into a book name and
 * a trailing locator, longest book name first, and lets the resolver keep the first one naming a
 * real book.
 */
object ReferenceParser {
    // Words that may introduce a number in a locator (דף יב, פרק א, סימן שכח...)
    private val LOCATOR_WORDS = setOf("דף", "עמוד", "פרק", "פסוק", "סימן", "סעיף", "הלכה", "אות", "משנה")

    private val TEAMIM_AND_NIKUD = Regex("[֑-ׇ]")
    private val QUOTES = Regex("[\"'`׳״‘’“”]")
    private val AMUD_TOKEN = Regex("^ע[\"'׳״’”]?([אב])$")
    private val DIGITS = Regex("^(\\d+)([ab])?$", RegexOption.IGNORE_CASE)

    /** Every (book name, locator) reading of [query], the longest book name first. */
    fun splits(query: String): List<ReferenceSplit> {
        val tokens = tokenize(query)
        if (tokens.size < 2) return emptyList()
        return (tokens.size - 1 downTo 1).mapNotNull { bookSize ->
            val locator = parseLocator(tokens.subList(bookSize, tokens.size)) ?: return@mapNotNull null
            val bookName = normalizeName(tokens.subList(0, bookSize).joinToString(" "))
            if (bookName.isEmpty()) null else ReferenceSplit(bookName, locator)
        }
    }

    /**
     * Parses a run of locator tokens, or returns null when any token is not part of a locator.
     * A trailing `.` or `:` on the first number, or a separate `ע״א` / `ע״ב`, gives the amud.
     */
    fun parseLocator(tokens: List<String>): ReferenceLocator? {
        val sections = mutableListOf<Int>()
        var amud: Amud? = null
        for (raw in tokens) {
            // ע״א / ע״ב; without quotes, עא and עב are the numbers 71 and 72
            val amudMatch = AMUD_TOKEN.find(raw)?.takeIf { raw.hasQuote() }
            if (amudMatch != null) {
                if (sections.size != 1 || amud != null) return null
                amud = if (amudMatch.groupValues[1] == "א") Amud.A else Amud.B
                continue
            }
            val bare = raw.replace(QUOTES, "")
            when {
                bare.isEmpty() || bare in LOCATOR_WORDS -> continue
                bare == "." || bare == ":" -> {
                    if (sections.size != 1 || amud != null) return null
                    amud = bare.toAmud()
                }
                else -> {
                    val marker = bare.last().takeIf { it == '.' || it == ':' }
                    val number = parseNumber(bare.trimEnd('.', ':'), sections.isEmpty()) ?: return null
                    sections += number.first
                    val numberAmud = marker?.toString()?.toAmud() ?: number.second
                    if (numberAmud != null) {
                        if (sections.size != 1 || amud != null) return null
                        amud = numberAmud
                    }
                }
            }
        }
        return if (sections.isEmpty()) null else ReferenceLocator(sections, amud)
    }

    /** Value of a Hebrew numeral written canonically (`יב`, `טו`, `שכח`), else null. */
    fun hebrewNumeralValue(token: String): Int? {
        if (token.isEmpty() || token.any { it !in 'א'..'ת' }) return null
        val value = token.toGematria()
        return value.takeIf { it > 0 && it.toHebrewNumeral(false) == token }
    }

    /** [value] as cited (`יב`, `טו`, `שכח`). */
    fun hebrewNumeral(value: Int): String = value.toHebrewNumeral(false)

    /** Book names compare without nikud, quotes, punctuation or extra spaces. */
    fun normalizeName(name: String): String =
        name
            .replace(TEAMIM_AND_NIKUD, "")
            .replace(QUOTES, "")
            .replace(Regex("[,.:;־-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun tokenize(query: String): List<String> =
        query
            .replace(TEAMIM_AND_NIKUD, "")
            .replace('־', ' ')
            .replace(',', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

    // A number, with the amud an Arabic daf may carry (12b); only the first section can have one
    private fun parseNumber(
        token: String,
        first: Boolean,
    ): Pair<Int, Amud?>? {
        DIGITS.find(token)?.let { match ->
            val value = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val side = match.groupValues[2].lowercase()
            if (side.isNotEmpty() && !first) return null
            return value to
                when (side) {
                    "a" -> Amud.A
                    "b" -> Amud.B
                    else -> null
                }
        }
        return hebrewNumeralValue(token)?.let { it to null }
    }

    private fun String.toAmud(): Amud = if (this == ":") Amud.B else Amud.A

    private fun String.hasQuote(): Boolean = QUOTES.containsMatchIn(this)
}
