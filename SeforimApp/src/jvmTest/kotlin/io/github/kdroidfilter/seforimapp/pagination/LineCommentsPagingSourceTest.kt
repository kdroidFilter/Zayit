package io.github.kdroidfilter.seforimapp.pagination

import androidx.paging.PagingSource
import io.github.kdroidfilter.seforimapp.pagination.LineCommentsPagingSource
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
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

class LineCommentsPagingSourceTest {
    private val mockRepository = mockk<SeforimRepository>()

    private fun createCommentary(id: Long): CommentaryWithText = CommentaryWithText(
        commentaryId = id,
        bookId = 1L,
        bookName = "Test Book",
        lineId = 100L,
        text = "Commentary text $id",
        startWord = 0,
        endWord = 10,
        connectionType = ConnectionType.COMMENTARY,
    )

    @Test
    fun `load returns page with commentaries`() = runTest {
        val lineId = 100L
        val commentaries = listOf(
            createCommentary(1),
            createCommentary(2),
        )

        coEvery {
            mockRepository.getCommentariesForLineRange(
                lineIds = listOf(lineId),
                activeCommentatorIds = emptySet(),
                connectionTypes = setOf(ConnectionType.COMMENTARY),
                offset = 0,
                limit = any(),
            )
        } returns commentaries

        val pagingSource = LineCommentsPagingSource(mockRepository, lineId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Page<Int, CommentaryWithText>>(result)
        assertEquals(2, result.data.size)
    }

    @Test
    fun `load with commentatorIds filters results`() = runTest {
        val lineId = 100L
        val commentatorIds = setOf(1L, 2L)
        val commentaries = listOf(createCommentary(1))

        coEvery {
            mockRepository.getCommentariesForLineRange(
                lineIds = listOf(lineId),
                activeCommentatorIds = commentatorIds,
                connectionTypes = setOf(ConnectionType.COMMENTARY),
                offset = any(),
                limit = any(),
            )
        } returns commentaries

        val pagingSource = LineCommentsPagingSource(mockRepository, lineId, commentatorIds)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Page<Int, CommentaryWithText>>(result)
        coVerify {
            mockRepository.getCommentariesForLineRange(
                lineIds = listOf(lineId),
                activeCommentatorIds = commentatorIds,
                connectionTypes = any(),
                offset = any(),
                limit = any(),
            )
        }
    }

    @Test
    fun `load returns empty page when no commentaries`() = runTest {
        val lineId = 100L
        coEvery {
            mockRepository.getCommentariesForLineRange(any(), any(), any(), any(), any())
        } returns emptyList()

        val pagingSource = LineCommentsPagingSource(mockRepository, lineId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Page<Int, CommentaryWithText>>(result)
        assertTrue(result.data.isEmpty())
        assertNull(result.nextKey)
    }

    @Test
    fun `load returns error on exception`() = runTest {
        val lineId = 100L
        coEvery {
            mockRepository.getCommentariesForLineRange(any(), any(), any(), any(), any())
        } throws RuntimeException("Database error")

        val pagingSource = LineCommentsPagingSource(mockRepository, lineId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Error<Int, CommentaryWithText>>(result)
        assertEquals("Database error", result.throwable.message)
    }

    @Test
    fun `prevKey is null on first page`() = runTest {
        val lineId = 100L
        coEvery {
            mockRepository.getCommentariesForLineRange(any(), any(), any(), any(), any())
        } returns listOf(createCommentary(1))

        val pagingSource = LineCommentsPagingSource(mockRepository, lineId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Page<Int, CommentaryWithText>>(result)
        assertNull(result.prevKey)
    }

    @Test
    fun `nextKey is page plus 1 when data exists`() = runTest {
        val lineId = 100L
        coEvery {
            mockRepository.getCommentariesForLineRange(any(), any(), any(), any(), any())
        } returns listOf(createCommentary(1))

        val pagingSource = LineCommentsPagingSource(mockRepository, lineId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Page<Int, CommentaryWithText>>(result)
        assertEquals(1, result.nextKey)
    }

    @Test
    fun `second page has correct prevKey`() = runTest {
        val lineId = 100L
        coEvery {
            mockRepository.getCommentariesForLineRange(any(), any(), any(), any(), any())
        } returns listOf(createCommentary(1))

        val pagingSource = LineCommentsPagingSource(mockRepository, lineId)
        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = 1,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        assertIs<PagingSource.LoadResult.Page<Int, CommentaryWithText>>(result)
        assertEquals(0, result.prevKey)
    }

    @Test
    fun `getRefreshKey returns null when anchorPosition is null`() {
        val pagingSource = LineCommentsPagingSource(mockRepository, 100L)
        val state = mockk<androidx.paging.PagingState<Int, CommentaryWithText>>()
        coEvery { state.anchorPosition } returns null

        val key = pagingSource.getRefreshKey(state)
        assertNull(key)
    }

    @Test
    fun `offset is calculated correctly for pagination`() = runTest {
        val lineId = 100L
        coEvery {
            mockRepository.getCommentariesForLineRange(
                lineIds = any(),
                activeCommentatorIds = any(),
                connectionTypes = any(),
                offset = 20,
                limit = 10,
            )
        } returns emptyList()

        val pagingSource = LineCommentsPagingSource(mockRepository, lineId)
        pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = 2,
                loadSize = 10,
                placeholdersEnabled = false,
            ),
        )

        coVerify {
            mockRepository.getCommentariesForLineRange(
                lineIds = any(),
                activeCommentatorIds = any(),
                connectionTypes = any(),
                offset = 20,
                limit = 10,
            )
        }
    }
}
