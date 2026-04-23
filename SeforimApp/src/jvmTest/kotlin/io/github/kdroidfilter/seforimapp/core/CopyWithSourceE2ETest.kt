package io.github.kdroidfilter.seforimapp.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.extractCategoryChildren
import io.github.kdroidfilter.seforimlibrary.core.models.extractRootCategories
import io.github.kdroidfilter.seforimlibrary.dao.CatalogLoader
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * End-to-end verification of the "copy with source" pipeline against the real database and
 * catalog shipped under `SeforimLibrary/build/`. Skipped automatically on CI / dev machines
 * where those artefacts aren't present.
 *
 * What it covers:
 * 1. `CatalogLoader` + the in-memory parent-walk used in production correctly resolve the root
 *    category of arbitrary books picked from the live catalog.
 * 2. `buildCopyWithSourcePayload` honors the per-tradition rule (currently: trim the in-page
 *    line segment for Talmud only) when given a real `Book` + `Line`.
 */
class CopyWithSourceE2ETest {
    private var dbPath: String? = null
    private var driver: JdbcSqliteDriver? = null
    private var repository: SeforimRepository? = null
    private var categoriesById: Map<Long, Category>? = null
    private var roots: List<Category> = emptyList()

    @BeforeTest
    fun setup() {
        for (basePath in POSSIBLE_BASE_PATHS) {
            val dbCandidate = Path.of("$basePath/seforim.db")
            val catalogCandidate = Path.of("$basePath/catalog.pb")
            if (Files.exists(dbCandidate) && Files.exists(catalogCandidate)) {
                dbPath = dbCandidate.toString()
                break
            }
        }
        val resolvedDbPath = dbPath ?: return

        driver = JdbcSqliteDriver("jdbc:sqlite:$resolvedDbPath")
        repository = SeforimRepository(resolvedDbPath, driver!!)

        val catalog = CatalogLoader.loadCatalog(resolvedDbPath) ?: return
        roots = catalog.extractRootCategories()
        val children = catalog.extractCategoryChildren()
        val map = HashMap<Long, Category>(roots.size + children.values.sumOf { it.size })
        roots.forEach { map[it.id] = it }
        children.values.forEach { list -> list.forEach { map[it.id] = it } }
        categoriesById = map
    }

    @AfterTest
    fun tearDown() {
        driver?.close()
        driver = null
        repository = null
        categoriesById = null
    }

    private fun skipIfNoData() {
        if (repository == null || categoriesById == null) {
            org.junit.Assume.assumeTrue(
                "E2E data not available (SeforimLibrary/build/{seforim.db,catalog.pb})",
                false,
            )
        }
    }

    /** In-memory parent walk, mirroring `CatalogCache.getRootForBook` exactly. */
    private fun rootForBook(book: Book): Category? {
        val byId = categoriesById ?: return null
        var current: Category? = byId[book.categoryId] ?: return null
        var safety = 32
        while (current?.parentId != null && safety-- > 0) {
            current = byId[current.parentId]
        }
        return current
    }

    /**
     * Walk all books under [rootTitle] and return the first (book, line) where the line has
     * a non-blank `heRef`. We pick the first non-empty `lineIndex` if available so we can also
     * exercise the "comma in the middle" Talmud format.
     */
    private suspend fun firstUsableSampleUnder(rootTitle: String): Pair<Book, Line>? {
        val repo = repository!!
        val root = roots.firstOrNull { it.title == rootTitle } ?: return null
        val books = repo.getBooksUnderCategoryTree(root.id)
        for (book in books) {
            val lines = repo.getLines(book.id, 0, 32)
            val line = lines.firstOrNull { !it.heRef.isNullOrBlank() } ?: continue
            return book to line
        }
        return null
    }

    private fun plainTextOf(line: Line): String =
        Jsoup
            .parse(line.content)
            .text()
            .trim()

