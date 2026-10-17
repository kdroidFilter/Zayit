package io.github.kdroidfilter.seforimapp.framework.search

import co.touchlab.kermit.Logger
import io.github.kdroidfilter.seforimlibrary.cache.concurrentCache
import io.github.kdroidfilter.seforimlibrary.search.SqliteReader
import io.github.kdroidfilter.seforimlibrary.search.openReadOnlySqlite

/**
 * Cache for acronym frequency data from seforim.db.
 * Provides fast lookup of how many books use each acronym.
 *
 * Uses the book_acronym table from seforim.db - no separate database needed!
 */
class AcronymFrequencyCache(
    private val databasePath: () -> String,
) {
    private val logger = Logger.withTag("AcronymFrequencyCache")

    private val db: SqliteReader by lazy {
        val dbPath = databasePath()
        logger.i { "Loading acronym data from seforim.db: $dbPath" }
        openReadOnlySqlite(dbPath)
    }

    // Cache: acronym → frequency (number of books using it)
    private val frequencyCache = concurrentCache<String, Int>()

    // Cache: acronym → list of book titles
    private val bookTitlesCache = concurrentCache<String, List<String>>()

    // Cache: book title → list of acronyms
    private val titleToAcronymsCache = concurrentCache<String, List<String>>()

    init {
        // Pre-load common acronyms into cache
        preloadCache()
    }

    /**
     * Get the frequency (number of books) for an acronym.
     */
    fun getFrequency(acronym: String): Int = frequencyCache[acronym] ?: queryFrequency(acronym).also { frequencyCache[acronym] = it }

    /**
     * Get all book titles that use this acronym.
     */
    fun getBookTitles(acronym: String): List<String> =
        bookTitlesCache[acronym] ?: queryBookTitles(acronym).also { bookTitlesCache[acronym] = it }

    /**
     * Check if an acronym is unique (used by only one book).
     */
    fun isUnique(acronym: String): Boolean = getFrequency(acronym) == 1

    /**
     * Get all acronyms for a given book title.
     */
    fun getAcronymsForBook(bookTitle: String): List<String> =
        titleToAcronymsCache[bookTitle] ?: queryAcronymsForTitle(bookTitle).also { titleToAcronymsCache[bookTitle] = it }

    private fun queryFrequency(acronym: String): Int =
        db
            .query("SELECT COUNT(DISTINCT bookId) as frequency FROM book_acronym WHERE term = ?", listOf(acronym))
            .firstOrNull()
            ?.get("frequency")
            ?.toIntOrNull() ?: 0

    private fun queryBookTitles(acronym: String): List<String> =
        db
            .query(
                """
                SELECT DISTINCT b.title
                FROM book_acronym ba
                JOIN book b ON ba.bookId = b.id
                WHERE ba.term = ?
                """.trimIndent(),
                listOf(acronym),
            ).mapNotNull { it["title"] }

    private fun queryAcronymsForTitle(bookTitle: String): List<String> =
        db
            .query(
                """
                SELECT DISTINCT ba.term
                FROM book b
                JOIN book_acronym ba ON b.id = ba.bookId
                WHERE b.title = ?
                """.trimIndent(),
                listOf(bookTitle),
            ).mapNotNull { it["term"] }

    private fun preloadCache() {
        // Pre-load top 100 most common acronyms
        val rows =
            db.query(
                """
                SELECT
                    term,
                    COUNT(DISTINCT bookId) as frequency
                FROM book_acronym
                GROUP BY term
                ORDER BY frequency DESC
                LIMIT 100
                """.trimIndent(),
            )
        for (row in rows) {
            val acronym = row["term"] ?: continue
            frequencyCache[acronym] = row["frequency"]?.toIntOrNull() ?: continue
        }

        logger.i { "Pre-loaded ${rows.size} acronyms into cache from seforim.db" }
    }

    fun close() {
        db.close()
    }
}
