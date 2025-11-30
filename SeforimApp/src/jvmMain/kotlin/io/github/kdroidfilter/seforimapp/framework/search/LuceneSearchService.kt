package io.github.kdroidfilter.seforimapp.framework.search

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.StoredFields
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.FuzzyQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.TermQuery
import org.apache.lucene.util.QueryBuilder
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.document.IntPoint
import java.io.Closeable
import java.nio.file.Path
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import io.github.kdroidfilter.seforimapp.logger.debugln

/**
 * Minimal Lucene search service for JVM runtime.
 * Supports book title suggestions and full-text queries (future extension).
 */
class LuceneSearchService(indexDir: Path, private val analyzer: Analyzer = StandardAnalyzer()) {
    companion object {
        // Hard cap on how many synonym/expansion terms we allow per token
        private const val MAX_SYNONYM_TERMS_PER_TOKEN: Int = 32
        // Global cap for boost queries built from dictionary expansions
        private const val MAX_SYNONYM_BOOST_TERMS: Int = 256
    }

    // Open Lucene directory lazily to avoid any I/O at app startup
    private val dir by lazy { FSDirectory.open(indexDir) }


    private val stdAnalyzer: Analyzer by lazy { analyzer }
    private val magicDict: MagicDictionaryIndex? by lazy {
        val candidates = listOfNotNull(
            System.getProperty("magicDict")?.let { Path.of(it) },
            System.getenv("SEFORIM_MAGIC_DICT")?.let { Path.of(it) },
            indexDir.resolveSibling("lexical.db"),
            indexDir.resolveSibling("seforim.db").resolveSibling("lexical.db"),
            Path.of("SeforimLibrary/SeforimMagicIndexer/magicindexer/build/db/lexical.db")
        ).distinct()
        val firstExisting = MagicDictionaryIndex.findValidDictionary(candidates)
        if (firstExisting == null) {
            debugln {
                "[MagicDictionary] Missing lexical.db; search will run without dictionary expansions. " +
                    "Provide -DmagicDict=/path/lexical.db or SEFORIM_MAGIC_DICT. Checked: " +
                    candidates.joinToString()
            }
            return@lazy null
        }
        debugln { "[MagicDictionary] Loading lexical db from $firstExisting" }
        val loaded = MagicDictionaryIndex.load(::normalizeHebrew, firstExisting)
        if (loaded == null) {
            debugln {
                "[MagicDictionary] Failed to load lexical db at $firstExisting; " +
                    "continuing without dictionary expansions"
            }
        }
        loaded
    }

    private inline fun <T> withSearcher(block: (IndexSearcher) -> T): T {
        DirectoryReader.open(dir).use { reader ->
            val searcher = IndexSearcher(reader)
            return block(searcher)
        }
    }

    // No eager index opening: the index is stable and does not need
    // to be checked or analyzed at application startup.

    // --- Title suggestions ---

    fun searchBooksByTitlePrefix(rawQuery: String, limit: Int = 20): List<Long> {
        val q = normalizeHebrew(rawQuery)
        if (q.isBlank()) return emptyList()
        val tokens = q.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        return withSearcher { searcher ->
            val must = BooleanQuery.Builder()
            // Restrict to book_title docs
            must.add(TermQuery(Term("type", "book_title")), BooleanClause.Occur.FILTER)
            tokens.forEach { tok ->
                // prefix on analyzed 'title'
                must.add(PrefixQuery(Term("title", tok)), BooleanClause.Occur.MUST)
            }
            val query = must.build()
            val top = searcher.search(query, limit)
            val stored: StoredFields = searcher.storedFields()
            val ids = LinkedHashSet<Long>()
            for (sd in top.scoreDocs) {
                val doc = stored.document(sd.doc)
                val id = doc.getField("book_id")?.numericValue()?.toLong()
                if (id != null) ids.add(id)
            }
            ids.toList().take(limit)
        }
    }

    // --- Full-text search ---

    data class LineHit(
        val bookId: Long,
        val bookTitle: String,
        val lineId: Long,
        val lineIndex: Int,
        val snippet: String,
        val score: Float,
        val rawText: String
    )

    data class SearchPage(
        val hits: List<LineHit>,
        val totalHits: Long,
        val isLastPage: Boolean
    )