    /**
     * Find the first book under [rootTitle] that exposes at least two consecutive lines with
     * non-blank, distinct `heRef` values — used to exercise multi-line range formatting.
     */
    private suspend fun firstUsableMultiLineSampleUnder(rootTitle: String): Pair<Book, List<Line>>? {
        val repo = repository!!
        val root = roots.firstOrNull { it.title == rootTitle } ?: return null
        val books = repo.getBooksUnderCategoryTree(root.id)
        for (book in books) {
            val lines = repo.getLines(book.id, 0, 128).filter { !it.heRef.isNullOrBlank() }
            if (lines.size < 2) continue
            val first = lines.first()
            val last = lines.firstOrNull { it.heRef != first.heRef } ?: continue
            return book to listOf(first, last)
        }
        return null
    }

    @Test
    fun catalog_walk_resolves_root_for_sample_books_under_each_root() =
        runBlocking {
            skipIfNoData()
            assertTrue(roots.isNotEmpty(), "Catalog should expose at least one root category")
            val repo = repository!!

            for (root in roots) {
                val books = repo.getBooksUnderCategoryTree(root.id)
                val sample = books.firstOrNull() ?: continue
                val resolved = rootForBook(sample)
                assertEquals(
                    expected = root.id,
                    actual = resolved?.id,
                    message = "rootForBook should return '${root.title}' for book '${sample.title}'",
                )
            }
        }

    @Test
    fun tanakh_payload_keeps_full_line_heref() =
        runBlocking {
            skipIfNoData()
            val sample = firstUsableSampleUnder("תנ\"ך") ?: return@runBlocking
            val (book, line) = sample
            val rootTitle = rootForBook(book)?.title

            val payload =
                buildCopyWithSourcePayload(
                    selectedText = plainTextOf(line),
                    book = book,
                    rootTitle = rootTitle,
                    loadedLines = listOf(line),
                )

            // Tanakh keeps the verse: the full line.heRef must appear inside the trailing parens.
            val tail = payload.substringAfterLast("\n\n")
            assertTrue(
                actual = tail.contains(line.heRef!!),
                message = "Tanakh payload should keep verse '${line.heRef}' — got: $tail",
            )
        }

    @Test
    fun talmud_payload_drops_in_page_line_segment() =
        runBlocking {
            skipIfNoData()
            // Find a Talmud line whose heRef has at least one comma (otherwise nothing to trim).
            val repo = repository!!
            val talmudRoot = roots.firstOrNull { it.title == "תלמוד" } ?: return@runBlocking
            val books = repo.getBooksUnderCategoryTree(talmudRoot.id)
            var sample: Pair<Book, Line>? = null
            outer@ for (book in books) {
                val lines = repo.getLines(book.id, 0, 64)
                for (line in lines) {
                    val heRef = line.heRef?.trim().orEmpty()
                    if (heRef.contains(',')) {
                        sample = book to line
                        break@outer
                    }
                }
            }
            val (book, line) = sample ?: return@runBlocking
            val rootTitle = rootForBook(book)?.title
            assertEquals("תלמוד", rootTitle, "Sanity: book '${book.title}' should resolve to Talmud root")

            val originalHeRef = line.heRef!!
            val droppedSegment = originalHeRef.substringAfterLast(',').trim()
            val expectedTail = originalHeRef.substringBeforeLast(',').trim()

            val payload =
                buildCopyWithSourcePayload(
                    selectedText = plainTextOf(line),
                    book = book,
                    rootTitle = rootTitle,
                    loadedLines = listOf(line),
                )
            val parens = payload.substringAfterLast("\n\n").removePrefix("(").removeSuffix(")")

            assertTrue(
                actual = parens.endsWith(expectedTail),
                message = "Talmud payload tail should end with trimmed ref '$expectedTail' — got: $parens",
            )
            assertTrue(
                actual = !parens.endsWith(", $droppedSegment") && !parens.endsWith(",$droppedSegment"),
                message = "Talmud payload must not keep dropped segment '$droppedSegment' — got: $parens",
            )
        }

