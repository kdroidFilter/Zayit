package io.github.kdroidfilter.seforimapp.pagination

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.kdroidfilter.seforimapp.logger.debugln
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.LineSelectionRepository

class LinesPagingSource(
    private val repository: LineSelectionRepository,
    private val bookId: Long,
    private val initialLineId: Long? = null, // For scrolling to a specific line
) : PagingSource<Int, Line>() {
    // Keep track of loaded line indices to avoid duplicates
    private val loadedLineIndices = mutableSetOf<Int>()

    override fun getRefreshKey(state: PagingState<Int, Line>): Int? {
        // Try to find the page key of the closest page to anchorPosition
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Line> {
        return try {
            val pageNumber = params.key ?: 0

            debugln { "[LinesPagingSource] Loading page $pageNumber, type: ${params.javaClass.simpleName}" }

            // Determine the range to load based on the load type
            val (startIndex, loadSize) =
                when (params) {
                    is LoadParams.Refresh -> {
                        // Clear loaded indices on refresh
                        loadedLineIndices.clear()

                        if (initialLineId != null && pageNumber == 0) {
                            // Center around the initial line
                            val targetLine = repository.getLine(initialLineId)
                            if (targetLine != null && targetLine.bookId == bookId) {
                                val targetIndex = targetLine.lineIndex
                                val start = maxOf(0, targetIndex - PagingDefaults.LINES.INITIAL_LOAD_SIZE / 2)
                                debugln { "[LinesPagingSource] Centering on line $initialLineId at index $targetIndex" }
                                Pair(start, PagingDefaults.LINES.INITIAL_LOAD_SIZE)
                            } else {
                                Pair(0, PagingDefaults.LINES.INITIAL_LOAD_SIZE)
                            }
                        } else {
                            Pair(pageNumber * PagingDefaults.LINES.PAGE_SIZE, PagingDefaults.LINES.INITIAL_LOAD_SIZE)
                        }
                    }

                    is LoadParams.Append -> {
                        // Calculate the next start index based on what we've already loaded
                        if (loadedLineIndices.isNotEmpty()) {
                            val lastLoadedIndex = loadedLineIndices.maxOrNull() ?: 0
                            // Start from the next index after the last loaded one
                            Pair(lastLoadedIndex + 1, PagingDefaults.LINES.PAGE_SIZE)
                        } else {
                            // Fallback calculation
                            Pair(pageNumber * PagingDefaults.LINES.PAGE_SIZE, PagingDefaults.LINES.PAGE_SIZE)
                        }
                    }

                    is LoadParams.Prepend -> {
                        // Calculate the previous start index
                        if (loadedLineIndices.isNotEmpty()) {
                            val firstLoadedIndex = loadedLineIndices.minOrNull() ?: PagingDefaults.LINES.PAGE_SIZE
                            val start = maxOf(0, firstLoadedIndex - PagingDefaults.LINES.PAGE_SIZE)
                            Pair(start, minOf(PagingDefaults.LINES.PAGE_SIZE, firstLoadedIndex))
                        } else {
                            // No prepend if we don't have loaded data
                            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
                        }
                    }
                }

            val endIndex = startIndex + loadSize

            debugln { "[LinesPagingSource] Loading lines from index $startIndex to $endIndex (count: $loadSize)" }

            // Load the lines from the repository
            val allLines = repository.getLines(bookId, startIndex, endIndex)

            // Filter out any lines that we've already loaded (to avoid duplicates)
            val newLines =
                allLines.filter { line ->
                    val lineIndex = line.lineIndex
                    if (lineIndex in loadedLineIndices) {
                        debugln { "[LinesPagingSource] Skipping duplicate line at index $lineIndex" }
                        false
                    } else {
                        loadedLineIndices.add(lineIndex)
                        true
                    }
                }

            debugln { "[LinesPagingSource] Loaded ${newLines.size} new lines (filtered from ${allLines.size})" }
            if (newLines.isNotEmpty()) {
                debugln { "[LinesPagingSource] Range: index ${newLines.first().lineIndex} to ${newLines.last().lineIndex}" }
            }

            // Calculate keys for pagination
            val prevKey =
                when {
                    startIndex <= 0 -> null
                    params is LoadParams.Refresh && initialLineId != null -> -1 // Special marker
                    else -> pageNumber - 1
                }

            val nextKey =
                when {
                    newLines.isEmpty() -> null
                    allLines.size < loadSize -> null // We've reached the end
                    params is LoadParams.Refresh && pageNumber == 0 -> 1
                    else -> pageNumber + 1
                }

            debugln { "[LinesPagingSource] Returning page with prevKey=$prevKey, nextKey=$nextKey" }

            LoadResult.Page(
                data = newLines,
                prevKey = prevKey,
                nextKey = nextKey,
            )
        } catch (e: Exception) {
            debugln { "[LinesPagingSource] Error loading page: ${e.message}" }
            e.printStackTrace()
            LoadResult.Error(e)
        }
    }

    override val keyReuseSupported: Boolean = true
}
