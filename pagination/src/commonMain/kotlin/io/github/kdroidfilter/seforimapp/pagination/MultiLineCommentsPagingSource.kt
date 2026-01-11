package io.github.kdroidfilter.seforimapp.pagination

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

class MultiLineCommentsPagingSource(
    private val repository: SeforimRepository,
    private val lineIds: List<Long>,
    private val commentatorIds: Set<Long> = emptySet()
) : PagingSource<Int, CommentaryWithText>() {

    override fun getRefreshKey(state: PagingState<Int, CommentaryWithText>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CommentaryWithText> {
        return try {
            val page = params.key ?: 0
            val limit = params.loadSize
            val offset = page * limit

            val commentaries = repository.getCommentariesForLineRange(
                lineIds = lineIds,
                activeCommentatorIds = commentatorIds,
                connectionTypes = setOf(io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType.COMMENTARY),
                offset = offset,
                limit = limit
            )

            val prevKey = if (page == 0) null else page - 1
            val nextKey = if (commentaries.isEmpty()) null else page + 1

            LoadResult.Page(
                data = commentaries,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
