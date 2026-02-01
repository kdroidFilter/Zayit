package io.github.kdroidfilter.seforimapp.core.presentation.typography

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FontCatalogTest {
    @Test
    fun `FontOption stores code and label`() {
        val option = FontCatalog.options.first()
        assertNotNull(option.code)
        assertNotNull(option.label)
    }

    @Test
    fun `FontCatalog has expected number of options`() {
        assertEquals(28, FontCatalog.options.size)
    }

    @Test
    fun `FontCatalog contains notoserifhebrew`() {
        val codes = FontCatalog.options.map { it.code }
        assertTrue("notoserifhebrew" in codes)
    }

    @Test
    fun `FontCatalog contains frankruhllibre`() {
        val codes = FontCatalog.options.map { it.code }
        assertTrue("frankruhllibre" in codes)
    }

    @Test
    fun `FontCatalog contains taameyashkenaz`() {
        val codes = FontCatalog.options.map { it.code }
        assertTrue("taameyashkenaz" in codes)
    }

    @Test
    fun `FontCatalog contains tinos`() {
        val codes = FontCatalog.options.map { it.code }
        assertTrue("tinos" in codes)
    }

    @Test
    fun `all font codes are unique`() {
        val codes = FontCatalog.options.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `all font codes are non-empty`() {
        FontCatalog.options.forEach { option ->
            assertTrue(option.code.isNotEmpty(), "Font code should not be empty")
        }
    }

    @Test
    fun `FontOption equals works correctly`() {
        val option1 = FontCatalog.options[0]
        val option2 = FontCatalog.options[0]
        val option3 = FontCatalog.options[1]

        assertEquals(option1, option2)
        assertTrue(option1 != option3)
    }

    @Test
    fun `FontCatalog options list is not empty`() {
        assertTrue(FontCatalog.options.isNotEmpty())
    }
}