    inner class SearchSession internal constructor(
        private val query: Query,
        private val anchorTerms: List<String>,
        private val highlightTerms: List<String>,
        private val reader: DirectoryReader
    ) : Closeable {
        private val searcher = IndexSearcher(reader)
        private var after: ScoreDoc? = null
        private var finished = false
        private var totalHitsValue: Long? = null

        fun nextPage(limit: Int): SearchPage? {
            if (finished) return null
            val top = searcher.searchAfter(after, query, limit)
            if (totalHitsValue == null) totalHitsValue = top.totalHits?.value
            if (top.scoreDocs.isEmpty()) {
                finished = true
                return null
            }
            val stored = searcher.storedFields()
            val hits = mapScoreDocs(stored, top.scoreDocs.toList(), anchorTerms, highlightTerms)
            after = top.scoreDocs.last()
            val isLast = top.scoreDocs.size < limit
            if (isLast) finished = true
            return SearchPage(
                hits = hits,
                totalHits = totalHitsValue ?: hits.size.toLong(),
                isLastPage = isLast
            )
        }

        override fun close() {
            reader.close()
        }
    }

    fun openSearchSession(
        rawQuery: String,
        near: Int,
        bookFilter: Long? = null,
        categoryFilter: Long? = null,
        bookIds: Collection<Long>? = null,
        lineIds: Collection<Long>? = null
    ): SearchSession? {
        val context = buildSearchContext(rawQuery, near, bookFilter, categoryFilter, bookIds, lineIds) ?: return null
        val reader = DirectoryReader.open(dir)
        return SearchSession(context.query, context.anchorTerms, context.highlightTerms, reader)
    }

    private data class SearchContext(
        val query: Query,
        val anchorTerms: List<String>,
        val highlightTerms: List<String>
    )

    private fun buildSearchContext(
        rawQuery: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?
    ): SearchContext? {
        val norm = normalizeHebrew(rawQuery)
        if (norm.isBlank()) return null

        val analyzedRaw = analyzeToTerms(stdAnalyzer, norm) ?: emptyList()

        // Check if the original query contained ה׳ (Hashem) before normalization
        val hasHashem = rawQuery.contains("ה׳") || rawQuery.contains("ה'")

        // Filter out single Hebrew letters and stop words BEFORE dictionary expansion
        // BUT preserve "ה" if the original query had "ה׳" (Hashem)
        val analyzedStd = analyzedRaw.filter { token ->
            // Special case: if query has ה׳, keep "ה" token
            if (token == "ה" && hasHashem) return@filter true
            // Preserve numeric tokens (e.g., "6") so they can expand via MagicDictionary
            if (token.any { it.isDigit() }) return@filter true

            token.length >= 2 && token !in setOf(
                "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט", "י", "כ", "ל", "מ",
                "נ", "ס", "ע", "פ", "צ", "ק", "ר", "ש", "ת",
            )
        }

        debugln { "[DEBUG] Original query had Hashem (ה׳): $hasHashem" }
        debugln { "[DEBUG] Analyzed tokens: $analyzedStd" }

        // Get all possible expansions for each token (a token can belong to multiple bases)
        val tokenExpansionsRaw: Map<String, List<MagicDictionaryIndex.Expansion>> =
            analyzedStd.associateWith { token ->
                // Get best expansion (prefers matching base, then largest)
                val expansion = magicDict?.expansionFor(token) ?: return@associateWith emptyList()
                listOf(expansion)
            }
        tokenExpansionsRaw.forEach { (token, exps) ->
            exps.forEach { exp ->
                debugln { "[DEBUG] Token '$token' -> expansion: surface=${exp.surface.take(10)}..., variants=${exp.variants.take(10)}..., base=${exp.base}" }
            }
        }

        val tokenExpansions: Map<String, List<MagicDictionaryIndex.Expansion>> = tokenExpansionsRaw

        val allExpansions = tokenExpansions.values.flatten()
        val expandedTerms = allExpansions.flatMap { it.surface + it.variants + it.base }.distinct()
        // Add 4-gram terms used in the query (matches text_ng4 clauses) so highlighting can
        // reflect matches that were found via the n-gram branch.
        val ngramTerms = buildNgramTerms(analyzedStd, gram = 4)
        // For highlighting/snippets, use the actual query tokens plus the concrete
        // terms that the search query uses (expansions + n-grams).
        val highlightTerms = filterTermsForHighlight(analyzedStd + expandedTerms + ngramTerms)
        val anchorTerms = buildAnchorTerms(norm, highlightTerms)

        val rankedQuery = buildExpandedQuery(norm, near, analyzedStd, tokenExpansions)
        val mustAllTokensQuery: Query? = buildPresenceFilterForTokens(analyzedStd, near, tokenExpansions)
        val phraseQuery: Query? = buildSynonymPhraseQuery(analyzedStd, tokenExpansions, near)

        val builder = BooleanQuery.Builder()
        builder.add(TermQuery(Term("type", "line")), BooleanClause.Occur.FILTER)
        if (bookFilter != null) builder.add(IntPoint.newExactQuery("book_id", bookFilter.toInt()), BooleanClause.Occur.FILTER)
        if (categoryFilter != null) builder.add(IntPoint.newExactQuery("category_id", categoryFilter.toInt()), BooleanClause.Occur.FILTER)
        val bookIdsArray = bookIds?.map { it.toInt() }?.toIntArray()
        if (bookIdsArray != null && bookIdsArray.isNotEmpty()) {
            builder.add(IntPoint.newSetQuery("book_id", *bookIdsArray), BooleanClause.Occur.FILTER)
        }
        val lineIdsArray = lineIds?.map { it.toInt() }?.toIntArray()
        if (lineIdsArray != null && lineIdsArray.isNotEmpty()) {
            builder.add(IntPoint.newSetQuery("line_id", *lineIdsArray), BooleanClause.Occur.FILTER)
        }
        if (mustAllTokensQuery != null) {
            builder.add(mustAllTokensQuery, BooleanClause.Occur.FILTER)
            debugln { "[DEBUG] Added mustAllTokensQuery as FILTER" }
        }
        val analyzedCount = analyzedStd.size
        if (phraseQuery != null && analyzedCount >= 2) {
            val occur = if (near == 0) BooleanClause.Occur.MUST else BooleanClause.Occur.SHOULD
            builder.add(phraseQuery, occur)
            debugln { "[DEBUG] Added phraseQuery with occur=$occur, near=$near" }
        }
        builder.add(rankedQuery, BooleanClause.Occur.SHOULD)
        debugln { "[DEBUG] Added rankedQuery as SHOULD" }

        val finalQuery = builder.build()
        debugln { "[DEBUG] Final query: $finalQuery" }

        return SearchContext(
            query = finalQuery,
            anchorTerms = anchorTerms,
            highlightTerms = highlightTerms
        )
    }

