package io.github.kdroidfilter.seforimapp.core.catalog

import io.github.kdroidfilter.seforimapp.catalog.CatalogPresets
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import io.github.kdroidfilter.seforimlibrary.dao.CatalogLoader
import org.junit.Assume
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies that [CatalogAccess] reproduces the display transformations the codegen
 * used to bake into PrecomputedCatalog.kt: Mishneh Torah and Shulchan Aruch
 * book-prefix filters, Talmud prefixing for Bavli/Yerushalmi, ancestor-label stripping.
 */
class CatalogAccessTest {
    private var catalog: PrecomputedCatalog? = null
    private var access: CatalogAccess? = null

    @BeforeTest
    fun setup() {
        for (basePath in POSSIBLE_BASE_PATHS) {
            val catalogCandidate = Path.of("$basePath/catalog.pb")
            val dbCandidate = Path.of("$basePath/seforim.db")
            if (Files.exists(catalogCandidate) && Files.exists(dbCandidate)) {
                catalog = CatalogLoader.loadCatalog(dbCandidate.toString())
                break
            }
        }
        catalog?.let { c -> access = CatalogAccess { c } }
    }

    private fun requireAccess(): CatalogAccess {
        Assume.assumeTrue(
            "E2E catalog fixture not available (SeforimLibrary/build/{catalog.pb,seforim.db})",
            access != null,
        )
        return access!!
    }

    @Test
    fun `categoryTitle prepends Talmud for Bavli`() {
        val ca = requireAccess()
        val title = ca.categoryTitle(CatalogPresets.Ids.Categories.BAVLI)
        assertNotNull(title, "BAVLI title must be available")
        assertTrue(
            title.startsWith("תלמוד") && title.contains("בבלי"),
            "BAVLI title expected to start with תלמוד and contain בבלי, got '$title'",
        )
    }

    @Test
    fun `categoryTitle prepends Talmud for Yerushalmi`() {
        val ca = requireAccess()
        val title = ca.categoryTitle(CatalogPresets.Ids.Categories.YERUSHALMI)
        assertNotNull(title)
        assertTrue(
            title.startsWith("תלמוד") && title.contains("ירושלמי"),
            "YERUSHALMI title expected to start with תלמוד and contain ירושלמי, got '$title'",
        )
    }

    @Test
    fun `booksFor Mishneh Torah excludes Mefarshim`() {
        val ca = requireAccess()
        val books = ca.booksFor(CatalogPresets.Ids.Categories.MISHNE_TORAH)
        assertFalse(
            books.any { it.title.trimStart().startsWith("מפרשים") },
            "Mishneh Torah books must not contain any 'מפרשים' entries",
        )
    }

    @Test
    fun `booksFor Shulchan Aruch excludes Hakdama and Pri Megadim`() {
        val ca = requireAccess()
        val books = ca.booksFor(CatalogPresets.Ids.Categories.SHULCHAN_ARUCH)
        assertFalse(
            books.any { it.title.trimStart().startsWith("הקדמה") },
            "Shulchan Aruch books must not contain any 'הקדמה' entries",
        )
        assertFalse(
            books.any { it.title.trimStart().startsWith("פרי מגדים") },
            "Shulchan Aruch books must not contain any 'פרי מגדים' entries",
        )
    }

    @Test
    fun `booksFor strips ancestor category label prefixes`() {
        val ca = requireAccess()
        // Pick any category that yields books; verify no displayed title starts with its own title.
        val torahId = CatalogPresets.Ids.Categories.TORAH
        val torahTitle = ca.categoryTitle(torahId) ?: return
        val books = ca.booksFor(torahId)
        if (books.isEmpty()) return
        assertFalse(
            books.any { it.title.startsWith("$torahTitle,") || it.title.startsWith("$torahTitle ") },
            "Torah books should not retain the 'תורה' label prefix in their display titles",
        )
    }

    @Test
    fun `bookTitle returns raw title for known book`() {
        val ca = requireAccess()
        val title = ca.bookTitle(CatalogPresets.Ids.Books.TUR)
        assertNotNull(title, "Tur book title must be available")
        assertEquals("טור", title.trim())
    }

    @Test
    fun `unknown category returns null and empty list`() {
        val ca = requireAccess()
        assertEquals(null, ca.categoryTitle(-1L))
        assertTrue(ca.booksFor(-1L).isEmpty())
    }

    private companion object {
        private val POSSIBLE_BASE_PATHS =
            listOf(
                "SeforimLibrary/build",
                "../SeforimLibrary/build",
            )
    }
}
