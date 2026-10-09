package io.github.kdroidfilter.seforimapp.features.search.domain.reference

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line

/** An entry of a book's TOC or of one of its alternative TOCs (parashot, chapter names...). */
data class TocNode(
    val id: Long,
    val parentId: Long?,
    val text: String,
    val lineId: Long?,
)

/** What the resolver needs from the database. */
interface ReferenceSource {
    /** Books whose title or acronym equals [normalizedName] (see [ReferenceParser.normalizeName]). */
    suspend fun booksNamed(normalizedName: String): List<Book>

    suspend fun toc(bookId: Long): List<TocNode>

    /** The book's alternative TOCs, one list of entries per structure. */
    suspend fun altTocs(bookId: Long): List<List<TocNode>>

    /** The lines under the main-TOC entry [tocEntryId], in reading order. */
    suspend fun linesOf(tocEntryId: Long): List<Line>
}

/** A typed reference resolved to a place in a book. */
data class ResolvedReference(
    val book: Book,
    val lineId: Long,
    /** The place, as shown to the user: `דף יב:`, `א, ג`, `נח`... */
    val label: String,
)

/**
 * Resolves a typed reference (`חולין יב:`, `בראשית א ג`, `בראשית נח`) to the books it may name.
 *
 * The place words walk down a TOC: each level matches an entry by its number (`דף יב:`,
 * `פרק א`) or by its name (`השוחט`, `לך לך`). The main TOC is tried first, then the alternative
 * ones. Numbers left once the main TOC is exhausted are looked up among the lines of the entry
 * reached, by their Sefaria citation (`בראשית א, ג`) or the number opening the line (`(ג)`).
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
                return rank(books, name).take(maxBooks).mapNotNull { book -> locate(book, split.place) }
            }
        }
        return emptyList()
    }

    // The book titled [name] first, then those whose title holds most of its words (`ירושלמי ברכות`
    // names תלמוד ירושלמי ברכות before ברכות), base books first. When a book is titled exactly so,
    // only other base books (the Mishna of a tractate...) are offered beside it, not commentaries.
    private fun rank(
        books: List<Book>,
        name: String,
    ): List<Book> {
        val words = name.split(' ').toSet()

        fun Book.titled() = ReferenceParser.normalizeName(title) == name

        fun Book.sharedWords() = ReferenceParser.normalizeName(title).split(' ').count { it in words }
        val ranked =
            books.sortedWith(
                compareByDescending<Book> { it.titled() }
                    .thenByDescending { it.sharedWords() }
                    .thenByDescending { it.isBaseBook }
                    .thenBy { it.order },
            )
        return if (ranked.first().titled()) ranked.filter { it.titled() || it.isBaseBook } else ranked
    }

    // `רשי בראשית` is usually typed for `רשי על בראשית`
    private fun nameVariants(name: String): List<String> {
        val words = name.split(' ')
        if (words.size < 2 || "על" in words) return listOf(name)
        return listOf(name, (listOf(words.first(), "על") + words.drop(1)).joinToString(" "))
    }

    private suspend fun locate(
        book: Book,
        place: List<ReferenceToken>,
    ): ResolvedReference? {
        val main = walk(source.toc(book.id), place)
        if (main != null && main.consumed == place.size) return resolved(book, main)
        val alt = source.altTocs(book.id).firstNotNullOfOrNull { tree -> walk(tree, place)?.takeIf { it.consumed == place.size } }
        if (alt != null) return resolved(book, alt)
        // Numbers below the main TOC: a verse, a segment of a daf...
        val remaining = place.drop(main?.consumed ?: return null)
        if (remaining.any { it.number == null }) return null
        val line = lineWithin(book, main.node, main.path.size, remaining.map { it.number!! })
        if (line != null) {
            return ResolvedReference(book, line.id, label(main.path + remaining.map { ReferenceParser.hebrewNumeral(it.number!!) }))
        }
        return resolved(book, main)
    }

    private fun resolved(
        book: Book,
        match: Match,
    ): ResolvedReference? = match.node.lineId?.let { ResolvedReference(book, it, label(match.path)) }

    private class Match(
        val node: TocNode,
        val consumed: Int,
        val path: List<String>,
    )

    // Matches the place words level by level, as deep as they go
    private fun walk(
        entries: List<TocNode>,
        place: List<ReferenceToken>,
    ): Match? {
        val children = entries.groupBy { it.parentId }
        var level = children[null].orEmpty()
        var position = 0
        var match: Match? = null
        while (position < place.size) {
            val hit = findEntry(level, children, place, position) ?: break
            position += hit.consumed
            match = Match(hit.node, position, match?.path.orEmpty() + hit.path)
            level = children[hit.node.id].orEmpty()
        }
        return match
    }

    // The first entry of [level] matching the words at [position], in TOC order, looking through
    // unnumbered entries (the book title, `חלק ראשון; ליקוטי אמרים`...) but not into other numbers
    private fun findEntry(
        level: List<TocNode>,
        children: Map<Long?, List<TocNode>>,
        place: List<ReferenceToken>,
        position: Int,
    ): Match? {
        for (candidate in level) {
            matchEntry(candidate, place, position)?.let { return it }
            if (TocNumber.of(candidate.text) == null) {
                findEntry(children[candidate.id].orEmpty(), children, place, position)?.let { return it }
            }
        }
        return null
    }

    // By number (`דף יב:` for יב:) or by name (`לך לך`, `יום ה`); [Match.consumed] counts words
    private fun matchEntry(
        entry: TocNode,
        place: List<ReferenceToken>,
        position: Int,
    ): Match? {
        val token = place[position]
        val number = TocNumber.of(entry.text)
        val words = ReferenceParser.placeWords(entry.text)
        if (number != null && token.number == number.value && (token.amud == null || token.amud == number.amud)) {
            // `יב.` for a daf, `א` for `פרק א`, but the whole name for `יום ה`
            val display = if (words.size == 1) number.display else entry.text.trim()
            return Match(entry, 1, listOf(display))
        }
        // A bare number (`דף יב.`, `פרק א`) matches by number only, amud included
        if (number != null && words.size == 1) return null
        if (words.isEmpty() || position + words.size > place.size) return null
        val typed = place.subList(position, position + words.size).map { it.text }
        return if (typed == words) Match(entry, words.size, listOf(entry.text.trim())) else null
    }

    // The first line under [entry] numbered [remaining], below the [depth] levels already matched
    private suspend fun lineWithin(
        book: Book,
        entry: TocNode,
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