    private fun mapScoreDocs(
        stored: StoredFields,
        scoreDocs: List<ScoreDoc>,
        anchorTerms: List<String>,
        highlightTerms: List<String>
    ): List<LineHit> {
        if (scoreDocs.isEmpty()) return emptyList()
        val hits = scoreDocs.map { sd ->
            val doc = stored.document(sd.doc)
            val bid = doc.getField("book_id").numericValue().toLong()
            val btitle = doc.getField("book_title").stringValue() ?: ""
            val lid = doc.getField("line_id").numericValue().toLong()
            val lidx = doc.getField("line_index").numericValue().toInt()
            val raw = doc.getField("text_raw")?.stringValue() ?: ""

            // Apply boost for base books based on orderIndex
            val isBaseBook = doc.getField("is_base_book")?.numericValue()?.toInt() == 1
            val orderIndex = doc.getField("order_index")?.numericValue()?.toInt() ?: 999
            val baseScore = sd.score

            // Calculate boost: lower orderIndex = higher boost (only for base books)
            val boostedScore = if (isBaseBook) {
                // Formula: boost = baseScore * (1 + (120 - orderIndex) / 60)
                // orderIndex 1 gets ~3x boost, orderIndex 50 gets ~2.2x boost, orderIndex 100+ gets ~1.3x boost
                val boostFactor = 1.0f + (120 - orderIndex).coerceAtLeast(0) / 60.0f
                baseScore * boostFactor
            } else {
                baseScore
            }

            val snippet = buildSnippet(raw, anchorTerms, highlightTerms)
            LineHit(
                bookId = bid,
                bookTitle = btitle,
                lineId = lid,
                lineIndex = lidx,
                snippet = snippet,
                score = boostedScore,
                rawText = raw
            )
        }
        // Re-sort by boosted score (descending)
        return hits.sortedByDescending { it.score }
    }

    fun searchAllText(rawQuery: String, near: Int = 5, limit: Int, offset: Int = 0): List<LineHit> =
        doSearch(rawQuery, near, limit, offset, bookFilter = null, categoryFilter = null)

    fun searchInBook(rawQuery: String, near: Int, bookId: Long, limit: Int, offset: Int = 0): List<LineHit> =
        doSearch(rawQuery, near, limit, offset, bookFilter = bookId, categoryFilter = null)

    fun searchInCategory(rawQuery: String, near: Int, categoryId: Long, limit: Int, offset: Int = 0): List<LineHit> =
        doSearch(rawQuery, near, limit, offset, bookFilter = null, categoryFilter = categoryId)

    fun searchInBooks(rawQuery: String, near: Int, bookIds: Collection<Long>, limit: Int, offset: Int = 0): List<LineHit> =
        doSearchInBooks(rawQuery, near, limit, offset, bookIds)

    // --- Snippet building (public) ---

