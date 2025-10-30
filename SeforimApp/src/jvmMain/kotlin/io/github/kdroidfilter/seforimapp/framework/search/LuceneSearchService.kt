package io.github.kdroidfilter.seforimapp.framework.search

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.index.StoredFields
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.document.IntPoint
import java.nio.file.Path

/**
 * Minimal Lucene search service for JVM runtime.
 * Supports book title suggestions and full-text queries (future extension).
 */
class LuceneSearchService(indexDir: Path, private val analyzer: Analyzer = StandardAnalyzer()) {
    private val dir = FSDirectory.open(indexDir)

    private inline fun <T> withSearcher(block: (IndexSearcher) -> T): T {
        DirectoryReader.open(dir).use { reader ->
            val searcher = IndexSearcher(reader)
            return block(searcher)
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
        val tokens = norm.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        val q = buildProximityQuery(tokens, near)

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
                val snippet = buildSnippet(raw, tokens)
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

    private fun buildProximityQuery(tokens: List<String>, near: Int): Query {
        // A basic phrase query with slop approximates NEAR; could also use SpanNearQuery
        val pq = PhraseQuery.Builder()
        pq.setSlop(near)
        tokens.forEach { t -> pq.add(Term("text", t)) }
        return pq.build()
    }

    private fun buildSnippet(raw: String, tokens: List<String>, context: Int = 60): String {
        if (raw.isEmpty()) return ""
        val lower = raw
        val idx = tokens.asSequence().mapNotNull { t ->
            val i = lower.indexOf(t)
            if (i >= 0) i else null
        }.firstOrNull() ?: 0
        val start = (idx - context).coerceAtLeast(0)
        val end = (idx + tokens.first().length + context).coerceAtMost(raw.length)
        val base = raw.substring(start, end)
        // naive highlight
        var out = base
        tokens.forEach { t -> out = out.replace(t, "<b>$t</b>") }
        if (start > 0) out = "...$out"
        if (end < raw.length) out = "$out..."
        return out
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
}
