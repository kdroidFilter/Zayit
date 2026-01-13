package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.logger.infoln
import java.sql.Connection
import java.sql.DriverManager

/**
 * Cache for acronym frequency data from seforim.db.
 * Provides fast lookup of how many books use each acronym.
 *
 * Uses the book_acronym table from seforim.db - no separate database needed!
 */
class AcronymFrequencyCache {

    private val connection: Connection by lazy {
        loadSeforimDb()
    }

    // Cache: acronym → frequency (number of books using it)
    private val frequencyCache = mutableMapOf<String, Int>()

    // Cache: acronym → list of book titles
    private val bookTitlesCache = mutableMapOf<String, List<String>>()

    // Cache: book title → list of acronyms
    private val titleToAcronymsCache = mutableMapOf<String, List<String>>()

    init {
        // Pre-load common acronyms into cache
        preloadCache()
    }

    /**
     * Get the frequency (number of books) for an acronym.
     */
    fun getFrequency(acronym: String): Int {
        return frequencyCache.getOrPut(acronym) {
            queryFrequency(acronym)
        }
    }

    /**
     * Get all book titles that use this acronym.
     */
    fun getBookTitles(acronym: String): List<String> {
        return bookTitlesCache.getOrPut(acronym) {
            queryBookTitles(acronym)
        }
    }

    /**
     * Check if an acronym is unique (used by only one book).
     */
    fun isUnique(acronym: String): Boolean {
        return getFrequency(acronym) == 1
    }

    /**
     * Get all acronyms for a given book title.
     */
    fun getAcronymsForBook(bookTitle: String): List<String> {
        return titleToAcronymsCache.getOrPut(bookTitle) {
            queryAcronymsForTitle(bookTitle)
        }
    }

    private fun loadSeforimDb(): Connection {
        val dbPath = getDatabasePath()
        infoln { "Loading acronym data from seforim.db: $dbPath" }
        return DriverManager.getConnection("jdbc:sqlite:$dbPath")
    }

    private fun queryFrequency(acronym: String): Int {
        val sql = """
            SELECT COUNT(DISTINCT bookId) as frequency
            FROM book_acronym
            WHERE term = ?
        """

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, acronym)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getInt("frequency") else 0
            }
        }
    }

    private fun queryBookTitles(acronym: String): List<String> {
        val sql = """
            SELECT DISTINCT b.title
            FROM book_acronym ba
            JOIN book b ON ba.bookId = b.id
            WHERE ba.term = ?
        """

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, acronym)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.getString("title"))
                    }
                }
            }
        }
    }

    private fun queryAcronymsForTitle(bookTitle: String): List<String> {
        val sql = """
            SELECT DISTINCT ba.term
            FROM book b
            JOIN book_acronym ba ON b.id = ba.bookId
            WHERE b.title = ?
        """

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, bookTitle)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.getString("term"))
                    }
                }
            }
        }
    }

    private fun preloadCache() {
        // Pre-load top 100 most common acronyms
        val sql = """
            SELECT
                term,
                COUNT(DISTINCT bookId) as frequency
            FROM book_acronym
            GROUP BY term
            ORDER BY frequency DESC
            LIMIT 100
        """

        connection.prepareStatement(sql).use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val acronym = rs.getString("term")
                    val frequency = rs.getInt("frequency")
                    frequencyCache[acronym] = frequency
                }
            }
        }

        infoln { "Pre-loaded ${frequencyCache.size} acronyms into cache from seforim.db" }
    }

    fun close() {
        connection.close()
    }
}