    /**
     * Build an HTML snippet from raw line text by highlighting query terms.
     * Uses StandardAnalyzer tokens; highlight is diacritic-agnostic and sofit-normalized.
     */
    fun buildSnippetFromRaw(raw: String, rawQuery: String, near: Int): String {
        val norm = normalizeHebrew(rawQuery)
        if (norm.isBlank()) return Jsoup.clean(raw, Safelist.none())
        val rawClean = Jsoup.clean(raw, Safelist.none())
        val analyzedStd = (analyzeToTerms(stdAnalyzer, norm) ?: emptyList())
        val highlightTerms = filterTermsForHighlight(analyzedStd + buildNgramTerms(analyzedStd, gram = 4))
        val anchorTerms = buildAnchorTerms(norm, highlightTerms)
        return buildSnippet(rawClean, anchorTerms, highlightTerms)
    }

    private fun doSearch(
        rawQuery: String,
        near: Int,
        limit: Int,
        offset: Int,
        bookFilter: Long?,
        categoryFilter: Long?
    ): List<LineHit> {
        val context = buildSearchContext(rawQuery, near, bookFilter, categoryFilter, null, null) ?: return emptyList()
        return withSearcher { searcher ->
            val top = searcher.search(context.query, offset + limit)
            val stored: StoredFields = searcher.storedFields()
            val sliced = top.scoreDocs.drop(offset)
            mapScoreDocs(stored, sliced, context.anchorTerms, context.highlightTerms)
        }
    }

    private fun doSearchInBooks(
        rawQuery: String,
        near: Int,
        limit: Int,
        offset: Int,
        bookIds: Collection<Long>
    ): List<LineHit> {
        if (bookIds.isEmpty()) return emptyList()
        val context = buildSearchContext(rawQuery, near, bookFilter = null, categoryFilter = null, bookIds = bookIds, lineIds = null) ?: return emptyList()
        return withSearcher { searcher ->
            val top = searcher.search(context.query, offset + limit)
            val stored: StoredFields = searcher.storedFields()
            val sliced = top.scoreDocs.drop(offset)
            mapScoreDocs(stored, sliced, context.anchorTerms, context.highlightTerms)
        }
    }

    private fun analyzeToTerms(analyzer: Analyzer, text: String): List<String>? = try {
        val out = mutableListOf<String>()
        val ts: TokenStream = analyzer.tokenStream("text", text)
        val termAtt = ts.addAttribute(CharTermAttribute::class.java)
        ts.reset()
        while (ts.incrementToken()) {
            val t = termAtt.toString()
            if (t.isNotBlank()) out += t
        }
        ts.end(); ts.close()
        out
    } catch (_: Exception) { null }

    /**
     * Build an n-gram presence query that requires all 4-grams of the token
     * to be present in field 'text_ng4'. Returns null when token < 4 chars.
     */
    private fun buildNgramPresenceForToken(token: String): Query? {
        if (token.length < 4) return null
        val grams = mutableListOf<String>()
        var i = 0
        val L = token.length
        while (i + 4 <= L) {
            grams += token.substring(i, i + 4)
            i += 1
        }
        if (grams.isEmpty()) return null
        val b = BooleanQuery.Builder()
        for (g in grams.distinct()) {
            b.add(TermQuery(Term("text_ng4", g)), BooleanClause.Occur.MUST)
        }
        return b.build()
    }

    /**
     * Presence filter (AND across tokens). For NEAR>0, each token may be satisfied by
     * either exact term in 'text' OR by its 4-gram presence in 'text_ng4'.
     */
    private fun buildPresenceFilterForTokens(
        tokens: List<String>,
        near: Int,
        expansionsByToken: Map<String, List<MagicDictionaryIndex.Expansion>>
    ): Query? {
        if (tokens.isEmpty()) return null
        val outer = BooleanQuery.Builder()
        for (t in tokens) {
            val expansions = expansionsByToken[t] ?: emptyList()
            val synonymTerms = buildLimitedTermsForToken(t, expansions)
            val ngram = if (near > 0) buildNgramPresenceForToken(t) else null
            val clause = BooleanQuery.Builder().apply {
                // Add the original token
                add(TermQuery(Term("text", t)), BooleanClause.Occur.SHOULD)
                if (ngram != null) add(ngram, BooleanClause.Occur.SHOULD)
                // Add capped set of expansion terms so we do not exceed Lucene's maxClauseCount.
                for (term in synonymTerms) {
                    if (term != t) {  // Avoid duplicating the original token
                        add(TermQuery(Term("text", term)), BooleanClause.Occur.SHOULD)
                    }
                }
            }.build()
            outer.add(clause, BooleanClause.Occur.MUST)
        }
        return outer.build()
    }

