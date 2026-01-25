package io.github.kdroidfilter.seforimapp.features.search.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Base class for search integration tests that need access to the real Lucene index
 * and SQLite database.
 *
 * Tests using this base class will be skipped in CI environments where the data files
 * are not available. The test data is searched in multiple locations:
 * - `SeforimLibrary/build/` (relative to project root)
 * - `../SeforimLibrary/build/` (relative to SeforimApp module)
 *
 * Usage:
 * ```kotlin
 * class MyUseCaseTest : SearchIntegrationTestBase() {
 *     @Test
 *     fun `my test`() = runBlocking {
 *         skipIfNoIndex()
 *         // Use searchEngine and repository here
 *     }
 * }
 * ```
 */
abstract class SearchIntegrationTestBase {
    protected companion object {
        private val POSSIBLE_BASE_PATHS =
            listOf(
                "SeforimLibrary/build", // From project root
                "../SeforimLibrary/build", // From SeforimApp module directory
            )

        private fun findDataPath(): Pair<Path, String>? {
            for (basePath in POSSIBLE_BASE_PATHS) {
                val lucenePath = Path.of("$basePath/seforim.db.lucene")
                val dbPath = "$basePath/seforim.db"
                if (Files.exists(lucenePath) && Files.exists(Path.of(dbPath))) {
                    return lucenePath to dbPath
                }
            }
            return null
        }

        private val resolvedPaths: Pair<Path, String>? by lazy { findDataPath() }

        val luceneIndexPath: Path? get() = resolvedPaths?.first
        val dbPath: String? get() = resolvedPaths?.second
    }

    protected var searchEngine: SearchEngine? = null
        private set

    protected var repository: SeforimRepository? = null
        private set

    private var driver: JdbcSqliteDriver? = null

    /**
     * Whether the test data (Lucene index and database) is available.
     */
    protected val hasTestData: Boolean
        get() = resolvedPaths != null

    @BeforeTest
    open fun setup() {
        if (!hasTestData) {
            return
        }

        val lucenePath = luceneIndexPath!!
        val db = dbPath!!

        // Initialize Lucene search engine
        searchEngine = LuceneSearchEngine(lucenePath)

        // Initialize SQLite database
        driver = JdbcSqliteDriver("jdbc:sqlite:$db")
        repository = SeforimRepository(db, driver!!)
    }

    @AfterTest
    open fun tearDown() {
        searchEngine?.close()
        searchEngine = null
        driver?.close()
        driver = null
        repository = null
    }

    /**
     * Skips the current test if the Lucene index or database is not available.
     * Call this at the start of any test that requires real data.
     *
     * @throws org.junit.AssumptionViolatedException (or similar) if data is not available
     */
    protected fun skipIfNoIndex() {
        if (!hasTestData) {
            println("SKIPPED: Test data not available. Checked paths: $POSSIBLE_BASE_PATHS")
            println("  Working directory: ${System.getProperty("user.dir")}")
            org.junit.Assume.assumeTrue("Test data not available", false)
        }
    }

    /**
     * Gets the search engine, ensuring it's initialized.
     * @throws IllegalStateException if search engine is not available
     */
    protected fun requireSearchEngine(): SearchEngine = searchEngine ?: error("SearchEngine not initialized. Call skipIfNoIndex() first.")

    /**
     * Gets the repository, ensuring it's initialized.
     * @throws IllegalStateException if repository is not available
     */
    protected fun requireRepository(): SeforimRepository = repository ?: error("Repository not initialized. Call skipIfNoIndex() first.")
}
