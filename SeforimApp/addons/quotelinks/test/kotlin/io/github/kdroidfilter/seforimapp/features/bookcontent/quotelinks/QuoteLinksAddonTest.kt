package io.github.kdroidfilter.seforimapp.features.bookcontent.quotelinks

import androidx.paging.PagingSource
import io.github.kdroidfilter.seforim.htmlparser.HtmlParser
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.pagination.LinesPagingSource
import io.github.kdroidfilter.seforimapp.pagination.PagingDefaults
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The quote links through the reader's own paging, on the DB the package was made for (SEFORIMAPP_DATABASE_PATH, or
 * SeforimLibrary/build/seforim.db; skipped without it). Prints what they cost per page.
 */
class QuoteLinksAddonTest {
    private val dbPath =
        (System.getenv("SEFORIMAPP_DATABASE_PATH") ?: File("../SeforimLibrary/build/seforim.db").absolutePath)
            .takeIf { File(it).isFile }

    private val repository by lazy { SeforimRepository(dbPath!!, PersistentSqliteDriver("jdbc:sqlite:$dbPath")) }
    private val quoteLinks = installedQuoteLinks!!

    private val anchor = Regex("""<a class="ref" href="seforim://line/(\d+)" data-quote="(\w+)">""")

    @Test
    fun `only the Talmud tractates of the package are covered`() =
        runBlocking {
            dbPath ?: return@runBlocking
            val covered = repository.getAllBooks().filter { quoteLinks.covers(it.id) }
            assertEquals(76, covered.size)
            assertTrue(covered.all { it.title.startsWith("תלמוד ירושלמי ") || it.categoryId in bavliCategories() }, "$covered")
            assertFalse(quoteLinks.covers(1), "בראשית")
        }

    @Test
    fun `every link of the package reaches the reader, pointing at its verse`() =
        runBlocking {
            dbPath ?: return@runBlocking
            val parser = HtmlParser()
            var links = 0
            var checked = 0
            for (book in repository.getAllBooks().filter { quoteLinks.covers(it.id) }) {
                for (line in readAll(book.id, decorated = true)) {
                    val found = anchor.findAll(line.content).toList()
                    links += found.size
                    if (found.isEmpty() || line.lineIndex % 10 != 0) continue
                    // What the reader renders: a span with the link, opening a verse of the Tanakh
                    val hrefs = parser.parse(line.content).mapNotNull { it.href }.toSet()
                    assertEquals(found.map { "seforim://line/" + it.groupValues[1] }.toSet(), hrefs, line.content)
                    for (m in found) {
                        val verse = repository.getLine(m.groupValues[1].toLong())!!
                        assertTrue(verse.bookId in 1L..39L, "${line.heRef} → ${verse.heRef}")
                        checked++
                    }
                }
            }
            println("quote links in the reader: $links (verses checked: $checked)")
            assertEquals(24_817, links)
        }

    @Test
    fun `cost per page`() =
        runBlocking {
            dbPath ?: return@runBlocking
            // Sanhedrin (most links), Berakhot, Yerushalmi Berakhot; Bereshit and Shulchan Arukh are not covered
            for (title in listOf("סנהדרין", "ברכות", "תלמוד ירושלמי ברכות", "בראשית", "שולחן ערוך, אורח חיים")) {
                val book = repository.getAllBooks().first { it.title == title }
                readAll(book.id, decorated = false) // warm the DB pages
                val plain = timed { readAll(book.id, decorated = false) }
                val cold = timed { readAll(book.id, decorated = true) }
                val warm = timed { readAll(book.id, decorated = true) }
                val pages = (book.totalLines + PagingDefaults.LINES.PAGE_SIZE - 1) / PagingDefaults.LINES.PAGE_SIZE
                println(
                    "%-22s covered=%-5s lines=%6d pages=%4d | plain %6.1f ms | first read %6.1f ms | again %6.1f ms | +%.3f ms/page"
                        .format(
                            title.take(22),
                            quoteLinks.covers(book.id),
                            book.totalLines,
                            pages,
                            plain,
                            cold,
                            warm,
                            (warm - plain) / pages,
                        ),
                )
            }
        }

    @Test
    fun `first page of a tractate opened for the first time`() =
        runBlocking {
            dbPath ?: return@runBlocking
            // Books no other test of this class reads first: the package's TSV is parsed here
            for (title in listOf("שבת", "בבא קמא", "תלמוד ירושלמי שבת", "ויקרא", "שולחן ערוך, יורה דעה")) {
                val book = repository.getAllBooks().first { it.title == title }
                firstPage(book.id, decorated = false) // warm the DB pages
                val plain = timed { firstPage(book.id, decorated = false) }
                val cold = timed { firstPage(book.id, decorated = true) }
                val warm = timed { firstPage(book.id, decorated = true) }
                println("%-22s first page: plain %5.2f ms | first time %5.2f ms | again %5.2f ms".format(title, plain, cold, warm))
            }
        }

    private suspend fun firstPage(
        bookId: Long,
        decorated: Boolean,
    ): List<Line> {
        val decorate: (suspend (List<Line>) -> List<Line>)? =
            if (decorated) { lines -> quoteLinks.decorate(bookId, lines, repository::getLinesByIds) } else null
        val page =
            LinesPagingSource(repository, bookId, decorate = decorate)
                .load(PagingSource.LoadParams.Refresh(0, PagingDefaults.LINES.INITIAL_LOAD_SIZE, false))
        return (page as PagingSource.LoadResult.Page).data
    }

    /** The whole book through LinesPagingSource, page by page as the reader scrolls it. */
    private suspend fun readAll(
        bookId: Long,
        decorated: Boolean,
    ): List<Line> {
        val decorate: (suspend (List<Line>) -> List<Line>)? =
            // As ContentUseCase: every book goes through decorate, which leaves the uncovered ones as they are
            if (decorated) {
                { lines -> quoteLinks.decorate(bookId, lines, repository::getLinesByIds) }
            } else {
                null
            }
        val source = LinesPagingSource(repository, bookId, decorate = decorate)
        val out = ArrayList<Line>()
        var page = source.load(PagingSource.LoadParams.Refresh(0, PagingDefaults.LINES.INITIAL_LOAD_SIZE, false))
        var key: Int? = 0
        while (page is PagingSource.LoadResult.Page) {
            out += page.data
            key = page.nextKey ?: break
            page = source.load(PagingSource.LoadParams.Append(key, PagingDefaults.LINES.PAGE_SIZE, false))
        }
        return out
    }

    private suspend fun bavliCategories(): Set<Long> {
        val root = repository.getRootCategories().first { it.title == "תלמוד" }
        val bavli = repository.getCategoryChildren(root.id).first { it.title == "בבלי" }
        return setOf(bavli.id) + repository.getCategoryChildren(bavli.id).map { it.id }
    }

    private inline fun timed(block: () -> Unit): Double {
        val t = System.nanoTime()
        block()
        return (System.nanoTime() - t) / 1e6
    }
}
