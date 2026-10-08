package io.github.kdroidfilter.seforimapp.features.search.semantic

import io.github.kdroidfilter.seforim.semantic.SemanticSearch
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
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
        if (!semanticConfigured || categoryFilter != null || lineIds != null) {
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

    override fun findInBookCandidates(
        query: String,
        bookId: Long,
    ): LongArray? = lexical.findInBookCandidates(query, bookId)

    override suspend fun semanticSpan(
        query: String,
        text: String,
    ): String? {
        val semantic = ensureSemantic() ?: return null
        return withContext(Dispatchers.Default) { semantic.bestPassage(query, text) }
    }

    override suspend fun denseReady(): Boolean = ensureSemantic() != null

    override suspend fun semanticFind(
        query: String,
        bookId: Long,
        limit: Int,
    ): List<Long> {
        if (query.isBlank()) return emptyList()
        val semantic = ensureSemantic() ?: return emptyList()
        return withContext(Dispatchers.Default) {
            semantic.search(query, limit, bookIds = listOf(bookId)).map { it.lineId }
        }
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

    /** The fused, RRF-ordered hits, and the ids the lexical path matched (the others get a meaning-based snippet). */
    private class FusedResult(
        val hits: List<LineHit>,
        val lexicalIds: Set<Long>,
    )

    private suspend fun fuse(
        query: String,
        near: Int,
        bookIds: Collection<Long>?,
        baseOnly: Boolean,
    ): FusedResult {
        val semantic = ensureSemantic()
        val lexHits =
            lexical
                .openSession(query, near = near, bookIds = bookIds, baseBookOnly = baseOnly)
                ?.use { it.nextPage(CANDIDATES)?.hits }
                .orEmpty()
        val lexicalOnly = FusedResult(lexHits, lexHits.mapTo(HashSet()) { it.lineId })
        if (semantic == null) return lexicalOnly

        // A dense failure (e.g. a native-image gap in the KNN vectors format) must not sink the search
        val denseHits =
            try {
                withContext(Dispatchers.Default) { semantic.search(query, CANDIDATES, baseOnly, bookIds) }
            } catch (e: Exception) {
                warnln(e) { "[HybridSearch] Dense KNN failed; lexical only" }
                return lexicalOnly
            }

        val rrf = HashMap<Long, Double>()
        lexHits.forEachIndexed { rank, hit -> rrf.merge(hit.lineId, 1.0 / (RRF_K + rank + 1), Double::plus) }
        denseHits.forEachIndexed { rank, hit -> rrf.merge(hit.lineId, 1.0 / (RRF_K + rank + 1), Double::plus) }
        val lexById = lexHits.associateBy { it.lineId }
        val hits =
            rrf.entries
                .sortedByDescending { it.value }
                .mapNotNull { (lineId, score) ->
                    (lexById[lineId] ?: resolveLine(lineId, query))?.copy(score = score.toFloat())
                }
        return FusedResult(hits, lexById.keys)
    }

    /**
     * Snippet for a dense-only hit: the lexical builder can't anchor (the query words aren't in the text), so bold the
     * passage closest in meaning as one span inside a context window. Null keeps the lexical snippet.
     */
    private suspend fun semanticSnippet(
        query: String,
        rawText: String,
        near: Int,
    ): String? {
        // Jsoup.clean returns HTML-escaped text; substrings stay escaped, so <b> is spliced in directly
        // (same convention as the lexical snippet builder)
        val clean = Jsoup.clean(rawText, Safelist.none())
        val passage = semanticSpan(query, clean) ?: return null
        val idx = clean.indexOf(passage)
        if (idx < 0) return lexical.buildSnippet(rawText, passage, near)
        val from = (idx - SNIPPET_CONTEXT).coerceAtLeast(0)
        val to = (idx + passage.length + SNIPPET_CONTEXT).coerceAtMost(clean.length)
        return buildString {
            if (from > 0) append("…")
            append(clean, from, idx)
            append("<b>").append(passage).append("</b>")
            append(clean, idx + passage.length, to)
            if (to < clean.length) append("…")
        }
    }

    private inner class HybridSession(
        private val query: String,
        private val near: Int,
        private val bookIds: Collection<Long>?,
        private val baseOnly: Boolean,
    ) : SearchSession {
        private var fused: FusedResult? = null
        private var offset = 0

        override suspend fun nextPage(limit: Int): SearchPage? {
            val all = fused ?: fuse(query, near, bookIds, baseOnly).also { fused = it }
            if (offset >= all.hits.size) return null
            val end = minOf(offset + limit, all.hits.size)
            // Re-snippet only this page's dense-only hits (bounded work), so they show the passage that matched
            val page =
                all.hits.subList(offset, end).map { hit ->
                    if (hit.lineId in all.lexicalIds) {
                        hit
                    } else {
                        semanticSnippet(query, hit.rawText, near)?.let { hit.copy(snippet = it) } ?: hit
                    }
                }
            offset = end
            return SearchPage(hits = page, totalHits = all.hits.size.toLong(), isLastPage = offset >= all.hits.size)
        }

        override fun close() {}
    }

    private companion object {
        const val RRF_K = 60
        const val CANDIDATES = 150
        const val SNIPPET_CONTEXT = 90 // chars of context kept on each side of the passage
    }
}
