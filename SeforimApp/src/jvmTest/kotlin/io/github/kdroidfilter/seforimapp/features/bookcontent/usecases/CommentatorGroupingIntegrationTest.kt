package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies commentator grouping/ordering against the real generated database.
 *
 * The test boots a [SeforimRepository] on `SeforimLibrary/build/seforim.db` (a
 * read path that exists on developer machines and dedicated CI nodes that ship
 * the corpus). When the DB is absent the assertions are skipped via JUnit
 * Assume so the test never fails on lean CI runners.
 */
class CommentatorGroupingIntegrationTest {
    private var driver: JdbcSqliteDriver? = null
    private var repository: SeforimRepository? = null
    private var scope: CoroutineScope? = null

    private companion object {
        // Lines and books reference the canonical IDs of the generated corpus
        // (Bereshit book.id = 1, "בראשית" verse 1:1 line.id = 3, Berakhot
        // book.id = 103 with the first sugya line at id 28957).
        const val BERESHIT_BOOK_ID = 1L
        const val BERESHIT_1_1_LINE_ID = 3L
        const val BERAKHOT_BOOK_ID = 103L
        const val BERAKHOT_2A_LINE_ID = 28957L

        private val POSSIBLE_DB_PATHS =
            listOf(
                "SeforimLibrary/build/seforim.db",
                "../SeforimLibrary/build/seforim.db",
            )

        private fun resolveDbPath(): String? {
            for (p in POSSIBLE_DB_PATHS) {
                if (Files.exists(Path.of(p))) return p
            }
            return null
        }
    }

    @BeforeTest
    fun setup() {
        val dbPath = resolveDbPath() ?: return
        driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
        repository = SeforimRepository(dbPath, driver!!)
        scope = CoroutineScope(SupervisorJob())
    }

    @AfterTest
    fun tearDown() {
        scope?.cancel()
        scope = null
        driver?.close()
        driver = null
        repository = null
    }

    private fun skipIfNoDb() {
        if (repository == null) {
            org.junit.Assume.assumeTrue("Generated DB not available", false)
        }
    }

    private suspend fun buildUseCase(bookId: Long): CommentariesUseCase {
        val repo = repository!!
        val book =
            repo.getBook(bookId)
                ?: error("Book $bookId not found in DB — corpus mismatch")
        val stateManager = BookContentStateManager("test-tab", TabPersistedStateStore())
        stateManager.updateNavigation { copy(selectedBook = book) }
        return CommentariesUseCase(repo, stateManager, scope!!)
    }

    @Test
    fun `Bereshit 1_1 — Tanakh Rishonim group exists and contains Rashi-Ramban-Ibn Ezra`() =
        runBlocking {
            skipIfNoDb()
            val uc = buildUseCase(BERESHIT_BOOK_ID)

            val groups = uc.getCommentatorGroupsForLines(listOf(BERESHIT_1_1_LINE_ID))

            val labels = groups.map { it.label }
            println("[Bereshit 1:1] Group labels in order:")
            labels.forEachIndexed { i, l -> println("  ${i + 1}. $l (${groups[i].commentators.size} books)") }

            val rishonim =
                groups.firstOrNull { it.label == "ראשונים על התנ״ך" }
                    ?: error("Missing 'ראשונים על התנ״ך' group. Got: $labels")
            val names = rishonim.commentators.map { it.name }
            println("[Bereshit 1:1] Rishonim books in order: $names")

            // Must include the canonical Rishonim
            val mustContain = listOf("רש\"י", "רשב\"ם", "אבן עזרא", "רד\"ק", "רמב\"ן", "ספורנו")
            mustContain.forEach { needle ->
                val found = names.any { it.contains(needle) || it.contains(needle.replace('"', '״')) }
                assertTrue(found, "Rishonim group should contain a book matching '$needle' — got $names")
            }

            // Canonical chronological order: Rashi (1040) < Rashbam (1085) < Ibn Ezra (1089)
            // < Radak (1160) < Ramban (1194) < Sforno (1475).
            fun firstIndexMatching(needle: String): Int {
                val nrm = needle.replace('"', '״')
                return names.indexOfFirst { it.contains(needle) || it.contains(nrm) }
            }
            val ranks = mustContain.map { firstIndexMatching(it) }
            println("[Bereshit 1:1] Canonical Rishonim indices: ${mustContain.zip(ranks)}")
            for (i in 0 until ranks.size - 1) {
                assertTrue(
                    ranks[i] < ranks[i + 1],
                    "Expected '${mustContain[i]}' (idx ${ranks[i]}) before '${mustContain[i + 1]}' (idx ${ranks[i + 1]}). Got: $names",
                )
            }
        }

    @Test
    fun `Bereshit 1_1 — Targums collapsed into a single תרגומים group`() =
        runBlocking {
            skipIfNoDb()
            val uc = buildUseCase(BERESHIT_BOOK_ID)

            val groups = uc.getCommentatorGroupsForLines(listOf(BERESHIT_1_1_LINE_ID))
            val labels = groups.map { it.label }

            val targumGroups =
                groups.filter {
                    it.label == "תרגומים" || it.label.startsWith("תרגום ") || it.label.startsWith("תפסיר ")
                }
            println("[Bereshit 1:1] Targum-related groups: ${targumGroups.map { it.label }}")
            if (targumGroups.isEmpty()) {
                // No Targum is wired up as COMMENTARY for this line — acceptable.
                return@runBlocking
            }
            assertTrue(
                targumGroups.size == 1 && targumGroups.first().label == "תרגומים",
                "Targums should collapse into one 'תרגומים' group. Got: ${targumGroups.map { it.label }}",
            )
        }

