package io.github.kdroidfilter.seforimapp.features.bookcontent.references

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforim.htmlparser.plainTextFromHtml
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.LineReference
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A place that refers to the selected line: its line, its place in its book, and its text and the line it quotes. */
@Immutable
data class ReferencePlace(
    val line: Line,
    val place: String?,
    /** The passage's plain text. */
    val text: String,
    /** The plain text of the selected line it is linked to, which it quotes. */
    val quoted: String?,
) {
    /**
     * HTML: the passage from a little before its quote of [quoted], the quote in `<b>` — the longest run of the line's
     * words the search finds in it ([SearchEngine.highlightRanges], the search's own matching, prefixes and inflections
     * included). Worked out for the places shown only: on a whole perek the passages run to hundreds of thousands of
     * letters, and the search loads its dictionary on its first call.
     */
    fun snippet(searchEngine: SearchEngine): String = snippetAround(text, quoted?.let { quoteIn(text, it, searchEngine) })

    /** The passage's start, before its quote is known. */
    val start: String get() = snippetAround(text, null)
}

/**
 * The kinds of places that refer to a line, in their order in the pane: the codes that rule on it (עין משפט), the
 * same passage elsewhere (מסורת הש״ס), then the other references. Each of the first two is a filter tab of its own.
 */
enum class ReferenceKind(
    val tabId: Long,
) {
    EinMishpat(-1),
    MesoratHashas(-2),
    Reference(0),
    ;

    companion object {
        fun of(type: ConnectionType): ReferenceKind =
            when (type) {
                ConnectionType.EIN_MISHPAT -> EinMishpat
                ConnectionType.MESORAT_HASHAS -> MesoratHashas
                else -> Reference
            }
    }
}

/** The places of one book, of one kind, that refer to the selected line. */
@Immutable
data class ReferenceBook(
    val bookId: Long,
    val title: String,
    val kind: ReferenceKind,
    val places: List<ReferencePlace>,
    /** The filter tab the book falls under, null when the places need no tabs. */
    val tabId: Long?,
)

/** The places that refer to the selected lines (their cross-reference links), a book at a time. */
@Immutable
data class LineReferences(
    val books: List<ReferenceBook>,
    /** The kinds with a tab of their own ([ReferenceKind.tabId]), before [categoryTabs]. */
    val kindTabs: List<ReferenceKind>,
    /** The other references' tabs by category, id to title, the most books first. */
    val categoryTabs: List<Pair<Long, String>>,
) {
    val placeCount: Int get() = books.sumOf { it.places.size }

    companion object {
        val Empty = LineReferences(emptyList(), emptyList(), emptyList())
    }
}

/** The places a line pane lists: the places that refer to the line, or the ones it comments on. */
enum class LinePlaces {
    /** Its cross-references: the books that refer the most first. */
    References,

    /** The SOURCE view: in its own order, declared bases first. */
    Sources,
}

/** The [kind] places of [lines], each with the line it is linked to (see [ReferencePlace.snippet]). */
suspend fun loadLinePlaces(
    kind: LinePlaces,
    repository: SeforimRepository,
    lines: Collection<Line>,
): LineReferences {
    val ids = lines.map { it.id }
    val references =
        when (kind) {
            LinePlaces.References -> repository.getReferencesForLines(ids)
            LinePlaces.Sources -> repository.getSourcesForLines(ids)
        }
    if (references.isEmpty()) return LineReferences.Empty
    val paths = references.map { it.categoryId }.distinct().associateWith { categoryPath(repository, it) }
    return withContext(Dispatchers.Default) {
        val quoted = lines.associate { it.id to plainTextFromHtml(it.content) }
        groupReferences(references, paths, byCount = kind == LinePlaces.References) { quoted[it.fromLineId] }
    }
}

/**
 * Groups [references] by kind and book: the kinds in their order, then [byCount] the books that refer the most first
 * (then in catalog order), else in the order of [references]; each book's places in its order. [paths] gives each
 * category's path from its root, for the other references' filter tabs; [quotedOf] the text of the line a reference
 * starts from.
 */