    private fun buildHebrewStdQuery(norm: String, near: Int): Query {
        // Use standard Hebrew tokenizer at query time against field 'text'
        val qb = QueryBuilder(stdAnalyzer)
        val phrase = qb.createPhraseQuery("text", norm, near)
        if (phrase != null) return phrase
        val bool = qb.createBooleanQuery("text", norm, BooleanClause.Occur.MUST)
        return bool ?: BooleanQuery.Builder().build()
    }

    private fun buildMagicBoostQuery(expansions: List<MagicDictionaryIndex.Expansion>): Query? {
        if (expansions.isEmpty()) return null
        val surfaceTerms = LinkedHashSet<String>()
        val variantTerms = LinkedHashSet<String>()
        val baseTerms = LinkedHashSet<String>()
        for (exp in expansions) {
            surfaceTerms.addAll(exp.surface)
            variantTerms.addAll(exp.variants)
            baseTerms.addAll(exp.base)
        }

        val limitedSurfaces = surfaceTerms.take(MAX_SYNONYM_BOOST_TERMS)
        val limitedVariants = variantTerms.take(MAX_SYNONYM_BOOST_TERMS)
        val limitedBases = baseTerms.take(MAX_SYNONYM_BOOST_TERMS)
        if (surfaceTerms.size > limitedSurfaces.size ||
            variantTerms.size > limitedVariants.size ||
            baseTerms.size > limitedBases.size
        ) {
            debugln {
                "[DEBUG] Capped magic boost terms: " +
                    "surface=${surfaceTerms.size}->${limitedSurfaces.size}, " +
                    "variants=${variantTerms.size}->${limitedVariants.size}, " +
                    "base=${baseTerms.size}->${limitedBases.size}"
            }
        }

        val b = BooleanQuery.Builder()
        // Reduced boosts to favor phrase matches over individual term matches
        for (s in limitedSurfaces) {
            b.add(BoostQuery(TermQuery(Term("text", s)), 2.0f), BooleanClause.Occur.SHOULD)
        }
        for (v in limitedVariants) {
            b.add(BoostQuery(TermQuery(Term("text", v)), 1.5f), BooleanClause.Occur.SHOULD)
        }
        for (ba in limitedBases) {
            b.add(BoostQuery(TermQuery(Term("text", ba)), 1.0f), BooleanClause.Occur.SHOULD)
        }
        return b.build()
    }

    private fun buildSynonymBoostQuery(expansions: List<MagicDictionaryIndex.Expansion>): Query? {
        if (expansions.isEmpty()) return null
        val surfaceTerms = LinkedHashSet<String>()
        val variantTerms = LinkedHashSet<String>()
        val baseTerms = LinkedHashSet<String>()
        for (exp in expansions) {
            surfaceTerms.addAll(exp.surface)
            variantTerms.addAll(exp.variants)
            baseTerms.addAll(exp.base)
        }

        val limitedSurfaces = surfaceTerms.take(MAX_SYNONYM_BOOST_TERMS)
        val limitedVariants = variantTerms.take(MAX_SYNONYM_BOOST_TERMS)
        val limitedBases = baseTerms.take(MAX_SYNONYM_BOOST_TERMS)
        if (surfaceTerms.size > limitedSurfaces.size ||
            variantTerms.size > limitedVariants.size ||
            baseTerms.size > limitedBases.size
        ) {
            debugln {
                "[DEBUG] Capped synonym boost terms: " +
                    "surface=${surfaceTerms.size}->${limitedSurfaces.size}, " +
                    "variants=${variantTerms.size}->${limitedVariants.size}, " +
                    "base=${baseTerms.size}->${limitedBases.size}"
            }
        }

        val b = BooleanQuery.Builder()
        for (s in limitedSurfaces) {
            b.add(TermQuery(Term("text", s)), BooleanClause.Occur.SHOULD)
        }
        for (v in limitedVariants) {
            b.add(TermQuery(Term("text", v)), BooleanClause.Occur.SHOULD)
        }
        for (ba in limitedBases) {
            b.add(TermQuery(Term("text", ba)), BooleanClause.Occur.SHOULD)
        }
        return b.build()
    }

    private fun buildSynonymPhrases(
        tokens: List<String>,
        expansionsByToken: Map<String, List<MagicDictionaryIndex.Expansion>>
    ): List<Pair<Query, Float>> {
        if (tokens.isEmpty()) return emptyList()
        val termExpansions = buildTermAlternativesForTokens(tokens, expansionsByToken)
        debugln { "[DEBUG] buildSynonymPhrases - termExpansions sizes: ${termExpansions.map { it.size }}" }
        fun buildMultiPhrase(slop: Int): Query {
            val builder = org.apache.lucene.search.MultiPhraseQuery.Builder()
            builder.setSlop(slop)
            var pos = 0
            for (alts in termExpansions) {
                builder.add(alts.map { Term("text", it) }.toTypedArray(), pos)
                pos++
            }
            return builder.build()
        }
        return listOf(
            buildMultiPhrase(0) to 50.0f,   // Very high boost for exact phrase match
            buildMultiPhrase(3) to 20.0f,   // High boost for near phrase match (within 3 words)
            buildMultiPhrase(8) to 5.0f     // Lower boost for distant phrase match
        )
    }

