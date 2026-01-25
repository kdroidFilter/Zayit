package io.github.kdroidfilter.seforimapp.pagination

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/**
 * Paging source for non-commentary links (TARGUM/SOURCE) attached to multiple lines.
 * Used for multi-line selection (Ctrl+Click).
 * Optionally filters by a set of target book IDs ("sources").
 */
class MultiLineTargumPagingSource(
    private val repository: SeforimRepository,
    private val lineIds: List<Long>,
    private val sourceBookIds: Set<Long> = emptySet(),
    private val connectionTypes: Set<ConnectionType> = setOf(ConnectionType.TARGUM),
) : PagingSource<Int, CommentaryWithText>() {
    override fun getRefreshKey(state: PagingState<Int, CommentaryWithText>): Int? =
        state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CommentaryWithText> =
        try {
            val page = params.key ?: 0
            val limit = params.loadSize
            val offset = page * limit

            val links =
                repository.getCommentariesForLineRange(
                    lineIds = lineIds,
                    activeCommentatorIds = sourceBookIds,
                    connectionTypes = connectionTypes,
                    offset = offset,
                    limit = limit,
                )

            val prevKey = if (page == 0) null else page - 1
            val nextKey = if (links.isEmpty()) null else page + 1

            LoadResult.Page(
                data = links,
                prevKey = prevKey,
                nextKey = nextKey,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
