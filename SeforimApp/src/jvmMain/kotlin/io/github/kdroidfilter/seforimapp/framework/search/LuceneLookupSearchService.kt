package io.github.kdroidfilter.seforimapp.framework.search

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.IndexReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.LRUQueryCache
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.SearcherFactory
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.UsageTrackingQueryCachingPolicy
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path

class LuceneLookupSearchService(
    indexDir: Path,
    private val analyzer: Analyzer = StandardAnalyzer(),
    private val acronymCache: AcronymFrequencyCache? = null,
) {
    // Open Lucene directory lazily to avoid any I/O at app startup
    private val dir by lazy { FSDirectory.open(indexDir) }

    // Cache searchers; refresh explicitly when index changes
    private val searcherManager by lazy {
        SearcherManager(
            dir,
            object : SearcherFactory() {
                override fun newSearcher(
                    reader: IndexReader,
                    previousReader: IndexReader?,
                ): IndexSearcher {
                    val s = IndexSearcher(reader)
                    s.setQueryCache(LRUQueryCache(1024, 64L * 1024 * 1024)) // up to 64MiB cache
                    s.setQueryCachingPolicy(UsageTrackingQueryCachingPolicy())
                    return s
                }
            },
        )
    }

    private val bookSort =
        Sort(
            SortField("is_base_book", SortField.Type.INT, true),
            SortField("order_index", SortField.Type.INT),
            SortField("book_id", SortField.Type.LONG),
        )

    data class TocHit(
        val tocId: Long,
        val bookId: Long,
        val bookTitle: String,
        val text: String,
        val level: Int,
        val score: Float,
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
    )

    fun close() {
        kotlin.runCatching { searcherManager.close() }
        kotlin.runCatching { dir.close() }
    }

    private inline fun <T> withSearcher(block: (IndexSearcher) -> T): T {
        // Index is static during app run; refresh is cheap if no changes
        searcherManager.maybeRefresh()
        val searcher = searcherManager.acquire()
        return try {
            block(searcher)
        } finally {
            searcherManager.release(searcher)
        }
    }

    fun searchBooksPrefix(
        raw: String,
        limit: Int = 20,
    ): List<BookHit> {
        val q = normalizeHebrew(raw)
        if (q.isBlank()) return emptyList()
        val tokens = q.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return withSearcher { searcher ->
            val b = BooleanQuery.Builder()
            b.add(TermQuery(Term("type", "book")), BooleanClause.Occur.FILTER)
            tokens.forEach { t -> b.add(PrefixQuery(Term("q", t)), BooleanClause.Occur.MUST) }
            val top = searcher.search(b.build(), limit, bookSort)
            val stored = searcher.storedFields()
            top.scoreDocs.map { sd ->
                val doc = stored.document(sd.doc)
                BookHit(
                    id = doc.getField("book_id").numericValue().toLong(),
                    categoryId = doc.getField("category_id").numericValue().toLong(),
                    title = doc.getField("book_title").stringValue(),
                    isBaseBook = doc.getField("is_base_book")?.numericValue()?.toInt() == 1,
                    orderIndex = doc.getField("order_index")?.numericValue()?.toInt() ?: Int.MAX_VALUE,
                )
            }
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
        return withSearcher { searcher ->
            val b = BooleanQuery.Builder()
            b.add(TermQuery(Term("type", "toc")), BooleanClause.Occur.FILTER)
            tokens.forEach { t -> b.add(PrefixQuery(Term("q", t)), BooleanClause.Occur.MUST) }
            val top = searcher.search(b.build(), limit, bookSort)
            val stored = searcher.storedFields()
            top.scoreDocs.map { sd ->
                val doc = stored.document(sd.doc)
                TocHit(
                    tocId = doc.getField("toc_id").numericValue().toLong(),
                    bookId = doc.getField("book_id").numericValue().toLong(),
                    bookTitle = doc.getField("book_title").stringValue(),
                    text = doc.getField("toc_text").stringValue(),
                    level = doc.getField("toc_level").numericValue().toInt(),
                    score = sd.score,
                )
            }
        }
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

        // 1. Get base results from Lucene (existing logic)
        val baseHits = searchBooksPrefix(raw, limit * 3) // Get more for re-ranking

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
                )
            }

        // 4. Re-sort by enhanced score and return top results
        return scoredHits
            .sortedByDescending { it.score }
            .take(limit)
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