    @Test
    fun tanakh_multi_line_selection_produces_range() =
        runBlocking {
            skipIfNoData()
            val (book, lines) = firstUsableMultiLineSampleUnder("תנ\"ך") ?: return@runBlocking
            val first = lines.first()
            val last = lines.last()
            val rootTitle = rootForBook(book)?.title

            val payload =
                buildCopyWithSourcePayload(
                    selectedText = lines.joinToString(" ") { plainTextOf(it) },
                    book = book,
                    rootTitle = rootTitle,
                    loadedLines = lines,
                )
            val parens = payload.substringAfterLast("\n\n").removePrefix("(").removeSuffix(")")

            assertTrue(parens.contains(first.heRef!!), "Range should contain first verse — got: $parens")
            assertTrue(parens.contains(last.heRef!!), "Range should contain last verse — got: $parens")
            assertTrue(parens.contains(" – "), "Range should use ' – ' separator — got: $parens")
        }

    @Test
    fun talmud_multi_line_selection_trims_both_ends() =
        runBlocking {
            skipIfNoData()
            val sample = firstUsableMultiLineSampleUnder("תלמוד") ?: return@runBlocking
            val (book, lines) = sample
            val first = lines.first()
            val last = lines.last()
            // Need distinct heRefs that both contain a comma to make the trim observable.
            if (first.heRef == last.heRef) return@runBlocking
            if (first.heRef?.contains(',') != true || last.heRef?.contains(',') != true) return@runBlocking

            val rootTitle = rootForBook(book)?.title

            val payload =
                buildCopyWithSourcePayload(
                    selectedText = lines.joinToString(" ") { plainTextOf(it) },
                    book = book,
                    rootTitle = rootTitle,
                    loadedLines = lines,
                )
            val parens = payload.substringAfterLast("\n\n").removePrefix("(").removeSuffix(")")

            val droppedFirst = first.heRef!!.substringAfterLast(',').trim()
            val droppedLast = last.heRef!!.substringAfterLast(',').trim()

            assertTrue(parens.contains(" – "), "Talmud range should use ' – ' separator — got: $parens")
            // Neither dropped segment should appear in the formatted output.
            for (segment in listOf(droppedFirst, droppedLast)) {
                assertTrue(
                    actual = !parens.contains(", $segment ") && !parens.endsWith(", $segment"),
                    message = "Talmud payload still contains dropped segment '$segment' — got: $parens",
                )
            }
        }

    @Test
    fun unmatched_selection_falls_back_to_book_ref_only() =
        runBlocking {
            skipIfNoData()
            val sample = firstUsableSampleUnder("תנ\"ך") ?: return@runBlocking
            val (book, line) = sample
            val rootTitle = rootForBook(book)?.title
            val expectedRef = book.heRef?.takeIf { it.isNotBlank() } ?: book.title

            val payload =
                buildCopyWithSourcePayload(
                    selectedText = "selection text that cannot match any line",
                    book = book,
                    rootTitle = rootTitle,
                    loadedLines = listOf(line),
                )
            val parens = payload.substringAfterLast("\n\n").removePrefix("(").removeSuffix(")")

            assertEquals(expectedRef, parens, "Unmatched selection should yield book ref only")
        }

    @Test
    fun empty_loaded_lines_falls_back_to_book_ref_only() =
        runBlocking {
            skipIfNoData()
            val sample = firstUsableSampleUnder("תלמוד") ?: return@runBlocking
            val (book, _) = sample
            val rootTitle = rootForBook(book)?.title
            val expectedRef = book.heRef?.takeIf { it.isNotBlank() } ?: book.title

            val payload =
                buildCopyWithSourcePayload(
                    selectedText = "anything",
                    book = book,
                    rootTitle = rootTitle,
                    loadedLines = emptyList(),
                )
            val parens = payload.substringAfterLast("\n\n").removePrefix("(").removeSuffix(")")

            assertEquals(expectedRef, parens)
        }

