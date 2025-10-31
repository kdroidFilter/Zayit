package io.github.kdroidfilter.seforimapp.framework.search

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.index.StoredFields
import org.apache.lucene.search.*
import org.apache.lucene.util.QueryBuilder
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.document.IntPoint
import java.nio.file.Path
import com.code972.hebmorph.hspell.HSpellDictionaryLoader
import com.code972.hebmorph.datastructures.DictHebMorph
import org.slf4j.LoggerFactory
import org.apache.lucene.analysis.hebrew.HebrewExactAnalyzer
import org.apache.lucene.analysis.hebrew.HebrewQueryAnalyzer

/**
 * Minimal Lucene search service for JVM runtime.
 * Supports book title suggestions and full-text queries (future extension).
 */
class LuceneSearchService(indexDir: Path, private val analyzer: Analyzer = StandardAnalyzer()) {
    private val indexRoot: Path = indexDir
    private val dir = FSDirectory.open(indexDir)
    private val log = LoggerFactory.getLogger(LuceneSearchService::class.java)

    // HebMorph analyzers (lazily initialized). Fallback to StandardAnalyzer if dictionary cannot be loaded.
    private val hebDict: DictHebMorph? by lazy {
        val path = resolveHSpellPath()
        if (path == null) {
            log.warn("HebMorph hspell path not found; using StandardAnalyzer")
            null
        } else {
            try {
                log.info("Loading HebMorph dictionary from: {}", path)
                val d = HSpellDictionaryLoader().loadDictionaryFromPath(path)
                log.info("HebMorph dictionary loaded successfully")
                d
            } catch (e: Exception) {
                log.error("Failed to load HebMorph dictionary from {}", path, e)
                null
            }
        }
    }
    private val hebrewQueryAnalyzer: Analyzer by lazy { hebDict?.let { HebrewQueryAnalyzer(it) } ?: analyzer }
    private val hebrewExactAnalyzer: Analyzer by lazy { hebDict?.let { HebrewExactAnalyzer(it) } ?: analyzer }

    private inline fun <T> withSearcher(block: (IndexSearcher) -> T): T {
        DirectoryReader.open(dir).use { reader ->
            val searcher = IndexSearcher(reader)
            return block(searcher)
        }
    }

