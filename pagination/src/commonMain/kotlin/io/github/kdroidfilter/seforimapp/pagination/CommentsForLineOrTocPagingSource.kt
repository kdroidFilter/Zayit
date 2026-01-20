package io.github.kdroidfilter.seforimapp.pagination

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlin.math.max
import kotlin.math.min

/**
 * PagingSource that loads commentaries for a single line, or if the line is a TOC heading,
 * for all lines that belong to that TOC entry (section).
 */
class CommentsForLineOrTocPagingSource(
    private val repository: SeforimRepository,
    private val baseLineId: Long,
    private val commentatorIds: Set<Long> = emptySet(),
) : PagingSource<Int, CommentaryWithText>() {
    // Resolved set of lineIds to fetch (single line or section lines). Lazy initialized.
    private var resolvedLineIds: List<Long>? = null

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
            val maxBatchSize = max(64, limit)

            if (resolvedLineIds == null) {
                val headingToc = repository.getHeadingTocEntryByLineId(baseLineId)
                resolvedLineIds =
                    if (headingToc != null) {
                        val lines = repository.getLineIdsForTocEntry(headingToc.id).filter { it != baseLineId }
                        val idx = lines.indexOf(baseLineId)
                        if (idx >= 0) {
                            val half = maxBatchSize / 2
                            val start = max(0, idx - half)
                            val end = min(lines.size, start + maxBatchSize)
                            lines.subList(start, end)
                        } else {
                            lines.take(maxBatchSize)
                        }
                    } else {
                        listOf(baseLineId)
                    }
            }

            val ids = resolvedLineIds ?: listOf(baseLineId)

            val commentaries =
                repository.getCommentariesForLineRange(
                    lineIds = ids,
                    activeCommentatorIds = commentatorIds,
                    connectionTypes = setOf(ConnectionType.COMMENTARY),
                    offset = offset,
                    limit = limit,
                )

            val prevKey = if (page == 0) null else page - 1
            val nextKey = if (commentaries.isEmpty()) null else page + 1

            LoadResult.Page(
                data = commentaries,
                prevKey = prevKey,
                nextKey = nextKey,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
}
