package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.vinceglb.filekit.FileKit
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Book suggestions of the home search bar over the installed database; skipped when it is absent. */
class BookSuggestionsE2ETest {
    private val dbPath =
        System.getenv("SEFORIMAPP_DATABASE_PATH")
            ?: (System.getProperty("user.home") + "/Library/Application Support/io.github.kdroidfilter.seforimapp/databases/seforim.db")

    private val lookup by lazy {
        FileKit.init("io.github.kdroidfilter.seforimapp")
        val settings = testAppSettings().apply { setDatabasePath(dbPath) }
        LuceneLookupSearchService(Paths.get("$dbPath.lookup.lucene"), acronymCache = AcronymFrequencyCache(DatabasePathProvider(settings)))
    }

    private fun titles(query: String): List<String> {
        assumeTrue("No database at $dbPath", File(dbPath).exists())
        return lookup.suggestBooks(query, limit = 120).map { it.title }
    }

    private fun first(query: String): String = titles(query).first()

    @Test
    fun titleBeingTypedComesFirst() {
        // תוספתא בבא קמא, first of its own category, used to come before בבא קמא
        assertEquals("בבא קמא", first("בבא ק"))
        assertEquals("בבא מציעא", first("בבא מ"))
        assertEquals("בבא בתרא", first("בבא בתר"))
        assertEquals("חולין", first("חולי"))
        assertEquals("עבודה זרה", first("עבודה ז"))
        assertEquals("מדרש תנחומא", first("מדרש תנחומא"))
    }

    @Test
    fun acronymsStillWin() {
        // once their quotes are dropped, these begin other titles (במדבר, חומת אנך, עזרא...)
        assertEquals("בבא מציעא", first("ב\"מ"))
        assertEquals("עבודה זרה", first("ע\"ז"))
        assertEquals("מלכים א", first("מל\"א"))
        assertEquals("טור", first("הטור"))
        assertEquals("דברי הימים ב", first("דבהי ב"))
        // a commentary carrying its base text's name as acronym does not pass it
        assertEquals("משנה תורה, הלכות שחיטה", first("הלכות שחיטה"))
    }

    @Test
    fun wordsTypedInOrder() {
        // the index read one word twice: ב of בבא ב matched בבא of תוספתא בבא קמא
        val bavaBatra = titles("בבא ב")
        assertTrue("תוספתא בבא קמא" !in bavaBatra && "תוספתא בבא מציעא" !in bavaBatra, bavaBatra.toString())
        assertTrue("רש\"י על בראשית" in titles("רשי בראשית"), titles("רשי בראשית").toString())
    }
}
