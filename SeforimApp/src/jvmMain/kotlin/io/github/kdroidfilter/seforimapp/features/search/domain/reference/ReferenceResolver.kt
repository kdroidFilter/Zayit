package io.github.kdroidfilter.seforimapp.features.search.domain.reference

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry

/** What the resolver needs from the database. */
interface ReferenceSource {
    /** Books whose title or acronym equals [normalizedName] (see [ReferenceParser.normalizeName]). */
    suspend fun booksNamed(normalizedName: String): List<Book>

    suspend fun toc(bookId: Long): List<TocEntry>

    /** The lines under [tocEntryId], in reading order. */
    suspend fun linesOf(tocEntryId: Long): List<Line>
}

/** A typed reference resolved to a place in a book. */
data class ResolvedReference(
    val book: Book,
    val lineId: Long,
    /** The place, as shown to the user: `דף יב:`, `א, ג`... */
    val label: String,
)

/**
 * Resolves a typed reference (`חולין יב:`, `בראשית א ג`) to the books it may name.
 *
 * The locator walks the numbered TOC levels (`דף יב:`, `פרק א`, `סימן שכח`); numbers left once
 * the TOC is exhausted are looked up among the lines of the last entry reached, by their Sefaria
 * citation (`בראשית א, ג`) or, without one, by the number opening the line (`(ג)`).
 */
class ReferenceResolver(
    private val source: ReferenceSource,
) {
    /** The places [query] may point to, best first; empty when it is not a reference. */
    suspend fun resolve(
        query: String,
        maxBooks: Int = 3,
    ): List<ResolvedReference> {
        for (split in ReferenceParser.splits(query)) {
            for (name in nameVariants(split.bookName)) {
                val books = source.booksNamed(name)
                if (books.isEmpty()) continue
                // The book titled so before those merely abbreviated so, then base books first
                return books
                    .sortedWith(
                        compareByDescending<Book> { ReferenceParser.normalizeName(it.title) == name }
                            .thenByDescending { it.isBaseBook }
                            .thenBy { it.order },
                    ).take(maxBooks)
                    .mapNotNull { book -> locate(book, split.locator) }
            }
        }
        return emptyList()
    }

    // `רשי בראשית` is usually typed for `רשי על בראשית`
    private fun nameVariants(name: String): List<String> {
        val words = name.split(' ')
        if (words.size < 2 || "על" in words) return listOf(name)
        return listOf(name, (listOf(words.first(), "על") + words.drop(1)).joinToString(" "))
    }

    private suspend fun locate(
        book: Book,
        locator: ReferenceLocator,
    ): ResolvedReference? {
        val entries = source.toc(book.id)
        val children = entries.groupBy { it.parentId }
        var level = children[null].orEmpty()
        var entry: TocEntry? = null
        val path = mutableListOf<String>()
        for ((index, section) in locator.sections.withIndex()) {
            val amud = locator.amud.takeIf { index == 0 }
            val hit = findNumbered(level, children) { it.value == section && (amud == null || it.amud == amud) } ?: break
            entry = hit.first
            path += hit.second.display
            level = children[hit.first.id].orEmpty()
        }
        val reached = entry ?: return null
        val remaining = locator.sections.drop(path.size)
        val line = if (remaining.isEmpty()) null else lineWithin(book, reached, path.size, remaining)
        val lineId = line?.id ?: reached.lineId ?: return null
        val shown = if (line != null) path + remaining.map(ReferenceParser::hebrewNumeral) else path
        return ResolvedReference(book, lineId, label(shown))
    }

    // The first line under [entry] numbered [remaining], below the [depth] levels already matched
    private suspend fun lineWithin(
        book: Book,
        entry: TocEntry,
        depth: Int,
        remaining: List<Int>,
    ): Line? {
        val lines = source.linesOf(entry.id)
        val bookRef = book.heRef
        return lines.firstOrNull { line ->
            val heRef = line.heRef
            if (bookRef != null && heRef != null && heRef.startsWith("$bookRef ")) {
                val parts = heRef.removePrefix("$bookRef ").split(", ").drop(depth)
                parts.size >= remaining.size &&
                    parts.take(remaining.size).map { ReferenceParser.hebrewNumeralValue(it.trimEnd('.', ':')) } == remaining
            } else {
                remaining.size == 1 && leadingNumber(line.content) == remaining.single()
            }
        }
    }

    // The first numbered entry of [level] accepted by [matches], in TOC order, looking through
    // unnumbered entries (the book title, `חלק ראשון; ליקוטי אמרים`...) but not into other numbers
    private fun findNumbered(
        level: List<TocEntry>,
        children: Map<Long?, List<TocEntry>>,
        matches: (TocNumber) -> Boolean,
    ): Pair<TocEntry, TocNumber>? {
        for (candidate in level) {
            val number = TocNumber.of(candidate.text)
            if (number != null) {
                if (matches(number)) return candidate to number
            } else {
                findNumbered(children[candidate.id].orEmpty(), children, matches)?.let { return it }
            }
        }
        return null
    }

    private fun label(path: List<String>): String {
        val first = path.first()
        val daf = first.endsWith('.') || first.endsWith(':')
        return ((if (daf) "דף $first" else first) + path.drop(1).joinToString("") { ", $it" })
    }

    private companion object {
        private val TAGS = Regex("<[^>]*>")
        private val LEADING_NUMBER = Regex("^\\(?([א-ת]{1,4})[).]")

        fun leadingNumber(content: String): Int? =
            LEADING_NUMBER
                .find(content.replace(TAGS, "").trim())
                ?.groupValues
                ?.get(1)
                ?.let(ReferenceParser::hebrewNumeralValue)
    }
}

/** The number a TOC entry carries (`דף יב.`, `פרק א`, `סימן שכח`), with its amud for a daf. */
internal data class TocNumber(
    val value: Int,
    val amud: Amud?,
    val display: String,
) {
    companion object {
        private val DAF = Regex("^(?:דף\\s+)?([א-ת]+)([.:])$")

        fun of(text: String): TocNumber? {
            val trimmed = text.trim()
            DAF.find(trimmed)?.let { match ->
                val (numeral, mark) = match.destructured
                ReferenceParser.hebrewNumeralValue(numeral)?.let { value ->
                    return TocNumber(value, if (mark == ":") Amud.B else Amud.A, numeral + mark)
                }
            }
            return ReferenceParser
                .normalizeName(trimmed)
                .split(' ')
                .firstNotNullOfOrNull { token -> ReferenceParser.hebrewNumeralValue(token)?.let { TocNumber(it, null, token) } }
        }
    }
}
