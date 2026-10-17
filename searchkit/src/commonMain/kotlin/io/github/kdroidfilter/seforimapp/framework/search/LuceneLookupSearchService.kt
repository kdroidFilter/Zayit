package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.search.index.BoolBuilder
import io.github.kdroidfilter.seforimlibrary.search.index.IndexDoc
import io.github.kdroidfilter.seforimlibrary.search.index.IndexQuery
import io.github.kdroidfilter.seforimlibrary.search.index.LuceneIndex
import io.github.kdroidfilter.seforimlibrary.search.index.Occur
import io.github.kdroidfilter.seforimlibrary.search.index.SortField
import io.github.kdroidfilter.seforimlibrary.search.index.SortType

/**
 * Book, author and table-of-contents lookup over the lookup index (seforim.db.lookup.lucene), with acronym-aware
 * ranking of the books: [index] is that index (opened with its query cache: [openLuceneIndex]), read on a JVM like the
 * others.
 */
class LuceneLookupSearchService(
    private val index: LuceneIndex,
    private val acronymCache: AcronymFrequencyCache? = null,
) {
    private val authorSort = listOf(SortField("book_count", SortType.LONG, reverse = true))

    private val bookSort =
        listOf(
            SortField("is_base_book", SortType.INT, reverse = true),
            SortField("order_index", SortType.INT),
            SortField("book_id", SortType.LONG),
        )

    data class TocHit(
        val tocId: Long,
        val bookId: Long,
        val bookTitle: String,
        val text: String,
        val level: Int,
        val score: Float,
    )

    /** An author of the lookup index, with the number of its books. */
    data class AuthorHit(
        val id: Long,
        val name: String,
        val bookCount: Int,
        // The alias the query was found by, when not the name (חפץ חיים); set by the caller
        val alias: String? = null,
    )

    data class BookHit(
        val id: Long,
        val categoryId: Long,
        val title: String,
        val isBaseBook: Boolean,
        val orderIndex: Int,
    )

    data class ScoredBookHit(
        val id: Long,
        val categoryId: Long,
        val title: String,
        val isBaseBook: Boolean,
        val orderIndex: Int,
        val score: Double,
        val matchedAcronyms: List<String>,
        // The query is one of the book's acronyms, whole (שוע for שולחן ערוך)
        val exactAcronym: Boolean = false,
    ) {
        /** A lightweight [Book] for display; load the full one by [id] when opening it. */
        fun toBook(): Book =
            Book(
                id = id,
                categoryId = categoryId,
                sourceId = 0,
                title = title,
                order = orderIndex.toFloat(),
                isBaseBook = isBaseBook,
            )
    }

    fun close() {
        runCatching { index.close() }
    }

    // Documents of [type] whose words start with each of [tokens], sorted by [sort]
    private fun prefixSearch(
        type: String,
        tokens: List<String>,
        limit: Int,
        sort: List<SortField>,
        fields: Set<String>,
    ): List<IndexDoc> {
        val query =
            BoolBuilder()
                .add(IndexQuery.Term("type", type), Occur.FILTER)
                .apply { tokens.forEach { t -> add(IndexQuery.Prefix("q", t), Occur.MUST) } }
                .build()
        return index.search(query, limit, fields, sort).docs
    }

    fun searchBooksPrefix(
        raw: String,
        limit: Int = 20,
    ): List<BookHit> {
        val q = normalizeHebrew(raw)
        if (q.isBlank()) return emptyList()
        val tokens = q.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return prefixSearch("book", tokens, limit, bookSort, BOOK_FIELDS).map { doc ->
            BookHit(
                id = doc.longs.getValue("book_id"),
                categoryId = doc.longs.getValue("category_id"),
                title = doc.strings.getValue("book_title"),
                isBaseBook = doc.longs["is_base_book"]?.toInt() == 1,
                orderIndex = doc.longs["order_index"]?.toInt() ?: Int.MAX_VALUE,
            )
        }
    }

    /**
     * Authors whose name or alias (רעק״א, ראב״ע...) starts with the typed words, those with the
     * most books first. Empty below [MIN_BOOK_QUERY_LENGTH] characters, or with an index built
     * before authors were indexed.
     */
    fun suggestAuthors(
        raw: String,
        limit: Int,
    ): List<AuthorHit> {
        // הרב is added on display only: a query typed as shown finds the bare name, else a stored one with it
        val bare = AuthorNames.withoutHonorific(raw)
        if (bare != raw && bare.isNotBlank()) authorsFor(bare, limit).takeIf { it.isNotEmpty() }?.let { return it }
        return authorsFor(raw, limit)
    }

    private fun authorsFor(
        raw: String,
        limit: Int,
    ): List<AuthorHit> {
        val q = normalizeHebrew(raw)
        if (q.length < MIN_BOOK_QUERY_LENGTH) return emptyList()
        // Aliases are indexed whole (רעקא) and split at their gershayim (רעק + א): the split reading
        // only when the whole one finds nobody, as its one-letter pieces match many names
        val whole = authorsMatching(q, limit)
        if (whole.isNotEmpty()) return whole
        val split = normalizeHebrew(raw.replace(Regex("[\"'״׳]"), " "))
        return if (split == q) whole else authorsMatching(split, limit)
    }

    private fun authorsMatching(
        normalized: String,
        limit: Int,
    ): List<AuthorHit> {
        val tokens = normalized.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return prefixSearch("author", tokens, limit, authorSort, AUTHOR_FIELDS).map { doc ->
            AuthorHit(
                id = doc.longs.getValue("author_id"),
                name = doc.strings.getValue("author_name"),
                bookCount = doc.longs.getValue("book_count").toInt(),
            )
        }
    }

    fun searchTocPrefix(
        raw: String,
        limit: Int = 20,
    ): List<TocHit> {
        val q = normalizeHebrew(raw)
        if (q.isBlank()) return emptyList()
        val tokens = q.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return prefixSearch("toc", tokens, limit, bookSort, TOC_FIELDS).map { doc ->
            TocHit(
                tocId = doc.longs.getValue("toc_id"),
                bookId = doc.longs.getValue("book_id"),
                bookTitle = doc.strings.getValue("book_title"),
                text = doc.strings.getValue("toc_text"),
                level = doc.longs.getValue("toc_level").toInt(),
                score = doc.score,
            )
        }
    }

    /**
     * Book suggestions for a typed query, as in the home search bar: diacritics and geresh
     * dropped, acronym-aware ranking. Empty below [MIN_BOOK_QUERY_LENGTH] characters.
     */
    fun suggestBooks(
        raw: String,
        limit: Int,
    ): List<ScoredBookHit> {
        val q = raw.trim()
        if (q.length < MIN_BOOK_QUERY_LENGTH) return emptyList()
        return searchBooksWithScoring(sanitizeHebrewForAcronym(q), limit)
    }

    /**
     * Search books with enhanced acronym scoring.
     * Requires acronymCache to be initialized.
     */
    fun searchBooksWithScoring(
        raw: String,
        limit: Int = 20,
    ): List<ScoredBookHit> {
        if (acronymCache == null) {
            // Fallback to simple search if cache not available
            return searchBooksPrefix(raw, limit).map { hit ->
                ScoredBookHit(
                    id = hit.id,
                    categoryId = hit.categoryId,
                    title = hit.title,
                    isBaseBook = hit.isBaseBook,
                    orderIndex = hit.orderIndex,
                    score = 100.0,
                    matchedAcronyms = emptyList(),
                )
            }
        }

        // 1. Get base results from Lucene (existing logic), without those found by reading one title
        // or acronym word twice (`בבא ב`: תוספתא בבא קמא). An acronym may be stored as one word
        // (`דבהי ב` for דבהיב). When none is left, all are kept: the index misses some acronyms'
        // books and finds their neighbours
        val luceneHits = searchBooksPrefix(raw, limit * 3) // Get more for re-ranking
        val typedJoined = normalizeHebrew(raw).replace(" ", "")
        val baseHits =
            luceneHits
                .filter { hit ->
                    typedAsWordsOf(raw, hit.title) ||
                        getAcronymsForBook(hit.title).any { acronym ->
                            typedAsWordsOf(raw, acronym) || normalizeHebrew(acronym).replace(" ", "").startsWith(typedJoined)
                        }
                }.ifEmpty { luceneHits }

        // 2. Normalize query for acronym matching
        val normalizedQuery = normalizeHebrew(raw)

        // 3. Calculate enhanced scores
        val scoredHits =
            baseHits.map { hit ->
                val score =
                    calculateEnhancedScore(
                        hit = hit,
                        query = normalizedQuery,
                    )

                ScoredBookHit(
                    id = hit.id,
                    categoryId = hit.categoryId,
                    title = hit.title,
                    isBaseBook = hit.isBaseBook,
                    orderIndex = hit.orderIndex,
                    score = score,
                    matchedAcronyms = findMatchedAcronyms(hit.title, normalizedQuery),
                    exactAcronym = hasExactAcronymMatch(hit.title, normalizedQuery),
                )
            }

        // 4. Re-sort: titles equal to the query, then titles beginning with it, then the rest; the
        // score only orders within a tier, as its base-book boost reads orderIndex, a rank within the
        // category (תוספתא בבא קמא, alone in its own, came before בבא קמא for `בבא ק`). A short query
        // or one that is a book's acronym (`חו"מ`, `הטור`) is no title being typed: no prefix tier.
        val titleTyped = normalizedQuery.length > 3 && scoredHits.none { it.exactAcronym }
        val order = compareByDescending<ScoredBookHit> { titleTier(it.title, normalizedQuery, titleTyped) }.thenByDescending { it.score }
        return scoredHits.sortedWith(order).take(limit)
    }

    private fun titleTier(
        title: String,
        query: String,
        titleTyped: Boolean,
    ): Int {
        val normalized = normalizeHebrew(title)
        return when {
            normalized == query -> 2
            titleTyped && normalized.startsWith(query) -> 1
            else -> 0
        }
    }

    private fun calculateEnhancedScore(
        hit: BookHit,
        query: String,
    ): Double {
        var score = 100.0

        // 1. Title match score (highest priority)
        val titleMatchScore = calculateTitleMatchScore(hit.title, query)
        score += titleMatchScore

        // 2. Acronym match score (with frequency boost) - uses book title for matching
        val acronymMatchScore = calculateAcronymMatchScore(hit.title, query)
        score += acronymMatchScore

        // 3. Short acronym boost - if query is 2-3 chars and matches a known acronym exactly
        if (query.length in 2..3 && hasExactAcronymMatch(hit.title, query)) {
            score *= 50.0 // Massive boost for known short acronyms
        }

        // 4. BASE BOOKS BOOST (CRITICAL - 50x for priority 1)
        val baseBookBoost = calculateBaseBookBoost(hit)
        score *= baseBookBoost

        // 5. Book ID boost - lower IDs get higher priority (MULTIPLICATIVE)
        // Apply AFTER other multipliers to ensure low IDs win ties
        val idMultiplier = calculateIdBoost(hit.id)
        score *= idMultiplier

        return score
    }

    private fun calculateTitleMatchScore(
        title: String,
        query: String,
    ): Double {
        val titleNorm = normalizeHebrew(title)
        val queryNorm = normalizeHebrew(query)

        return when {
            titleNorm == queryNorm -> 10000.0 // Exact match - MASSIVELY boosted
            titleNorm.startsWith(queryNorm) -> 1000.0 // Prefix match
            titleNorm.contains(queryNorm) -> 200.0 // Contains
            else -> 50.0 // Partial match
        }
    }

    private fun calculateAcronymMatchScore(
        bookTitle: String,
        query: String,
    ): Double {
        if (acronymCache == null) return 0.0

        // Get all acronyms for this book from acronymizer.db (by title)
        val acronyms = getAcronymsForBook(bookTitle)

        var bestScore = 0.0

        for (acronym in acronyms) {
            val acronymNorm = normalizeHebrew(acronym)
            val queryNorm = normalizeHebrew(query)

            // Check if this acronym matches
            val matchScore =
                when {
                    acronymNorm == queryNorm -> 500.0 // Exact acronym match
                    acronymNorm.startsWith(queryNorm) -> 300.0 // Prefix match
                    acronymNorm.contains(queryNorm) -> 150.0 // Contains
                    else -> 0.0
                }

            if (matchScore > 0) {
                // Apply rarity bonus based on frequency
                val frequency = acronymCache.getFrequency(acronym)
                val rarityBonus =
                    when (frequency) {
                        1 -> 200.0 // Unique acronym
                        in 2..3 -> 150.0
                        in 4..10 -> 100.0
                        in 11..50 -> 50.0
                        else -> 20.0 // Very common
                    }

                val totalScore = matchScore + rarityBonus
                if (totalScore > bestScore) {
                    bestScore = totalScore
                }
            }
        }

        return bestScore
    }

    private fun calculateBaseBookBoost(hit: BookHit): Double {
        if (!hit.isBaseBook) return 1.0

        val orderIndex = hit.orderIndex

        // Aggressive exponential boost for base books
        return when {
            orderIndex <= 1 -> 50.0 // Top priority
            orderIndex <= 5 -> 40.0 - (orderIndex - 1) * 3.0
            orderIndex <= 10 -> 25.0 - (orderIndex - 5) * 2.0
            orderIndex <= 30 -> 15.0 - (orderIndex - 10) * 0.25
            orderIndex <= 60 -> 10.0 - (orderIndex - 30) * 0.15
            orderIndex <= 100 -> 5.0 - (orderIndex - 60) * 0.05
            else -> kotlin.math.max(2.0, 5.0 - (orderIndex - 100) * 0.02)
        }
    }

    /**
     * Calculate boost based on book ID - lower IDs get higher priority.
     * This ensures books earlier in the hierarchy appear first in ties.
     * Uses continuous formula for smooth, granular differentiation.
     */
    private fun calculateIdBoost(bookId: Long): Double {
        // Continuous inverse formula: lower IDs get exponentially higher boost
        // ID 1 gets ~10x, ID 100 gets ~5x, ID 1000 gets ~2x, ID 10000 gets ~1.5x
        val id = bookId.toDouble().coerceAtLeast(1.0)

        // Formula: 1 + (9000 / (id + 1000))
        // This gives smooth decay where even small ID differences matter
        return 1.0 + (9000.0 / (id + 1000.0))
    }

    /**
     * Check if query exactly matches a known acronym for this book.
     * Uses book_acronym table to verify it's a real acronym, not just initial letters.
     */
    private fun hasExactAcronymMatch(
        bookTitle: String,
        query: String,
    ): Boolean {
        if (acronymCache == null) return false

        val acronyms = getAcronymsForBook(bookTitle)
        val queryNorm = normalizeHebrew(query)

        // Check if query exactly matches any acronym
        return acronyms.any { acronym ->
            normalizeHebrew(acronym) == queryNorm
        }
    }

    private fun findMatchedAcronyms(
        bookTitle: String,
        query: String,
    ): List<String> {
        if (acronymCache == null) return emptyList()

        val acronyms = getAcronymsForBook(bookTitle)
        val queryNorm = normalizeHebrew(query)

        return acronyms.filter { acronym ->
            val acronymNorm = normalizeHebrew(acronym)
            acronymNorm == queryNorm ||
                acronymNorm.startsWith(queryNorm) ||
                acronymNorm.contains(queryNorm)
        }
    }

    private fun getAcronymsForBook(bookTitle: String): List<String> = acronymCache?.getAcronymsForBook(bookTitle) ?: emptyList()

    /**
     * Whether each word of [typed] begins a different word of [name], as the index matches them but
     * for one word read twice: the index takes each typed word as a prefix of any word, so `בבא ב`
     * finds תוספתא בבא קמא and רב בבאר שבע, both words on the same בבא(ר). Words compare as the
     * index normalizes them.
     */
    fun typedAsWordsOf(
        typed: String,
        name: String,
    ): Boolean {
        val words = normalizeHebrew(typed).split(' ').filter { it.isNotEmpty() }
        val nameWords = normalizeHebrew(name).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > nameWords.size) return false
        val used = BooleanArray(nameWords.size)

        // Backtracking: a short word must not take the only word a longer one could begin
        fun assign(i: Int): Boolean {
            if (i == words.size) return true
            for (j in nameWords.indices) {
                if (used[j] || !nameWords[j].startsWith(words[i])) continue
                used[j] = true
                if (assign(i + 1)) return true
                used[j] = false
            }
            return false
        }
        return assign(0)
    }

    private fun normalizeHebrew(input: String): String {
        if (input.isBlank()) return ""
        var s = input.trim()

        // Remove diacritics (nikud, cantillation marks)
        s = s.replace("[\u0591-\u05AF]".toRegex(), "")
        s = s.replace("[\u05B0\u05B1\u05B2\u05B3\u05B4\u05B5\u05B6\u05B7\u05B8\u05B9\u05BB\u05BC\u05BD\u05C1\u05C2\u05C7]".toRegex(), "")

        // Remove Hebrew punctuation
        s = s.replace('\u05BE', ' ') // Maqaf
        s = s.replace("\u05F4", "") // Gershayim
        s = s.replace("\u05F3", "") // Geresh

        // Remove ALL quote/apostrophe characters (ASCII and Unicode)
        s = s.replace("\"", "") // ASCII double quote
        s = s.replace("'", "") // ASCII single quote
        s = s.replace("`", "") // Backtick
        s = s.replace("'", "") // Right single quote
        s = s.replace("'", "") // Left single quote
        s =
            s.replace(
                """, "")        // Left double quote
        s = s.replace(""",
                "",
            ) // Right double quote
        s = s.replace("״", "") // Hebrew gershayim
        s = s.replace("׳", "") // Hebrew geresh

        // Remove punctuation and special characters
        s = s.replace("-", "") // ASCII hyphen
        s = s.replace("־", "") // Hebrew maqaf
        s = s.replace(",", "") // Comma
        s = s.replace(".", "") // Period
        s = s.replace(":", "") // Colon
        s = s.replace(";", "") // Semicolon

        // Normalize final letters to base forms
        s =
            s
                .replace('\u05DA', '\u05DB') // ך -> כ
                .replace('\u05DD', '\u05DE') // ם -> מ
                .replace('\u05DF', '\u05E0') // ן -> נ
                .replace('\u05E3', '\u05E4') // ף -> פ
                .replace('\u05E5', '\u05E6') // ץ -> צ

        // Collapse multiple spaces into one
        s = s.replace("\\s+".toRegex(), " ").trim()

        return s
    }
}