    @Test
    fun consecutive_lines_with_identical_heref_collapse_to_single_ref() =
        runBlocking {
            skipIfNoData()
            // Many books have multiple internal lines mapped to the same verse/section.
            // When that's the selection, the output should NOT contain a "X – X" range.
            val repo = repository!!
            var sample: Pair<Book, List<Line>>? = null
            outerLoop@ for (root in roots) {
                val books = repo.getBooksUnderCategoryTree(root.id)
                for (book in books) {
                    val lines = repo.getLines(book.id, 0, 64).filter { !it.heRef.isNullOrBlank() }
                    if (lines.size < 2) continue
                    val grouped = lines.groupBy { it.heRef!! }
                    val sameRef = grouped.values.firstOrNull { it.size >= 2 } ?: continue
                    sample = book to sameRef.take(2)
                    break@outerLoop
                }
            }
            val (book, lines) = sample ?: return@runBlocking
            val rootTitle = rootForBook(book)?.title

            val payload =
                buildCopyWithSourcePayload(
                    selectedText = lines.joinToString(" ") { plainTextOf(it) },
                    book = book,
                    rootTitle = rootTitle,
                    loadedLines = lines,
                )
            val parens = payload.substringAfterLast("\n\n").removePrefix("(").removeSuffix(")")

            assertTrue(!parens.contains(" – "), "Same-heRef range should not contain ' – ' — got: $parens")
        }

    @Test
    fun book_with_blank_heref_uses_title_in_payload() =
        runBlocking {
            skipIfNoData()
            // Walk the catalog for a book where heRef is null/blank.
            val repo = repository!!
            var sample: Pair<Book, Line>? = null
            outerLoop@ for (root in roots) {
                val books = repo.getBooksUnderCategoryTree(root.id)
                for (book in books) {
                    if (!book.heRef.isNullOrBlank()) continue
                    val line = repo.getLines(book.id, 0, 16).firstOrNull() ?: continue
                    sample = book to line
                    break@outerLoop
                }
            }
            val (book, line) = sample ?: return@runBlocking
            val rootTitle = rootForBook(book)?.title

            val payload =
                buildCopyWithSourcePayload(
                    selectedText = plainTextOf(line),
                    book = book,
                    rootTitle = rootTitle,
                    loadedLines = listOf(line),
                )
            val parens = payload.substringAfterLast("\n\n").removePrefix("(").removeSuffix(")")

            assertTrue(
                actual = parens.contains(book.title),
                message = "Book without heRef should fall back to title '${book.title}' — got: $parens",
            )
        }

    @Test
    fun halakha_payload_keeps_seif_segment() =
        runBlocking {
            skipIfNoData()
            val sample = firstUsableSampleUnder("הלכה") ?: return@runBlocking
            val (book, line) = sample
            val rootTitle = rootForBook(book)?.title

            val payload =
                buildCopyWithSourcePayload(
                    selectedText = plainTextOf(line),
                    book = book,
                    rootTitle = rootTitle,
                    loadedLines = listOf(line),
                )
            val parens = payload.substringAfterLast("\n\n").removePrefix("(").removeSuffix(")")

            // Halakha doesn't trim: the original line.heRef (or its book-stripped local part)
            // must remain present, including any comma-separated seif/halacha segment.
            val heRef = line.heRef!!.trim()
            val expectedFragment = heRef.substringAfterLast(',').trim()
            assertTrue(
                actual = parens.contains(expectedFragment),
                message = "Halakha payload should retain final segment '$expectedFragment' — got: $parens",
            )
        }

