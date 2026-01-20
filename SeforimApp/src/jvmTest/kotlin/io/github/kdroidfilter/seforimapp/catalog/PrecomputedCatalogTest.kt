package io.github.kdroidfilter.seforimapp.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PrecomputedCatalogTest {
    // BookRef tests
    @Test
    fun `BookRef data class stores id and title`() {
        val bookRef = BookRef(1L, "בראשית")
        assertEquals(1L, bookRef.id)
        assertEquals("בראשית", bookRef.title)
    }

    @Test
    fun `BookRef equality works correctly`() {
        val bookRef1 = BookRef(1L, "בראשית")
        val bookRef2 = BookRef(1L, "בראשית")
        assertEquals(bookRef1, bookRef2)
    }

    @Test
    fun `BookRef copy works correctly`() {
        val original = BookRef(1L, "בראשית")
        val copied = original.copy(title = "שמות")
        assertEquals(1L, copied.id)
        assertEquals("שמות", copied.title)
    }

    // TocQuickLink tests
    @Test
    fun `TocQuickLink data class stores all fields`() {
        val link = TocQuickLink("אורח חיים", 30_015L, 252_674L)
        assertEquals("אורח חיים", link.label)
        assertEquals(30_015L, link.tocEntryId)
        assertEquals(252_674L, link.firstLineId)
    }

    @Test
    fun `TocQuickLink allows null firstLineId`() {
        val link = TocQuickLink("Label", 100L, null)
        assertEquals(null, link.firstLineId)
    }

    // DropdownSpec sealed interface tests
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
    fun `TocQuickLinksSpec implements DropdownSpec`() {
        val spec: DropdownSpec = TocQuickLinksSpec(381L, listOf(3_768L, 4_411L))
        assertIs<TocQuickLinksSpec>(spec)
        assertEquals(381L, spec.bookId)
        assertEquals(listOf(3_768L, 4_411L), spec.tocTextIds)
    }

    // PrecomputedCatalog.BOOK_TITLES tests
    @Test
    fun `BOOK_TITLES contains expected books`() {
        assertTrue(PrecomputedCatalog.BOOK_TITLES.isNotEmpty())
        assertEquals("בראשית", PrecomputedCatalog.BOOK_TITLES[1L])
        assertEquals("שמות", PrecomputedCatalog.BOOK_TITLES[2L])
        assertEquals("ויקרא", PrecomputedCatalog.BOOK_TITLES[3L])
    }

    @Test
    fun `BOOK_TITLES contains Talmud tractates`() {
        assertEquals("ברכות", PrecomputedCatalog.BOOK_TITLES[103L])
        assertEquals("שבת", PrecomputedCatalog.BOOK_TITLES[104L])
    }

    // PrecomputedCatalog.CATEGORY_TITLES tests
    @Test
    fun `CATEGORY_TITLES contains expected categories`() {
        assertTrue(PrecomputedCatalog.CATEGORY_TITLES.isNotEmpty())
        assertEquals("תנ״ך", PrecomputedCatalog.CATEGORY_TITLES[1L])
        assertEquals("תורה", PrecomputedCatalog.CATEGORY_TITLES[2L])
        assertEquals("משנה", PrecomputedCatalog.CATEGORY_TITLES[5L])
    }

    // PrecomputedCatalog.CATEGORY_BOOKS tests
    @Test
    fun `CATEGORY_BOOKS contains Torah books`() {
        val torahBooks = PrecomputedCatalog.CATEGORY_BOOKS[2L]
        assertNotNull(torahBooks)
        assertEquals(5, torahBooks.size)
        assertEquals("בראשית", torahBooks[0].title)
        assertEquals("דברים", torahBooks[4].title)
    }

    @Test
    fun `CATEGORY_BOOKS for Tanakh parent is empty`() {
        val tanakhBooks = PrecomputedCatalog.CATEGORY_BOOKS[1L]
        assertNotNull(tanakhBooks)
        assertTrue(tanakhBooks.isEmpty())
    }

    // PrecomputedCatalog.TOC_BY_TOC_TEXT_ID tests
    @Test
    fun `TOC_BY_TOC_TEXT_ID contains Tur quick links`() {
        val turToc = PrecomputedCatalog.TOC_BY_TOC_TEXT_ID[381L]
        assertNotNull(turToc)
        assertTrue(turToc.isNotEmpty())

        val orachChaim = turToc[3_768L]
        assertNotNull(orachChaim)
        assertEquals("אורח חיים", orachChaim.label)
    }

    // PrecomputedCatalog.Ids tests
    @Test
    fun `Ids Categories constants are correct`() {
        assertEquals(1L, PrecomputedCatalog.Ids.Categories.TANAKH)
        assertEquals(2L, PrecomputedCatalog.Ids.Categories.TORAH)
        assertEquals(5L, PrecomputedCatalog.Ids.Categories.MISHNA)
        assertEquals(13L, PrecomputedCatalog.Ids.Categories.BAVLI)
        assertEquals(20L, PrecomputedCatalog.Ids.Categories.YERUSHALMI)
        assertEquals(45L, PrecomputedCatalog.Ids.Categories.MISHNE_TORAH)
        assertEquals(62L, PrecomputedCatalog.Ids.Categories.SHULCHAN_ARUCH)
    }

    @Test
    fun `Ids Books constants are correct`() {
        assertEquals(381L, PrecomputedCatalog.Ids.Books.TUR)
    }

    @Test
    fun `Ids TocTexts constants are correct`() {
        assertEquals(3_768L, PrecomputedCatalog.Ids.TocTexts.ORACH_CHAIM)
        assertEquals(4_411L, PrecomputedCatalog.Ids.TocTexts.YOREH_DEAH)
        assertEquals(4_412L, PrecomputedCatalog.Ids.TocTexts.EVEN_HAEZER)
        assertEquals(4_413L, PrecomputedCatalog.Ids.TocTexts.CHOSHEN_MISHPAT)
    }

    // PrecomputedCatalog.Dropdowns tests
    @Test
    fun `Dropdowns HOME contains all main sections`() {
        val homeDropdowns = PrecomputedCatalog.Dropdowns.HOME
        assertEquals(6, homeDropdowns.size)
    }

    @Test
    fun `Dropdowns TANAKH is MultiCategoryDropdownSpec`() {
        val tanakh = PrecomputedCatalog.Dropdowns.TANAKH
        assertIs<MultiCategoryDropdownSpec>(tanakh)
        assertEquals(1L, tanakh.labelCategoryId)
        assertEquals(listOf(2L, 3L, 4L), tanakh.bookCategoryIds)
    }

    @Test
    fun `Dropdowns individual category specs are correct`() {
        assertIs<CategoryDropdownSpec>(PrecomputedCatalog.Dropdowns.TORAH)
        assertIs<CategoryDropdownSpec>(PrecomputedCatalog.Dropdowns.NEVIIM)
        assertIs<CategoryDropdownSpec>(PrecomputedCatalog.Dropdowns.KETUVIM)
        assertIs<CategoryDropdownSpec>(PrecomputedCatalog.Dropdowns.SHULCHAN_ARUCH)
    }

    @Test
    fun `Dropdowns TUR_QUICK_LINKS is TocQuickLinksSpec`() {
        val turLinks = PrecomputedCatalog.Dropdowns.TUR_QUICK_LINKS
        assertIs<TocQuickLinksSpec>(turLinks)
        assertEquals(381L, turLinks.bookId)
        assertEquals(4, turLinks.tocTextIds.size)
    }
}