    /**
     * Build a phrase query that treats each token as a synonym set of surface/variant/base.
     * This allows a query token (e.g., הלך) to match a surface form (e.g., וילך) in a phrase with slop.
     */
    private fun buildSynonymPhraseQuery(
        tokens: List<String>,
        expansionsByToken: Map<String, List<MagicDictionaryIndex.Expansion>>,
        near: Int
    ): Query? {
        if (tokens.isEmpty()) return null
        val termExpansions = buildTermAlternativesForTokens(tokens, expansionsByToken)
        val builder = org.apache.lucene.search.MultiPhraseQuery.Builder()
        builder.setSlop(near)
        var position = 0
        for (alts in termExpansions) {
            builder.add(alts.map { Term("text", it) }.toTypedArray(), position)
            position++
        }
        return builder.build()
    }

    private fun buildNgram4Query(norm: String): Query? {
        // Build MUST query over 4-gram terms on field 'text_ng4'
        val tokens = norm.split("\\s+".toRegex()).map { it.trim() }.filter { it.length >= 4 }
        if (tokens.isEmpty()) return null
        val grams = mutableListOf<String>()
        for (t in tokens) {
            val L = t.length
            var i = 0
            while (i + 4 <= L) {
                grams += t.substring(i, i + 4)
                i += 1
            }
        }
        val uniq = grams.distinct()
        if (uniq.isEmpty()) return null
        val b = BooleanQuery.Builder()
        for (g in uniq) {
            b.add(TermQuery(Term("text_ng4", g)), BooleanClause.Occur.MUST)
        }
        return b.build()
    }

    private fun buildExpandedQuery(
        norm: String,
        near: Int,
        tokens: List<String>,
        expansionsByToken: Map<String, List<MagicDictionaryIndex.Expansion>>
    ): Query {
        val base = buildHebrewStdQuery(norm, near)
        val allExpansions = expansionsByToken.values.flatten()
        val synonymPhrases = buildSynonymPhrases(tokens, expansionsByToken)
        val ngram = buildNgram4Query(norm)
        val fuzzy = buildFuzzyQuery(norm, near)
        val builder = BooleanQuery.Builder()
        builder.add(base, BooleanClause.Occur.SHOULD)
        for ((query, boost) in synonymPhrases) {
            builder.add(BoostQuery(query, boost), BooleanClause.Occur.SHOULD)
        }
        if (ngram != null) builder.add(ngram, BooleanClause.Occur.SHOULD)
        if (fuzzy != null) builder.add(fuzzy, BooleanClause.Occur.SHOULD)
        val magic = buildMagicBoostQuery(allExpansions)
        if (magic != null) builder.add(magic, BooleanClause.Occur.SHOULD)
        val synonymBoost = buildSynonymBoostQuery(allExpansions)
        if (synonymBoost != null) builder.add(synonymBoost, BooleanClause.Occur.SHOULD)
        return builder.build()
    }

    private fun buildFuzzyQuery(norm: String, near: Int): Query? {
        if (near == 0) return null
        if (norm.length < 4) return null
        val tokens = analyzeToTerms(stdAnalyzer, norm)?.filter { it.length >= 4 } ?: emptyList()
        if (tokens.isEmpty()) return null
        val b = BooleanQuery.Builder()
        for (t in tokens.distinct()) {
            // Add per-token fuzzy match on the main text field; require all tokens (MUST)
            b.add(FuzzyQuery(Term("text", t), 1), BooleanClause.Occur.MUST)
        }
        return b.build()
    }

    // Use only StandardAnalyzer + optional 4-gram

    private fun buildAnchorTerms(normQuery: String, analyzedTerms: List<String>): List<String> {
        val qTokens = normQuery.split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val combined = (qTokens + analyzedTerms.map { it.trimEnd('$') })
        val filtered = filterTermsForHighlight(combined)
        if (filtered.isNotEmpty()) return filtered
        val qFiltered = filterTermsForHighlight(qTokens)
        return qFiltered.ifEmpty { qTokens }
    }

    private fun filterTermsForHighlight(terms: List<String>): List<String> {
        if (terms.isEmpty()) return emptyList()

        fun useful(t: String): Boolean {
            val s = t.trim()
            if (s.isEmpty()) return false
            // Drop single-letter tokens
            if (s.length < 2) return false
            // Must contain at least one letter or digit
            if (s.none { it.isLetterOrDigit() }) return false
            return true
        }
        return terms
            .map { it.trim() }
            .filter { useful(it) }
            .distinct()
            .sortedByDescending { it.length }
    }

