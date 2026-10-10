package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.paging.PagingSource
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.AltTocUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.CommentariesUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.ContentUseCase
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.TocUseCase
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.database.SqlTrace
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimapp.pagination.CommentsForLineOrTocPagingSource
import io.github.kdroidfilter.seforimapp.pagination.LinesPagingSource
import io.github.kdroidfilter.seforimapp.pagination.PagingDefaults
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// Bereshit, Berakhot, Rambam Shabbat, Shulchan Aruch OC
private val BOOKS = listOf(1L, 103L, 308L, 382L)

/**
 * Headless replay of the reader's DB work: opening a book (BookContentViewModel.loadBookData), selecting a line
 * with its commentaries open (selectLine + postSelectLine + the commentaries pane) and scrolling (line pages +
 * the visible lines' connections). Env: SEFORIMAPP_DATABASE_PATH, BENCH_BOOKS (ids), BENCH_SQL=1 (per-query top).
 */
fun main() {
    val dbPath = System.getenv("SEFORIMAPP_DATABASE_PATH") ?: error("SEFORIMAPP_DATABASE_PATH")
    val books = System.getenv("BENCH_BOOKS")?.split(',')?.map { it.trim().toLong() } ?: BOOKS
    val repository = SeforimRepository(dbPath, PersistentSqliteDriver("jdbc:sqlite:$dbPath"))
    val perSql = ConcurrentHashMap<String, LongArray>()
    val totalWait = AtomicLong()
    SqlTrace.listener = { sql, wait, exec ->
        val key = sql.replace(Regex("\\s+"), " ").replace(Regex("\\?(,\\?)+"), "?..").take(110)
        perSql.compute(key) { _, v ->
            (v ?: LongArray(2)).also {
                it[0]++
                it[1] += exec
            }
        }
        totalWait.addAndGet(wait)
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    if (System.getenv("BENCH_HTML") != null) {
        runBlocking { htmlBench(repository) }
        System.exit(0)
    }
    runBlocking {
        for (round in 0..1) {
            println("=== round $round ${if (round == 0) "(cold)" else "(warm)"}")
            for (bookId in books) {
                val store = TabPersistedStateStore()
                val sm = BookContentStateManager("bench-$bookId-$round", store)
                val content = ContentUseCase(repository, sm)
                val toc = TocUseCase(repository, sm, scope)
                val alt = AltTocUseCase(repository, sm)
                val comm = CommentariesUseCase(repository, sm, scope)

                // --- open
                val t0 = System.nanoTime()
                val book = repository.getBookCore(bookId)!!
                sm.updateNavigation { copy(selectedBook = book) }
                comm.applyDefaultCommentatorsForBook(book.id)
                val root = repository.getBookRootToc(book.id)
                val leaf = root.firstOrNull()?.let { repository.getFirstLeafTocId(it.id) ?: it.id }
                val initialLineId =
                    leaf?.let { repository.getLineIdsForTocEntry(it).firstOrNull() } ?: repository.getLineByIndex(book.id, 0)!!.id
                val firstPage = refresh(LinesPagingSource(repository, book.id, initialLineId))
                val tFirstContent = ms(t0)
                repository.getBookCharCounts(book.id)
                toc.loadRootToc(book.id)
                alt.loadStructures(book)
                content.loadAndSelectLine(initialLineId, scroll = false)?.let { line -> postSelect(comm, sm, line) }
                toc.expandPathToLine(initialLineId)
                comm.loadLineConnections(firstPage.take(20).map { it.id })
                val tOpen = ms(t0)

                // --- select a line in the middle with its commentaries
                val mid = repository.getLineByIndex(book.id, book.totalLines / 2)!!
                val t1 = System.nanoTime()
                content.selectLine(mid)
                postSelect(comm, sm, mid)
                comm.loadLineConnections(listOf(mid.id))
                val groups = comm.getCommentatorGroups(mid.id)
                val tGroups = ms(t1)
                val commentators =
                    sm.state.value.content.selectedCommentatorsByBook[book.id]
                        .orEmpty()
                        .ifEmpty {
                            groups
                                .flatMap { it.commentators }
                                .take(3)
                                .map { it.bookId }
                                .toSet()
                        }
                var commentaryRows = 0
                for (c in commentators) {
                    val res =
                        CommentsForLineOrTocPagingSource(repository, mid.id, setOf(c)).load(
                            PagingSource.LoadParams.Refresh(0, PagingDefaults.COMMENTS.INITIAL_LOAD_SIZE, false),
                        )
                    commentaryRows += (res as PagingSource.LoadResult.Page).data.size
                    comm.getCommentaryCharCountsForLine(mid.id, c)
                    comm.prefetchCommentaries(mid.id, setOf(c))
                }
                val tSelect = ms(t1)

                // --- scroll 10 pages down from the middle
                val src = LinesPagingSource(repository, book.id, mid.id)
                var loaded = refresh(src)
                val pageTimes = ArrayList<Long>()
                val t2 = System.nanoTime()
                repeat(10) {
                    val p = System.nanoTime()
                    val res =
                        src.load(PagingSource.LoadParams.Append(it + 1, PagingDefaults.LINES.PAGE_SIZE, false))
                    val page = (res as PagingSource.LoadResult.Page).data
                    comm.loadLineConnections(page.take(20).map { l -> l.id })
                    comm.loadLineConnections(page.drop(20).take(20).map { l -> l.id })
                    loaded = loaded + page
                    pageTimes += ms(p)
                }
                val tScroll = ms(t2)
                println(
                    "%-28s firstContent=%5d open=%5d | groups=%5d select=%5d (%d commentators, %d rows) | scroll10=%5d page max=%d"
                        .format(
                            book.title.take(28),
                            tFirstContent,
                            tOpen,
                            tGroups,
                            tSelect,
                            commentators.size,
                            commentaryRows,
                            tScroll,
                            pageTimes.max(),
                        ),
                )
            }
        }
    }
    if (System.getenv("BENCH_SQL") != null) {
        println("--- top queries (count, total ms)")
        perSql.entries
            .sortedByDescending { it.value[1] }
            .take(25)
            .forEach { (k, v) -> println("%6d %7d  %s".format(v[0], v[1] / 1_000_000, k)) }
        println("total connection wait ${totalWait.get() / 1_000_000} ms")
    }
    System.exit(0)
}

private suspend fun htmlBench(repository: SeforimRepository) {
    // Bereshit, Rashi on Bereshit, Berakhot, Shulchan Aruch
    for (title in listOf("בראשית", "רש\"י על בראשית", "ברכות", "משנה ברורה")) {
        val book = repository.getBookByTitle(title) ?: continue
        val lines = repository.getLines(book.id, 0, 3000).map { it.content }
        repeat(3) { round ->
            val t = System.nanoTime()
            var chars = 0L
            for (l in lines) {
                io.github.kdroidfilter.seforim.htmlparser
                    .buildAnnotatedFromHtml(l, 20f)
                chars += l.length
            }
            val total = System.nanoTime() - t
            val sorted = lines.sortedBy { it.length }
            if (round == 2) {
                println(
                    "%-20s lines=%d avgChars=%d p95Chars=%d maxChars=%d  avg=%.1fus  per1kChars=%.1fus".format(
                        title,
                        lines.size,
                        chars / lines.size,
                        sorted[(sorted.size * 0.95).toInt()].length,
                        sorted.last().length,
                        total / 1e3 / lines.size,
                        total / 1e3 / (chars / 1000.0),
                    ),
                )
            }
        }
    }
}

private suspend fun postSelect(
    comm: CommentariesUseCase,
    sm: BookContentStateManager,
    line: Line,
) {
    val primary =
        sm.state.value.content.selectedLines
            .firstOrNull() ?: line
    comm.reapplySelectedCommentators(primary)
    comm.reapplySelectedLinkSources(primary)
    comm.reapplySelectedSources(primary)
}

private suspend fun refresh(src: LinesPagingSource): List<Line> =
    (src.load(PagingSource.LoadParams.Refresh(0, PagingDefaults.LINES.INITIAL_LOAD_SIZE, false)) as PagingSource.LoadResult.Page).data

private fun ms(start: Long) = (System.nanoTime() - start) / 1_000_000