internal fun groupReferences(
    references: List<LineReference>,
    paths: Map<Long, List<Category>>,
    byCount: Boolean = true,
    quotedOf: (LineReference) -> String?,
): LineReferences {
    val grouped = references.groupBy { ReferenceKind.of(it.connectionType) to it.line.bookId }.entries
    val byBook =
        if (byCount) {
            grouped.sortedWith(
                compareBy<Map.Entry<Pair<ReferenceKind, Long>, List<LineReference>>> { it.key.first }
                    .thenByDescending { it.value.size }
                    .thenBy { it.value.first().bookOrderIndex }
                    .thenBy { it.value.first().bookTitle },
            )
        } else {
            // groupBy keeps the order of the first place of each book; the sort is stable
            grouped.sortedBy { it.key.first }
        }
    val others = byBook.filter { it.key.first == ReferenceKind.Reference }
    val categoryOf = others.map { it.key }.zip(filterTabs(others.map { paths[it.value.first().categoryId].orEmpty() })).toMap()
    val categoryTabs =
        categoryOf.values
            .filterNotNull()
            .groupingBy { it.id to it.title }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    val kindTabs = byBook.map { it.key.first }.distinct().filter { it != ReferenceKind.Reference }
    val withTabs = kindTabs.size + categoryTabs.size > 1
    val books =
        byBook.map { (key, refs) ->
            val first = refs.first()
            ReferenceBook(
                bookId = first.line.bookId,
                title = first.bookTitle,
                kind = key.first,
                places =
                    refs.sortedBy { it.line.lineIndex }.map { ref ->
                        ReferencePlace(ref.line, placeOf(ref.line, ref.bookTitle), plainTextFromHtml(ref.line.content), quotedOf(ref))
                    },
                tabId =
                    when {
                        !withTabs -> null
                        key.first != ReferenceKind.Reference -> key.first.tabId
                        else -> categoryOf[key]?.id
                    },
            )
        }
    return if (withTabs) LineReferences(books, kindTabs, categoryTabs) else LineReferences(books, emptyList(), emptyList())
}

/**
 * The filter tab of each book, from its category path: the books' top categories, and a category holding more than
 * two thirds of them split into its children, as long as it splits (the commentators on the Torah all sit under
 * תנ״ך: their ראשונים and אחרונים tell more). A book right in a split category keeps it.
 */
internal fun filterTabs(paths: List<List<Category>>): List<Category?> {
    val depth = IntArray(paths.size)
    val tabs = paths.map { it.firstOrNull() }.toMutableList()
    while (true) {
        val dominant =
            tabs.indices
                .filter { tabs[it] != null }
                .groupBy { tabs[it]!!.id }
                .values
                .maxByOrNull { it.size } ?: break
        if (dominant.size * 3 <= paths.size * 2) break
        val deeper = dominant.filter { paths[it].size > depth[it] + 1 }
        if (deeper.isEmpty()) break
        deeper.forEach {
            depth[it]++
            tabs[it] = paths[it][depth[it]]
        }
    }
    return tabs
}

/** The place of [line] in its book, as its reference reads without the book's title ("בראשית, יב, ב"). */
internal fun placeOf(
    line: Line,
    bookTitle: String,
): String? =
    line.heRef
        ?.removePrefix(bookTitle)
        ?.trimStart(',', ' ')
        ?.replace(SPACES, " ")
        ?.trim()
        ?.ifBlank { null }

/** [text] from a little before [quote] (cut at a word), the quote in `<b>`; the text's start when there is none. */
internal fun snippetAround(
    text: String,
    quote: IntRange?,
): String {
    if (quote == null) return text.take(SNIPPET_CHARS).escapeHtml()
    var start = (quote.first - CONTEXT_CHARS).coerceAtLeast(0)
    if (start > 0) start = text.indexOf(' ', start).takeIf { it in 0 until quote.first }?.plus(1) ?: quote.first
    val end = (quote.last + 1 + SNIPPET_CHARS).coerceAtMost(text.length)
    return buildString {
        if (start > 0) append("…")
        append(text.substring(start, quote.first).escapeHtml())
        append("<b>").append(text.substring(quote.first, quote.last + 1).escapeHtml()).append("</b>")
        append(text.substring(quote.last + 1, end).escapeHtml())
    }
}

/**
 * The quote of [quoted] in [text]: the longest run of its words the search finds there. A lone short word (את, על)
 * is no quote.
 */
private fun quoteIn(
    text: String,
    quoted: String,
    searchEngine: SearchEngine,
): IntRange? =
    searchEngine
        .highlightRanges(text, quoted)
        .filter { range -> ' ' in text.substring(range) || range.last - range.first + 1 >= MIN_QUOTE_CHARS }
        .maxByOrNull { it.last - it.first }

private suspend fun categoryPath(
    repository: SeforimRepository,
    categoryId: Long,
): List<Category> {
    val path = ArrayList<Category>()
    var id: Long? = categoryId
    while (id != null && path.size < MAX_DEPTH) {
        val category = repository.getCategory(id) ?: break
        path += category
        id = category.parentId
    }
    return path.asReversed()
}

private fun String.escapeHtml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private val SPACES = Regex("\\s+")

// The passage kept before the quote, and after it (the pane shows two to six lines of it)
private const val CONTEXT_CHARS = 40
private const val SNIPPET_CHARS = 1200
private const val MIN_QUOTE_CHARS = 4
private const val MAX_DEPTH = 32