    private fun buildSnippet(raw: String, anchorTerms: List<String>, highlightTerms: List<String>, context: Int = 220): String {
        if (raw.isEmpty()) return ""
        // Strip diacritics (nikud + teamim) to align matching with normalized tokens, and keep a mapping to original indices
        val (plain, mapToOrig) = stripDiacriticsWithMap(raw)
        val hasDiacritics = plain.length != raw.length
        val effContext = if (hasDiacritics) maxOf(context, 360) else context
        // For matching only, normalize final letters in the plain text to base forms
        val plainSearch = replaceFinalsWithBase(plain)

        // Find first anchor term found in the plain text
        val plainIdx = anchorTerms.asSequence().mapNotNull { t ->
            val i = plainSearch.indexOf(t)
            if (i >= 0) i else null
        }.firstOrNull() ?: 0

        // Convert plain window to original indices
        val plainLen = anchorTerms.firstOrNull()?.length ?: 0
        val plainStart = (plainIdx - effContext).coerceAtLeast(0)
        val plainEnd = (plainIdx + plainLen + effContext).coerceAtMost(plain.length)
        val origStart = mapToOrigIndex(mapToOrig, plainStart)
        val origEnd = mapToOrigIndex(mapToOrig, plainEnd).coerceAtMost(raw.length)

        val base = raw.substring(origStart, origEnd)
        // Compute basePlain and its map to baseOriginal-local indices
        val basePlain = plain.substring(plainStart, plainEnd)
        val basePlainSearch = replaceFinalsWithBase(basePlain)
        val baseMap = IntArray(plainEnd - plainStart) { idx ->
            (mapToOrig[plainStart + idx] - origStart).coerceIn(0, base.length.coerceAtLeast(1) - 1)
        }

        // Build highlight intervals in original snippet coordinates using diacritic-agnostic matching
        val pool = (highlightTerms + highlightTerms.map { it.trimEnd('$') }).distinct().filter { it.isNotBlank() }
        val intervals = mutableListOf<IntRange>()
        val basePlainLower = basePlainSearch.lowercase()

        // Helper to check if a character is a word boundary (whitespace or punctuation)
        fun isWordBoundary(text: String, index: Int): Boolean {
            if (index < 0 || index >= text.length) return true
            val ch = text[index]
            return ch.isWhitespace() || !ch.isLetterOrDigit()
        }

        for (term in pool) {
            if (term.isEmpty()) continue
            val t = term.lowercase()
            var from = 0
            while (from <= basePlainLower.length - t.length && t.isNotEmpty()) {
                val idx = basePlainLower.indexOf(t, startIndex = from)
                if (idx == -1) break

                // Check if this is a word-internal match for a short term
                val isAtWordStart = isWordBoundary(basePlainLower, idx - 1)
                val isAtWordEnd = isWordBoundary(basePlainLower, idx + t.length)
                val isWholeWord = isAtWordStart && isAtWordEnd
                // Only highlight whole-word matches to avoid mid-word highlights.
                val shouldHighlight = isWholeWord

                if (shouldHighlight) {
                    val startOrig = mapToOrigIndex(baseMap, idx)
                    val endOrig = mapToOrigIndex(baseMap, (idx + t.length - 1)) + 1
                    if (startOrig in 0 until endOrig && endOrig <= base.length) {
                        intervals += (startOrig until endOrig)
                    }
                }
                from = idx + t.length
            }
        }
        val merged = mergeIntervals(intervals.sortedBy { it.first })
        var out = insertBoldTags(base, merged)
        if (origStart > 0) out = "...$out"
        if (origEnd < raw.length) out = "$out..."
        return out
    }

    private fun mapToOrigIndex(mapToOrig: IntArray, plainIndex: Int): Int {
        if (mapToOrig.isEmpty()) return plainIndex
        val idx = plainIndex.coerceIn(0, mapToOrig.size - 1)
        return mapToOrig[idx]
    }

    // Returns the string without nikud+teamim and an index map from plain index -> original index
    private fun stripDiacriticsWithMap(src: String): Pair<String, IntArray> {
        val nikudOrTeamim: (Char) -> Boolean = { c ->
            (c.code in 0x0591..0x05AF) || // teamim
            (c.code in 0x05B0..0x05BD) || // nikud + meteg
            (c == '\u05C1') || (c == '\u05C2') || (c == '\u05C7')
        }
        val out = StringBuilder(src.length)
        val map = ArrayList<Int>(src.length)
        var i = 0
        while (i < src.length) {
            val ch = src[i]
            if (!nikudOrTeamim(ch)) {
                out.append(ch)
                map.add(i)
            }
            i++
        }
        val arr = IntArray(map.size) { map[it] }
        return out.toString() to arr
    }

