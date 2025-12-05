package io.github.kdroidfilter.seforimapp.framework.search

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.Term
import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.LRUQueryCache
import org.apache.lucene.search.UsageTrackingQueryCachingPolicy
import org.apache.lucene.search.SearcherFactory
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path

class LuceneLookupSearchService(indexDir: Path, private val analyzer: Analyzer = StandardAnalyzer()) {
    // Open Lucene directory lazily to avoid any I/O at app startup
    private val dir by lazy { FSDirectory.open(indexDir) }
    // Cache searchers; refresh explicitly when index changes
    private val searcherManager by lazy {
        SearcherManager(
            dir,
            object : SearcherFactory() {
                override fun newSearcher(reader: IndexReader, previousReader: IndexReader?): IndexSearcher {
                    val s = IndexSearcher(reader)
                    s.setQueryCache(LRUQueryCache(1024, 64L * 1024 * 1024)) // up to 64MiB cache
                    s.setQueryCachingPolicy(UsageTrackingQueryCachingPolicy())
                    return s
                }
            }
        )
    }

    private val bookSort = Sort(
        SortField("is_base_book", SortField.Type.INT, true),
        SortField("order_index", SortField.Type.INT),
        SortField("book_id", SortField.Type.LONG)
    )

    data class TocHit(
        val tocId: Long,
        val bookId: Long,
        val bookTitle: String,
        val text: String,
        val level: Int,
        val score: Float
    )
    data class BookHit(
        val id: Long,
        val categoryId: Long,
        val title: String,
        val isBaseBook: Boolean,
        val orderIndex: Int
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

    fun searchBooksPrefix(raw: String, limit: Int = 20): List<BookHit> {
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
                    orderIndex = doc.getField("order_index")?.numericValue()?.toInt() ?: Int.MAX_VALUE
                )
            }
        }
    }

    fun searchTocPrefix(raw: String, limit: Int = 20): List<TocHit> {
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
                    score = sd.score
                )
            }
        }
    }

    private fun normalizeHebrew(input: String): String {
        if (input.isBlank()) return ""
        var s = input.trim()
        s = s.replace("[\u0591-\u05AF]".toRegex(), "")
        s = s.replace("[\u05B0\u05B1\u05B2\u05B3\u05B4\u05B5\u05B6\u05B7\u05B8\u05B9\u05BB\u05BC\u05BD\u05C1\u05C2\u05C7]".toRegex(), "")
        s = s.replace('\u05BE', ' ')
        s = s.replace("\u05F4", "").replace("\u05F3", "")
        // Normalize final letters to base forms to align with index-time char filter
        s = s.replace('\u05DA', '\u05DB') // ך -> כ
            .replace('\u05DD', '\u05DE') // ם -> מ
            .replace('\u05DF', '\u05E0') // ן -> נ
            .replace('\u05E3', '\u05E4') // ף -> פ
            .replace('\u05E5', '\u05E6') // ץ -> צ
        s = s.replace("\\s+".toRegex(), " ").trim()
        return s
    }
}