/** Minimum length of a query before books are suggested. */
const val MIN_BOOK_QUERY_LENGTH = 2

// Sanitization aligned with the generator’s acronym normalization, but minimal and local to app
private fun sanitizeHebrewForAcronym(input: String): String {
    if (input.isBlank()) return ""
    var s = input.trim()
    // Remove Hebrew diacritics: teamim U+0591–U+05AF
    s = s.replace("[\u0591-\u05AF]".toRegex(), "")
    // Remove nikud signs (set incl. meteg U+05BD and QAMATZ QATAN U+05C7)
    val nikud = "[\u05B0\u05B1\u05B2\u05B3\u05B4\u05B5\u05B6\u05B7\u05B8\u05B9\u05BB\u05BC\u05BD\u05C1\u05C2\u05C7]".toRegex()
    s = s.replace(nikud, "")
    // Replace maqaf (U+05BE) with space
    s = s.replace('\u05BE', ' ')
    // Remove gershayim (U+05F4) and geresh (U+05F3)
    s = s.replace("\u05F4", "").replace("\u05F3", "")
    // Collapse whitespace
    s = s.replace("\\s+".toRegex(), " ").trim()
    return s
}

// The stored fields each kind of lookup document is read from
private val BOOK_FIELDS = setOf("book_id", "category_id", "book_title", "is_base_book", "order_index")
private val AUTHOR_FIELDS = setOf("author_id", "author_name", "book_count")
private val TOC_FIELDS = setOf("toc_id", "book_id", "book_title", "toc_text", "toc_level")
