package io.github.kdroidfilter.seforimapp.features.search.domain.reference

import com.kdroid.gematria.converter.toGematria
import com.kdroid.gematria.converter.toHebrewNumeral

/** Side of a Talmud daf. */
enum class Amud { A, B }

/**
 * One word of the place part of a reference: its normalized [text], and its value when it is
 * a number (`יב`, `12`), with the amud given for it (`יב:`, `יב ע״ב`, `12b`).
 */
data class ReferenceToken(
    val text: String,
    val number: Int? = null,
    val amud: Amud? = null,
)

/** One way to read a typed reference: the words naming the book, then the words naming the place. */
data class ReferenceSplit(
    val bookName: String,
    val place: List<ReferenceToken>,
)

/**
 * Parses typed references such as `חולין יב:`, `חולין דף יב ע״ב`, `בראשית א ג`, `בראשית נח` or
 * `שו״ע או״ח שכח ג`.
 *
 * The parser knows nothing about books: it lists every split of the query into a book name and
 * a place, longest book name first, and lets the resolver keep the first one naming a real book.
 */
object ReferenceParser {
    // Words announcing a place, dropped on both sides when matching (דף יב, פרק א, פרשת נח...)
    private val PLACE_WORDS =
        setOf("דף", "עמוד", "פרק", "פסוק", "סימן", "סעיף", "הלכה", "אות", "משנה", "פרשת", "פרשה", "מזמור")

    private val TEAMIM_AND_NIKUD = Regex("[֑-ׇ]")
    private val QUOTES = Regex("[\"'`׳״‘’“”]")
    private val AMUD_TOKEN = Regex("^ע[\"'׳״’”]([אב])$")
    private val DIGITS = Regex("^(\\d+)([ab])?$", RegexOption.IGNORE_CASE)

    /** Every (book name, place) reading of [query], the longest book name first. */
    fun splits(query: String): List<ReferenceSplit> {
        val words =
            query
                .replace(TEAMIM_AND_NIKUD, "")
                .replace('־', ' ')
                .replace(',', ' ')
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
        if (words.size < 2) return emptyList()
        return (words.size - 1 downTo 1).mapNotNull { bookSize ->
            val place = parsePlace(words.subList(bookSize, words.size)) ?: return@mapNotNull null
            val bookName = normalizeName(words.subList(0, bookSize).joinToString(" "))
            if (bookName.isEmpty()) null else ReferenceSplit(bookName, place)
        }
    }

    /**
     * Reads the place words: place words like `דף` are dropped, and an amud (`.`/`:` stuck to a
     * number, or `ע״א`/`ע״ב` after it) is attached to the number before it. Returns null when
     * nothing is left or an amud follows no number.
     */
    fun parsePlace(words: List<String>): List<ReferenceToken>? {
        val tokens = mutableListOf<ReferenceToken>()
        for (raw in words) {
            val amudWord = AMUD_TOKEN.find(raw)?.groupValues?.get(1)
            if (amudWord != null) {
                tokens.attachAmud(if (amudWord == "א") Amud.A else Amud.B) ?: return null
                continue
            }
            val bare = raw.replace(QUOTES, "")
            if (bare == "." || bare == ":") {
                tokens.attachAmud(if (bare == ":") Amud.B else Amud.A) ?: return null
                continue
            }
            val mark = bare.last().takeIf { it == '.' || it == ':' }
            val text = normalizeName(bare)
            if (text.isEmpty() || text in PLACE_WORDS) continue
            val digits = DIGITS.find(text)
            val token =
                when {
                    digits != null ->
                        ReferenceToken(
                            text = digits.groupValues[1],
                            number = digits.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null,
                            amud =
                                when (digits.groupValues[2].lowercase()) {
                                    "a" -> Amud.A
                                    "b" -> Amud.B
                                    else -> null
                                },
                        )
                    else -> ReferenceToken(text, hebrewNumeralValue(text))
                }
            tokens += if (mark != null && token.number != null) token.copy(amud = if (mark == ':') Amud.B else Amud.A) else token
        }
        return tokens.ifEmpty { null }
    }

    /** The words of a TOC entry compared with typed place words: normalized, without place words. */
    fun placeWords(text: String): List<String> = normalizeName(text).split(' ').filter { it.isNotEmpty() && it !in PLACE_WORDS }

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

    // The last token must be a number without an amud yet
    private fun MutableList<ReferenceToken>.attachAmud(amud: Amud): Unit? {
        val last = lastOrNull()?.takeIf { it.number != null && it.amud == null } ?: return null
        this[lastIndex] = last.copy(amud = amud)
        return Unit
    }
}
