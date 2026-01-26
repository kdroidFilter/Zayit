package io.github.kdroidfilter.seforimapp.pagination

import io.github.kdroidfilter.seforimapp.pagination.PagingDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PagingDefaultsTest {
    // LINES tests
    @Test
    fun `LINES PAGE_SIZE is 10`() {
        assertEquals(10, PagingDefaults.LINES.PAGE_SIZE)
    }

    @Test
    fun `LINES PREFETCH_DISTANCE is 10`() {
        assertEquals(10, PagingDefaults.LINES.PREFETCH_DISTANCE)
    }

    @Test
    fun `LINES INITIAL_LOAD_SIZE is 30`() {
        assertEquals(30, PagingDefaults.LINES.INITIAL_LOAD_SIZE)
    }

    @Test
    fun `LINES config returns correct pageSize`() {
        val config = PagingDefaults.LINES.config()
        assertEquals(10, config.pageSize)
    }

    @Test
    fun `LINES config returns correct prefetchDistance`() {
        val config = PagingDefaults.LINES.config()
        assertEquals(10, config.prefetchDistance)
    }

    @Test
    fun `LINES config returns correct initialLoadSize`() {
        val config = PagingDefaults.LINES.config()
        assertEquals(30, config.initialLoadSize)
    }

    @Test
    fun `LINES config placeholders disabled by default`() {
        val config = PagingDefaults.LINES.config()
        assertFalse(config.enablePlaceholders)
    }

    @Test
    fun `LINES config with placeholders enabled`() {
        val config = PagingDefaults.LINES.config(placeholders = true)
        assertTrue(config.enablePlaceholders)
    }

    // COMMENTS tests
    @Test
    fun `COMMENTS PAGE_SIZE is 10`() {
        assertEquals(10, PagingDefaults.COMMENTS.PAGE_SIZE)
    }

    @Test
    fun `COMMENTS PREFETCH_DISTANCE is 5`() {
        assertEquals(5, PagingDefaults.COMMENTS.PREFETCH_DISTANCE)
    }

    @Test
    fun `COMMENTS INITIAL_LOAD_SIZE is 10`() {
        assertEquals(10, PagingDefaults.COMMENTS.INITIAL_LOAD_SIZE)
    }

    @Test
    fun `COMMENTS config returns correct pageSize`() {
        val config = PagingDefaults.COMMENTS.config()
        assertEquals(10, config.pageSize)
    }

    @Test
    fun `COMMENTS config returns correct prefetchDistance`() {
        val config = PagingDefaults.COMMENTS.config()
        assertEquals(5, config.prefetchDistance)
    }

    @Test
    fun `COMMENTS config returns correct initialLoadSize`() {
        val config = PagingDefaults.COMMENTS.config()
        assertEquals(10, config.initialLoadSize)
    }

    @Test
    fun `COMMENTS config placeholders disabled by default`() {
        val config = PagingDefaults.COMMENTS.config()
        assertFalse(config.enablePlaceholders)
    }

    @Test
    fun `COMMENTS config with placeholders enabled`() {
        val config = PagingDefaults.COMMENTS.config(placeholders = true)
        assertTrue(config.enablePlaceholders)
    }

    // Comparison tests
    @Test
    fun `LINES and COMMENTS have same PAGE_SIZE`() {
        assertEquals(PagingDefaults.LINES.PAGE_SIZE, PagingDefaults.COMMENTS.PAGE_SIZE)
    }

    @Test
    fun `LINES has larger PREFETCH_DISTANCE than COMMENTS`() {
        assertTrue(PagingDefaults.LINES.PREFETCH_DISTANCE > PagingDefaults.COMMENTS.PREFETCH_DISTANCE)
    }

    @Test
    fun `LINES has larger INITIAL_LOAD_SIZE than COMMENTS`() {
        assertTrue(PagingDefaults.LINES.INITIAL_LOAD_SIZE > PagingDefaults.COMMENTS.INITIAL_LOAD_SIZE)
    }
}