    init {
        // Light sanity check that the index can be opened
        runCatching {
            DirectoryReader.open(dir).use { r ->
                log.info("Lucene index opened at {} (docs={})", indexRoot, r.maxDoc())
            }
        }.onFailure { e ->
            log.error("Failed to open Lucene index at {}", indexRoot, e)
        }
    }

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
        val score: Float
    )

    fun searchAllText(rawQuery: String, near: Int = 5, limit: Int, offset: Int = 0): List<LineHit> =
        doSearch(rawQuery, near, limit, offset, bookFilter = null, categoryFilter = null)

    fun searchInBook(rawQuery: String, near: Int, bookId: Long, limit: Int, offset: Int = 0): List<LineHit> =
        doSearch(rawQuery, near, limit, offset, bookFilter = bookId, categoryFilter = null)

    fun searchInCategory(rawQuery: String, near: Int, categoryId: Long, limit: Int, offset: Int = 0): List<LineHit> =
        doSearch(rawQuery, near, limit, offset, bookFilter = null, categoryFilter = categoryId)

    fun searchInBooks(rawQuery: String, near: Int, bookIds: Collection<Long>, limit: Int, offset: Int = 0): List<LineHit> =
        doSearchInBooks(rawQuery, near, limit, offset, bookIds)

    private fun doSearch(
        rawQuery: String,
        near: Int,
        limit: Int,
        offset: Int,
        bookFilter: Long?,
        categoryFilter: Long?
    ): List<LineHit> {
        val norm = normalizeHebrew(rawQuery)
        if (norm.isBlank()) return emptyList()

        val analyzerForQuery = if (near == 0) {
            if (hebDict != null) log.debug("Using HebrewExactAnalyzer (near=0)") else log.debug("HebMorph unavailable; using StandardAnalyzer (near=0)")
            hebrewExactAnalyzer
        } else {
            if (hebDict != null) log.debug("Using HebrewQueryAnalyzer (near={})", near) else log.debug("HebMorph unavailable; using StandardAnalyzer (near={})", near)
            hebrewQueryAnalyzer
        }
        val q = QueryBuilder(analyzerForQuery).createPhraseQuery("text", norm, near)
            ?: return emptyList()
        val highlightTerms = analyzeToTerms(analyzerForQuery, norm) ?: emptyList()
        val anchorTerms = buildAnchorTerms(norm, highlightTerms)

        return withSearcher { searcher ->
            val b = BooleanQuery.Builder()
            b.add(TermQuery(Term("type", "line")), BooleanClause.Occur.FILTER)
            if (bookFilter != null) b.add(IntPoint.newExactQuery("book_id", bookFilter.toInt()), BooleanClause.Occur.FILTER)
            if (categoryFilter != null) b.add(IntPoint.newExactQuery("category_id", categoryFilter.toInt()), BooleanClause.Occur.FILTER)
            b.add(q, BooleanClause.Occur.MUST)
            val query = b.build()

            // Apply offset by fetching up to offset+limit and then subList
            val top = searcher.search(query, offset + limit)
            val stored: StoredFields = searcher.storedFields()
            val hits = top.scoreDocs.drop(offset)
            hits.map { sd ->
                val doc = stored.document(sd.doc)
                val bid = doc.getField("book_id").numericValue().toLong()
                val btitle = doc.getField("book_title").stringValue()
                val lid = doc.getField("line_id").numericValue().toLong()
                val lidx = doc.getField("line_index").numericValue().toInt()
                val raw = doc.getField("text_raw")?.stringValue() ?: ""
                val snippet = buildSnippet(raw, anchorTerms, highlightTerms)
                LineHit(
                    bookId = bid,
                    bookTitle = btitle ?: "",
                    lineId = lid,
                    lineIndex = lidx,
                    snippet = snippet,
                    score = sd.score
                )
            }
        }
    }

    private fun doSearchInBooks(
        rawQuery: String,
        near: Int,
        limit: Int,
        offset: Int,
        bookIds: Collection<Long>
    ): List<LineHit> {
        val norm = normalizeHebrew(rawQuery)
        if (norm.isBlank()) return emptyList()

        val analyzerForQuery = if (near == 0) {
            if (hebDict != null) log.debug("Using HebrewExactAnalyzer (near=0)") else log.debug("HebMorph unavailable; using StandardAnalyzer (near=0)")
            hebrewExactAnalyzer
        } else {
            if (hebDict != null) log.debug("Using HebrewQueryAnalyzer (near={})", near) else log.debug("HebMorph unavailable; using StandardAnalyzer (near={})", near)
            hebrewQueryAnalyzer
        }
        val q = QueryBuilder(analyzerForQuery).createPhraseQuery("text", norm, near)
            ?: return emptyList()
        val highlightTerms = analyzeToTerms(analyzerForQuery, norm) ?: emptyList()
        val anchorTerms = buildAnchorTerms(norm, highlightTerms)

        val bookIdInts = bookIds.asSequence().map { it.toInt() }.toList().toIntArray()
        if (bookIdInts.isEmpty()) return emptyList()

        return withSearcher { searcher ->
            val b = BooleanQuery.Builder()
            b.add(TermQuery(Term("type", "line")), BooleanClause.Occur.FILTER)
            b.add(IntPoint.newSetQuery("book_id", *bookIdInts), BooleanClause.Occur.FILTER)
            b.add(q, BooleanClause.Occur.MUST)
            val query = b.build()

            val top = searcher.search(query, offset + limit)
            val stored: StoredFields = searcher.storedFields()
            val hits = top.scoreDocs.drop(offset)
            hits.map { sd ->
                val doc = stored.document(sd.doc)
                val bid = doc.getField("book_id").numericValue().toLong()
                val btitle = doc.getField("book_title").stringValue()
                val lid = doc.getField("line_id").numericValue().toLong()
                val lidx = doc.getField("line_index").numericValue().toInt()
                val raw = doc.getField("text_raw")?.stringValue() ?: ""
                val snippet = buildSnippet(raw, anchorTerms, highlightTerms)
                LineHit(
                    bookId = bid,
                    bookTitle = btitle ?: "",
                    lineId = lid,
                    lineIndex = lidx,
                    snippet = snippet,
                    score = sd.score
                )
            }
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

    private fun buildAnchorTerms(normQuery: String, analyzedTerms: List<String>): List<String> {
        val qTokens = normQuery.split("\\s+".toRegex()).mapNotNull { t ->
            val s = t.trim(); if (s.isEmpty()) null else s
        }
        val analyzedStripped = analyzedTerms.map { it.trimEnd('$') }
        return (qTokens + analyzedStripped).filter { it.isNotBlank() }.distinct()
    }

    private fun buildSnippet(raw: String, anchorTerms: List<String>, highlightTerms: List<String>, context: Int = 120): String {
        if (raw.isEmpty()) return ""
        // Strip diacritics (nikud + teamim) to align matching with normalized tokens, and keep a mapping to original indices
        val (plain, mapToOrig) = stripDiacriticsWithMap(raw)
        val hasDiacritics = plain.length != raw.length
        val effContext = if (hasDiacritics) maxOf(context, 260) else context

        // Find first anchor term found in the plain text
        val plainIdx = anchorTerms.asSequence().mapNotNull { t ->
            val i = plain.indexOf(t)
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
        val baseMap: IntArray = IntArray(plainEnd - plainStart) { idx ->
            (mapToOrig[plainStart + idx] - origStart).coerceIn(0, base.length.coerceAtLeast(1) - 1)
        }

        // Build highlight intervals in original snippet coordinates using diacritic-agnostic matching
        val pool = (highlightTerms + highlightTerms.map { it.trimEnd('$') }).distinct().filter { it.isNotBlank() }
        val intervals = mutableListOf<IntRange>()
        val basePlainLower = basePlain.lowercase()
        for (term in pool) {
            if (term.isEmpty()) continue
            val t = term.lowercase()
            var from = 0
            while (from <= basePlainLower.length - t.length && t.isNotEmpty()) {
                val idx = basePlainLower.indexOf(t, startIndex = from)
                if (idx == -1) break
                val startOrig = mapToOrigIndex(baseMap, idx)
                val endOrig = mapToOrigIndex(baseMap, (idx + t.length - 1)) + 1
                if (startOrig in 0 until endOrig && endOrig <= base.length) {
                    intervals += (startOrig until endOrig)
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
        // Collapse whitespace
        s = s.replace("\\s+".toRegex(), " ").trim()
        return s
    }

    private fun resolveHSpellPath(): String? {
        System.getProperty("hebmorph.hspell.path")?.let { if (it.isNotBlank()) return it }
        System.getenv("HEBMORPH_HSPELL_PATH")?.let { if (it.isNotBlank()) return it }
        // Same directory as the DB (sibling to the index directory)
        runCatching { indexRoot.parent?.resolve("hspell-data-files")?.toFile() }
            .getOrNull()?.let { if (it.exists() && it.isDirectory) return it.absolutePath }
        val candidates = listOf(
            "SeforimLibrary/HebMorph/hspell-data-files",
            "HebMorph/hspell-data-files",
            "hspell-data-files",
            "../hspell-data-files",
            "../../hspell-data-files"
        )
        for (c in candidates) {
            val f = java.io.File(c)
            if (f.exists() && f.isDirectory) return f.absolutePath
        }
        return null
    }
}
