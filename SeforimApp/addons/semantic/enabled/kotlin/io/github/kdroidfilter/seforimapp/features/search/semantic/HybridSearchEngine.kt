package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforim.semantic.SemanticSearch
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchQueryParser
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import io.github.kdroidfilter.seforimlibrary.search.SnippetSources
import io.github.kdroidfilter.seforimlibrary.search.WARMUP_PAGE_SIZE
import io.github.kdroidfilter.seforimlibrary.search.WARMUP_QUERIES
import io.github.kdroidfilter.seforimlibrary.search.WARMUP_ROUNDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Hybrid search: the lexical engine (BM25 + MagicDictionary) fused with dense semantic search ([SemanticSearch]) by
 * Reciprocal Rank Fusion, RRF(doc) = Σ 1/(K + rank_in_list).
 *
 * [openSession] runs the fused search (the session pages over the in-memory fused list); facets, snippets and title
 * prefix delegate to the lexical engine. Falls back to pure lexical when the index or its vectors are absent, or for
 * filters the dense index doesn't support (category / line ids); book and base-book filters work on both paths.
 */
internal class HybridSearchEngine(
    private val lexical: SearchEngine,
    private val indexDir: Path,
    private val resolveLine: suspend (lineId: Long, query: String) -> LineHit?,
) : SearchEngine {
    // The model (heavy ONNX session) loads lazily on the first dense search, off the main thread, so the first query
    // shows the normal search spinner instead of freezing the UI
    @Volatile private var semantic: SemanticSearch? = null

    @Volatile private var semanticTried = false
    private val semanticMutex = Mutex()

    // Cheap, no-load check: decides whether to take the dense path at all
    private val semanticConfigured = SemanticSearch.isAvailable(indexDir)

    private suspend fun ensureSemantic(): SemanticSearch? {
        if (semanticTried) return semantic
        semanticMutex.withLock {
            if (!semanticTried) {
                semantic =
                    if (semanticConfigured) {
                        // The tokenizer library otherwise "calls home" (an EC2 metadata probe, then a tracking ping)
                        // when it loads: seconds of network timeouts on the first search, and a privacy leak
                        System.setProperty("OPT_OUT_TRACKING", "true")
                        withContext(Dispatchers.IO) {
                            runCatching { SemanticSearch.open(indexDir) }
                                .onFailure { warnln(it) { "[HybridSearch] Semantic search unavailable; lexical only" } }
                                .getOrNull()
                        }
                    } else {
                        null
                    }
                semanticTried = true
            }
        }
        return semantic
    }

    override fun openSession(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
    ): SearchSession? {
        if (query.isBlank()) return null
        // A quoted phrase asks for those exact words: lexical only, no lines merely close in meaning
        if (!semanticConfigured || categoryFilter != null || lineIds != null || SearchQueryParser.parse(query).hasExactPhrases) {
            return lexical.openSession(query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)
        }
        return HybridSession(query, near, bookIds ?: bookFilter?.let { listOf(it) }, baseBookOnly)
    }

    override fun searchBooksByTitlePrefix(
        query: String,
        limit: Int,
    ): List<Long> = lexical.searchBooksByTitlePrefix(query, limit)

    override fun buildSnippet(
        rawText: String,
        query: String,
        near: Int,
    ): String = lexical.buildSnippet(rawText, query, near)

    override fun highlightRanges(
        text: String,
        query: String,
    ): List<IntRange> = lexical.highlightRanges(text, query)

    override fun rangeSnippet(
        text: String,
        ranges: List<IntRange>,
        lineStart: Int,
        lineEnd: Int,
    ): String = lexical.rangeSnippet(text, ranges, lineStart, lineEnd)

    // A quoted phrase asks for those exact words: they alone are highlighted, as they alone are found
    override fun highlightsByMeaning(query: String): Boolean = semanticConfigured && !SearchQueryParser.parse(query).hasExactPhrases

    override suspend fun passagesByMeaning(
        query: String,
        texts: List<String>,
    ): List<String?>? {
        val semantic = ensureSemantic() ?: return null
        return withContext(Dispatchers.Default) { semantic.bestPassages(query, texts) }
    }

    override fun findInBookCandidates(
        query: String,
        bookId: Long,
    ): LongArray? = lexical.findInBookCandidates(query, bookId)

    override suspend fun denseReady(): Boolean = ensureSemantic() != null

    override suspend fun attachSnippets(
        hits: List<LineHit>,
        query: String,
        near: Int,
    ): List<LineHit> = lexical.attachSnippets(hits, query, near)

    override suspend fun warmUp() {
        lexical.warmUp()
        ensureSemantic() ?: return
        // The fused path too: query embeddings, vector graph, meaning-based snippets
        repeat(WARMUP_ROUNDS) {
            for (query in WARMUP_QUERIES) {
                openSession(query, near = 5, baseBookOnly = true)?.use { session ->
                    session.nextPage(WARMUP_PAGE_SIZE)?.let { session.refinedSnippets(it.hits) }
                }
            }
        }
    }

    override suspend fun semanticFind(
        query: String,
        bookId: Long,
        limit: Int,
    ): List<Long> {
        if (query.isBlank()) return emptyList()
        // The search itself, scoped to the book: the lines holding the query's words and those closest in meaning,
        // fused as the results page fuses them
        return fuse(query, near = SMART_FIND_NEAR, bookIds = listOf(bookId), baseOnly = false).take(limit).map { it.lineId }
    }

    override fun computeFacets(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
    ): SearchFacets? = lexical.computeFacets(query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly)

    override fun close() {
        runCatching { semantic?.close() }
        runCatching { lexical.close() }
    }

    /**
     * A fused entry: [lexical] is the lexical hit (no snippet yet) when the lexical path matched the line, null for a
     * dense-only line, resolved (with a meaning-based snippet) only once its page is shown.
     */
    private class FusedEntry(
        val lineId: Long,
        val score: Float,
        val lexical: LineHit?,
    )

    private suspend fun fuse(
        query: String,
        near: Int,
        bookIds: Collection<Long>?,
        baseOnly: Boolean,
    ): List<FusedEntry> =
        coroutineScope {
            // Both rankers run at once, on ids and metadata only: snippets are built for the shown page alone
            val dense =
                async(Dispatchers.Default) {
                    val semantic = ensureSemantic() ?: return@async null
                    // A dense failure (e.g. a native-image gap in the KNN vectors format) must not sink the search
                    try {
                        semantic.search(query, CANDIDATES, baseOnly, bookIds)
                    } catch (e: Exception) {
                        warnln(e) { "[HybridSearch] Dense KNN failed; lexical only" }
                        null
                    }
                }
            val lexHits =
                lexical
                    .openSession(query, near = near, bookIds = bookIds, baseBookOnly = baseOnly)
                    ?.use { it.nextPage(CANDIDATES, snippets = false)?.hits }
                    .orEmpty()
            val denseHits = dense.await() ?: return@coroutineScope lexHits.map { FusedEntry(it.lineId, it.score, it) }

            val rrf = HashMap<Long, Double>()
            lexHits.forEachIndexed { rank, hit -> rrf.merge(hit.lineId, 1.0 / (RRF_K + rank + 1), Double::plus) }
            denseHits.forEachIndexed { rank, hit -> rrf.merge(hit.lineId, 1.0 / (RRF_K + rank + 1), Double::plus) }
            val lexById = lexHits.associateBy { it.lineId }
            rrf.entries
                .sortedByDescending { it.value }
                .map { (lineId, score) -> FusedEntry(lineId, score.toFloat(), lexById[lineId]) }
        }

    private inner class HybridSession(
        private val query: String,
        private val near: Int,
        private val bookIds: Collection<Long>?,
        private val baseOnly: Boolean,
    ) : SearchSession {
        private var fused: List<FusedEntry>? = null
        private var offset = 0

        override suspend fun nextPage(
            limit: Int,
            snippets: Boolean,
        ): SearchPage? {
            val all = fused ?: fuse(query, near, bookIds, baseOnly).also { fused = it }
            // Dense-only lines that no longer resolve (absent from the DB) are dropped, so a page may hold fewer hits
            while (offset < all.size) {
                val end = minOf(offset + limit, all.size)
                val entries = all.subList(offset, end)
                offset = end
                val page = resolvePage(entries, snippets)
                if (page.isNotEmpty() || offset >= all.size) {
                    return SearchPage(hits = page, totalHits = all.size.toLong(), isLastPage = offset >= all.size)
                }
            }
            return null
        }

        private suspend fun resolvePage(
            entries: List<FusedEntry>,
            snippets: Boolean,
        ): List<LineHit> {
            val lexicalHits = entries.mapNotNull { it.lexical }
            val withSnippets = if (snippets) lexical.attachSnippets(lexicalHits, query, near) else lexicalHits
            val lexicalById = withSnippets.associateBy { it.lineId }
            return entries.mapNotNull { entry ->
                val hit = lexicalById[entry.lineId] ?: denseHit(entry.lineId) ?: return@mapNotNull null
                // No word in bold: the passage closest in meaning is highlighted once ready (refinedSnippets)
                val snippet = if (snippets) lexical.rangeSnippet(hit.rawText, emptyList(), hit.lineStart, hit.lineEnd) else hit.snippet
                hit.copy(score = entry.score, snippet = snippet)
            }
        }

        // A dense-only line, its text cleaned as a lexical snippet source
        private suspend fun denseHit(lineId: Long): LineHit? {
            val hit = resolveLine(lineId, query) ?: return null
            val text = SnippetSources.clean(hit.rawText)
            return hit.copy(rawText = text, lineStart = 0, lineEnd = text.length)
        }

        override suspend fun refinedSnippets(hits: List<LineHit>): Map<Long, String> {
            val shown = hits.filter { it.rawText.isNotBlank() && it.lineEnd > it.lineStart }
            if (shown.isEmpty()) return emptyMap()
            // The found line alone, not the neighbors its snippet shows around it
            val highlights = highlights(shown.map { it.rawText.substring(it.lineStart, it.lineEnd) }, query)
            return buildMap {
                shown.forEachIndexed { i, hit ->
                    val ranges = highlights[i].ranges.map { (it.first + hit.lineStart)..(it.last + hit.lineStart) }
                    if (ranges.isNotEmpty()) put(hit.lineId, lexical.rangeSnippet(hit.rawText, ranges, hit.lineStart, hit.lineEnd))
                }
            }
        }

        override fun close() {}
    }

    private companion object {
        const val RRF_K = 60
        const val SMART_FIND_NEAR = 5
        const val CANDIDATES = 150
    }
}