    @Test
    fun stress_test_at_least_100_books_across_all_roots() =
        runBlocking {
            skipIfNoData()
            val repo = repository!!
            val perRootCap = 12 // ≈ 12 × 16 roots > 100; we cap to keep the test fast.
            val samples = mutableListOf<Triple<Book, Line, String?>>() // book, line, rootTitle

            for (root in roots) {
                val books = repo.getBooksUnderCategoryTree(root.id)
                var taken = 0
                for (book in books) {
                    if (taken >= perRootCap) break
                    val line =
                        repo
                            .getLines(book.id, 0, 64)
                            .firstOrNull { !it.heRef.isNullOrBlank() && it.content.isNotBlank() }
                            ?: continue
                    samples += Triple(book, line, root.title)
                    taken++
                }
            }

            assertTrue(
                actual = samples.size >= MIN_STRESS_SAMPLES,
                message = "Expected at least $MIN_STRESS_SAMPLES sampled books, got ${samples.size}",
            )

            val perRootCount = samples.groupingBy { it.third ?: "?" }.eachCount()
            println("[stress] sampled ${samples.size} books across ${perRootCount.size} roots: $perRootCount")

            val failures = mutableListOf<String>()
            var talmudTrimmed = 0
            var talmudNoOp = 0

            for ((book, line, rootTitle) in samples) {
                val plain = plainTextOf(line)
                val payload =
                    buildCopyWithSourcePayload(
                        selectedText = plain,
                        book = book,
                        rootTitle = rootTitle,
                        loadedLines = listOf(line),
                    )

                fun fail(message: String) {
                    failures += "[${rootTitle ?: "?"}] book='${book.title}' line.heRef='${line.heRef}' → $message | payload=$payload"
                }

                // Structural invariants
                if (!payload.startsWith("$plain\n\n(")) fail("payload should start with selection + blank line + '('")
                if (!payload.endsWith(")")) fail("payload should end with ')'")
                // Strip the outer wrapping parens; the reference itself may legitimately contain
                // inner parens (e.g. variant editions like "תוספתא בבא קמא (ליברמן) א, א").
                val parens =
                    payload
                        .substringAfterLast("\n\n")
                        .removePrefix("(")
                        .removeSuffix(")")
                if (parens.isBlank()) fail("parens content is blank")
                if (parens.trim() != parens) fail("parens has surrounding whitespace")
                if (parens.contains(",,")) fail("double comma in parens")
                if (parens.contains(" ,")) fail("space-before-comma in parens")
                if (Regex("\\s{2,}").containsMatchIn(parens)) fail("collapsed-whitespace violation in parens")

                // Reference must include a recognizable identifier of the book OR the line ref.
                // The formatter normalizes whitespace, so compare against a normalized line.heRef.
                val bookIdent = listOfNotNull(book.heRef?.takeIf { it.isNotBlank() }, book.title)
                val anyBookHit = bookIdent.any { parens.contains(it) }
                val lineHeRef = line.heRef!!.trim().replace(Regex("\\s+"), " ")
                val containsLine = parens.contains(lineHeRef)

                if (rootTitle == "תלמוד") {
                    val hasComma = lineHeRef.contains(',')
                    if (hasComma) {
                        val droppedSegment = lineHeRef.substringAfterLast(',').trim()
                        val expectedTail = lineHeRef.substringBeforeLast(',').trim().replace(Regex("\\s+"), " ")
                        // The dropped segment must not appear at the tail (allowing for ranges,
                        // we only check the suffix).
                        if (parens.endsWith(", $droppedSegment") || parens.endsWith(",$droppedSegment")) {
                            fail("Talmud payload still ends with dropped segment '$droppedSegment'")
                        }
                        if (!parens.endsWith(expectedTail)) {
                            fail("Talmud payload should end with trimmed ref '$expectedTail'")
                        }
                        talmudTrimmed++
                    } else {
                        // No comma → trim is a no-op; the line ref itself should appear.
                        if (!containsLine && !anyBookHit) fail("Talmud payload missing both line and book identifiers")
                        talmudNoOp++
                    }
                } else {
                    // Non-Talmud: the original line.heRef must survive in the output.
                    if (!containsLine) fail("Non-Talmud payload should preserve line.heRef '$lineHeRef'")
                }

                // Determinism: same inputs must yield the same output.
                val payload2 =
                    buildCopyWithSourcePayload(
                        selectedText = plain,
                        book = book,
                        rootTitle = rootTitle,
                        loadedLines = listOf(line),
                    )
                if (payload2 != payload) fail("non-deterministic output")
            }

            println("[stress] talmud trimmed=$talmudTrimmed, talmud no-op (no comma)=$talmudNoOp")

            if (failures.isNotEmpty()) {
                val preview =
                    failures
                        .take(20)
                        .joinToString("\n  - ", prefix = "  - ")
                fail("${failures.size} payload invariant violations across ${samples.size} books:\n$preview")
            }
        }

    private companion object {
        private const val MIN_STRESS_SAMPLES = 100

        private val POSSIBLE_BASE_PATHS =
            listOf(
                "SeforimLibrary/build", // From project root
                "../SeforimLibrary/build", // From SeforimApp module directory
            )
    }
}
