package io.github.kdroidfilter.seforimapp.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CatalogPresetsTest {
    // BookRef
    @Test
    fun `BookRef stores id and title`() {
        val bookRef = BookRef(1L, "בראשית")
        assertEquals(1L, bookRef.id)
        assertEquals("בראשית", bookRef.title)
    }

    @Test
    fun `BookRef equality and copy`() {
        val original = BookRef(1L, "בראשית")
        assertEquals(BookRef(1L, "בראשית"), original)
        assertEquals(BookRef(1L, "שמות"), original.copy(title = "שמות"))
    }

    // TocQuickLink
    @Test
    fun `TocQuickLink stores all fields`() {
        val link = TocQuickLink("אורח חיים", 30_149L, 252_607L)
        assertEquals("אורח חיים", link.label)
        assertEquals(30_149L, link.tocEntryId)
        assertEquals(252_607L, link.firstLineId)
    }

    @Test
    fun `TocQuickLink allows null firstLineId`() {
        val link = TocQuickLink("Label", 100L, null)
        assertEquals(null, link.firstLineId)
    }

    // DropdownSpec sealed hierarchy
    @Test
    fun `CategoryDropdownSpec implements DropdownSpec`() {
        val spec: DropdownSpec = CategoryDropdownSpec(62L)
        assertIs<CategoryDropdownSpec>(spec)
        assertEquals(62L, spec.categoryId)
    }

    @Test
    fun `MultiCategoryDropdownSpec implements DropdownSpec`() {
        val spec: DropdownSpec = MultiCategoryDropdownSpec(1L, listOf(2L, 3L, 4L))
        assertIs<MultiCategoryDropdownSpec>(spec)
        assertEquals(1L, spec.labelCategoryId)
        assertEquals(listOf(2L, 3L, 4L), spec.bookCategoryIds)
    }

    @Test
    fun `TocQuickLinksSpec embeds links inline`() {
        val link = TocQuickLink("אורח חיים", 30_149L, 252_607L)
        val spec: DropdownSpec = TocQuickLinksSpec(380L, listOf(link))
        assertIs<TocQuickLinksSpec>(spec)
        assertEquals(380L, spec.bookId)
        assertEquals(listOf(link), spec.links)
    }

    // Ids — sanity check on the codegen output. Asserts presence + non-collision,
    // not exact values (those track upstream catalog evolution).
    @Test
    fun `Ids Categories core constants are set`() {
        assertTrue(CatalogPresets.Ids.Categories.TANAKH > 0L)
        assertTrue(CatalogPresets.Ids.Categories.TORAH > 0L)
        assertTrue(CatalogPresets.Ids.Categories.MISHNA > 0L)
        assertTrue(CatalogPresets.Ids.Categories.BAVLI > 0L)
        assertTrue(CatalogPresets.Ids.Categories.YERUSHALMI > 0L)
        assertTrue(CatalogPresets.Ids.Categories.MISHNE_TORAH > 0L)
        assertTrue(CatalogPresets.Ids.Categories.SHULCHAN_ARUCH > 0L)
        assertTrue(CatalogPresets.Ids.Categories.TUR > 0L)
        // Talmud roots distinct from their parents
        assertTrue(CatalogPresets.Ids.Categories.BAVLI != CatalogPresets.Ids.Categories.YERUSHALMI)
    }

    @Test
    fun `Ids Books TUR is set`() {
        assertTrue(CatalogPresets.Ids.Books.TUR > 0L)
    }

    @Test
    fun `Ids TocTexts has all four Tur sections`() {
        val ids =
            setOf(
                CatalogPresets.Ids.TocTexts.ORACH_CHAIM,
                CatalogPresets.Ids.TocTexts.YOREH_DEAH,
                CatalogPresets.Ids.TocTexts.EVEN_HAEZER,
                CatalogPresets.Ids.TocTexts.CHOSHEN_MISHPAT,
            )
        assertEquals(4, ids.size, "TOC text IDs must be distinct")
        assertTrue(ids.all { it > 0L })
    }

    // Dropdowns
    @Test
    fun `Dropdowns HOME contains all main sections`() {
        assertEquals(6, CatalogPresets.Dropdowns.HOME.size)
    }

    @Test
    fun `Dropdowns TANAKH is MultiCategoryDropdownSpec with three orders`() {
        val tanakh = CatalogPresets.Dropdowns.TANAKH
        assertIs<MultiCategoryDropdownSpec>(tanakh)
        assertEquals(CatalogPresets.Ids.Categories.TANAKH, tanakh.labelCategoryId)
        assertEquals(
            listOf(
                CatalogPresets.Ids.Categories.TORAH,
                CatalogPresets.Ids.Categories.NEVIIM,
                CatalogPresets.Ids.Categories.KETUVIM,
            ),
            tanakh.bookCategoryIds,
        )
    }

    @Test
    fun `Dropdowns individual category specs are CategoryDropdownSpec`() {
        assertIs<CategoryDropdownSpec>(CatalogPresets.Dropdowns.TORAH)
        assertIs<CategoryDropdownSpec>(CatalogPresets.Dropdowns.NEVIIM)
        assertIs<CategoryDropdownSpec>(CatalogPresets.Dropdowns.KETUVIM)
        assertIs<CategoryDropdownSpec>(CatalogPresets.Dropdowns.SHULCHAN_ARUCH)
    }

    @Test
    fun `Dropdowns TUR_QUICK_LINKS embeds four links`() {
        val turLinks = CatalogPresets.Dropdowns.TUR_QUICK_LINKS
        assertIs<TocQuickLinksSpec>(turLinks)
        assertEquals(CatalogPresets.Ids.Books.TUR, turLinks.bookId)
        assertEquals(4, turLinks.links.size)
        assertEquals(
            listOf("אורח חיים", "יורה דעה", "אבן העזר", "חושן משפט"),
            turLinks.links.map { it.label },
        )
        // firstLineId must be populated for navigation to work
        assertTrue(turLinks.links.all { it.firstLineId != null })
    }

    @Test
    fun `Dropdowns BAVLI lists six orders`() {
        val bavli = CatalogPresets.Dropdowns.BAVLI
        assertIs<MultiCategoryDropdownSpec>(bavli)
        assertEquals(6, bavli.bookCategoryIds.size)
    }

    @Test
    fun `Dropdowns MISHNE_TORAH lists multiple children`() {
        val mt = CatalogPresets.Dropdowns.MISHNE_TORAH
        assertIs<MultiCategoryDropdownSpec>(mt)
        assertEquals(CatalogPresets.Ids.Categories.MISHNE_TORAH, mt.labelCategoryId)
        assertTrue(mt.bookCategoryIds.isNotEmpty())
    }
}