    @Test
    fun `Bereshit 1_1 — Midrash subgroups merge under a single מדרש group`() =
        runBlocking {
            skipIfNoDb()
            val uc = buildUseCase(BERESHIT_BOOK_ID)

            val groups = uc.getCommentatorGroupsForLines(listOf(BERESHIT_1_1_LINE_ID))
            val midrashFragments =
                groups.filter {
                    it.label == "מדרש לקח טוב" ||
                        it.label == "מדרש רבה" ||
                        it.label == "בראשית רבה"
                }
            println("[Bereshit 1:1] Midrash fragmentary groups: ${midrashFragments.map { it.label }}")
            assertTrue(
                midrashFragments.isEmpty(),
                "Midrash books should not appear as individual labels — expected merge into 'מדרש'. Found: ${midrashFragments.map {
                    it.label
                }}",
            )
        }

    @Test
    fun `Bereshit 1_1 — group order respects editorial rank`() =
        runBlocking {
            skipIfNoDb()
            val uc = buildUseCase(BERESHIT_BOOK_ID)

            val groups = uc.getCommentatorGroupsForLines(listOf(BERESHIT_1_1_LINE_ID))
            val labels = groups.map { it.label }
            val rishonimIdx = labels.indexOf("ראשונים על התנ״ך")
            val acharonimIdx = labels.indexOf("אחרונים על התנ״ך")
            val midrashIdx = labels.indexOf("מדרש")
            val chasidutIdx = labels.indexOf("חסידות")
            val kabbalaIdx = labels.indexOf("קבלה")

            println(
                "[Bereshit 1:1] Rank indices — ראשונים=$rishonimIdx, אחרונים=$acharonimIdx, מדרש=$midrashIdx, חסידות=$chasidutIdx, קבלה=$kabbalaIdx",
            )

            if (rishonimIdx >= 0 && acharonimIdx >= 0) {
                assertTrue(rishonimIdx < acharonimIdx, "ראשונים should precede אחרונים. Got: $labels")
            }
            if (acharonimIdx >= 0 && midrashIdx >= 0) {
                assertTrue(acharonimIdx < midrashIdx, "אחרונים should precede מדרש. Got: $labels")
            }
            if (midrashIdx >= 0 && chasidutIdx >= 0) {
                assertTrue(midrashIdx < chasidutIdx, "מדרש should precede חסידות. Got: $labels")
            }
            if (chasidutIdx >= 0 && kabbalaIdx >= 0) {
                assertTrue(chasidutIdx < kabbalaIdx, "חסידות should precede קבלה. Got: $labels")
            }
        }

    @Test
    fun `Berakhot 2a — cross-corpus commentators excluded`() =
        runBlocking {
            skipIfNoDb()
            val uc = buildUseCase(BERAKHOT_BOOK_ID)
            val groups = uc.getCommentatorGroupsForLines(listOf(BERAKHOT_2A_LINE_ID))
            val labels = groups.map { it.label }
            println("[Berakhot 2a cross-corpus] Labels: $labels")
            // Tora Temima lives in `תנ״ך` and Beit Yosef lives in `הלכה`.
            // Their CSV COMMENTARY rows on Berakhot are demoted to RELATED at
            // generation time so the Talmud commentator panel stays clean.
            assertTrue(
                "אחרונים על התנ״ך" !in labels,
                "Talmud reader must not see Tanakh-anchored Acharonim group. Got: $labels",
            )
            assertTrue(
                labels.none {
                    it.startsWith("מפרשים על טור") ||
                        it.startsWith("מפרשים על שולחן ערוך") ||
                        it.startsWith("מפרשים על משנה תורה")
                },
                "Talmud reader must not see Halakha-anchored 'מפרשים על X' groups. Got: $labels",
            )
            // Sub-commentaries on Rif/Rosh must roll up to the Talmud Rishonim
            // bucket (resolveGroupLabel pass 1) — they should NOT keep the
            // intermediate "מפרשים על רי״ף" / "מפרשים על רא״ש" labels.
            assertTrue(
                labels.none { it.startsWith("מפרשים על רי") || it.startsWith("מפרשים על רא") },
                "Rif/Rosh sub-commentaries must roll up to Talmud Rishonim. Got: $labels",
            )
        }

    @Test
    fun `Berakhot 2a — Talmud Rishonim group exists and is non-empty`() =
        runBlocking {
            skipIfNoDb()
            val uc = buildUseCase(BERAKHOT_BOOK_ID)

            val groups = uc.getCommentatorGroupsForLines(listOf(BERAKHOT_2A_LINE_ID))
            val labels = groups.map { it.label }
            println("[Berakhot 2a] Group labels in order:")
            labels.forEachIndexed { i, l -> println("  ${i + 1}. $l (${groups[i].commentators.size} books)") }

            val rishonim =
                groups.firstOrNull { it.label == "ראשונים על התלמוד" }
                    ?: error("Missing 'ראשונים על התלמוד'. Got: $labels")
            assertTrue(rishonim.commentators.isNotEmpty(), "Rishonim group should not be empty")

            // Tosafot / Rashi / Rashba / Ritba / Ramban / Rosh — at least 3 must be present.
            val names = rishonim.commentators.map { it.name }
            val canon = listOf("רש\"י", "תוספות", "רא\"ש", "רשב\"א", "רמב\"ן", "ריטב\"א", "רי\"ף")
            val hits =
                canon.count { needle ->
                    val nrm = needle.replace('"', '״')
                    names.any { it.contains(needle) || it.contains(nrm) }
                }
            println("[Berakhot 2a] Canonical Rishonim matched: $hits/${canon.size} — names: $names")
            assertTrue(hits >= 3, "Expected at least 3 canonical Talmudic Rishonim, found $hits. Got: $names")
        }
}
