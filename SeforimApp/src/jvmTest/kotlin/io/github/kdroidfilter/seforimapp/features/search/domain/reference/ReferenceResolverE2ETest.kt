package io.github.kdroidfilter.seforimapp.features.search.domain.reference

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [ReferenceResolver] over the installed database; skipped when the app's database is absent. */
class ReferenceResolverE2ETest {
    private val dbPath =
        System.getenv("SEFORIMAPP_DATABASE_PATH")
            ?: (System.getProperty("user.home") + "/Library/Application Support/io.github.kdroidfilter.seforimapp/databases/seforim.db")
    private val driver by lazy { JdbcSqliteDriver("jdbc:sqlite:$dbPath") }
    private val resolver by lazy { ReferenceResolver(RepositoryReferenceSource(SeforimRepository(dbPath, driver))) }

    @AfterTest
    fun tearDown() {
        if (File(dbPath).exists()) driver.close()
    }

    private fun jumps(query: String): List<String> {
        assumeTrue("No database at $dbPath", File(dbPath).exists())
        return runBlocking { resolver.resolve(query) }.map { "${it.book.title} | ${it.label}" }
    }

    @Test
    fun bookNamedWholeIsNotSplitIntoAnotherBook() {
        // בבא is an acronym of בבא בתרא, קמא reads as 141
        assertTrue(jumps("בבא קמא").none { it.startsWith("בבא בתרא") })
        // nor while it is being typed
        assertTrue(jumps("בבא ק").none { it.startsWith("בבא בתרא") })
        assertTrue(jumps("בבא קמ").none { it.startsWith("בבא בתרא") })
        // מאירי ע"ז / רי"ף ב"ק are acronyms of מאירי על עבודה זרה / רי"ף בבא קמא
        assertTrue(jumps("מאירי ע\"ז").none { it.startsWith("מאירי על שבת") })
        assertTrue(jumps("רי\"ף ב\"ק").none { it.startsWith("רי\"ף פסחים") })
        assertTrue(jumps("חתם סופר ע\"ז").none { it.contains("שבת") })
    }

    @Test
    fun bookNamedWholeStillJumpsWithinItself() {
        // במדבר א is an acronym of במדבר
        assertEquals(listOf("במדבר | א"), jumps("במדבר א"))
    }

    @Test
    fun referencesStillResolve() {
        // the Mishna of the tractate comes second
        assertEquals("חולין | דף יב.", jumps("חולין יב").first())
        assertEquals("חולין | דף יב:", jumps("חולין דף יב ע\"ב").first())
        assertEquals(listOf("שבת | דף יז."), jumps("שבת יז"))
        assertEquals(listOf("בבא מציעא | דף יז."), jumps("בבא מציעא יז"))
        assertEquals(listOf("בבא בתרא | דף יז."), jumps("בבא בתרא יז"))
        assertEquals(listOf("בבא בתרא | דף קמא."), jumps("בבא בתרא קמא"))
        // קמא in the title reads as 141, which must not hide the dapim
        assertEquals("בבא קמא | דף יז.", jumps("בבא קמא יז").first())
        assertEquals("בבא קמא | דף יז:", jumps("בבא קמא דף יז ע\"ב").first())
        assertEquals(listOf("בראשית | א, ג"), jumps("בראשית א ג"))
        assertTrue(jumps("בראשית נח").single().startsWith("בראשית | נח"))
    }

    @Test
    fun strayQuotesDoNotBreakTheLookup() {
        assertTrue(jumps("שבת \"").isEmpty())
        assertEquals(listOf("שבת | דף יז."), jumps("שבת \" יז"))
    }
}
