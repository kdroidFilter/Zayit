package io.github.kdroidfilter.seforimapp.pagination

import androidx.paging.PagingSource
import io.github.kdroidfilter.seforimapp.pagination.LinesPagingSource
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinesPagingSourceTest {
    private val mockRepository = mockk<SeforimRepository>()

    private fun createLine(id: Long, bookId: Long, lineIndex: Int): Line = Line(
        id = id,
        bookId = bookId,
        lineIndex = lineIndex,
        text = "Test line $id",
        title = null,
        level = 0,
    )

    @Test
    fun `load returns page with data`() = runTest {
        val bookId = 1L
        val lines = listOf(
            createLine(1, bookId, 0),
            createLine(2, bookId, 1),
            createLine(3, bookId, 2),
        )

        coEvery { mockRepository.getLines(bookId, any(), any()) } returns lines

        val pagingSource = LinesPagingSource(mockRepository, bookId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Page<Int, Line>>(result)
        assertEquals(3, result.data.size)
        assertEquals(1L, result.data[0].id)
    }

    @Test
    fun `load returns empty page when no data`() = runTest {
        val bookId = 1L
        coEvery { mockRepository.getLines(bookId, any(), any()) } returns emptyList()

        val pagingSource = LinesPagingSource(mockRepository, bookId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Page<Int, Line>>(result)
        assertTrue(result.data.isEmpty())
        assertNull(result.nextKey)
    }

    @Test
    fun `load returns error on exception`() = runTest {
        val bookId = 1L
        coEvery { mockRepository.getLines(bookId, any(), any()) } throws RuntimeException("Database error")

        val pagingSource = LinesPagingSource(mockRepository, bookId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Error<Int, Line>>(result)
        assertEquals("Database error", result.throwable.message)
    }

    @Test
    fun `load with initialLineId centers around target line`() = runTest {
        val bookId = 1L
        val targetLineId = 100L
        val targetLine = createLine(targetLineId, bookId, 50)

        coEvery { mockRepository.getLine(targetLineId) } returns targetLine
        coEvery { mockRepository.getLines(bookId, any(), any()) } returns listOf(targetLine)

        val pagingSource = LinesPagingSource(mockRepository, bookId, initialLineId = targetLineId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 30,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Page<Int, Line>>(result)
        coVerify { mockRepository.getLine(targetLineId) }
    }

    @Test
    fun `getRefreshKey returns null when anchorPosition is null`() {
        val pagingSource = LinesPagingSource(mockRepository, 1L)
        val state = mockk<androidx.paging.PagingState<Int, Line>>()
        coEvery { state.anchorPosition } returns null

        val key = pagingSource.getRefreshKey(state)
        assertNull(key)
    }

    @Test
    fun `keyReuseSupported is true`() {
        val pagingSource = LinesPagingSource(mockRepository, 1L)
        assertTrue(pagingSource.keyReuseSupported)
    }

    @Test
    fun `append load calculates correct start index`() = runTest {
        val bookId = 1L
        val initialLines = (0..9).map { createLine(it.toLong(), bookId, it) }
        val appendLines = (10..19).map { createLine(it.toLong(), bookId, it) }

        coEvery { mockRepository.getLines(bookId, 0, 30) } returns initialLines
        coEvery { mockRepository.getLines(bookId, 10, any()) } returns appendLines

        val pagingSource = LinesPagingSource(mockRepository, bookId)

        // First load (refresh)
        pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 30,
                placeholdersEnabled = false,
            ),
        )

        // Append load
        val result = pagingSource.load(
            PagingSource.LoadParams.Append(
                key = 1,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Page<Int, Line>>(result)
    }

    @Test
    fun `prevKey is null when at start`() = runTest {
        val bookId = 1L
        val lines = listOf(createLine(1, bookId, 0))
        coEvery { mockRepository.getLines(bookId, any(), any()) } returns lines

        val pagingSource = LinesPagingSource(mockRepository, bookId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Page<Int, Line>>(result)
        assertNull(result.prevKey)
    }

    @Test
    fun `nextKey is null when data is less than load size`() = runTest {
        val bookId = 1L
        val lines = listOf(createLine(1, bookId, 0))
        coEvery { mockRepository.getLines(bookId, any(), any()) } returns lines

        val pagingSource = LinesPagingSource(mockRepository, bookId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Page<Int, Line>>(result)
        assertNull(result.nextKey)
    }
}