    private fun mergeIntervals(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return ranges
        val out = mutableListOf<IntRange>()
        var cur = ranges[0]
        for (i in 1 until ranges.size) {
            val r = ranges[i]
            if (r.first <= cur.last + 1) {
                cur = cur.first .. maxOf(cur.last, r.last)
            } else {
                out += cur
                cur = r
            }
        }
        out += cur
        return out
    }

    private fun insertBoldTags(text: String, intervals: List<IntRange>): String {
        if (intervals.isEmpty()) return text
        val sb = StringBuilder(text)
        // Insert from end to start to keep indices valid
        for (r in intervals.asReversed()) {
            val start = r.first.coerceIn(0, sb.length)
            val end = (r.last + 1).coerceIn(0, sb.length)
            if (end > start) {
                sb.insert(end, "</b>")
                sb.insert(start, "<b>")
            }
        }
        return sb.toString()
    }

    // --- Helpers ---
    private fun buildNgramTerms(tokens: List<String>, gram: Int = 4): List<String> {
        if (gram <= 0) return emptyList()
        val out = mutableListOf<String>()
        tokens.forEach { t ->
            val trimmed = t.trim()
            if (trimmed.length >= gram) {
                var i = 0
                while (i + gram <= trimmed.length) {
                    out += trimmed.substring(i, i + gram)
                    i += 1
                }
            }
        }
        return out.distinct()
    }


    private fun normalizeHebrew(input: String): String {
        if (input.isBlank()) return ""
        var s = input.trim()

        // Remove biblical cantillation marks (teamim) U+0591–U+05AF
        s = s.replace("[\u0591-\u05AF]".toRegex(), "")
        // Remove nikud signs including meteg and qamatz qatan
        s = s.replace("[\u05B0\u05B1\u05B2\u05B3\u05B4\u05B5\u05B6\u05B7\u05B8\u05B9\u05BB\u05BC\u05BD\u05C1\u05C2\u05C7]".toRegex(), "")
        // Replace maqaf U+05BE with space
        s = s.replace('\u05BE', ' ')
        // Remove gershayim/geresh
        s = s.replace("\u05F4", "").replace("\u05F3", "")
        // Normalize Hebrew final letters (sofit) to base forms
        s = replaceFinalsWithBase(s)
        // Collapse whitespace
        s = s.replace("\\s+".toRegex(), " ").trim()
        return s
    }

    private fun replaceFinalsWithBase(text: String): String = text
        .replace('\u05DA', '\u05DB') // ך -> כ
        .replace('\u05DD', '\u05DE') // ם -> מ
        .replace('\u05DF', '\u05E0') // ן -> נ
        .replace('\u05E3', '\u05E4') // ף -> פ
        .replace('\u05E5', '\u05E6') // ץ -> צ

    // StandardAnalyzer only

    /**
     * Build a capped list of alternative terms for a single token using dictionary expansions.
     * The resulting list always includes the original token and its base forms (when present),
     * followed by additional surface/variant forms up to MAX_SYNONYM_TERMS_PER_TOKEN.
     */
    private fun buildLimitedTermsForToken(
        token: String,
        expansions: List<MagicDictionaryIndex.Expansion>
    ): List<String> {
        if (expansions.isEmpty()) return listOf(token)

        val baseTerms = expansions.flatMap { it.base }.distinct()
        val otherTerms = expansions.flatMap { it.surface + it.variants }.distinct()

        val ordered = LinkedHashSet<String>()
        if (token.isNotBlank()) {
            ordered += token
        }
        baseTerms.forEach { ordered += it }
        otherTerms.forEach { ordered += it }

        val totalSize = ordered.size
        val limited = ordered.take(MAX_SYNONYM_TERMS_PER_TOKEN)
        if (totalSize > limited.size) {
            debugln {
                "[DEBUG] Capped synonym terms for token '$token' from $totalSize to ${limited.size}"
            }
        }
        return limited
    }

    /**
     * Build per-token synonym alternative lists for phrase queries, with per-token caps.
     */
    private fun buildTermAlternativesForTokens(
        tokens: List<String>,
        expansionsByToken: Map<String, List<MagicDictionaryIndex.Expansion>>
    ): List<List<String>> {
        if (tokens.isEmpty()) return emptyList()
        return tokens.map { token ->
            val expansions = expansionsByToken[token] ?: emptyList()
            buildLimitedTermsForToken(token, expansions)
        }
    }
}
