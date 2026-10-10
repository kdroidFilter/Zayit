package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.features.search.semantic.installedSemanticSearch
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

private val QUERIES =
    listOf(
        "ואהבת לרעך כמוך",
        "שבת",
        "בראשית ברא",
        "תפילין",
        "כל ישראל יש להם חלק לעולם הבא",
        "אמר",
        "קריעת ים סוף",
        "הלכה כרבי עקיבא",
        "מצות עשה שהזמן גרמא",
        "ה׳ אלקים",
        "\"אין עומדין להתפלל\"",
        "גמילות חסדים",
    )

/** Headless replay of SearchResultViewModel.executeSearch: facets, then a session and its first page. */
fun main(args: Array<String>) {
    val dbPath = System.getenv("SEFORIMAPP_DATABASE_PATH") ?: error("SEFORIMAPP_DATABASE_PATH")
    val rounds = args.firstOrNull()?.toInt() ?: 3
    val t0 = System.nanoTime()
    val repository = SeforimRepository(dbPath, PersistentSqliteDriver("jdbc:sqlite:$dbPath"))
    val engine = buildEngine(repository, dbPath)
    println("setup ${ms(t0)} ms")
    runBlocking {
        if (System.getenv("BENCH_NO_WARMUP") == null) {
            val w = System.nanoTime()
            engine.warmUp()
            println("warmUp ${ms(w)} ms")
        }
        repeat(rounds) { round ->
            println("--- round $round")
            for (q in System.getenv("BENCH_QUERY")?.let { listOf(it) } ?: QUERIES) {
                val s = System.nanoTime()
                var facets = engine.computeFacets(q, 5, baseBookOnly = true)
                var base = true
                if (facets != null && facets.totalHits == 0L) {
                    base = false
                    facets = engine.computeFacets(q, 5, baseBookOnly = false)
                }
                val tFacets = ms(s)
                val s2 = System.nanoTime()
                val session = engine.openSession(q, 5, baseBookOnly = base)
                val page = session?.nextPage(25)
                val tPage = ms(s2)
                val s3 = System.nanoTime()
                val refined = page?.let { session.refinedSnippets(it.hits) }.orEmpty()
                val tRefine = ms(s3)
                session?.close()
                println(
                    "%-34s total=%6d facets=%6d page=%6d refine=%5d(%2d) hits=%8d first=%s".format(
                        q,
                        tFacets + tPage,
                        tFacets,
                        tPage,
                        tRefine,
                        refined.size,
                        facets?.totalHits ?: -1,
                        page?.hits?.firstOrNull()?.let { "${it.bookTitle}#${it.lineIndex}" },
                    ),
                )
            }
        }
    }
    engine.close()
}

private fun ms(start: Long) = (System.nanoTime() - start) / 1_000_000

private fun buildEngine(
    repository: SeforimRepository,
    dbPath: String,
): SearchEngine {
    val indexPath = Paths.get("$dbPath.lucene")
    val lexical =
        LuceneSearchEngine(indexPath, RepositorySnippetSourceProvider(repository), dictionaryPath = indexPath.resolveSibling("lexical.db"))
    if (System.getenv("BENCH_LEXICAL_ONLY") != null) return lexical
    val semantic = installedSemanticSearch ?: return lexical
    return semantic.hybrid(lexical, indexPath) { lineId, query ->
        repository.getLine(lineId)?.let { line ->
            LineHit(
                bookId = line.bookId,
                bookTitle = repository.getBook(line.bookId)?.title.orEmpty(),
                lineId = lineId,
                lineIndex = line.lineIndex,
                snippet = lexical.buildSnippet(line.content, query, 5),
                score = 0f,
                rawText = line.content,
            )
        }
    }
}
